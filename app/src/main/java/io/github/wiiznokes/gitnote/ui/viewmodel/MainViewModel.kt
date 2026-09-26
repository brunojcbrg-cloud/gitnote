package io.github.wiiznokes.gitnote.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.data.AppPreferences
import io.github.wiiznokes.gitnote.data.StartupSyncGate
import io.github.wiiznokes.gitnote.data.StorageConfig
import io.github.wiiznokes.gitnote.data.platform.NodeFs
import io.github.wiiznokes.gitnote.helper.StoragePermissionHelper
import io.github.wiiznokes.gitnote.helper.UiHelper
import io.github.wiiznokes.gitnote.ui.model.StorageConfiguration
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val gitManager = MyApp.appModule.gitManager
    val uiHelper: UiHelper = MyApp.appModule.uiHelper

    private val storageManager = MyApp.appModule.storageManager
    private val startupSyncGate = StartupSyncGate(SystemClock::elapsedRealtime)
    val startupSyncState = startupSyncGate.state
    private var syncJob: Job? = null
    private var repoReady = false


    suspend fun tryInit(): Boolean {

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
        if (!repoReady || syncJob?.isActive == true || !startupSyncGate.shouldSync(force)) return
        syncJob = viewModelScope.launch(
            context = Dispatchers.IO,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            startupSyncGate.run(force) {
                storageManager.updateDatabaseAndRepo()
            }
        }
    }

}
