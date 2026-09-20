package com.example.petcare.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.petcare.MainActivity
import com.example.petcare.R

/** Builds reminder notifications and their mark-done and snooze actions. */
object CareReminderNotifier {

    fun show(context: Context, careTaskId: Long, careTaskTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createNotificationChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            careTaskId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("petcare://task/$careTaskId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.care_reminder_notification_title))
            .setContentText(context.getString(R.string.care_reminder_notification_text, careTaskTitle))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_check, context.getString(R.string.reminder_mark_done),
                actionIntent(context, careTaskId, CareReminderActionReceiver.ACTION_DONE, 1))
            .addAction(R.drawable.ic_alarm, context.getString(R.string.reminder_snooze),
                actionIntent(context, careTaskId, CareReminderActionReceiver.ACTION_SNOOZE, 2))
            .build()

        NotificationManagerCompat.from(context).notify(careTaskId.hashCode(), notification)
    }

    private fun actionIntent(context: Context, taskId: Long, action: String, offset: Int): PendingIntent =
        PendingIntent.getBroadcast(context, taskId.hashCode() * 3 + offset,
            Intent(context, CareReminderActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("petcare://reminder/$taskId/$offset")
                putExtra(CareReminderActionReceiver.EXTRA_TASK_ID, taskId)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.care_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private const val CHANNEL_ID = "care_reminders"
}
