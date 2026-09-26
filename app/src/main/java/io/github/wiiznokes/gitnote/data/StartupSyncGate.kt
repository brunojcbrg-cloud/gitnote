package io.github.wiiznokes.gitnote.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val STARTUP_SYNC_MIN_INTERVAL_MS = 30_000L

sealed interface StartupSyncState {
    data object Idle : StartupSyncState
    data class Syncing(val revision: Long) : StartupSyncState
    data class Synced(val revision: Long, val completedAt: Long) : StartupSyncState
    data class Failed(val revision: Long, val message: String) : StartupSyncState
    data class Override(val revision: Long) : StartupSyncState
}

class StartupSyncGate(
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<StartupSyncState>(StartupSyncState.Idle)
    val state: StateFlow<StartupSyncState> = _state.asStateFlow()

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
        _state.value = StartupSyncState.Syncing(revision)
        val result = runCatching { sync() }.getOrElse { Result.failure(it) }
        result.fold(
            onSuccess = {
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
