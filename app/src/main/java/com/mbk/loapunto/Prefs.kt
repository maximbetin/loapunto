package com.mbk.loapunto

import android.content.Context
import java.time.LocalTime

/** The handful of settings, in one small file. */
private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

/** The daily nudge time, or none. On at 09:00 until changed. */
object Nudge {
    private const val KEY = "nudgeTime"

    fun time(context: Context): LocalTime? =
        prefs(context).getString(KEY, "09:00")?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse)

    fun set(context: Context, time: LocalTime?) {
        prefs(context).edit().putString(KEY, time?.toString().orEmpty()).apply()
        Reminders.scheduleNudge(context)
    }
}

/** Whether Done and Trash let go of entries after 30 days. On unless turned off. */
object ClearHistory {
    private const val KEY = "clearHistory"

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY, true)

    fun set(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY, on).apply()
}

/**
 * Gestures you have already used. A hint is worth showing until you know it; after that it is
 * just furniture, so the app says it once and then shuts up about it.
 */
object Learned {
    const val HOLD = "learnedHold"
    const val DRAG = "learnedDrag"

    fun has(context: Context, gesture: String): Boolean = prefs(context).getBoolean(gesture, false)

    fun mark(context: Context, gesture: String) = prefs(context).edit().putBoolean(gesture, true).apply()
}
