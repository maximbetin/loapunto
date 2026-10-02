package com.mbk.loapunto

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Two kinds of notification: one alarm per entry that has a due date *and* time, and an optional
 * daily nudge that only ever shows counts.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val NUDGE_CHANNEL = "nudge"
    private const val NUDGE_ID = 1
    const val ACTION_FIRE = "com.mbk.loapunto.REMIND"
    const val ACTION_DONE = "com.mbk.loapunto.MARK_DONE"
    const val ACTION_SNOOZE_HOUR = "com.mbk.loapunto.SNOOZE_HOUR"
    const val ACTION_SNOOZE_DAY = "com.mbk.loapunto.SNOOZE_DAY"
    const val ACTION_NUDGE = "com.mbk.loapunto.NUDGE"
    const val EXTRA_ENTRY_ID = "entryId"

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(NUDGE_CHANNEL, context.getString(R.string.daily_nudge), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Re-arms everything; alarms don't survive reboots. Already-passed reminders are skipped. */
    fun scheduleAll(context: Context, entries: List<Entry>) {
        entries.forEach { schedule(context, it.id, it.reminderAt) }
        scheduleNudge(context)
    }

    fun sync(context: Context, old: List<Entry>, new: List<Entry>) {
        val before = old.associate { it.id to it.reminderAt }
        val after = new.associate { it.id to it.reminderAt }
        (before.keys + after.keys).forEach { id ->
            if (before[id] != after[id]) schedule(context, id, after[id])
        }
    }

    private fun schedule(context: Context, id: String, at: Long?) =
        setAlarm(context, broadcast(context, id, ACTION_FIRE), at?.takeIf { it > System.currentTimeMillis() })

    private fun setAlarm(context: Context, intent: PendingIntent, at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        when {
            at == null -> alarms.cancel(intent)
            alarms.canScheduleExactAlarms() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    // The entry id lives in the data URI so each entry gets its own distinct PendingIntent.
    private fun broadcast(context: Context, id: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(action, Uri.parse("loapunto://entry/$id"), context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context, entryId: String?): PendingIntent = PendingIntent.getActivity(
        context,
        entryId?.hashCode() ?: NUDGE_ID,
        Intent(context, MainActivity::class.java)
            .apply { if (entryId != null) putExtra(EXTRA_ENTRY_ID, entryId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, entry: Entry) {
        fun action(label: Int, action: String) =
            Notification.Action.Builder(null, context.getString(label), broadcast(context, entry.id, action)).build()
        val builder = { Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground) }
        val body = entry.notes.ifBlank { context.getString(R.string.reminder) }
        val notification = builder()
            .setContentTitle(entry.text)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context, entry.id))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(action(R.string.done, ACTION_DONE))
            .addAction(action(R.string.snooze_hour, ACTION_SNOOZE_HOUR))
            .addAction(action(R.string.snooze_tomorrow, ACTION_SNOOZE_DAY))
            // Lock screen shows only this generic version, never the entry text.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(builder().setContentTitle(context.getString(R.string.reminder_public)).build())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(entry.id.hashCode(), notification)
    }

    fun scheduleNudge(context: Context) {
        val intent = PendingIntent.getBroadcast(
            context,
            NUDGE_ID,
            Intent(ACTION_NUDGE, null, context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = Nudge.time(context)?.let { time ->
            val now = LocalDateTime.now()
            val today = LocalDate.now().atTime(time)
            (if (today.isAfter(now)) today else today.plusDays(1)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        setAlarm(context, intent, at)
    }

    /** Counts only, so it's fine on the lock screen. Says nothing when there's nothing to do. */
    fun showNudge(context: Context, entries: List<Entry>) {
        val today = entries.count { it.status == Status.TODAY }
        val inbox = entries.count { it.status == Status.INBOX }
        if (today == 0 && inbox == 0) return
        val title = listOfNotNull(
            context.getString(R.string.nudge_today, today).takeIf { today > 0 },
            context.getString(R.string.nudge_inbox, inbox).takeIf { inbox > 0 },
        ).joinToString(" · ")
        val notification = Notification.Builder(context, NUDGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentIntent(openApp(context, null))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NUDGE_ID, notification)
    }
}

/** The daily nudge time, or none. On at 09:00 until changed. */
object Nudge {
    private const val KEY = "nudgeTime"
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun time(context: Context): LocalTime? =
        prefs(context).getString(KEY, "09:00")?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse)

    fun set(context: Context, time: LocalTime?) {
        prefs(context).edit().putString(KEY, time?.toString().orEmpty()).apply()
        Reminders.scheduleNudge(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION_NUDGE) {
            // Housekeeping first, so entries due back today are counted in Today.
            val pending = goAsync()
            val save = EntryStore.housekeeping()
            Reminders.showNudge(context, EntryStore.entries.value)
            Reminders.scheduleNudge(context)
            if (save == null) pending.finish() else save.invokeOnCompletion { pending.finish() }
            return
        }
        val id = intent.data?.lastPathSegment ?: return
        val entry = EntryStore.entries.value.firstOrNull { it.id == id } ?: return
        if (intent.action == Reminders.ACTION_FIRE) {
            if (entry.isOpen) Reminders.show(context, entry)
            return
        }
        val save = when (intent.action) {
            Reminders.ACTION_DONE -> EntryStore.move(id, Status.DONE, immediate = true)
            Reminders.ACTION_SNOOZE_HOUR -> {
                val at = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                EntryStore.update(id, immediate = true) { it.copy(due = at.toLocalDate(), dueTime = at.toLocalTime()) }
            }
            Reminders.ACTION_SNOOZE_DAY -> {
                val time = entry.dueTime ?: LocalTime.now().withSecond(0).withNano(0)
                EntryStore.update(id, immediate = true) { it.copy(due = LocalDate.now().plusDays(1), dueTime = time) }
            }
            else -> return
        }
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
        finishAfter(save)
    }

    private fun finishAfter(save: Job) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            save.join()
            pending.finish()
        }
    }
}

/** App start (which this triggers) re-arms the alarms; nothing else to do here. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
