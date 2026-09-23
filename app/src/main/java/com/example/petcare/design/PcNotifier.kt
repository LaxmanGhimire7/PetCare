package com.example.petcare.design

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.petcare.R

/**
 * One call for every alert: it is saved to the in-app notification centre AND shown
 * in the phone's notification shade (when the user has allowed notifications).
 *
 * Call [createChannels] once in Application.onCreate. Use [post] from your reminder
 * WorkManager worker, the SMS share flow, and anywhere else the user should be told something.
 */
object PcNotifier {

    const val CHANNEL_REMINDERS = "pc_reminders"
    const val CHANNEL_HEALTH = "pc_health"
    const val CHANNEL_UPDATES = "pc_updates"

    /** Extra on the launch intent: the task to open when a notification is tapped. */
    const val EXTRA_TASK_ID = "pc_task_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(context, CHANNEL_REMINDERS, R.string.pc_channel_reminders, R.string.pc_channel_reminders_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(context, CHANNEL_HEALTH, R.string.pc_channel_health, R.string.pc_channel_health_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(context, CHANNEL_UPDATES, R.string.pc_channel_updates, R.string.pc_channel_updates_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** True when a system notification would actually be shown. */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Saves to the notification centre and shows a system notification.
     * Returns the stored item, or null if [dedupeKey] was already used.
     */
    @SuppressLint("MissingPermission") // checked by canPost() before notify()
    fun post(
        context: Context,
        type: InboxType,
        title: String,
        body: String,
        contentIntent: PendingIntent? = null,
        taskId: Long? = null,
        petId: Long? = null,
        petColorIndex: Int? = null,
        dedupeKey: String? = null,
    ): InboxItem? {
        val item = InboxStore.get(context).add(type, title, body, taskId, petId, petColorIndex, dedupeKey) ?: return null
        if (!canPost(context)) return item

        val builder = NotificationCompat.Builder(context, channelFor(type))
            .setSmallIcon(iconFor(type))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(ContextCompat.getColor(context, R.color.pc_primary))
            .setAutoCancel(true)
            .setPriority(
                if (type == InboxType.DELEGATION || type == InboxType.ACCOUNT) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_HIGH
                },
            )
            .setCategory(
                if (type == InboxType.REMINDER) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS,
            )
        if (contentIntent != null) builder.setContentIntent(contentIntent)

        try {
            NotificationManagerCompat.from(context).notify(item.id.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: the in-app item still exists.
        }
        return item
    }

    /** A tap target that opens [activity] (usually MainActivity) and passes the task id. */
    fun openAppIntent(context: Context, activity: Class<*>, taskId: Long?, requestCode: Int): PendingIntent {
        val intent = Intent(context, activity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TASK_ID, taskId ?: -1L)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun channelFor(type: InboxType): String = when (type) {
        InboxType.REMINDER -> CHANNEL_REMINDERS
        InboxType.OVERDUE, InboxType.HEALTH -> CHANNEL_HEALTH
        InboxType.DELEGATION, InboxType.ACCOUNT -> CHANNEL_UPDATES
    }

    @DrawableRes
    internal fun iconFor(type: InboxType): Int = when (type) {
        InboxType.REMINDER -> R.drawable.pc_ic_bell
        InboxType.OVERDUE -> R.drawable.pc_ic_alert
        InboxType.HEALTH -> R.drawable.pc_ic_vaccine
        InboxType.DELEGATION -> R.drawable.pc_ic_message
        InboxType.ACCOUNT -> R.drawable.pc_ic_shield
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun channel(context: Context, id: String, name: Int, description: Int, importance: Int) =
        NotificationChannel(id, context.getString(name), importance).apply {
            this.description = context.getString(description)
        }
}

/**
 * Asks for the Android 13+ notification permission. Create it as a property of the
 * fragment (so it registers before the fragment starts), then call [requestIfNeeded]
 * once, e.g. the first time Today appears after sign-in.
 */
class NotificationPermissionRequester(fragment: Fragment, onResult: (granted: Boolean) -> Unit = {}) {

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
    }

    fun requestIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
