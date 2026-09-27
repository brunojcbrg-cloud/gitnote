package io.github.wiiznokes.gitnote.atualizador

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wiiznokes.gitnote.BuildConfig
import io.github.wiiznokes.gitnote.MyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

private const val UPDATE_PERIODIC_WORK = "life-so-update-periodic"
private const val UPDATE_OPEN_WORK = "life-so-update-on-open"
private const val UPDATE_DOWNLOAD_WORK = "life-so-update-download"
private const val UPDATE_CHANNEL = "atualizacoes"
private const val UPDATE_NOTIFICATION_ID = 8700
private const val ACTION_DOWNLOAD = "io.github.wiiznokes.gitnote.UPDATE_DOWNLOAD"
private const val ACTION_INSTALL_RESULT = "io.github.wiiznokes.gitnote.UPDATE_INSTALL_RESULT"
private const val EXTRA_APK_PATH = "apk_path"

object UpdateCoordinator {
    fun schedule(context: Context, lastCheckEpochSeconds: Int, automatic: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!automatic || BuildConfig.BUILD_TYPE != "nightly") {
            workManager.cancelUniqueWork(UPDATE_PERIODIC_WORK)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UPDATE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        val now = System.currentTimeMillis() / 1_000L
        if (now - lastCheckEpochSeconds >= UPDATE_CHECK_INTERVAL_SECONDS) enqueueCheck(context)
    }

    fun enqueueCheck(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_OPEN_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueDownload(context: Context) {
        // Sem setExpedited: a interação entre "expedited" e setForeground()
        // manual dentro do CoroutineWorker varia por versão do Android e não
        // dá para testar sem o aparelho real — não vale o risco de crash por
        // uma melhoria de latência que não é essencial aqui.
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // KEEP, não REPLACE: tocar em "Atualizar" de novo enquanto já está
        // baixando não pode cancelar e reiniciar o download (ficava sem
        // terminar nunca se o Bruno tocasse mais de uma vez).
        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_DOWNLOAD_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.BUILD_TYPE != "nightly") return Result.success()
        val prefs = MyApp.appModule.appPreferences
        val now = (System.currentTimeMillis() / 1_000L).toInt()
        return withContext(Dispatchers.IO) {
            try {
                val connection = (URI(UPDATE_RELEASES_URL).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "LifeSO-Android-Updater")
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                prefs.lastUpdateCheckEpochSeconds.update(now)
                when (val http = releaseHttpOutcome(status, body)) {
                    is ReleaseHttpOutcome.Failure -> {
                        prefs.lastUpdateStatus.update("Última verificação: falhou (${http.reason})")
                    }
                    is ReleaseHttpOutcome.Success -> when (
                        val evaluation = evaluateUpdate(
                            http.body,
                            installedVersionCode(applicationContext),
                            BuildConfig.BUILD_TYPE,
                        )
                    ) {
                        is UpdateEvaluation.Available -> {
                            prefs.availableUpdateJson.update(http.body)
                            prefs.lastUpdateStatus.update("Life SO ${evaluation.release.tag} disponível")
                            showAvailableNotification(applicationContext, evaluation.release)
                        }
                        UpdateEvaluation.Current -> {
                            prefs.availableUpdateJson.update("")
                            prefs.lastUpdateStatus.update("Atualizado")
                        }
                        is UpdateEvaluation.Rejected -> {
                            prefs.availableUpdateJson.update("")
                            prefs.lastUpdateStatus.update("Última verificação: falhou (${evaluation.reason})")
                        }
                        UpdateEvaluation.DisabledBuild -> Unit
                    }
                }
                Result.success()
            } catch (error: Exception) {
                prefs.lastUpdateCheckEpochSeconds.update(now)
                prefs.lastUpdateStatus.update("Última verificação: falhou (${error.message ?: "sem rede"})")
                Result.success()
            }
        }
    }
}

