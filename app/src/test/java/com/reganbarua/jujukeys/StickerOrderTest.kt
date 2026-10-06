package com.reganbarua.jujukeys

import com.reganbarua.jujukeys.sticker.StickerOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class StickerOrderTest {
    @Test fun reconcileKeepsOrderAndPutsNewFirst() {
        val files = mapOf("a.webp" to 1L, "b.webp" to 2L, "n1.webp" to 10L, "n2.webp" to 20L)
        assertEquals(
            listOf("n2.webp", "n1.webp", "b.webp", "a.webp"),
            StickerOrder.reconcile(listOf("gone.webp", "b.webp", "a.webp", "b.webp"), files)
        )
    }

    @Test fun addMoveRemove() {
        var o = listOf("a", "b", "c")
        o = StickerOrder.addFront(o, listOf("x", "y"))
        assertEquals(listOf("x", "y", "a", "b", "c"), o)
        o = StickerOrder.moveTop(o, "c")
        assertEquals(listOf("c", "x", "y", "a", "b"), o)
        assertEquals(o, StickerOrder.moveTop(o, "missing"))
        o = StickerOrder.remove(o, "x")
        assertEquals(listOf("c", "y", "a", "b"), o)
    }

    @Test fun parseAndFormat() {
        val o = listOf("s1.webp", "s2.webp")
        assertEquals(o, StickerOrder.parse(StickerOrder.format(o) + "\n\n  "))
        assertEquals(emptyList<String>(), StickerOrder.parse(""))
    }

    @Test fun manyStickers() {
        val files = (1..5000).associate { "s$it.webp" to it.toLong() }
        val o = StickerOrder.reconcile(emptyList(), files)
        assertEquals(5000, o.size)
        assertEquals("s5000.webp", o.first())
    }
}
