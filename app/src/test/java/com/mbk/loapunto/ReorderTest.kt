package com.mbk.loapunto

import org.junit.Assert.assertEquals
import org.junit.Test

/** Dragging a card must change only that list, and must survive a reload: order lives in ranks. */
class ReorderTest {
    private fun entry(id: String, rank: Long, status: Status = Status.INBOX) =
        Entry(id = id, text = id, status = status, createdAt = rank, rank = rank)

    private fun order(list: List<Entry>, status: Status = Status.INBOX) =
        list.filter { it.status == status }.sortedBy { it.rank }.map { it.id }

    @Test
    fun theDraggedOrderIsWhatYouReadBack() {
        val list = listOf(entry("a", 10), entry("b", 20), entry("c", 30))

        assertEquals(listOf("c", "a", "b"), order(reordered(list, listOf("c", "a", "b"))))
    }

    @Test
    fun otherListsKeepTheirRanks() {
        val later = entry("parked", 5, Status.LATER)
        val list = listOf(entry("a", 10), later, entry("b", 20))

        val result = reordered(list, listOf("b", "a")).associateBy { it.id }

        assertEquals(5L, result.getValue("parked").rank)
        assertEquals(listOf("b", "a"), order(result.values.toList()))
    }

    @Test
    fun ranksAreReusedSoTheyCannotDriftApart() {
        val list = listOf(entry("a", 10), entry("b", 20), entry("c", 30))

        val result = reordered(list, listOf("b", "c", "a"))

        assertEquals(listOf(10L, 20L, 30L), result.map { it.rank }.sorted())
    }
}
