package io.github.wiiznokes.gitnote.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.data.AppPreferences
import io.github.wiiznokes.gitnote.data.AsyncSyncRunner
import io.github.wiiznokes.gitnote.data.ProcessInitialization
import io.github.wiiznokes.gitnote.data.StartupSyncGate
import io.github.wiiznokes.gitnote.data.StorageConfig
import io.github.wiiznokes.gitnote.data.platform.NodeFs
import io.github.wiiznokes.gitnote.helper.StoragePermissionHelper
import io.github.wiiznokes.gitnote.helper.UiHelper
import io.github.wiiznokes.gitnote.ui.model.StorageConfiguration
import kotlinx.coroutines.Dispatchers

class MainViewModel : ViewModel() {

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val gitManager = MyApp.appModule.gitManager
    val uiHelper: UiHelper = MyApp.appModule.uiHelper

    private val storageManager = MyApp.appModule.storageManager
    private val startupSyncGate = StartupSyncGate(SystemClock::elapsedRealtime)
    val startupSyncState = startupSyncGate.state
    private var repoReady = false
    private val processInitialization = ProcessInitialization(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
        initialize = ::initialize,
    )
    val initializationState = processInitialization.state
    private val syncRunner = AsyncSyncRunner(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
        shouldRun = startupSyncGate::shouldSync,
    ) { force ->
        startupSyncGate.run(force) {
            storageManager.updateDatabaseAndRepo()
        }
    }

    fun ensureInitialized() {
        processInitialization.start()
    }

    private suspend fun initialize(): Boolean {

        if (!prefs.isInit.get()) {
            return false
        }

        val storageConfig = when (prefs.storageConfig.get()) {
            StorageConfig.App -> {
                StorageConfiguration.App
            }

            StorageConfig.Device -> {
                if (!StoragePermissionHelper.isPermissionGranted()) {
                    return false
                }
                val repoPath = try {
                    prefs.repoPath()
                } catch (_: Exception) {
                    return false
                }
                StorageConfiguration.Device(repoPath)
            }
        }

        if (!NodeFs.Folder.fromPath(storageConfig.repoPath()).exist()) {
            return false
        }

        gitManager.openRepo(storageConfig.repoPath()).onFailure {
            return false
        }
        prefs.applyGitAuthorDefaults(null, gitManager.currentSignature())
        repoReady = true
        syncNow(force = true)

        return true
    }

    fun syncAfterUnlock() {
        syncNow(force = false)
    }

    fun retrySync() {
        syncNow(force = true)
    }

    fun editAnyway() {
        startupSyncGate.editAnyway()
    }

    private fun syncNow(force: Boolean) {
        if (!repoReady) return
        syncRunner.request(force)
    }

}
