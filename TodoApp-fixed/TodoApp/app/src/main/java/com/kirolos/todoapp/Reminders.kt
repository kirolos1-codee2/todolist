package com.kirolos.todoapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderScheduler {
    const val CHANNEL_ID = "reminders"
    const val ACTION_FIRE = "com.kirolos.todoapp.REMINDER_FIRE"
    const val ACTION_DONE = "com.kirolos.todoapp.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.kirolos.todoapp.REMINDER_SNOOZE"
    const val EXTRA_ID = "task_id"
    private const val SNOOZE_MS = 10 * 60 * 1000L

    fun pendingFor(ctx: Context, id: Int, action: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun schedule(ctx: Context, t: Task) {
        cancel(ctx, t.id)
        if (t.isDone) return
        val at = t.remindAt() ?: return
        if (at <= System.currentTimeMillis()) return
        scheduleAt(ctx, t.id, at)
    }

    fun scheduleAt(ctx: Context, id: Int, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAndAllowWhileIdle: مش محتاجة صلاحية خاصة، ممكن تتأخر دقايق قليلة في وضع توفير الطاقة
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingFor(ctx, id, ACTION_FIRE))
    }

    fun snooze(ctx: Context, id: Int) = scheduleAt(ctx, id, System.currentTimeMillis() + SNOOZE_MS)

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingFor(ctx, id, ACTION_FIRE))
    }

    fun rescheduleAll(ctx: Context) {
        TaskDatabase.getInstance(ctx).taskDao().withReminders().forEach { schedule(ctx, it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, -1)
        if (id == -1) return
        val ctx = context.applicationContext
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ReminderScheduler.ACTION_DONE -> {
                        TaskActions.complete(ctx, id)
                        NotificationManagerCompat.from(ctx).cancel(id)
                    }
                    ReminderScheduler.ACTION_SNOOZE -> {
                        ReminderScheduler.snooze(ctx, id)
                        NotificationManagerCompat.from(ctx).cancel(id)
                    }
                    else -> show(ctx, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun show(ctx: Context, id: Int) {
        val t = TaskDatabase.getInstance(ctx).taskDao().byId(id) ?: return
        if (t.isDone) return
        ReminderScheduler.ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = t.notes.ifBlank { ctx.getString(R.string.reminder_default_body) }
        val n = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_todo)
            .setContentTitle(t.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, ctx.getString(R.string.action_done), ReminderScheduler.pendingFor(ctx, id, ReminderScheduler.ACTION_DONE))
            .addAction(0, ctx.getString(R.string.action_snooze), ReminderScheduler.pendingFor(ctx, id, ReminderScheduler.ACTION_SNOOZE))
            .build()
        nm.notify(id, n)
    }
}

/** بيرجّع جدولة التذكيرات بعد إعادة تشغيل الموبايل أو تحديث التطبيق */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val ctx = context.applicationContext
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { ReminderScheduler.rescheduleAll(ctx) } finally { pending.finish() }
            }
        }
    }
}
