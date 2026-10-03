package com.mbk.loapunto

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EntryJsonTest {
    @Test
    fun roundTripKeepsEveryField() {
        val entry = Entry(
            text = "Title",
            notes = "Some details\nacross lines",
            status = Status.TODAY,
            due = LocalDate.of(2026, 10, 3),
            dueTime = LocalTime.of(9, 30),
            createdAt = 1_000,
            updatedAt = 2_000,
            rank = 42,
            movedAt = 1_500,
            backOn = LocalDate.of(2026, 10, 6),
        )
        assertEquals(entry, entryFromJson(JSONObject(entry.toJson().toString())))
    }

    @Test
    fun missingFieldsAndUnknownStatusFallBack() {
        val json = JSONObject("""{"id":"x","text":"t","status":"BOGUS","due":null,"createdAt":5}""")
        val entry = entryFromJson(json)
        assertNull(entry.due)
        assertNull(entry.dueTime)
        assertEquals(Status.INBOX, entry.status)
        assertEquals(5L, entry.updatedAt)
    }

    @Test
    fun legacyMultiLineTextSplitsIntoTitleAndNotes() {
        val json = JSONObject("""{"id":"x","text":"First\nSecond\nThird","status":"INBOX","createdAt":5}""")
        val entry = entryFromJson(json)
        assertEquals("First", entry.text)
        assertEquals("Second\nThird", entry.notes)
    }

    @Test
    fun onlyOpenEntriesWithDateAndTimeHaveReminders() {
        val base = Entry(text = "t", due = LocalDate.of(2026, 10, 3), dueTime = LocalTime.NOON)
        assertNotNull(base.reminderAt)
        assertNull(base.copy(dueTime = null).reminderAt)
        assertNull(base.copy(status = Status.DONE).reminderAt)
        assertNull(base.copy(status = Status.TRASH).reminderAt)
    }
}
