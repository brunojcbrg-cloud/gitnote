package io.github.wiiznokes.gitnote.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

const val STARTUP_SYNC_MIN_INTERVAL_MS = 30_000L
const val STARTUP_SYNC_TIMEOUT_MS = 120_000L

sealed interface SyncProgressEvent {
    data object CheckingLocalChanges : SyncProgressEvent
    data class Downloading(val current: Int, val total: Int) : SyncProgressEvent
    data object Merging : SyncProgressEvent
    data class Uploading(val current: Int, val total: Int) : SyncProgressEvent
    data class Indexing(val current: Int, val total: Int, val path: String) : SyncProgressEvent
    data object Finished : SyncProgressEvent
}

data class SyncProgressSnapshot(
    val percent: Int,
    val message: String,
    val indeterminate: Boolean = false,
)

class SyncProgressReducer {
    private var lastPercent = 0

    fun reset(): SyncProgressSnapshot {
        lastPercent = 0
        return SyncProgressSnapshot(0, "Conferindo suas edições…")
    }

    fun apply(event: SyncProgressEvent): SyncProgressSnapshot {
        val candidate = when (event) {
            SyncProgressEvent.CheckingLocalChanges ->
                SyncProgressSnapshot(0, "Conferindo suas edições…", indeterminate = true)
            is SyncProgressEvent.Downloading -> ranged(
                start = 10,
                end = 70,
                current = event.current,
                total = event.total,
                label = "Baixando do GitHub",
            )
            SyncProgressEvent.Merging ->
                SyncProgressSnapshot(70, "Juntando mudanças…", indeterminate = true)
            is SyncProgressEvent.Uploading -> ranged(
                start = 80,
                end = 90,
                current = event.current,
                total = event.total,
                label = "Enviando",
            )
            is SyncProgressEvent.Indexing -> ranged(
                start = 90,
                end = 100,
                current = event.current,
                total = event.total,
                label = "Atualizando notas",
            )
            SyncProgressEvent.Finished -> SyncProgressSnapshot(100, "Notas atualizadas")
        }
        lastPercent = maxOf(lastPercent, candidate.percent)
        return candidate.copy(percent = lastPercent)
    }

    private fun ranged(
        start: Int,
        end: Int,
        current: Int,
        total: Int,
        label: String,
    ): SyncProgressSnapshot {
        if (total <= 0) return SyncProgressSnapshot(start, "$label…", indeterminate = true)
        val safeCurrent = current.coerceIn(0, total)
        val percent = start + ((end - start) * safeCurrent / total)
        return SyncProgressSnapshot(percent, "$label — $safeCurrent de $total")
    }
}

sealed interface StartupSyncState {
    data object Idle : StartupSyncState
    data class Syncing(val revision: Long) : StartupSyncState
    data class Synced(val revision: Long, val completedAt: Long) : StartupSyncState
    data class Failed(val revision: Long, val message: String) : StartupSyncState
    data class Override(val revision: Long) : StartupSyncState
}

enum class SyncPresentationMode {
    FullScreen,
    Banner,
    Free,
}

fun syncPresentationMode(
    state: StartupSyncState,
    firstAttemptFinished: Boolean,
): SyncPresentationMode = when (state) {
    StartupSyncState.Idle -> SyncPresentationMode.FullScreen
    is StartupSyncState.Syncing -> if (firstAttemptFinished) {
        SyncPresentationMode.Banner
    } else {
        SyncPresentationMode.FullScreen
    }
    is StartupSyncState.Failed -> if (firstAttemptFinished) {
        SyncPresentationMode.Banner
    } else {
        SyncPresentationMode.FullScreen
    }
    is StartupSyncState.Synced, is StartupSyncState.Override -> SyncPresentationMode.Free
}

fun readingAllowed(state: StartupSyncState, firstAttemptFinished: Boolean): Boolean =
    syncPresentationMode(state, firstAttemptFinished) != SyncPresentationMode.FullScreen

class StartupSyncGate(
    private val now: () -> Long,
    private val timeoutMs: Long = STARTUP_SYNC_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow<StartupSyncState>(StartupSyncState.Idle)
    val state: StateFlow<StartupSyncState> = _state.asStateFlow()
    private val reducer = SyncProgressReducer()
    private val _progress = MutableStateFlow(reducer.reset())
    val progress: StateFlow<SyncProgressSnapshot> = _progress.asStateFlow()

    private var revision = 0L
    private var lastAttemptAt: Long? = null

    fun shouldSync(force: Boolean): Boolean {
        if (_state.value is StartupSyncState.Syncing) return false
        val last = lastAttemptAt ?: return true
        return force || now() - last >= STARTUP_SYNC_MIN_INTERVAL_MS
    }

    suspend fun run(force: Boolean, sync: suspend () -> Result<Unit>): Result<Unit>? {
        if (!shouldSync(force)) return null
        revision += 1
        lastAttemptAt = now()
        _progress.value = reducer.reset()
        _state.value = StartupSyncState.Syncing(revision)
        val result = withTimeoutOrNull(timeoutMs) {
            runCatching { sync() }.getOrElse { Result.failure(it) }
        } ?: Result.failure(IllegalStateException("demorou demais"))
        result.fold(
            onSuccess = {
                _progress.value = reducer.apply(SyncProgressEvent.Finished)
                _state.value = StartupSyncState.Synced(revision, now())
            },
            onFailure = { error ->
                _state.value = StartupSyncState.Failed(
                    revision,
                    error.message ?: "falha desconhecida",
                )
            },
        )
        return result
    }

    fun reportProgress(event: SyncProgressEvent) {
        _progress.value = reducer.apply(event)
    }

    fun editAnyway() {
        val currentRevision = when (val current = _state.value) {
            is StartupSyncState.Failed -> current.revision
            is StartupSyncState.Syncing -> current.revision
            is StartupSyncState.Synced -> current.revision
            is StartupSyncState.Override -> current.revision
            StartupSyncState.Idle -> revision
        }
        _state.value = StartupSyncState.Override(currentRevision)
    }

    fun editingAllowed(): Boolean = when (_state.value) {
        is StartupSyncState.Synced, is StartupSyncState.Override -> true
        is StartupSyncState.Idle, is StartupSyncState.Syncing, is StartupSyncState.Failed -> false
    }
}
