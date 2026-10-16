package io.github.sergioflatness.photoorderfix

import android.app.Application
import android.content.IntentSender
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sergioflatness.photoorderfix.core.DateSource
import io.github.sergioflatness.photoorderfix.core.ReorderPlan
import io.github.sergioflatness.photoorderfix.core.ReorderPlanner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanSummary(
    val totalItems: Int,
    val toResave: Int,
    val resaveBytes: Long,
    val skipped: Int,
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
    data class Working(val done: Int, val total: Int) : UiState
    data class Finished(val moved: Int, val failed: Int, val cancelled: Boolean) : UiState
    data class Failed(val message: String) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val resaver = Resaver(resolver)

    private val _state = MutableStateFlow<UiState>(UiState.Intro)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** When set, the screen must launch this system dialog ("Allow deleting N items?"). */
    private val _consentRequest = MutableStateFlow<IntentSender?>(null)
    val consentRequest: StateFlow<IntentSender?> = _consentRequest.asStateFlow()

    private val _canManageMedia = MutableStateFlow(checkManageMedia())
    val canManageMedia: StateFlow<Boolean> = _canManageMedia.asStateFlow()

    private var plan: ReorderPlan<DeviceMedia>? = null
    private var pendingConsent: CompletableDeferred<Boolean>? = null

    fun refreshManageMedia() {
        _canManageMedia.value = checkManageMedia()
    }

    fun scan(partialAccess: Boolean) {
        _state.value = UiState.Scanning
        viewModelScope.launch {
            try {
                val newPlan = withContext(Dispatchers.IO) { ReorderPlanner.plan(MediaScanner.scan(resolver)) }
                plan = newPlan
                _state.value = UiState.Ready(
                    ScanSummary(
                        totalItems = newPlan.kept.size + newPlan.toResave.size,
                        toResave = newPlan.toResave.size,
                        resaveBytes = newPlan.resaveBytes,
                        skipped = newPlan.skipped.size,
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
                        _state.value = UiState.Working(done, work.size)
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

    private fun freeBytes(): Long {
        val dir = getApplication<Application>().getExternalFilesDir(null) ?: return 0
        return StatFs(dir.path).availableBytes
    }

    private fun checkManageMedia(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(getApplication())

    private companion object {
        const val MIN_BATCH_BYTES = 64L * 1024 * 1024
        const val MAX_BATCH_COUNT = 300
    }
}
