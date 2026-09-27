package io.github.wiiznokes.gitnote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withStateAtLeast
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.popAll
import dev.olshevski.navigation.reimagined.popUpTo
import dev.olshevski.navigation.reimagined.rememberNavController
import io.github.wiiznokes.gitnote.ui.destination.AppDestination
import io.github.wiiznokes.gitnote.ui.destination.Destination
import io.github.wiiznokes.gitnote.ui.destination.SetupDestination
import io.github.wiiznokes.gitnote.ui.screen.app.AppScreen
import io.github.wiiznokes.gitnote.ui.screen.setup.SetupNav
import io.github.wiiznokes.gitnote.ui.theme.GitNoteTheme
import io.github.wiiznokes.gitnote.ui.theme.Theme
import io.github.wiiznokes.gitnote.ui.viewmodel.MainViewModel
import io.github.wiiznokes.gitnote.data.PortaoDeSeguranca
import io.github.wiiznokes.gitnote.data.InitializationState
import io.github.wiiznokes.gitnote.data.StartupSyncState
import io.github.wiiznokes.gitnote.data.SyncPresentationMode
import io.github.wiiznokes.gitnote.data.syncPresentationMode
import io.github.wiiznokes.gitnote.atualizador.UpdateCoordinator
import io.github.wiiznokes.gitnote.atualizador.shouldRequestUpdateNotificationPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }


    val authFlow: MutableSharedFlow<String> = MutableSharedFlow(replay = 0)
    private val prefs get() = MyApp.appModule.appPreferences
    private val portao = PortaoDeSeguranca()
    private var aberto by mutableStateOf(false)
    private var verificandoRetorno by mutableStateOf(false)
    private var mensagem by mutableStateOf<String?>(null)
    private var recuperacao by mutableStateOf(false)
    private var mostrarConfirmacao by mutableStateOf(false)
    private var promptEmCurso by mutableStateOf(false)
    private var pendente: ((Boolean) -> Unit)? = null
    private var mainViewModel: MainViewModel? = null
    private val autenticadores = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun confirmarTrava(aoTerminar: (Boolean) -> Unit) = autenticar(aoTerminar)

    private fun autenticar(aoTerminar: ((Boolean) -> Unit)? = null) {
        if (promptEmCurso) return
        if (BiometricManager.from(this).canAuthenticate(autenticadores) != BiometricManager.BIOMETRIC_SUCCESS) {
            mensagem = getString(R.string.lock_unavailable)
            aoTerminar?.invoke(false)
            return
        }
        promptEmCurso = true
        pendente = aoTerminar
        lifecycleScope.launch {
            try {
                val cipher = prefs.prepararCofre()
                lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                    val prompt = BiometricPrompt(this@MainActivity, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val autenticado = result.cryptoObject?.cipher
                            if (autenticado == null) {
                                terminarPrompt(false, getString(R.string.lock_no_crypto))
                                return
                            }
                            lifecycleScope.launch {
                                try {
                                    prefs.desbloquearCofre(autenticado)
                                    aberto = true
                                    mainViewModel?.syncAfterUnlock()
                                    terminarPrompt(true, null)
                                } catch (_: Exception) {
                                    recuperacao = true
                                    terminarPrompt(false, getString(R.string.lock_key_unavailable))
                                }
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            terminarPrompt(false, getString(R.string.lock_auth_failed, errString))
                        }
                    })
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.lock_prompt_title))
                            .setSubtitle(getString(R.string.lock_prompt_subtitle))
                            .setAllowedAuthenticators(autenticadores)
                            .build(),
                        BiometricPrompt.CryptoObject(cipher)
                    )
                }
            } catch (_: Exception) {
                recuperacao = prefs.temEnvelope()
                terminarPrompt(false, getString(
                    if (recuperacao) R.string.lock_key_unavailable else R.string.lock_auth_start_failed
                ))
            }
        }
    }

    private fun terminarPrompt(sucesso: Boolean, erro: String?) {
        promptEmCurso = false
        mensagem = erro
        pendente?.invoke(sucesso)
        pendente = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Apply before the first frame, including the recents thumbnail.
        setScreenCaptureBlocked(runBlocking {
            MyApp.appModule.appPreferences.bloquearCapturaDeTela.get()
        })

        setContent {

            val vm: MainViewModel = viewModel()
            mainViewModel = vm

            val theme by vm.prefs.theme.getAsState()
            val dynamicColor by vm.prefs.dynamicColor.getAsState()
            val bloquearCapturaDeTela by vm.prefs.bloquearCapturaDeTela.getAsState()
            val updateStatus by vm.prefs.lastUpdateStatus.getAsState()
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) UpdateCoordinator.enqueueCheck(this@MainActivity)
            }
            LaunchedEffect(updateStatus) {
                val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (shouldRequestUpdateNotificationPermission(
                        BuildConfig.BUILD_TYPE,
                        Build.VERSION.SDK_INT,
                        updateStatus,
                        granted,
                    )
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(bloquearCapturaDeTela) {
                setScreenCaptureBlocked(bloquearCapturaDeTela)
            }


            GitNoteTheme(
                darkTheme = (theme == Theme.SYSTEM && isSystemInDarkTheme()) || theme == Theme.DARK,
                dynamicColor = dynamicColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!aberto) {
                        TelaDeBloqueio()
                    } else {
                    LaunchedEffect(Unit) { vm.ensureInitialized() }
                    val initializationState by vm.initializationState.collectAsStateWithLifecycle()
                    val syncState by vm.startupSyncState.collectAsStateWithLifecycle()
                    val syncProgress by vm.startupSyncProgress.collectAsStateWithLifecycle()
                    val firstSyncAttemptFinished by vm.firstSyncAttemptFinished.collectAsStateWithLifecycle()

                    if (initializationState is InitializationState.Loading) {
                        TelaDeSincronizacao(
                            state = syncState,
                            progress = syncProgress,
                            retry = vm::retrySync,
                            editAnyway = vm::editAnyway,
                        )
                    } else {
                        val configured = (initializationState as InitializationState.Ready).configured
                        val startDestination: Destination = remember(configured) {
                            if (configured) Destination.App(AppDestination.Home)
                            else Destination.Setup(SetupDestination.Main)
                        }
                        val presentation = if (configured) {
                            syncPresentationMode(syncState, firstSyncAttemptFinished)
                        } else {
                            SyncPresentationMode.Free
                        }

                        if (presentation == SyncPresentationMode.FullScreen) {
                            TelaDeSincronizacao(
                                state = syncState,
                                progress = syncProgress,
                                retry = vm::retrySync,
                                editAnyway = vm::editAnyway,
                            )
                        } else {
                            val revision = when (val currentSyncState = syncState) {
                                is StartupSyncState.Synced -> currentSyncState.revision
                                is StartupSyncState.Override -> currentSyncState.revision
                                else -> 0L
                            }
                            val navController =
                                rememberNavController(startDestination = startDestination)

                            NavBackHandler(navController)

                            AnimatedNavHost(
                                controller = navController
                            ) { destination ->
                                when (destination) {
                                    is Destination.Setup -> {
                                        SetupNav(
                                            startDestination = destination.setupDestination,
                                            authFlow = authFlow,
                                            onSetupSuccess = {
                                                navController.popUpTo(
                                                    inclusive = true
                                                ) {
                                                    it is Destination.Setup
                                                }
                                                navController.navigate(Destination.App(AppDestination.Home))
                                            }
                                        )
                                    }


                                    is Destination.App -> AppScreen(
                                        appDestination = destination.appDestination,
                                        runtimeReadOnly = syncState is StartupSyncState.Syncing ||
                                            syncState is StartupSyncState.Failed,
                                        syncRevision = revision,
                                        startupSyncState = syncState,
                                        onSyncNow = vm::retrySync,
                                        onCloseRepo = {
                                            navController.popAll()
                                            navController.navigate(Destination.Setup(SetupDestination.Main))
                                        }
                                    )
                                }
                            }
                            if (presentation == SyncPresentationMode.Banner) {
                                Surface(
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        when (val currentSyncState = syncState) {
                                            is StartupSyncState.Failed -> {
                                                Text("Sem sincronizar — ${currentSyncState.message}")
                                                Row {
                                                    Button(onClick = vm::retrySync) { Text("Tentar de novo") }
                                                    TextButton(onClick = vm::editAnyway) {
                                                        Text("Editar mesmo assim")
                                                    }
                                                }
                                            }
                                            else -> Text(
                                                "Sincronizando… ${syncProgress.percent}% — ${syncProgress.message}",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                    if (verificandoRetorno) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator()
                                Text("Conferindo bloqueio…", modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            val disponivel = BiometricManager.from(this@MainActivity).canAuthenticate(autenticadores) ==
                BiometricManager.BIOMETRIC_SUCCESS
            prefs.definirProtecaoDisponivel(disponivel)
            if (disponivel) autenticar()
            else if (prefs.temEnvelope()) {
                recuperacao = true
                mensagem = getString(R.string.lock_no_screen_lock)
            } else aberto = true
        }
    }

    @androidx.compose.runtime.Composable
    private fun TelaDeBloqueio() {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(mensagem ?: getString(R.string.lock_screen_title))
            if (!promptEmCurso && !recuperacao) {
                Button(onClick = { autenticar() }) { Text(getString(R.string.lock_unlock)) }
            }
            if (recuperacao) {
                Button(onClick = { recuperacao = false; mensagem = null; autenticar() }) {
                    Text(getString(R.string.lock_try_again))
                }
                TextButton(onClick = { mostrarConfirmacao = true }) {
                    Text(getString(R.string.lock_reconfigure))
                }
            }
        }
        if (mostrarConfirmacao) {
            AlertDialog(
                onDismissRequest = { mostrarConfirmacao = false },
                title = { Text(getString(R.string.lock_reconfigure_title)) },
                text = { Text(getString(R.string.lock_reconfigure_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        mostrarConfirmacao = false
                        lifecycleScope.launch {
                            try {
                                prefs.reconfigurarCredenciais()
                                recuperacao = false
                                val disponivel = BiometricManager.from(this@MainActivity)
                                    .canAuthenticate(autenticadores) == BiometricManager.BIOMETRIC_SUCCESS
                                prefs.definirProtecaoDisponivel(disponivel)
                                if (disponivel) autenticar() else aberto = true
                            } catch (_: Exception) {
                                mensagem = getString(R.string.lock_reconfigure_failed)
                            }
                        }
                    }) { Text(getString(R.string.lock_reconfigure_confirm)) }
                },
                dismissButton = { TextButton(onClick = { mostrarConfirmacao = false }) { Text(getString(R.string.cancel)) } }
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun TelaDeSincronizacao(
        state: StartupSyncState,
        progress: io.github.wiiznokes.gitnote.data.SyncProgressSnapshot,
        retry: () -> Unit,
        editAnyway: () -> Unit,
    ) {
        var elapsedSeconds by remember(state) { mutableIntStateOf(0) }
        LaunchedEffect(state) {
            while (state is StartupSyncState.Syncing || state is StartupSyncState.Idle) {
                delay(1_000)
                elapsedSeconds += 1
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                StartupSyncState.Idle, is StartupSyncState.Syncing -> {
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${progress.percent}% — ${progress.message}", modifier = Modifier.padding(top = 16.dp))
                    Text("há $elapsedSeconds s", modifier = Modifier.padding(top = 8.dp))
                    if (progress.indeterminate) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                    }
                }

                is StartupSyncState.Failed -> {
                    Text("Sem sincronizar — ${state.message}")
                    Button(
                        onClick = retry,
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Tentar de novo") }
                    TextButton(onClick = editAnyway) { Text("Editar mesmo assim") }
                }

                is StartupSyncState.Synced, is StartupSyncState.Override -> Unit
            }
        }
    }

    override fun onStop() {
        if (!promptEmCurso) portao.aoParar(SystemClock.elapsedRealtime(), aberto)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (!aberto || promptEmCurso) return
        // Keep the navigation composed while an opaque overlay protects the notes.
        verificandoRetorno = true
        lifecycleScope.launch {
            if (portao.deveRetravar(SystemClock.elapsedRealtime(), prefs.prazoDaTrava.get(), prefs.travaDeAbertura.get())) {
                aberto = false
                verificandoRetorno = false
                prefs.bloquearCofre()
                autenticar()
            } else {
                verificandoRetorno = false
                mainViewModel?.syncAfterUnlock()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent $intent")

        val uri = intent.data ?: return
        if (uri.scheme == "gitnote-identity" && uri.host == "register-callback") {
            val code = uri.getQueryParameter("code")

            if (code != null) {
                Log.d(TAG, "received code from intent, sending it...")
                CoroutineScope(Dispatchers.Default).launch {
                    authFlow.emit(code)
                }
            } else {
                Log.w(TAG, "code is null")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy")
    }

    private fun setScreenCaptureBlocked(blocked: Boolean) {
        if (blocked) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
