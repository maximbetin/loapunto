package com.mbk.loapunto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    @Test
    fun plainTextIsNotAChecklist() {
        assertFalse(Checklist.has("Groceries\n- milk"))
    }

    @Test
    fun convertsLinesAndKeepsExistingTicks() {
        assertEquals("[ ] milk\n[x] eggs\n[ ] bread", Checklist.from("- milk\n\n[x] eggs\n  bread "))
    }

    @Test
    fun toggleOnlyTouchesThatLine() {
        val notes = "Shop\n[ ] milk\n[x] eggs"
        assertEquals("Shop\n[x] milk\n[x] eggs", Checklist.toggle(notes, 1))
        assertEquals("Shop\n[ ] milk\n[ ] eggs", Checklist.toggle(notes, 2))
        assertEquals(notes, Checklist.toggle(notes, 0))
    }

    @Test
    fun addAndPreview() {
        val notes = Checklist.add(Checklist.add("", "milk"), "eggs")
        assertEquals("[ ] milk\n[ ] eggs", notes)
        assertTrue(Checklist.has(notes))
        assertEquals("✓ milk\n○ eggs", Checklist.preview(Checklist.toggle(notes, 0)))
    }
}
