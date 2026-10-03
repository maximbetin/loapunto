package com.mbk.loapunto

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate

private const val KEEP_HISTORY_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * Brings parked entries whose day has come (in the order of their days) back into the Inbox, to be
 * sorted like anything new: the day you picked says "look at this again", not "do it today". Also
 * moves a due date that fell before the day back up to it, and, when [clearHistory], lets go of
 * anything that has sat in Done or Trash for 30 days. Returns null when nothing needs to change.
 */
fun housekeep(list: List<Entry>, today: LocalDate, now: Long, clearHistory: Boolean): List<Entry>? {
    val forgetBefore = now - KEEP_HISTORY_MILLIS
    fun isDue(e: Entry) = e.status == Status.LATER && e.backOn != null && !e.backOn.isAfter(today)
    fun isOld(e: Entry) = clearHistory && !e.isOpen && e.movedAt < forgetBefore
    fun dueTooEarly(e: Entry) = e.backOn != null && e.due != null && e.due.isBefore(e.backOn)
    if (list.none { isDue(it) || isOld(it) || dueTooEarly(it) }) return null
    val waking = list.filter(::isDue).sortedBy { it.backOn }.map { it.id }
    return list.filterNot(::isOld).map { e ->
        val index = waking.indexOf(e.id)
        if (index < 0) { if (dueTooEarly(e)) e.copy(due = e.backOn) else e }
        else e.copy(status = Status.INBOX, rank = now + index, movedAt = now, updatedAt = now, backOn = null)
    }
}

/**
 * Puts [ids] in that order within their own list, by handing out the ranks those entries already
 * occupy. Entries not named keep theirs, so one list can be reordered without touching the others.
 */
fun reordered(list: List<Entry>, ids: List<String>): List<Entry> {
    val named = ids.toSet()
    val ranks = list.filter { it.id in named }.map { it.rank }.sorted()
    val newRank = ids.zip(ranks).toMap()
    return list.map { entry -> newRank[entry.id]?.let { entry.copy(rank = it) } ?: entry }
}

/**
 * All entries live in memory and are mirrored to a single JSON file.
 * Small enough for a personal inbox; no database needed.
 */
object EntryStore {
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private lateinit var file: AtomicFile
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private var pendingSave: Job? = null

    /** Called with the old and new list after every change (used to keep reminders in sync). */
    var onChange: ((old: List<Entry>, new: List<Entry>) -> Unit)? = null

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = AtomicFile(File(context.filesDir, "entries.json"))
        _entries.value = load()
    }

    // A corrupt file throws instead of being silently replaced by an empty list.
    private fun load(): List<Entry> = try {
        val array = JSONArray(String(file.readFully()))
        List(array.length()) { entryFromJson(array.getJSONObject(it)) }
    } catch (_: FileNotFoundException) {
        emptyList()
    }

    /** New captures are written straight away: the capture screen closes right after. */
    fun add(text: String, status: Status = Status.INBOX): Job {
        val (title, notes) = splitTitle(text)
        val now = System.currentTimeMillis()
        return change(immediate = true) { it + Entry(text = title, notes = notes, status = status, createdAt = now, rank = now) }
    }

    /** Puts back an exact copy (used by undo). */
    fun upsert(entry: Entry) = change { list ->
        if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it } else list + entry
    }

    fun update(id: String, immediate: Boolean = false, transform: (Entry) -> Entry) = change(immediate) { list ->
        list.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
    }

    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    /** Moving to another list puts the entry at the bottom of it, so things keep the order you sent them in. */
    fun move(id: String, status: Status, immediate: Boolean = false) = update(id, immediate) {
        val now = System.currentTimeMillis()
        if (it.status == status) it else it.copy(status = status, rank = now, movedAt = now, backOn = null)
    }

    /** Parks an entry in Later until [day]; [housekeeping] brings it back into the Inbox. */
    fun parkUntil(id: String, day: LocalDate) = update(id) { it.parkedUntil(day) }

    /**
     * Applies [housekeep] to the stored entries. Cheap; called whenever the app comes to the front
     * and before the daily nudge. Null when there was nothing to do.
     */
    fun housekeeping(clearHistory: Boolean): Job? {
        val tidied = housekeep(_entries.value, LocalDate.now(), System.currentTimeMillis(), clearHistory) ?: return null
        return change(immediate = true) { tidied }
    }

    /** Moves several entries, keeping their order, to the bottom of another list. */
    fun moveAll(ids: List<String>, status: Status) = change { list ->
        val now = System.currentTimeMillis()
        val position = ids.withIndex().associate { (index, id) -> id to index }
        list.map { entry ->
            position[entry.id]?.let { entry.copy(status = status, rank = now + it, movedAt = now, updatedAt = now, backOn = null) } ?: entry
        }
    }

    /** Today's left-overs that you chose to keep count as today's again. */
    fun keepForToday(ids: List<String>) = change { list ->
        val now = System.currentTimeMillis()
        list.map { if (it.id in ids) it.copy(movedAt = now) else it }
    }

    fun exportJson(): String = JSONArray().apply { _entries.value.forEach { put(it.toJson()) } }.toString(2)

    /** Adds the entries from a backup, replacing ones with the same id. Returns how many it read. */
    fun importJson(json: String): Int {
        val array = JSONArray(json)
        val imported = List(array.length()) { entryFromJson(array.getJSONObject(it)) }.associateBy { it.id }
        change(immediate = true) { list -> list.filterNot { it.id in imported } + imported.values }
        return imported.size
    }

    fun reorder(ids: List<String>) = change { reordered(it, ids) }

    /** Returns the save job so callers outside the UI (notification actions) can wait for it. */
    private fun change(immediate: Boolean = false, transform: (List<Entry>) -> List<Entry>): Job {
        val old = _entries.value
        _entries.update(transform)
        onChange?.invoke(old, _entries.value)
        pendingSave?.cancel()
        return io.launch {
            if (!immediate) delay(300) // coalesce keystrokes while editing
            persist()
        }.also { pendingSave = it }
    }

    private suspend fun persist() = writeLock.withLock {
        val json = JSONArray().apply { _entries.value.forEach { put(it.toJson()) } }.toString()
        val out = file.startWrite()
        try {
            out.write(json.toByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e("EntryStore", "Saving entries failed", e)
        }
    }
}
