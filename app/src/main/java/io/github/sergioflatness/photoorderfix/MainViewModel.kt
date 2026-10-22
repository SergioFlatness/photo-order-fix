package io.github.sergioflatness.photoorderfix

import android.app.Application
import android.media.MediaScannerConnection
import android.content.IntentSender
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import io.github.sergioflatness.photoorderfix.core.UndoLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    /** Room for the copies while the originals wait in the trash, so the fix can be undone. */
    val canKeepOriginals: Boolean,
    /** Out-of-order items per year taken, oldest year first. */
    val byYear: List<Pair<Int, Int>> = emptyList(),
)

sealed interface UiState {
    data object Intro : UiState
    data object Scanning : UiState
    data class Ready(val summary: ScanSummary) : UiState
    /** [currentMs] is the date of the item being re-saved, to show which period the work has reached. */
    data class Working(val done: Int, val total: Int, val currentMs: Long? = null, val undoing: Boolean = false) : UiState
    data class Finished(
        val moved: Int,
        val failed: Int,
        val cancelled: Boolean,
        val keptOriginals: Boolean = false,
        /** Years taken of the re-saved items, oldest first. */
        val years: List<Int> = emptyList(),
    ) : UiState
    /** [missing] originals were no longer in the trash; [cancelled] means the user declined restoring. */
    data class Undone(val restored: Int, val missing: Int, val cancelled: Boolean) : UiState
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

    private val undoFile = File(app.filesDir, UNDO_FILE)
    private val _undo = MutableStateFlow(loadUndo())
    /** The last fix, while its originals are still in Android's trash. */
    val undo: StateFlow<UndoLog?> = _undo.asStateFlow()

