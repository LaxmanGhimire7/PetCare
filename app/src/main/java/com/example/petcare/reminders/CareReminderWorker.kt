package com.example.petcare.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.SettingsPreferences

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
        val careTask = PetCareDatabase.getInstance(applicationContext)
            .careTaskDao()
            .getById(careTaskId, ownerId)

        if (careTask == null || careTask.isCompleted) {
            return Result.success()
        }

        CareReminderNotifier.show(applicationContext, careTask.id, careTask.title)
        return Result.success()
    }

    companion object {
        const val KEY_CARE_TASK_ID = "care_task_id"
        private const val INVALID_CARE_TASK_ID = -1L
    }
}
