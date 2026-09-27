package io.github.wiiznokes.gitnote.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.manager.GitSyncSnapshot
import io.github.wiiznokes.gitnote.manager.GitSyncSummary
import io.github.wiiznokes.gitnote.manager.summarizeGitSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncDashboardUiState(
    val summary: GitSyncSummary = GitSyncSummary.Loading,
    val snapshot: GitSyncSnapshot? = null,
)

class SyncDashboardViewModel : ViewModel() {
    private val gitManager = MyApp.appModule.gitManager
    val prefs = MyApp.appModule.appPreferences
    private val _state = MutableStateFlow(SyncDashboardUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SyncDashboardUiState()
            gitManager.syncSnapshot(prefs.cred()).fold(
                onSuccess = { snapshot ->
                    _state.value = SyncDashboardUiState(
                        summary = summarizeGitSync(
                            snapshot.ahead,
                            snapshot.behind,
                            snapshot.changes.count { it.path.endsWith(".md", ignoreCase = true) },
                        ),
                        snapshot = snapshot,
                    )
                },
                onFailure = { error ->
                    _state.value = SyncDashboardUiState(
                        summary = GitSyncSummary.Failed(error.message ?: "falha desconhecida"),
                    )
                },
            )
        }
    }
}
