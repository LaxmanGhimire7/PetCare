package com.example.petcare.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.SettingsPreferences
import com.example.petcare.MainActivity
import com.example.petcare.design.InboxType
import com.example.petcare.design.PcNotifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Delivers a due reminder only while its task and account are still valid. */
class CareReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val careTaskId = inputData.getLong(KEY_CARE_TASK_ID, INVALID_CARE_TASK_ID)
        if (careTaskId == INVALID_CARE_TASK_ID) {
            return Result.failure()
        }
        if (!SettingsPreferences(applicationContext).notificationsEnabled()) return Result.success()

        val ownerId = AuthPreferences(applicationContext).ownerId()
        if (ownerId <= 0) return Result.success()
        val database = PetCareDatabase.getInstance(applicationContext)
        val careTask = database.careTaskDao().getById(careTaskId, ownerId)

        if (careTask == null || careTask.isCompleted) {
            return Result.success()
        }

        val pet = database.petDao().getById(careTask.petId, ownerId) ?: return Result.success()
        val dueMillis = dueMillis(careTask.dueDateEpochDay, careTask.reminderMinutesOfDay)
        val overdueCheck = inputData.getBoolean(KEY_OVERDUE_CHECK, false)
        if (overdueCheck && System.currentTimeMillis() < dueMillis + OVERDUE_AFTER_MS) return Result.success()

        val type = if (overdueCheck) {
            InboxType.OVERDUE
        } else if (isHealthTask(careTask.category, careTask.title)) {
            InboxType.HEALTH
        } else {
            InboxType.REMINDER
        }
        val body = if (overdueCheck) {
            "${pet.name}'s ${careTask.title.lowercase(Locale.getDefault())} is overdue."
        } else {
            "Time for ${pet.name}'s ${careTask.title.lowercase(Locale.getDefault())}."
        }
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(dueMillis))
        PcNotifier.post(
            context = applicationContext,
            type = type,
            title = careTask.title,
            body = body,
            contentIntent = PcNotifier.openAppIntent(applicationContext, MainActivity::class.java, careTask.id, careTask.id.hashCode()),
            taskId = careTask.id,
            petId = pet.id,
            petColorIndex = pet.colorIndex,
            dedupeKey = if (overdueCheck) "overdue:${careTask.id}:$dayKey" else "reminder:${careTask.id}:$dueMillis",
        )
        return Result.success()
    }

    private fun isHealthTask(category: String, title: String): Boolean {
        val words = "$category $title".lowercase(Locale.ROOT)
        return listOf("health", "vet", "vaccin", "medicat", "pill", "flea").any(words::contains)
    }

    private fun dueMillis(day: Long, minutes: Int): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = day * 86_400_000L
        }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), minutes / 60, minutes % 60)
        }.timeInMillis
    }

    companion object {
        const val KEY_CARE_TASK_ID = "care_task_id"
        const val KEY_OVERDUE_CHECK = "overdue_check"
        private const val INVALID_CARE_TASK_ID = -1L
        private const val OVERDUE_AFTER_MS = 60L * 60 * 1000
    }
}
