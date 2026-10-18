package com.yesmin.ai

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
import java.util.Calendar

data class Reminder(
    val id: Long,
    val title: String,
    val note: String,
    val timeMillis: Long,
    /** none | daily | weekly */
    val repeat: String,
    val done: Boolean
)

object ReminderScheduler {
    const val CHANNEL = "yesmin_reminders"
    const val ACTION_FIRE = "com.yesmin.ai.REMINDER_FIRE"
    const val ACTION_FIRE_ONCE = "com.yesmin.ai.REMINDER_FIRE_ONCE"
    const val ACTION_SNOOZE = "com.yesmin.ai.REMINDER_SNOOZE"
    const val ACTION_DONE = "com.yesmin.ai.REMINDER_DONE"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Yesmin AI reminders"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun offset(action: String): Int = when (action) {
        ACTION_SNOOZE -> 200000
        ACTION_DONE -> 400000
        ACTION_FIRE_ONCE -> 600000
        else -> 0
    }

    private fun pending(ctx: Context, id: Long, action: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, (id % 100000).toInt() + offset(action), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun setAlarm(ctx: Context, time: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun schedule(ctx: Context, r: Reminder) {
        if (r.done) return
        setAlarm(ctx, r.timeMillis, pending(ctx, r.id, ACTION_FIRE))
    }

    /** One extra alarm (used for snooze) that only shows the notification and does not touch the repeat schedule. */
    fun scheduleOnce(ctx: Context, id: Long, time: Long) {
        setAlarm(ctx, time, pending(ctx, id, ACTION_FIRE_ONCE))
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, id, ACTION_FIRE))
        am.cancel(pending(ctx, id, ACTION_FIRE_ONCE))
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
    }

    fun nextTime(r: Reminder, now: Long): Long {
        if (r.repeat == "none") return r.timeMillis
        val c = Calendar.getInstance().apply { timeInMillis = r.timeMillis }
        val field = if (r.repeat == "weekly") Calendar.WEEK_OF_YEAR else Calendar.DAY_OF_YEAR
        while (c.timeInMillis <= now) c.add(field, 1)
        return c.timeInMillis
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, r: Reminder) {
        createChannel(ctx)
        val hide = Prefs(ctx).hideReminderText
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pub = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Yesmin AI reminder")
            .build()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (hide) "Yesmin AI reminder" else r.title)
            .setContentText(if (hide) "Ek reminder aaya hai" else r.note.ifBlank { "Reminder" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(pub)
            .addAction(0, "Snooze 10 min", pending(ctx, r.id, ACTION_SNOOZE))
            .addAction(0, "Done", pending(ctx, r.id, ACTION_DONE))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(r.id.toInt(), n)
        } catch (e: SecurityException) {
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val db = MemoryDb(ctx)
        val r = db.reminder(id) ?: return
        val now = System.currentTimeMillis()
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                ReminderScheduler.notify(ctx, r)
                if (r.repeat != "none") {
                    val nt = ReminderScheduler.nextTime(r, now)
                    db.updateReminderTime(id, nt)
                    ReminderScheduler.schedule(ctx, r.copy(timeMillis = nt))
                }
            }
            ReminderScheduler.ACTION_FIRE_ONCE -> ReminderScheduler.notify(ctx, r)
            ReminderScheduler.ACTION_SNOOZE -> {
                NotificationManagerCompat.from(ctx).cancel(id.toInt())
                val nt = now + 10 * 60_000L
                if (r.repeat == "none") {
                    db.updateReminderTime(id, nt)
                    db.setReminderDone(id, false)
                    ReminderScheduler.schedule(ctx, r.copy(timeMillis = nt, done = false))
                } else {
                    ReminderScheduler.scheduleOnce(ctx, id, nt)
                }
            }
            ReminderScheduler.ACTION_DONE -> {
                db.setReminderDone(id, true)
                ReminderScheduler.cancel(ctx, id)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val db = MemoryDb(ctx)
        val now = System.currentTimeMillis()
        for (r in db.reminders()) {
            if (r.done) continue
            if (r.timeMillis > now) {
                ReminderScheduler.schedule(ctx, r)
            } else if (r.repeat != "none") {
                val nt = ReminderScheduler.nextTime(r, now)
                db.updateReminderTime(r.id, nt)
                ReminderScheduler.schedule(ctx, r.copy(timeMillis = nt))
            }
        }
    }
}
