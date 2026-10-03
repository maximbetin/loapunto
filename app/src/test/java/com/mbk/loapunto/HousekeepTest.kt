package com.mbk.loapunto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HousekeepTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun parkedEntriesComeBackIntoTodayOnTheirDayEarliestFirst() {
        val wednesday = Entry(id = "wed", text = "a").parkedUntil(monday.plusDays(2), now = 0)
        val sunday = Entry(id = "sun", text = "b").parkedUntil(monday.minusDays(1), now = 0)
        val mon = Entry(id = "mon", text = "c").parkedUntil(monday, now = 0)

        val result = housekeep(listOf(mon, wednesday, sunday), monday, now, clearHistory = false)!!.associateBy { it.id }

        assertEquals(Status.LATER, result.getValue("wed").status)
        assertEquals(Status.TODAY, result.getValue("sun").status)
        assertEquals(Status.TODAY, result.getValue("mon").status)
        assertNull(result.getValue("mon").backOn)
        // The one parked for the earlier day sits above.
        assert(result.getValue("sun").rank < result.getValue("mon").rank)
    }

    @Test
    fun plainLaterStaysPut() {
        val later = Entry(text = "someday", status = Status.LATER, createdAt = 0)
        assertNull(housekeep(listOf(later), monday, now, clearHistory = true))
    }

    @Test
    fun historyIsClearedAfter30DaysOnlyWhenOn() {
        val oldDone = Entry(id = "done", text = "a", status = Status.DONE, createdAt = 0)
        val oldTrash = Entry(id = "trash", text = "b", status = Status.TRASH, createdAt = 0)
        val recentDone = Entry(id = "recent", text = "c", status = Status.DONE, createdAt = now - day)
        val oldOpen = Entry(id = "open", text = "d", status = Status.INBOX, createdAt = 0)
        val all = listOf(oldDone, oldTrash, recentDone, oldOpen)

        assertEquals(listOf("recent", "open"), housekeep(all, monday, now, clearHistory = true)!!.map { it.id })
        assertNull(housekeep(all, monday, now, clearHistory = false))
    }
}
