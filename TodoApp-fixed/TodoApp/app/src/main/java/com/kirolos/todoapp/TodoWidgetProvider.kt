package com.kirolos.todoapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getIntExtra(EXTRA_ID, -1)
            if (id != -1) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        TaskDatabase.getInstance(context).taskDao().toggleById(id)
                        refresh(context)
                    } finally { pending.finish() }
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.kirolos.todoapp.TOGGLE"
        const val EXTRA_ID = "task_id"

        // لازم تتنادى من thread خلفي (بتقرا من الداتابيز)
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val count = TaskDatabase.getInstance(context).taskDao().pendingCountNow()
            for (id in ids) mgr.updateAppWidget(id, build(context, id, count))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun build(context: Context, widgetId: Int, count: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_todo)
            v.setTextViewText(R.id.widget_count, if (count == 0) "مفيش مهام متبقية" else "$count متبقية")

            val svc = Intent(context, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            v.setRemoteAdapter(R.id.widget_list, svc)
            v.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val toggle = Intent(context, TodoWidgetProvider::class.java).apply { action = ACTION_TOGGLE }
            v.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getBroadcast(context, 0, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            )

            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openNew = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            v.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(context, 1, open, flags))
            v.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 2, openNew, flags))
            return v
        }
    }
}
