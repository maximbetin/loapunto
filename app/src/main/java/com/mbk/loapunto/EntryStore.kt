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
    fun add(text: String): Job {
        val (title, notes) = splitTitle(text)
        return change(immediate = true) { listOf(Entry(text = title, notes = notes)) + it }
    }

    /** Puts back an exact copy (used by undo). */
    fun upsert(entry: Entry) = change { list ->
        if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it } else list + entry
    }

    fun update(id: String, immediate: Boolean = false, transform: (Entry) -> Entry) = change(immediate) { list ->
        list.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
    }

    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    /** Moving to another list puts the entry at the top of it. */
    fun move(id: String, status: Status, immediate: Boolean = false) = update(id, immediate) {
        val now = System.currentTimeMillis()
        if (it.status == status) it else it.copy(status = status, rank = -now, movedAt = now)
    }

    /** Moves several entries, keeping their order, to the top of another list. */
    fun moveAll(ids: List<String>, status: Status) = change { list ->
        val now = System.currentTimeMillis()
        val position = ids.withIndex().associate { (index, id) -> id to index }
        list.map { entry ->
            position[entry.id]?.let { entry.copy(status = status, rank = -now + it, movedAt = now, updatedAt = now) } ?: entry
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

    /** Applies a new order to one list, reusing that list's existing rank values. */
    fun reorder(ids: List<String>) = change { list ->
        val idSet = ids.toSet()
        val ranks = list.filter { it.id in idSet }.map { it.rank }.sorted()
        val newRank = ids.zip(ranks).toMap()
        list.map { entry -> newRank[entry.id]?.let { entry.copy(rank = it) } ?: entry }
    }

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
