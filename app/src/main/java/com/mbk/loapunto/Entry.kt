package com.mbk.loapunto

import androidx.annotation.StringRes
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class Status(@param:StringRes val label: Int) {
    INBOX(R.string.inbox),
    TODAY(R.string.today),
    LATER(R.string.later),
    DONE(R.string.done),
    TRASH(R.string.trash),
    ;

    /** Inbox, Today and Later are hand-ordered lists; Done and Trash are just history. */
    val isOrdered get() = this == INBOX || this == TODAY || this == LATER
}

private const val STALE_AFTER_MILLIS = 30L * 24 * 60 * 60 * 1000

/** One captured thought. Everything except [text] is optional. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    /** The title: what you typed when capturing. */
    val text: String,
    /** Optional details added later; lines starting with "[ ]" or "[x]" are checklist items. */
    val notes: String = "",
    val status: Status = Status.INBOX,
    val due: LocalDate? = null,
    /** Only meaningful with [due]; setting it turns the due date into a reminder. */
    val dueTime: LocalTime? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** Position within its list, smallest first. New entries join the bottom. */
    val rank: Long = createdAt,
    /** When it last changed list: drives "done today" and Today's left-overs. */
    val movedAt: Long = createdAt,
    /** Parked in Later until this day, then it comes back into the Inbox on its own. */
    val backOn: LocalDate? = null,
) {
    val isOpen get() = status != Status.DONE && status != Status.TRASH

    /** Open but untouched for a month: shown faded, never hidden. */
    fun isStale(now: Long = System.currentTimeMillis()) = isOpen && now - updatedAt > STALE_AFTER_MILLIS

    /**
     * In Later with no day to come back on, and untouched for a month: the kind of thing that
     * quietly becomes "later forever". Later offers to walk through these.
     */
    fun isForgotten(now: Long = System.currentTimeMillis()) =
        status == Status.LATER && backOn == null && isStale(now)

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

/**
 * Parked in Later until [day]. A due date (or reminder) set before that day moves to it, keeping
 * its time: it can't be acted on while parked, so "remind me at 9" becomes 9 on the day it's back.
 */
fun Entry.parkedUntil(day: LocalDate, now: Long = System.currentTimeMillis()): Entry = copy(
    status = Status.LATER,
    rank = now,
    movedAt = now,
    backOn = day,
    due = due?.let { if (it.isBefore(day)) day else it },
)

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
    .put("movedAt", movedAt)
    .put("backOn", backOn?.toString() ?: JSONObject.NULL)

private fun JSONObject.optText(key: String) = optString(key).takeIf { it.isNotEmpty() && it != "null" }

fun entryFromJson(json: JSONObject): Entry {
    val createdAt = json.getLong("createdAt")
    val updatedAt = json.optLong("updatedAt", createdAt)
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
        updatedAt = updatedAt,
        rank = json.optLong("rank", -createdAt),
        movedAt = json.optLong("movedAt", updatedAt),
        backOn = json.optText("backOn")?.let(LocalDate::parse),
    )
}

/**
 * Checklists are plain text: "[ ] milk" / "[x] eggs". Nothing is converted while you type;
 * only the explicit "Checklist" button and ticking a box touch the text.
 */
object Checklist {
    private val item = Regex("""^\s*\[([ xX])]\s?(.*)$""")

    /** One line of the details: [checked] is null for ordinary text. */
    data class Line(val checked: Boolean?, val text: String)

    fun parse(notes: String): List<Line> = notes.lines().map { line ->
        item.matchEntire(line)?.let { Line(it.groupValues[1] != " ", it.groupValues[2]) } ?: Line(null, line)
    }

    fun has(notes: String) = notes.lines().any { item.matches(it) }

    fun toggle(notes: String, index: Int): String = notes.lines().mapIndexed { i, line ->
        val match = item.matchEntire(line)
        if (i != index || match == null) line
        else (if (match.groupValues[1] == " ") "[x] " else "[ ] ") + match.groupValues[2]
    }.joinToString("\n")

    /** Every non-blank line becomes an unticked item; existing items and their ticks are kept. */
    fun from(notes: String): String = notes.lines().filter { it.isNotBlank() }.joinToString("\n") { line ->
        if (item.matches(line)) line.trim()
        else "[ ] " + line.trim().removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
    }

    fun add(notes: String, text: String): String =
        (if (notes.isBlank()) "" else notes.trimEnd() + "\n") + "[ ] " + text.trim()

    /** For card previews: boxes become ○ and ✓. */
    fun preview(notes: String): String = parse(notes).joinToString("\n") { line ->
        when (line.checked) {
            null -> line.text
            true -> "✓ " + line.text
            false -> "○ " + line.text
        }
    }
}
