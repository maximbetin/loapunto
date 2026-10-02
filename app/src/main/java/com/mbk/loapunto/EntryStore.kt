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
    fun add(text: String) = change(immediate = true) { listOf(Entry(text = text)) + it }

    /** Puts back an exact copy (used by undo). */
    fun upsert(entry: Entry) = change { list ->
        if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it } else list + entry
    }

    fun update(id: String, transform: (Entry) -> Entry) = change { list ->
        list.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
    }

    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    private fun change(immediate: Boolean = false, transform: (List<Entry>) -> List<Entry>) {
        _entries.update(transform)
        pendingSave?.cancel()
        pendingSave = io.launch {
            if (!immediate) delay(300) // coalesce keystrokes while editing
            persist()
        }
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
