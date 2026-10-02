package com.mbk.loapunto

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EntryJsonTest {
    @Test
    fun roundTripKeepsEveryField() {
        val entry = Entry(
            text = "Pharmacy\nIbuprofen",
            status = Status.TODAY,
            starred = true,
            due = LocalDate.of(2026, 10, 3),
            createdAt = 1_000,
            updatedAt = 2_000,
        )
        assertEquals(entry, entryFromJson(JSONObject(entry.toJson().toString())))
    }

    @Test
    fun missingDueAndUnknownStatusFallBack() {
        val json = JSONObject("""{"id":"x","text":"t","status":"BOGUS","due":null,"createdAt":5}""")
        val entry = entryFromJson(json)
        assertNull(entry.due)
        assertEquals(Status.INBOX, entry.status)
        assertEquals(5L, entry.updatedAt)
    }
}
