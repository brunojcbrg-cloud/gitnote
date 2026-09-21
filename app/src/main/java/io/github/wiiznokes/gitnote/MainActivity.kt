package io.github.wiiznokes.gitnote

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private var mensagem by mutableStateOf<String?>(null)
    private var recuperacao by mutableStateOf(false)
    private var mostrarConfirmacao by mutableStateOf(false)
    private var promptEmCurso by mutableStateOf(false)
    private var pendente: ((Boolean) -> Unit)? = null
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

            val theme by vm.prefs.theme.getAsState()
            val dynamicColor by vm.prefs.dynamicColor.getAsState()
            val bloquearCapturaDeTela by vm.prefs.bloquearCapturaDeTela.getAsState()
            LaunchedEffect(bloquearCapturaDeTela) {
                setScreenCaptureBlocked(bloquearCapturaDeTela)
            }


            GitNoteTheme(
                darkTheme = (theme == Theme.SYSTEM && isSystemInDarkTheme()) || theme == Theme.DARK,
                dynamicColor = dynamicColor
            ) {
                if (!aberto) {
                    TelaDeBloqueio()
                } else {
                val startDestination: Destination = remember {
                    if (runBlocking { vm.tryInit() }) {
                        Destination.App(AppDestination.Home)
                    } else Destination.Setup(SetupDestination.Main)
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
                            onCloseRepo = {
                                navController.popAll()
                                navController.navigate(Destination.Setup(SetupDestination.Main))
                            }
                        )
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

    override fun onStop() {
        if (!promptEmCurso) portao.aoParar(SystemClock.elapsedRealtime(), aberto)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (!aberto || promptEmCurso) return
        // Hide the navigation before asynchronous preference reads can show a frame of notes.
        aberto = false
        lifecycleScope.launch {
            if (portao.deveRetravar(SystemClock.elapsedRealtime(), prefs.prazoDaTrava.get(), prefs.travaDeAbertura.get())) {
                prefs.bloquearCofre()
                autenticar()
            } else aberto = true
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
