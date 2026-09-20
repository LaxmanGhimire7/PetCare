package com.example.petcare.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Applies reminder actions only to tasks owned by the currently signed-in account. */
class CareReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ownerId = AuthPreferences(app).ownerId()
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (ownerId <= 0 || taskId <= 0) return@launch
                val repository = CareTaskRepository(PetCareDatabase.getInstance(app).careTaskDao(), ownerId)
                val task = repository.getTask(taskId) ?: return@launch
                if (task.isCompleted) return@launch
                val scheduler = CareReminderScheduler(app)
                when (intent.action) {
                    ACTION_DONE -> {
                        repository.completeTask(taskId)?.let(scheduler::schedule)
                        scheduler.cancel(taskId)
                    }
                    ACTION_SNOOZE -> scheduler.snooze(taskId)
                    else -> return@launch
                }
                NotificationManagerCompat.from(app).cancel(taskId.hashCode())
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_DONE = "com.example.petcare.reminder.DONE"
        const val ACTION_SNOOZE = "com.example.petcare.reminder.SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
    }
}
