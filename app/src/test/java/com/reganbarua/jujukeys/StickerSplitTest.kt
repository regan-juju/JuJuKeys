package com.reganbarua.jujukeys

import com.reganbarua.jujukeys.sticker.StickerSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerSplitTest {
    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val RED = 0xFFE03030.toInt()

    /** 3×3 stickers (red disc + white outline) on black; two of them joined by a thin white bar. */
    private fun sheet(w: Int = 330): IntArray {
        val px = IntArray(w * w) { BLACK }
        val c = w / 3
        for (gy in 0 until 3) for (gx in 0 until 3) {
            val cx = gx * c + c / 2; val cy = gy * c + c / 2
            for (y in 0 until w) for (x in 0 until w) {
                val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                if (d2 <= 40 * 40) px[y * w + x] = RED else if (d2 <= 47 * 47) px[y * w + x] = WHITE
            }
        }
        for (y in c / 2 - 2..c / 2 + 2) for (x in c / 2..c + c / 2) if (px[y * w + x] == BLACK) px[y * w + x] = WHITE
        return px
    }

    @Test fun darkSheetBecomesNineStickers() {
        val w = 330
        val px = sheet(w)
        assertTrue(StickerSplit.darkBackgroundToAlpha(px, w, w))
        assertEquals(0, px[0])                                       // corner is see-through now
        val parts = StickerSplit.split(px, w, w)
        assertEquals(9, parts.size)
        parts.forEach { assertTrue("${it.w}x${it.h}", it.w in 90..110 && it.h in 90..110) }
    }

    @Test fun transparentSheetSplitsToo() {
        val w = 330
        val px = sheet(w).map { if (it == BLACK) 0 else it }.toIntArray()
        assertEquals(9, StickerSplit.split(px, w, w).size)
    }

    @Test fun singleStickerStaysOne() {
        val w = 200
        val px = IntArray(w * w)
        for (y in 0 until w) for (x in 0 until w) {
            val d2 = (x - 100) * (x - 100) + (y - 100) * (y - 100)
            if (d2 <= 70 * 70) px[y * w + x] = RED else if (d2 <= 78 * 78) px[y * w + x] = WHITE
        }
        val parts = StickerSplit.split(px, w, w)
        assertEquals(1, parts.size)
        assertEquals(157, parts[0].w)
    }

    @Test fun ordinaryDarkPhotoIsLeftAlone() {
        val w = 200
        // dark edges, a grey (not white-outlined) object in the middle
        val px = IntArray(w * w) { i -> val x = i % w; val y = i / w
            if (x in 60..140 && y in 60..140) 0xFF707070.toInt() else BLACK }
        val copy = px.copyOf()
        assertFalse(StickerSplit.darkBackgroundToAlpha(px, w, w))
        assertTrue(px.contentEquals(copy))
    }
}
