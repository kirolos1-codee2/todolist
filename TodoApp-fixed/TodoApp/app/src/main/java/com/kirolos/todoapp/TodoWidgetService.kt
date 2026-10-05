package com.kirolos.todoapp

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

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
        val due = t.dueDate?.let { Dates.label(it) } ?: ""
        rv.setTextViewText(R.id.item_title, if (t.priority > 0) "${"!".repeat(t.priority)} ${t.title}" else t.title)
        rv.setTextViewText(R.id.item_due, due)
        rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(TodoWidgetProvider.EXTRA_ID, t.id))
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
