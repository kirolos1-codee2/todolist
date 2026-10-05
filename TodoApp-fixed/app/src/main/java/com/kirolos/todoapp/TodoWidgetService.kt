package com.kirolos.todoapp

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory =
        TodoWidgetFactory(applicationContext)
}

private class TodoWidgetFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<Task> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = TaskDatabase.getInstance(ctx).taskDao().pendingNow()
    }

    override fun onDestroy() { items = emptyList() }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
        val t = items.getOrNull(position) ?: return rv

        // لون الدايرة حسب الأولوية
        val ring = when (t.priority) {
            3 -> R.color.widget_red
            2 -> R.color.widget_orange
            else -> R.color.widget_accent
        }
        rv.setTextColor(R.id.item_check, ContextCompat.getColor(ctx, ring))
        rv.setTextViewText(R.id.item_title, if (t.priority > 0) "${"!".repeat(t.priority)} ${t.title}" else t.title)

        val due = t.dueDate
        val dueText = if (due == null) "" else {
            Dates.label(due) + (t.reminderMin?.let { " " + Dates.timeLabel(it) } ?: "")
        }
        rv.setTextViewText(R.id.item_due, dueText)
        rv.setTextColor(
            R.id.item_due,
            ContextCompat.getColor(ctx, if (t.isOverdue()) R.color.widget_red else R.color.widget_secondary)
        )
        rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(TodoWidgetProvider.EXTRA_ID, t.id))
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
