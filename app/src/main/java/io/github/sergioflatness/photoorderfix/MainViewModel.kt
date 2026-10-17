package io.github.sergioflatness.photoorderfix

import android.app.Application
import android.media.MediaScannerConnection
import android.content.IntentSender
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sergioflatness.photoorderfix.core.DateSource
import io.github.sergioflatness.photoorderfix.core.PlanItem
import io.github.sergioflatness.photoorderfix.core.ReorderPlan
import io.github.sergioflatness.photoorderfix.core.ReorderPlanner
import io.github.sergioflatness.photoorderfix.core.TakeoutDates
import io.github.sergioflatness.photoorderfix.core.TargetFolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ScanSummary(
    val totalItems: Int,
    val toResave: Int,
    val resaveBytes: Long,
    /** Re-saved items that land in a new folder (e.g. iPhoneData/ moves under DCIM/). */
    val movedToNewFolder: Int,
    /** Copies made earlier whose Gallery date is wrong (videos re-saved by v0.2.0 and older). */
    val datesToRepair: Int,
    /** Files listed more than once in Android's media list, to diagnose duplicates in the Gallery. */
    val duplicateGroups: Int,
    val duplicateExamples: List<String>,
    val skipped: Int,
    /** Where the skipped items live, biggest folders first, so we can see why they were skipped. */
    val skippedFolders: List<Pair<String, Int>>,
    val fromCaptureDate: Int,
    val fromFileName: Int,
    val fromModifiedTime: Int,
    val freeBytes: Long,
    val partialAccess: Boolean,
)

sealed interface UiState {
    data object Intro : UiState
    data object Scanning : UiState
    data class Ready(val summary: ScanSummary) : UiState
    /** [currentMs] is the date of the item being re-saved, to show which period the work has reached. */
    data class Working(val done: Int, val total: Int, val currentMs: Long? = null) : UiState
    data class Finished(val moved: Int, val failed: Int, val cancelled: Boolean) : UiState
    data class DatesRepaired(val fixed: Int, val failed: Int) : UiState
    data class TakeoutImported(
        val found: Int,
        val fixed: Int,
        val failed: Int,
        val alreadyEarlier: Int,
        val ambiguous: Int,
        val notOnPhone: Int,
    ) : UiState
    /** The user picked photos to give a date of their own choosing. */
    data class PickDate(val names: List<String>, val count: Int, val initial: String) : UiState
    data class DatesSet(val fixed: Int, val failed: Int) : UiState
    data class Failed(val message: String) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val resaver = Resaver(resolver)
    private val manualFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val _state = MutableStateFlow<UiState>(UiState.Intro)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** When set, the screen must launch this system dialog ("Allow deleting N items?"). */
    private val _consentRequest = MutableStateFlow<IntentSender?>(null)
    val consentRequest: StateFlow<IntentSender?> = _consentRequest.asStateFlow()

    private val _canManageMedia = MutableStateFlow(checkManageMedia())
    val canManageMedia: StateFlow<Boolean> = _canManageMedia.asStateFlow()

    private var plan: ReorderPlan<DeviceMedia>? = null
    private var repairs: List<PlanItem<DeviceMedia>> = emptyList()
    private var scanned: List<PlanItem<DeviceMedia>> = emptyList()
    private var picked: List<PlanItem<DeviceMedia>> = emptyList()
    private var pendingConsent: CompletableDeferred<Boolean>? = null

    fun refreshManageMedia() {
        _canManageMedia.value = checkManageMedia()
    }

