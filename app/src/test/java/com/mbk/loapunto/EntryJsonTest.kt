package com.mbk.loapunto

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EntryJsonTest {
    @Test
    fun roundTripKeepsEveryField() {
        val entry = Entry(
            text = "First line\nSecond line",
            status = Status.TODAY,
            priority = Priority.HIGH,
            due = LocalDate.of(2026, 10, 3),
            dueTime = LocalTime.of(9, 30),
            createdAt = 1_000,
            updatedAt = 2_000,
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
        assertEquals(Priority.NORMAL, entry.priority)
        assertEquals(5L, entry.updatedAt)
    }

    @Test
    fun legacyStarBecomesHighPriority() {
        val json = JSONObject("""{"id":"x","text":"t","status":"INBOX","starred":true,"createdAt":5}""")
        assertEquals(Priority.HIGH, entryFromJson(json).priority)
    }

    @Test
    fun onlyOpenEntriesWithDateAndTimeHaveReminders() {
        val base = Entry(text = "t", due = LocalDate.of(2026, 10, 3), dueTime = LocalTime.NOON)
        assert(base.reminderAt != null)
        assertNull(base.copy(dueTime = null).reminderAt)
        assertNull(base.copy(status = Status.DONE).reminderAt)
        assertNull(base.copy(status = Status.TRASH).reminderAt)
    }
}
