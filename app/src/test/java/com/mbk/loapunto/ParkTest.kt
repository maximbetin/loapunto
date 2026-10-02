package com.mbk.loapunto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ParkTest {
    private val friday = LocalDate.of(2026, 10, 2)
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun earlierReminderMovesToTheDayBackKeepingItsTime() {
        val parked = Entry(text = "Call", due = friday, dueTime = LocalTime.of(9, 0)).parkedUntil(monday)
        assertEquals(Status.LATER, parked.status)
        assertEquals(monday, parked.backOn)
        assertEquals(monday, parked.due)
        assertEquals(LocalTime.of(9, 0), parked.dueTime)
    }

    @Test
    fun laterDueDateAndNoDueDateAreLeftAlone() {
        val nextWeek = monday.plusDays(7)
        assertEquals(nextWeek, Entry(text = "a", due = nextWeek).parkedUntil(monday).due)
        assertEquals(null, Entry(text = "b").parkedUntil(monday).due)
    }
}
