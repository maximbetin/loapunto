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
import kotlinx.coroutines.launch

/** Schedules one alarm per entry that has a due date *and* time, and shows the notification. */
object Reminders {
    private const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.mbk.loapunto.REMIND"
    const val ACTION_DONE = "com.mbk.loapunto.MARK_DONE"
    const val EXTRA_ENTRY_ID = "entryId"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** Re-arms everything; alarms don't survive reboots. Already-passed reminders are skipped. */
    fun scheduleAll(context: Context, entries: List<Entry>) = entries.forEach { schedule(context, it.id, it.reminderAt) }

    fun sync(context: Context, old: List<Entry>, new: List<Entry>) {
        val before = old.associate { it.id to it.reminderAt }
        val after = new.associate { it.id to it.reminderAt }
        (before.keys + after.keys).forEach { id ->
            if (before[id] != after[id]) schedule(context, id, after[id])
        }
    }

    private fun schedule(context: Context, id: String, at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = broadcast(context, id, ACTION_FIRE)
        if (at == null || at <= System.currentTimeMillis()) {
            alarms.cancel(intent)
        } else if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    // The entry id lives in the data URI so each entry gets its own distinct PendingIntent.
    private fun broadcast(context: Context, id: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(action, Uri.parse("loapunto://entry/$id"), context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, entry: Entry) {
        val open = PendingIntent.getActivity(
            context,
            entry.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ENTRY_ID, entry.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = { Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground) }
        val notification = builder()
            .setContentTitle(entry.text)
            .setContentText(entry.notes.ifBlank { "Reminder" })
            .setStyle(Notification.BigTextStyle().bigText(entry.notes.ifBlank { "Reminder" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(Notification.Action.Builder(null, "Done", broadcast(context, entry.id, ACTION_DONE)).build())
            // Lock screen shows only this generic version, never the entry text.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(builder().setContentTitle("LoApunto reminder").build())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(entry.id.hashCode(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment ?: return
        val entry = EntryStore.entries.value.firstOrNull { it.id == id } ?: return
        when (intent.action) {
            Reminders.ACTION_FIRE -> if (entry.isOpen) Reminders.show(context, entry)
            Reminders.ACTION_DONE -> {
                context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
                val save = EntryStore.update(id, immediate = true) { it.copy(status = Status.DONE) }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    save.join()
                    pending.finish()
                }
            }
        }
    }
}

/** App start (which this triggers) re-arms the alarms; nothing else to do here. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
