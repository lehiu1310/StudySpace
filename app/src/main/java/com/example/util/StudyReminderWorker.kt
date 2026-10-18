package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.data.StudyTask
import java.util.concurrent.TimeUnit

class StudyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getInt(KEY_TASK_ID, 0)
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Mục tiêu học tập" }
        val category = inputData.getString(KEY_CATEGORY).orEmpty().ifBlank { "StudySpace" }

        createChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Đến giờ học: $title")
            .setContentText("Chuyên mục $category đang chờ bạn hoàn thành.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(taskId.coerceAtLeast(1), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "study_reminders"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_TITLE = "title"
        private const val KEY_CATEGORY = "category"

        fun workName(taskId: Int): String = "study_reminder_$taskId"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nhắc lịch học theo mục tiêu StudySpace"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        fun schedule(context: Context, task: StudyTask) {
            if (task.id <= 0 || !task.isSmartReminderEnabled || task.isCompleted) {
                cancel(context, task.id)
                return
            }

            val delayMillis = StudyReminderPolicy.delayMillis(task.dueDate, System.currentTimeMillis())
            if (delayMillis == null) {
                cancel(context, task.id)
                return
            }
            val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_TASK_ID to task.id,
                        KEY_TITLE to task.title,
                        KEY_CATEGORY to task.category
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(task.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, taskId: Int) {
            if (taskId > 0) {
                WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
            }
        }
    }
}

object StudyReminderPolicy {
    const val LEAD_TIME_MILLIS = 15 * 60 * 1000L

    fun delayMillis(dueAtMillis: Long, nowMillis: Long): Long? {
        if (dueAtMillis <= nowMillis) return null
        return (dueAtMillis - LEAD_TIME_MILLIS - nowMillis).coerceAtLeast(0L)
    }
}