    fun scan(partialAccess: Boolean) {
        _state.value = UiState.Scanning
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { MediaScanner.scan(getApplication()) }
                val newPlan = ReorderPlanner.plan(items)
                plan = newPlan
                scanned = items
                repairs = items.filter { needsDateRepair(it) }
                val duplicates = items
                    .groupBy { (it.payload.dataPath ?: "${it.payload.relativePath}${it.payload.name}").lowercase() }
                    .values
                    .filter { it.size > 1 }
                _state.value = UiState.Ready(
                    ScanSummary(
                        totalItems = newPlan.kept.size + newPlan.toResave.size,
                        toResave = newPlan.toResave.size,
                        resaveBytes = newPlan.resaveBytes,
                        datesToRepair = repairs.size,
                        duplicateGroups = duplicates.size,
                        duplicateExamples = duplicates.take(MAX_DUPLICATE_EXAMPLES).map { describeDuplicate(it) },
                        movedToNewFolder = newPlan.toResave.count { TargetFolder.isMoved(it.payload.relativePath, it.payload.targetPath) },
                        skipped = newPlan.skipped.size,
                        skippedFolders = newPlan.skipped
                            .groupingBy { skipLabel(it.payload) }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                            .take(MAX_SKIPPED_FOLDERS)
                            .map { it.key to it.value },
                        fromCaptureDate = newPlan.countBySource(DateSource.TAKEN),
                        fromFileName = newPlan.countBySource(DateSource.FILENAME),
                        fromModifiedTime = newPlan.countBySource(DateSource.MODIFIED),
                        freeBytes = freeBytes(),
                        partialAccess = partialAccess,
                    )
                )
            } catch (e: Exception) {
                _state.value = UiState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun fix() {
        val work = plan?.toResave ?: return
        if (work.isEmpty()) return
        viewModelScope.launch {
            // Each batch needs room for its copies until the originals are deleted.
            val maxBytes = maxOf(freeBytes() / 2, MIN_BATCH_BYTES)
            val batches = ReorderPlanner.batches(work, maxBytes, MAX_BATCH_COUNT)
            var done = 0
            var moved = 0
            var failed = 0
            _state.value = UiState.Working(0, work.size)

            for (batch in batches) {
                val copies = withContext(Dispatchers.IO) {
                    batch.mapNotNull { item ->
                        val copy = runCatching { resaver.copy(item.payload, item.date) }.getOrNull()
                        if (copy == null) failed++
                        done++
                        _state.value = UiState.Working(done, work.size, item.date.epochMs)
                        copy?.let { item.payload to it }
                    }
                }
                if (copies.isEmpty()) continue

                val request = MediaStore.createDeleteRequest(resolver, copies.map { it.first.uri })
                if (!askConsent(request.intentSender)) {
                    // Declined: remove this batch's copies so nothing is duplicated.
                    withContext(Dispatchers.IO) { copies.forEach { resaver.discard(it.second) } }
                    _state.value = UiState.Finished(moved, failed, cancelled = true)
                    return@launch
                }
                withContext(Dispatchers.IO) { copies.forEach { (original, copy) -> resaver.restoreName(copy, original.name) } }
                moved += copies.size
            }
            plan = null
            _state.value = UiState.Finished(moved, failed, cancelled = false)
        }
    }

    /** Sets the file time of earlier copies back to their real date, so the Gallery shows it. */
    fun repairDates() {
        val work = repairs
        if (work.isEmpty()) return
        viewModelScope.launch {
            _state.value = UiState.Working(0, work.size)
            var fixed = 0
            val paths = ArrayList<String>()
            withContext(Dispatchers.IO) {
                work.forEachIndexed { i, item ->
                    val path = item.payload.dataPath
                    if (path != null && resaver.setFileTime(path, item.date.epochMs)) {
                        fixed++
                        paths += path
                    }
                    _state.value = UiState.Working(i + 1, work.size)
                }
            }
            // Ask Android to re-read the files so the new time reaches the Gallery.
            if (paths.isNotEmpty()) {
                MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
            }
            repairs = emptyList()
            _state.value = UiState.DatesRepaired(fixed, work.size - fixed)
        }
    }

    /** Takes earlier dates from a Google Takeout export (zip or json files) and writes them into the photos. */
    fun importTakeout(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val items = scanned
        viewModelScope.launch {
            _state.value = UiState.Scanning
            try {
                val entries = withContext(Dispatchers.IO) { TakeoutReader.read(resolver, uris) }
                val match = TakeoutDates.match(entries, items) { it.name }
                val fixes = match.fixes
                // Files another app made need the user's OK before we can write to them.
                val notOurs = fixes.filter { !it.item.payload.ownedByApp }.map { it.item.payload.uri }
                val allowed = notOurs.isEmpty() || askConsent(MediaStore.createWriteRequest(resolver, notOurs).intentSender)
                val work = if (allowed) fixes else fixes.filter { it.item.payload.ownedByApp }

                var fixed = 0
                val paths = ArrayList<String>()
                _state.value = UiState.Working(0, work.size)
                withContext(Dispatchers.IO) {
                    work.forEachIndexed { i, fix ->
                        if (resaver.setDate(fix.item.payload, fix.newMs)) {
                            fixed++
                            fix.item.payload.dataPath?.let(paths::add)
                        }
                        _state.value = UiState.Working(i + 1, work.size)
                    }
                }
                // Ask Android to re-read the files so the new dates reach the Gallery.
                if (paths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
                }
                plan = null
                _state.value = UiState.TakeoutImported(
                    found = entries.size,
                    fixed = fixed,
                    failed = fixes.size - fixed,
                    alreadyEarlier = match.alreadyEarlier,
                    ambiguous = match.ambiguous,
                    notOnPhone = match.notOnPhone,
                )
            } catch (e: Exception) {
                _state.value = UiState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Photos the user picked (from the file picker) to set a date for by hand. */
    fun pickForDate(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val byId = scanned.associateBy { it.id }
        val items = uris.mapNotNull { uri ->
            val mediaUri = runCatching { MediaStore.getMediaUri(getApplication(), uri) }.getOrNull() ?: uri
            runCatching { ContentUris.parseId(mediaUri) }.getOrNull()?.let { byId[it] }
        }.distinctBy { it.id }
        if (items.isEmpty()) {
            _state.value = UiState.DatesSet(fixed = 0, failed = uris.size)
            return
        }
        picked = items
        val earliest = items.minOf { it.date.epochMs }
        _state.value = UiState.PickDate(
            names = items.take(MAX_PICKED_NAMES).map { it.payload.name },
            count = items.size,
            initial = manualFormat.format(Instant.ofEpochMilli(earliest).atZone(ZoneId.systemDefault())),
        )
    }

    /** Returns false when [text] is not a date like 2026-09-23 14:30, so the screen can say so. */
    fun applyPickedDate(text: String): Boolean {
        val ms = parseManualDate(text) ?: return false
        val items = picked
        viewModelScope.launch {
            val notOurs = items.filter { !it.payload.ownedByApp }.map { it.payload.uri }
            val allowed = notOurs.isEmpty() || askConsent(MediaStore.createWriteRequest(resolver, notOurs).intentSender)
            val work = if (allowed) items else items.filter { it.payload.ownedByApp }
            var fixed = 0
            val paths = ArrayList<String>()
            _state.value = UiState.Working(0, work.size)
            withContext(Dispatchers.IO) {
                work.forEachIndexed { i, item ->
                    // One second apart, in the order picked, so they keep a stable order.
                    if (resaver.setDate(item.payload, ms + i * 1000L)) {
                        fixed++
                        item.payload.dataPath?.let(paths::add)
                    }
                    _state.value = UiState.Working(i + 1, work.size)
                }
            }
            if (paths.isNotEmpty()) {
                MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
            }
            picked = emptyList()
            plan = null
            _state.value = UiState.DatesSet(fixed, items.size - fixed)
        }
        return true
    }

    private fun parseManualDate(text: String): Long? {
        val t = text.trim()
        return runCatching { LocalDateTime.parse(t, manualFormat) }
            .recoverCatching { LocalDate.parse(t).atTime(12, 0) }
            .getOrNull()
            ?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }

    fun onConsentResult(approved: Boolean) {
        _consentRequest.value = null
        pendingConsent?.complete(approved)
        pendingConsent = null
    }

    fun restart() {
        plan = null
        _state.value = UiState.Intro
    }

    private suspend fun askConsent(sender: IntentSender): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingConsent = deferred
        _consentRequest.value = sender
        return deferred.await()
    }

    private fun needsDateRepair(item: PlanItem<DeviceMedia>): Boolean {
        val media = item.payload
        if (!media.ownedByApp || media.hasMediaStoreDate || item.date.source == DateSource.MODIFIED) return false
        return kotlin.math.abs(media.modifiedSec * 1000 - item.date.epochMs) > ONE_DAY_MS
    }

    private fun describeDuplicate(group: List<PlanItem<DeviceMedia>>): String {
        val first = group.first().payload
        val rows = group.joinToString("; ") { item ->
            val m = item.payload
            val added = java.time.Instant.ofEpochSecond(item.addedSec)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString()
            "#${m.id} ${m.volume} ${m.sizeBytes}B added $added ${if (m.ownedByApp) "app" else "other"}"
        }
        return "${first.relativePath}${first.name}: $rows"
    }

    private fun skipLabel(item: DeviceMedia): String = when {
        item.sizeBytes <= 0 -> "(empty file)"
        item.relativePath.isEmpty() -> "(no folder)"
        else -> "${item.volume}: ${item.relativePath}"
    }

    private fun freeBytes(): Long {
        val dir = getApplication<Application>().getExternalFilesDir(null) ?: return 0
        return StatFs(dir.path).availableBytes
    }

    private fun checkManageMedia(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(getApplication())

    private companion object {
        const val MIN_BATCH_BYTES = 64L * 1024 * 1024
        const val MAX_BATCH_COUNT = 300
        const val MAX_SKIPPED_FOLDERS = 8
        const val MAX_DUPLICATE_EXAMPLES = 3
        const val MAX_PICKED_NAMES = 5
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}