class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.BUILD_TYPE != "nightly") return Result.success()
        val prefs = MyApp.appModule.appPreferences
        val evaluation = evaluateUpdate(
            prefs.availableUpdateJson.get(),
            installedVersionCode(applicationContext),
            BuildConfig.BUILD_TYPE,
        )
        val release = (evaluation as? UpdateEvaluation.Available)?.release ?: return Result.success()
        if (!applicationContext.packageManager.canRequestPackageInstalls()) {
            prefs.lastUpdateStatus.update("Permita instalar apps desta fonte e toque em Atualizar novamente")
            return Result.failure()
        }
        val directory = File(applicationContext.cacheDir, "atualizacao").apply { mkdirs() }
        val apk = File(directory, release.asset.name)
        return withContext(Dispatchers.IO) {
            try {
                setForeground(updateForeground("Baixando ${release.tag}", 0))
                prefs.lastUpdateStatus.update(textoProgressoDownload(release.tag, 0, 0, release.asset.size))
                val connection = (URI(release.asset.downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "LifeSO-Android-Updater")
                }
                require(connection.responseCode in 200..299) { "download HTTP ${connection.responseCode}" }
                var copied = 0L
                var ultimoPercentGravado = -1
                connection.inputStream.use { input ->
                    apk.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val percent = if (release.asset.size > 0) {
                                (copied * 100L / release.asset.size).toInt().coerceIn(0, 100)
                            } else 0
                            setForegroundAsync(updateForeground("Baixando ${release.tag}", percent))
                            // A notificação de progresso some se o Bruno não estiver
                            // olhando o painel de notificações nesse instante; a tela
                            // Configurações > Atualizações precisa mostrar o mesmo
                            // número, sem regravar a cada pedaço lido.
                            if (deveAtualizarProgressoDownload(ultimoPercentGravado, percent)) {
                                ultimoPercentGravado = percent
                                prefs.lastUpdateStatus.update(
                                    textoProgressoDownload(release.tag, percent, copied, release.asset.size),
                                )
                            }
                        }
                    }
                }
                connection.disconnect()
                check(verifySha256AndSize(apk, release.asset.size, requireNotNull(release.asset.digest))) {
                    "tamanho ou SHA-256 divergente"
                }
                prefs.lastUpdateStatus.update("Download conferido; abrindo instalador")
                commitInstall(applicationContext, apk)
                Result.success()
            } catch (error: Exception) {
                apk.delete()
                prefs.lastUpdateStatus.update("Atualização falhou: ${error.message ?: "erro desconhecido"}")
                Result.failure()
            }
        }
    }
}

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Um receiver que lança sem capturar derruba o app inteiro (a tela que
        // o Bruno via fecha na hora) — nunca deixar nada escapar daqui, nem um
        // erro que eu não previ. Mesma cautela do setForeground em
        // LessonWorkers.kt (§1.3 do handoff-mãe).
        try {
            onReceiveInterno(context, intent)
        } catch (error: Exception) {
            Log.e("UpdateActionReceiver", "falha ao processar Atualizar", error)
            runCatching {
                Toast.makeText(
                    context.applicationContext,
                    "Não consegui iniciar a atualização (${error.message ?: "erro desconhecido"})",
                    Toast.LENGTH_LONG,
                ).show()
            }
            updateStatusAsync(
                MyApp.appModule.appPreferences,
                "Atualização falhou ao iniciar: ${error.message ?: "erro desconhecido"}",
            )
        }
    }

    private fun onReceiveInterno(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOWNLOAD || BuildConfig.BUILD_TYPE != "nightly") return
        if (!context.packageManager.canRequestPackageInstalls()) {
            updateStatusAsync(
                MyApp.appModule.appPreferences,
                "Permita instalar apps desta fonte e toque em Atualizar novamente",
            )
            Toast.makeText(
                context.applicationContext,
                "Permita instalar apps desta fonte e toque em Atualizar de novo",
                Toast.LENGTH_LONG,
            ).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        // Tocar em "Atualizar" não abre o app (é uma ação de notificação), então
        // sem isto o Bruno não tem nenhum sinal de que algo aconteceu — daí ele
        // tocar de novo achando que não funcionou.
        Toast.makeText(
            context.applicationContext,
            "Baixando atualização — acompanhe pela notificação",
            Toast.LENGTH_SHORT,
        ).show()
        UpdateCoordinator.enqueueDownload(context)
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val prefs = MyApp.appModule.appPreferences
        val apk = intent.getStringExtra(EXTRA_APK_PATH)?.let(::File)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmation != null) context.startActivity(confirmation)
                return
            }
            PackageInstaller.STATUS_SUCCESS -> updateStatusAsync(prefs, "Atualização instalada")
            else -> updateStatusAsync(
                prefs,
                "Instalação falhou ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}",
            )
        }
        apk?.delete()
    }
}

private fun installedVersionCode(context: Context): Long {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
}

private fun showAvailableNotification(context: Context, release: UpdateRelease) {
    ensureUpdateChannel(context)
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val action = PendingIntent.getBroadcast(
        context,
        release.buildNumber,
        Intent(context, UpdateActionReceiver::class.java).setAction(ACTION_DOWNLOAD),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Life SO ${release.tag} disponível")
        .setContentText("Toque em Atualizar para baixar e instalar")
        .addAction(0, "Atualizar", action)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
}

private fun updateForeground(text: String, percent: Int): ForegroundInfo {
    val context = MyApp.appModule.context
    ensureUpdateChannel(context)
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Atualizando Life SO")
        .setContentText(text)
        .setProgress(100, percent.coerceIn(0, 100), false)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build()
    return ForegroundInfo(UPDATE_NOTIFICATION_ID, notification)
}

private fun ensureUpdateChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(UPDATE_CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT),
    )
}

private fun commitInstall(context: Context, apk: File) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        if (Build.VERSION.SDK_INT >= 31) {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
    }
    val sessionId = installer.createSession(params)
    installer.openSession(sessionId).use { session ->
        apk.inputStream().use { input ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }
        val resultIntent = Intent(context, UpdateInstallReceiver::class.java)
            .setAction(ACTION_INSTALL_RESULT)
            .putExtra(EXTRA_APK_PATH, apk.absolutePath)
        val result = PendingIntent.getBroadcast(
            context,
            sessionId,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        session.commit(result.intentSender)
    }
}

private fun updateStatusAsync(
    prefs: io.github.wiiznokes.gitnote.data.AppPreferences,
    status: String,
) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        prefs.lastUpdateStatus.update(status)
    }
}
