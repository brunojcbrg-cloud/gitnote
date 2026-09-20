package io.github.wiiznokes.gitnote

import android.app.Application
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import io.github.wiiznokes.gitnote.aulas.LessonHistoryWorker

const val TAG = "MyApp (Application)"

class MyApp : Application() {

    companion object {
        lateinit var appModule: AppModule
    }

    private val scope = MainScope()
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        appModule = AppModuleImpl(this)
        LessonHistoryWorker.schedule(this)


        scope.launch {
            appModule.appPreferences.preload()
            // Tem de vir junto: a retomada da posicao le de forma bloqueante na
            // composicao da tela, e uma leitura em arquivo ainda nao aberto volta
            // vazia -- a nota abriria no topo depois de o app ser fechado.
            appModule.posicoesDeLeitura.preload()
        }
    }
}
