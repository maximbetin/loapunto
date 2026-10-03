package com.mbk.loapunto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What Later offers to walk you through, so nothing becomes later forever by accident. */
class ForgottenTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val longAgo = now - 40 * day

    private fun parked(updatedAt: Long, backOn: LocalDate? = null) =
        Entry(text = "someday", status = Status.LATER, createdAt = updatedAt, backOn = backOn)

    @Test
    fun datelessAndUntouchedForAMonthCountsAsForgotten() {
        assertTrue(parked(longAgo).isForgotten(now))
    }

    @Test
    fun aDayToComeBackOnOrARecentLookDoesNot() {
        assertFalse(parked(longAgo, backOn = LocalDate.of(2026, 12, 1)).isForgotten(now))
        assertFalse(parked(now - day).isForgotten(now))
    }

    @Test
    fun onlyLaterIsForgettable() {
        assertFalse(parked(longAgo).copy(status = Status.INBOX).isForgotten(now))
        assertFalse(parked(longAgo).copy(status = Status.DONE).isForgotten(now))
    }
}
