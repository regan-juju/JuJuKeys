package com.reganbarua.jujukeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchPickTest {
    // two rows of 100 px keys: row 0 = A B C (y 0..100), row 1 = D E (char) + SPACE (y 100..200)
    private val l = floatArrayOf(0f, 100f, 200f, 0f, 100f, 200f)
    private val t = floatArrayOf(0f, 0f, 0f, 100f, 100f, 100f)
    private val r = floatArrayOf(100f, 200f, 300f, 100f, 200f, 300f)
    private val b = floatArrayOf(100f, 100f, 100f, 200f, 200f, 200f)
    private val gap = BooleanArray(6)
    private val isChar = booleanArrayOf(true, true, true, true, true, false)

    private fun pick(x: Float, y: Float, off: Float) = TouchPick.pick(l, t, r, b, gap, isChar, x, y, off)

    @Test fun insideAKeyIsThatKey() {
        assertEquals(1, pick(150f, 50f, 0f))
        assertEquals(4, pick(150f, 150f, 0f))
    }

    @Test fun offsetMovesAGapTouchOfTheLowerRowUpToTheRowAbove() {
        assertEquals(4, pick(150f, 105f, 0f))                                          // no offset: lower row
        assertEquals(1, TouchPick.pick(l, t, r, b, gap, isChar, 150f, 105f, 12f, 10f)) // in the gap, finger landed low: the key above
    }

    @Test fun offsetKeepsAMidKeyTouchOnItsKey() {
        assertEquals(4, pick(150f, 150f, 12f))
    }

    @Test fun aTouchOnTheDrawnKeyIsNeverMovedToTheRowAbove() {
        // the drawn key starts 10 px inside its cell: y = 115 is on the key, y = 105 is in the gap
        assertEquals(4, TouchPick.pick(l, t, r, b, gap, isChar, 150f, 115f, 12f, 10f))
        assertEquals(1, TouchPick.pick(l, t, r, b, gap, isChar, 150f, 105f, 12f, 10f))
    }

    @Test fun withoutOffsetTheGapIsSplitAtTheRowLine() {
        assertEquals(1, TouchPick.pick(l, t, r, b, gap, isChar, 150f, 99f, 0f, 10f))
        assertEquals(4, TouchPick.pick(l, t, r, b, gap, isChar, 150f, 101f, 0f, 10f))
    }

    @Test fun nonLetterKeysKeepTheExactPoint() {
        assertEquals(5, pick(250f, 105f, 12f))    // top edge of SPACE stays SPACE
    }

    @Test fun touchNearTheTopOfTheKeyboardIsNeverLost() {
        assertEquals(0, pick(50f, 3f, 12f))
    }

    @Test fun gapGoesToTheNearestKeyInTheRow() {
        val g = booleanArrayOf(false, true, false, false, false, false)   // B is a gap
        assertEquals(0, TouchPick.hit(l, t, r, b, g, 120f, 50f))
        assertEquals(2, TouchPick.hit(l, t, r, b, g, 190f, 50f))
    }
}
