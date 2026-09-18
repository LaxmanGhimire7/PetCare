package com.example.petcare.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.petcare.data.local.PetCareDatabase

class CareReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val careTaskId = inputData.getLong(KEY_CARE_TASK_ID, INVALID_CARE_TASK_ID)
        if (careTaskId == INVALID_CARE_TASK_ID) {
            return Result.failure()
        }

        val careTask = PetCareDatabase.getInstance(applicationContext)
            .careTaskDao()
            .getById(careTaskId)

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
