package com.example.petcare.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.petcare.MainActivity
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone

/** Shows up to three remaining tasks and links each row to its task detail. */
class CareWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context.applicationContext) } finally { pending.finish() }
        }
    }

    override fun onEnabled(context: Context) { CareWidgetWorker.schedule(context) }
    override fun onDisabled(context: Context) { CareWidgetWorker.cancel(context) }

    companion object {
        /** No-op when the user has not added the widget. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CareWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val ownerId = AuthPreferences(context).ownerId()
            val local = Calendar.getInstance()
            val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis / 86_400_000L
            val tasks = if (ownerId > 0) PetCareDatabase.getInstance(context).careTaskDao()
                .widgetTasks(ownerId, day) else emptyList()
            val rowIds = intArrayOf(R.id.widget_row_one, R.id.widget_row_two, R.id.widget_row_three)
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_care)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
                views.setViewVisibility(R.id.widget_empty,
                    if (tasks.isEmpty()) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_empty, context.getString(
                    if (ownerId > 0) R.string.widget_empty else R.string.widget_sign_in))
                views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, 0))
                rowIds.forEachIndexed { index, rowId ->
                    val task = tasks.getOrNull(index)
                    views.setViewVisibility(rowId, if (task == null) View.GONE else View.VISIBLE)
                    if (task != null) {
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, task.reminderMinutesOfDay / 60)
                            set(Calendar.MINUTE, task.reminderMinutesOfDay % 60)
                        }
                        views.setTextViewText(rowId, context.getString(R.string.widget_task,
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time),
                            task.petName, task.title))
                        views.setOnClickPendingIntent(rowId, openIntent(context, task.id))
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun openIntent(context: Context, taskId: Long): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                if (taskId > 0) {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("petcare://task/$taskId")
                }
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(context, taskId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
