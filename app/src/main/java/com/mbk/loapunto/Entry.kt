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
    ;

    /** Inbox, Today and Later are hand-ordered lists; Done and Trash are just history. */
    val isOrdered get() = this == INBOX || this == TODAY || this == LATER
}

/** One captured thought. Everything except [text] is optional. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    /** The title: what you typed when capturing. */
    val text: String,
    /** Optional details added later. */
    val notes: String = "",
    val status: Status = Status.INBOX,
    val due: LocalDate? = null,
    /** Only meaningful with [due]; setting it turns the due date into a reminder. */
    val dueTime: LocalTime? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** Position within its list, smallest first. Newer entries land on top by default. */
    val rank: Long = -createdAt,
) {
    val isOpen get() = status != Status.DONE && status != Status.TRASH

    /** When to notify, or null if this entry has no pending reminder. */
    val reminderAt: Long?
        get() {
            if (!isOpen || due == null || dueTime == null) return null
            return LocalDateTime.of(due, dueTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
}

/** Typed text becomes a title, and anything after the first line becomes details. */
fun splitTitle(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    val lineBreak = trimmed.indexOf('\n')
    return if (lineBreak < 0) trimmed to "" else trimmed.substring(0, lineBreak).trim() to trimmed.substring(lineBreak + 1).trim()
}

fun Entry.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("text", text)
    .put("notes", notes)
    .put("status", status.name)
    .put("due", due?.toString() ?: JSONObject.NULL)
    .put("dueTime", dueTime?.toString() ?: JSONObject.NULL)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)
    .put("rank", rank)

private fun JSONObject.optText(key: String) = optString(key).takeIf { it.isNotEmpty() && it != "null" }

fun entryFromJson(json: JSONObject): Entry {
    val createdAt = json.getLong("createdAt")
    // v0.1 kept details as extra lines of the text.
    val (title, legacyNotes) = if (json.has("notes")) json.getString("text") to json.getString("notes")
    else splitTitle(json.getString("text"))
    return Entry(
        id = json.getString("id"),
        text = title,
        notes = legacyNotes,
        status = runCatching { Status.valueOf(json.getString("status")) }.getOrDefault(Status.INBOX),
        due = json.optText("due")?.let(LocalDate::parse),
        dueTime = json.optText("dueTime")?.let(LocalTime::parse),
        createdAt = createdAt,
        updatedAt = json.optLong("updatedAt", createdAt),
        rank = json.optLong("rank", -createdAt),
    )
}
