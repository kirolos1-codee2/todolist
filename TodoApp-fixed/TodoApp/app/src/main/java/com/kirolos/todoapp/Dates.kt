package com.kirolos.todoapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Dates {
    // أسماء شهور عربي بأرقام إنجليزي
    val locale: Locale = Locale.forLanguageTag("ar-u-nu-latn")

    fun startOfDay(ms: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun plusDays(days: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay()
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    fun label(due: Long): String {
        val diff = Math.round((due - startOfDay()) / 86_400_000.0).toInt()
        return when (diff) {
            0 -> "اليوم"
            1 -> "بكرة"
            -1 -> "أمس"
            else -> SimpleDateFormat("d MMM", locale).format(Date(due))
        }
    }

    /** يوم + دقائق من بداية اليوم → millis */
    fun atTime(dayStart: Long, minutes: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = dayStart
        c.set(Calendar.HOUR_OF_DAY, minutes / 60)
        c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun timeLabel(minutes: Int): String {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, minutes / 60)
        c.set(Calendar.MINUTE, minutes % 60)
        return SimpleDateFormat("h:mm a", locale).format(c.time)
    }

    fun repeatLabel(mode: Int): String = when (mode) {
        1 -> "يوميًا"
        2 -> "أسبوعيًا"
        3 -> "شهريًا"
        else -> "بدون"
    }

    /** الموعد الجاي لمهمة متكررة (دايمًا النهارده أو بعده) */
    fun nextDue(due: Long?, mode: Int): Long {
        val today = startOfDay()
        val c = Calendar.getInstance()
        c.timeInMillis = due ?: today
        do {
            when (mode) {
                1 -> c.add(Calendar.DAY_OF_YEAR, 1)
                2 -> c.add(Calendar.WEEK_OF_YEAR, 1)
                else -> c.add(Calendar.MONTH, 1)
            }
        } while (c.timeInMillis < today)
        return startOfDay(c.timeInMillis)
    }
}

fun Task.isOverdue(): Boolean {
    val d = dueDate ?: return false
    return !isDone && d < Dates.startOfDay()
}

/** وقت التذكير بالـ millis لو المهمة ليها موعد ووقت */
fun Task.remindAt(): Long? {
    val d = dueDate ?: return null
    val m = reminderMin ?: return null
    return Dates.atTime(d, m)
}
