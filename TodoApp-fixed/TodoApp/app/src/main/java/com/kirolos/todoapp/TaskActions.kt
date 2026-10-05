package com.kirolos.todoapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * كل عمليات المهام في مكان واحد، عشان التطبيق والويدجت والإشعارات
 * كلهم يتصرفوا بنفس الطريقة (التكرار، التذكيرات، تحديث الويدجت).
 */
object TaskActions {

    private fun dao(c: Context) = TaskDatabase.getInstance(c).taskDao()

    suspend fun save(ctx: Context, t: Task) = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        val saved = if (t.id == 0) t.copy(id = dao(c).insert(t).toInt()) else { dao(c).update(t); t }
        ReminderScheduler.schedule(c, saved)
        TodoWidgetProvider.refresh(c)
    }

    suspend fun toggle(ctx: Context, t: Task) = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        if (!t.isDone && t.repeatMode != 0) {
            // مهمة متكررة: بدل ما تتقفل بننقلها للموعد الجاي
            val next = t.copy(dueDate = Dates.nextDue(t.dueDate, t.repeatMode))
            dao(c).update(next)
            ReminderScheduler.schedule(c, next)
        } else {
            val n = t.copy(isDone = !t.isDone)
            dao(c).update(n)
            if (n.isDone) ReminderScheduler.cancel(c, n.id) else ReminderScheduler.schedule(c, n)
        }
        TodoWidgetProvider.refresh(c)
    }

    /** بتقفل المهمة بس لو لسه مقفولة (للويدجت والإشعار) */
    suspend fun complete(ctx: Context, id: Int) = withContext(Dispatchers.IO) {
        val t = dao(ctx.applicationContext).byId(id)
        if (t != null && !t.isDone) toggle(ctx, t)
    }

    suspend fun delete(ctx: Context, t: Task) = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        dao(c).delete(t)
        ReminderScheduler.cancel(c, t.id)
        TodoWidgetProvider.refresh(c)
    }

    /** تراجع عن الحذف: بنرجع المهمة بنفس الـ id */
    suspend fun restore(ctx: Context, t: Task) = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        dao(c).insert(t)
        ReminderScheduler.schedule(c, t)
        TodoWidgetProvider.refresh(c)
    }

    suspend fun clearDone(ctx: Context) = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        dao(c).clearDone()
        TodoWidgetProvider.refresh(c)
    }
}
