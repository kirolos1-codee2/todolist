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
}

fun Task.isOverdue(): Boolean {
    val d = dueDate ?: return false
    return !isDone && d < Dates.startOfDay()
}
