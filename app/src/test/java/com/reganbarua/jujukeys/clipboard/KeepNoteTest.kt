package com.reganbarua.jujukeys.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepNoteTest {
    @Test fun joinPutsADividerBetweenItems() {
        assertEquals("a\n\n———\n\nb\n\n———\n\nc", KeepNote.join(listOf("a", "b", "c")))
        assertEquals("only", KeepNote.join(listOf("only")))
    }

    @Test fun splitUndoesJoinEvenWhenAnItemHasBlankLines() {
        val items = listOf("প্রথম লেখা", "line one\n\nline two after a blank line", "তৃতীয়")
        val r = KeepNote.split(KeepNote.join(items))
        assertTrue(r.bySeparator)
        assertEquals(items, r.items)
    }

    @Test fun oldNotesWithoutDividerSplitAtBlankLines() {
        val r = KeepNote.split("one\n\ntwo\nstill two\n\n\n\nthree")
        assertFalse(r.bySeparator)
        assertEquals(listOf("one", "two\nstill two", "three"), r.items)
    }

    @Test fun windowsLineEndingsAndSpacesAreHandled() {
        val r = KeepNote.split("a\r\n\r\n  ———  \r\n\r\nb\r\n")
        assertEquals(listOf("a", "b"), r.items)
    }

    @Test fun titleLineAndEmptyPiecesAndRepeatsAreDropped() {
        val r = KeepNote.split("${KeepNote.TITLE}\n\n———\n\n———\n\nx\n\n———\n\nx\n\n———\n\ny")
        assertEquals(listOf("x", "y"), r.items)
    }

    @Test fun emptyNoteGivesNothing() {
        assertEquals(emptyList<String>(), KeepNote.split("  \n\n ").items)
        assertEquals(emptyList<String>(), KeepNote.split("").items)
    }

    @Test fun aDividerInsideALineIsNotADivider() {
        val r = KeepNote.split("a ——— b\n\n———\n\nc")
        assertEquals(listOf("a ——— b", "c"), r.items)
    }
}
