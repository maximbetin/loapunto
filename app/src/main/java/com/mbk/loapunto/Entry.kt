package com.mbk.loapunto

import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class Status(val label: String) {
    INBOX("Inbox"),
    TODAY("Today"),
    LATER("Later"),
    DONE("Done"),
    TRASH("Trash"),
}

/** One captured thought. Everything except [text] is optional triage metadata. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val status: Status = Status.INBOX,
    val starred: Boolean = false,
    val due: LocalDate? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

fun Entry.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("text", text)
    .put("status", status.name)
    .put("starred", starred)
    .put("due", due?.toString() ?: JSONObject.NULL)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

fun entryFromJson(json: JSONObject): Entry {
    val createdAt = json.getLong("createdAt")
    return Entry(
        id = json.getString("id"),
        text = json.getString("text"),
        status = runCatching { Status.valueOf(json.getString("status")) }.getOrDefault(Status.INBOX),
        starred = json.optBoolean("starred"),
        due = json.optString("due").takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDate::parse),
        createdAt = createdAt,
        updatedAt = json.optLong("updatedAt", createdAt),
    )
}
