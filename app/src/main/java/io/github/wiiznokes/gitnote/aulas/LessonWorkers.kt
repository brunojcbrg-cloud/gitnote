package io.github.wiiznokes.gitnote.aulas

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val JOB_PATH = "job_path"
private const val HISTORY_WORK = "life-so-aulas-history"
private const val NOTIFICATION_CHANNEL = "aulas_concluidas"

class LessonUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString(JOB_PATH) ?: return Result.failure()
        return try {
            val job = LessonJobStore(applicationContext).read(path)
            val auth = DriveAuthorization(applicationContext).tokenBlocking()
            if (auth.resolution != null || auth.accessToken == null) return Result.retry()
            DriveRestClient(applicationContext, auth.accessToken).uploadLesson(job)
            Result.success()
        } catch (error: Exception) {
            when (uploadFailureAction(error is IOException)) {
                UploadFailureAction.RETRY -> Result.retry()
                UploadFailureAction.FAIL -> Result.failure()
            }
        }
    }

    companion object {
        fun enqueue(context: Context, job: LessonUploadJob) {
            val file = LessonJobStore(context).save(job)
            val request = OneTimeWorkRequestBuilder<LessonUploadWorker>()
                .setInputData(Data.Builder().putString(JOB_PATH, file.absolutePath).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "life-so-aula-${job.idAula}", ExistingWorkPolicy.KEEP, request,
            )
        }
    }
}

class LessonHistoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val auth = DriveAuthorization(applicationContext).tokenBlocking()
            if (auth.resolution != null || auth.accessToken == null) return Result.retry()
            val state = DriveRestClient(applicationContext, auth.accessToken).downloadState()
            notifyNewCompletions(state)
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun notifyNewCompletions(state: MobileLessonState) {
        val preferences = applicationContext.getSharedPreferences("life_so_aulas", Context.MODE_PRIVATE)
        val previous = preferences.getStringSet("known_completed", null)
        val completed = state.aulas.filter { it.status == "concluida" }.map { it.idAula }.toSet()
        if (previous != null) {
            state.aulas.filter { it.status == "concluida" && it.idAula !in previous }.forEach { lesson ->
                showNotification(lesson)
            }
        }
        preferences.edit().putStringSet("known_completed", completed).apply()
    }

    private fun showNotification(lesson: MobileLesson) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "Aulas concluidas", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val triage = if (lesson.pendenteTriagem) " · aguardando classificacao" else ""
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(lesson.nomeFinal.ifBlank { "Aula concluida" })
            .setContentText("${lesson.materia.ifBlank { "Materia ainda nao definida" }}$triage")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(lesson.idAula.hashCode(), notification)
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LessonHistoryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                HISTORY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }
    }
}