    /**
     * Originals go to the trash, so the fix can be undone for 30 days. Only when the copies and a
     * reserve don't fit are they deleted at once.
     */
    private var canKeepOriginals = false

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
                val free = freeBytes()
                canKeepOriginals = newPlan.resaveBytes + KEEP_RESERVE_BYTES <= free
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
                        freeBytes = free,
                        partialAccess = partialAccess,
                        canKeepOriginals = canKeepOriginals,
                        byYear = newPlan.toResave.groupingBy { yearOf(it.date.epochMs) }.eachCount().toSortedMap().map { it.key to it.value },
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
        val keep = canKeepOriginals
        val years = work.map { yearOf(it.date.epochMs) }.distinct().sorted()
        viewModelScope.launch {
            // A new fix replaces whatever the last one could undo.
            var log = UndoLog(System.currentTimeMillis(), emptyList())
            saveUndo(if (keep) log else null)
            // Each batch needs room for its copies until the originals are deleted. Trashed
            // originals free nothing, so then the scan already checked that everything fits.
            val maxBytes = if (keep) Long.MAX_VALUE else maxOf(freeBytes() / 2, MIN_BATCH_BYTES)
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

                val originals = copies.map { it.first.uri }
                val request = if (keep) MediaStore.createTrashRequest(resolver, originals, true)
                else MediaStore.createDeleteRequest(resolver, originals)
                if (!askConsent(request.intentSender)) {
                    // Declined: remove this batch's copies so nothing is duplicated.
                    withContext(Dispatchers.IO) { copies.forEach { resaver.discard(it.second) } }
                    _state.value = UiState.Finished(moved, failed, cancelled = true, keptOriginals = keep && moved > 0, years = years)
                    return@launch
                }
                // Trashing renames the original on disk, so its name is free for the copy either way.
                withContext(Dispatchers.IO) { copies.forEach { (original, copy) -> resaver.restoreName(copy, original.name) } }
                moved += copies.size
                if (keep) {
                    log = log.plus(copies.map { (original, copy) -> original.uri.toString() to copy.toString() })
                    saveUndo(log)
                }
            }
            plan = null
            _state.value = UiState.Finished(moved, failed, cancelled = false, keptOriginals = keep, years = years)
        }
    }

    /**
     * Sets file times back to the real date, so the Gallery and file-time pickers show it in place.
     * Photos Android has no capture date for also get the date written inside, so Instagram stops
     * labelling them by their "date added".
     */
    fun repairDates() {
        val all = repairs
        if (all.isEmpty()) return
        viewModelScope.launch {
            // Files another app made need the user's OK before we can change them.
            val notOurs = all.filter { !it.payload.ownedByApp }
            val allowed = notOurs.chunked(MAX_BATCH_COUNT).all { chunk ->
                askConsent(MediaStore.createWriteRequest(resolver, chunk.map { it.payload.uri }).intentSender)
            }
            val work = if (allowed) all else all.filter { it.payload.ownedByApp }
            if (work.isEmpty()) {
                _state.value = UiState.DatesRepaired(0, all.size)
                return@launch
            }
            _state.value = UiState.Working(0, work.size)
            var fixed = 0
            val paths = ArrayList<String>()
            withContext(Dispatchers.IO) {
                work.forEachIndexed { i, item ->
                    val path = item.payload.dataPath
                    val ok = if (resaver.needsDateInside(item.payload)) {
                        resaver.setDate(item.payload, item.date.epochMs)
                    } else {
                        path != null && resaver.setFileTime(path, item.date.epochMs)
                    }
                    if (path != null && ok) {
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
            _state.value = UiState.DatesRepaired(fixed, all.size - fixed)
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

    /** Deletes the copies from the last fix and takes the originals back out of the trash. */
    fun undo() {
        val log = _undo.value ?: return
        viewModelScope.launch {
            val total = log.pairs.size
            _state.value = UiState.Working(0, total, undoing = true)
            val originals = withContext(Dispatchers.IO) {
                // Copies go first: they carry the originals' names, which the restored files need back.
                log.pairs.forEachIndexed { i, (_, copy) ->
                    resaver.discard(Uri.parse(copy))
                    _state.value = UiState.Working(i + 1, total, undoing = true)
                }
                log.pairs.map { Uri.parse(it.first) }.filter { isStillTrashed(it) }
            }
            var restored = 0
            for (chunk in originals.chunked(MAX_BATCH_COUNT)) {
                val request = MediaStore.createTrashRequest(resolver, chunk, false)
                if (!askConsent(request.intentSender)) {
                    // Keep the log: the originals are safe in the trash and the user can try again.
                    _state.value = UiState.Undone(restored, total - originals.size, cancelled = true)
                    return@launch
                }
                restored += chunk.size
            }
            saveUndo(null)
            _state.value = UiState.Undone(restored, total - originals.size, cancelled = false)
        }
    }

    private fun isStillTrashed(uri: Uri): Boolean {
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), args, null)?.use { c ->
                c.moveToFirst() && c.getInt(0) == 1
            } ?: false
        }.getOrDefault(true) // Without read access, let Android's own request decide.
    }

    private fun loadUndo(): UndoLog? {
        val log = runCatching { UndoLog.parse(undoFile.readText()) }.getOrNull() ?: return null
        if (log.pairs.isEmpty() || log.isExpired(System.currentTimeMillis())) {
            undoFile.delete()
            return null
        }
        return log
    }

    private fun saveUndo(log: UndoLog?) {
        if (log == null) undoFile.delete() else runCatching { undoFile.writeText(log.serialize()) }
        _undo.value = log?.takeIf { it.pairs.isNotEmpty() }
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

    /**
     * KakaoTalk and similar pickers sort by the file time, and the Gallery falls back to it, so a file
     * time far from the real date (for example after a date edit in the Gallery) puts the photo in the
     * wrong place even when "date added" is in order. Only the time changes; the file stays as it is.
     */
    private fun needsDateRepair(item: PlanItem<DeviceMedia>): Boolean {
        val media = item.payload
        if (media.dataPath == null) return false
        // Instagram labels a photo with no capture date by its "date added", so a copy this app
        // re-saved shows up as taken today.
        if (resaver.needsDateInside(media) && kotlin.math.abs(item.addedSec * 1000 - item.date.epochMs) > ONE_DAY_MS) return true
        if (item.date.source == DateSource.MODIFIED) return false
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

    private fun yearOf(epochMs: Long): Int = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).year

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
        const val UNDO_FILE = "undo.txt"
        /** Left free beyond the copies when originals stay in the trash. */
        const val KEEP_RESERVE_BYTES = 1024L * 1024 * 1024
    }
}
