package com.mbk.loapunto

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class Status(val label: String) {
    INBOX("Inbox"),
    TODAY("Today"),
    LATER("Later"),
    DONE("Done"),
    TRASH("Trash"),
}

/** Declared most urgent first so ordinal order sorts High to the top. */
enum class Priority(val label: String) {
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low"),
}

/** One captured thought. Everything except [text] is optional triage metadata. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val status: Status = Status.INBOX,
    val priority: Priority = Priority.NORMAL,
    val due: LocalDate? = null,
    /** Only meaningful with [due]; setting it turns the due date into a reminder. */
    val dueTime: LocalTime? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    val isOpen get() = status != Status.DONE && status != Status.TRASH

    /** When to notify, or null if this entry has no pending reminder. */
    val reminderAt: Long?
        get() {
            if (!isOpen || due == null || dueTime == null) return null
            return LocalDateTime.of(due, dueTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
}

fun Entry.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("text", text)
    .put("status", status.name)
    .put("priority", priority.name)
    .put("due", due?.toString() ?: JSONObject.NULL)
    .put("dueTime", dueTime?.toString() ?: JSONObject.NULL)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

private fun JSONObject.optText(key: String) = optString(key).takeIf { it.isNotEmpty() && it != "null" }

fun entryFromJson(json: JSONObject): Entry {
    val createdAt = json.getLong("createdAt")
    // v0.1 had a star instead of priorities.
    val legacyPriority = if (json.optBoolean("starred")) Priority.HIGH else Priority.NORMAL
    return Entry(
        id = json.getString("id"),
        text = json.getString("text"),
        status = runCatching { Status.valueOf(json.getString("status")) }.getOrDefault(Status.INBOX),
        priority = json.optText("priority")?.let { runCatching { Priority.valueOf(it) }.getOrNull() } ?: legacyPriority,
        due = json.optText("due")?.let(LocalDate::parse),
        dueTime = json.optText("dueTime")?.let(LocalTime::parse),
        createdAt = createdAt,
        updatedAt = json.optLong("updatedAt", createdAt),
    )
}
