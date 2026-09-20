package com.example.petcare.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.petcare.data.local.care.CareTaskEntity
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Schedules and cancels per-task WorkManager reminders. */
class CareReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(careTask: CareTaskEntity) {
        cancel(careTask.id)

        val delay = (reminderTimeMillis(careTask) - System.currentTimeMillis())
            .coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<CareReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(CareReminderWorker.KEY_CARE_TASK_ID, careTask.id)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            workName(careTask.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(careTaskId: Long) {
        workManager.cancelUniqueWork(workName(careTaskId))
    }

    /** Replaces the scheduled reminder without changing the care task due date. */
    fun snooze(careTaskId: Long) {
        val request = OneTimeWorkRequestBuilder<CareReminderWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(Data.Builder().putLong(CareReminderWorker.KEY_CARE_TASK_ID,
                careTaskId).build())
            .build()
        workManager.enqueueUniqueWork(workName(careTaskId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun reminderTimeMillis(careTask: CareTaskEntity): Long {
        val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = careTask.dueDateEpochDay * MILLIS_PER_DAY
        }
        return Calendar.getInstance().apply {
            clear()
            set(
                utcDate.get(Calendar.YEAR),
                utcDate.get(Calendar.MONTH),
                utcDate.get(Calendar.DAY_OF_MONTH),
                careTask.reminderMinutesOfDay / MINUTES_PER_HOUR,
                careTask.reminderMinutesOfDay % MINUTES_PER_HOUR,
                0
            )
        }.timeInMillis
    }

    private fun workName(careTaskId: Long): String = "care_reminder_$careTaskId"

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val MINUTES_PER_HOUR = 60
    }
}
