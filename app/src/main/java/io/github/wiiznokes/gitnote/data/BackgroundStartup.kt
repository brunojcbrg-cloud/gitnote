package io.github.wiiznokes.gitnote.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface InitializationState {
    data object Loading : InitializationState
    data class Ready(val configured: Boolean) : InitializationState
}

class ProcessInitialization(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val initialize: suspend () -> Boolean,
) {
    private val started = AtomicBoolean(false)
    private val _state = MutableStateFlow<InitializationState>(InitializationState.Loading)
    val state: StateFlow<InitializationState> = _state.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(dispatcher) {
            _state.value = InitializationState.Ready(
                configured = runCatching { initialize() }.getOrDefault(false),
            )
        }
    }
}

class AsyncSyncRunner(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val shouldRun: (Boolean) -> Boolean,
    private val run: suspend (Boolean) -> Unit,
) {
    private val lock = Any()
    private var job: Job? = null

    fun request(force: Boolean) {
        synchronized(lock) {
            if (job?.isActive == true || !shouldRun(force)) return
            job = scope.launch(dispatcher) { run(force) }
        }
    }
}
