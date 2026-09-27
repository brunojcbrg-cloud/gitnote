package io.github.wiiznokes.gitnote.aulas

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val JOB_PATH = "job_path"
private const val HISTORY_WORK = "life-so-aulas-history"
private const val NOTIFICATION_CHANNEL = "aulas_concluidas"
private const val UPLOAD_NOTIFICATION_CHANNEL = "aulas_envio"
const val LESSON_PROGRESS_FILE_INDEX = "lesson_file_index"
const val LESSON_PROGRESS_FILE_TOTAL = "lesson_file_total"
const val LESSON_PROGRESS_PERCENT = "lesson_percent"
const val LESSON_ERROR_REASON = "lesson_error_reason"
const val LESSON_ERROR_MESSAGE = "lesson_error_message"
const val LESSON_ERROR_AUTHORIZATION = "authorization"

fun lessonWorkName(idAula: String): String = "life-so-aula-$idAula"

class LessonUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString(JOB_PATH) ?: return Result.failure()
        return try {
            val job = LessonJobStore(applicationContext).read(path)
            setForeground(foregroundInfo(job, 1, job.arquivos.size, 0))
            val auth = DriveAuthorization(applicationContext).tokenBlocking()
            if (auth.resolution != null || auth.accessToken == null) {
                return Result.failure(workDataOf(
                    LESSON_ERROR_REASON to LESSON_ERROR_AUTHORIZATION,
                    LESSON_ERROR_MESSAGE to "entre de novo no Google",
                ))
            }
            var lastUpdateAt = 0L
            DriveRestClient(applicationContext, auth.accessToken).uploadLesson(job) { progress ->
                val now = SystemClock.elapsedRealtime()
                if (progress.percent == 0 || progress.percent == 100 || now - lastUpdateAt >= 1_000L) {
                    lastUpdateAt = now
                    val data = workDataOf(
                        LESSON_PROGRESS_FILE_INDEX to progress.fileIndex,
                        LESSON_PROGRESS_FILE_TOTAL to progress.fileTotal,
                        LESSON_PROGRESS_PERCENT to progress.percent,
                    )
                    setProgressAsync(data)
                    setForegroundAsync(foregroundInfo(
                        job,
                        progress.fileIndex,
                        progress.fileTotal,
                        progress.percent,
                    ))
                }
            }
            Result.success()
        } catch (error: Exception) {
            if (error is DriveHttpException && error.status in setOf(401, 403)) {
                return Result.failure(workDataOf(
                    LESSON_ERROR_REASON to LESSON_ERROR_AUTHORIZATION,
                    LESSON_ERROR_MESSAGE to "entre de novo no Google",
                ))
            }
            when (uploadFailureAction(error is IOException)) {
                UploadFailureAction.RETRY -> Result.retry()
                UploadFailureAction.FAIL -> Result.failure(workDataOf(
                    LESSON_ERROR_REASON to "other",
                    LESSON_ERROR_MESSAGE to (error.message ?: "não foi possível enviar"),
                ))
            }
        }
    }

    private fun foregroundInfo(
        job: LessonUploadJob,
        fileIndex: Int,
        fileTotal: Int,
        percent: Int,
    ): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                UPLOAD_NOTIFICATION_CHANNEL,
                "Envio de aulas",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, UPLOAD_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Enviando aula")
            .setContentText("Arquivo ${fileIndex.coerceAtLeast(1)} de ${fileTotal.coerceAtLeast(1)} — ${percent.coerceIn(0, 100)}%")
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(
            job.idAula.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        fun enqueue(context: Context, job: LessonUploadJob, replace: Boolean = false) {
            val file = LessonJobStore(context).save(job)
            val request = OneTimeWorkRequestBuilder<LessonUploadWorker>()
                .setInputData(Data.Builder().putString(JOB_PATH, file.absolutePath).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                lessonWorkName(job.idAula),
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
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
