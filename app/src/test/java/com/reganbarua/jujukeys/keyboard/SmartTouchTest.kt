package com.reganbarua.jujukeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartTouchTest {
    // one row of three 100 px letter keys, then a SPACE (not a letter)
    private val l = floatArrayOf(0f, 100f, 200f, 300f)
    private val t = floatArrayOf(0f, 0f, 0f, 0f)
    private val r = floatArrayOf(100f, 200f, 300f, 400f)
    private val isChar = booleanArrayOf(true, true, true, false)

    @Test fun middleOfAKeyHasNoNeighbour() {
        assertEquals(-1, TouchPick.neighbor(l, t, r, isChar, 1, 150f, 0.25f))
    }
    @Test fun nearTheRightEdgeThe_NextKeyIsTheNeighbour() {
        assertEquals(2, TouchPick.neighbor(l, t, r, isChar, 1, 180f, 0.25f))
    }
    @Test fun nearTheLeftEdgeThePreviousKeyIsTheNeighbour() {
        assertEquals(0, TouchPick.neighbor(l, t, r, isChar, 1, 120f, 0.25f))
    }
    @Test fun noNeighbourPastTheEndOrOnANonLetterKey() {
        assertEquals(-1, TouchPick.neighbor(l, t, r, isChar, 0, 5f, 0.25f))      // left of the first key
        assertEquals(-1, TouchPick.neighbor(l, t, r, isChar, 2, 290f, 0.25f))    // next key is SPACE
    }
    @Test fun aKeyOnAnotherRowIsNotANeighbour() {
        val t2 = floatArrayOf(0f, 100f, 100f, 100f)
        assertEquals(-1, TouchPick.neighbor(l, t2, r, isChar, 0, 95f, 0.25f))
    }

    // pretend dictionary: cat is common, car is common too, "cax" is nothing
    private val words = mapOf("cat" to 1000L, "car" to 900L, "cab" to 100L, "ca" to 5000L, "c" to 9000L, "do" to 100L, "dog" to 100L)
    private val score: (String) -> Long = { p -> words.filterKeys { it.startsWith(p) }.values.maxOrNull() ?: 0L }

    @Test fun noLettersTypedYetKeepsTheTouchedKey() {
        assertEquals('r', SmartTouch.choose("", 'r', 't', score))
    }
    @Test fun onlyOneLetterMakesAWordPicksIt() {
        assertEquals('t', SmartTouch.choose("ca", 'x', 't', score))
        assertEquals('r', SmartTouch.choose("ca", 'r', 'x', score))
    }
    @Test fun bothPossibleAndSimilarKeepsTheTouchedKey() {
        assertEquals('r', SmartTouch.choose("ca", 'r', 't', score))      // 900 vs 1000: not 4x
    }
    @Test fun clearlyLikelierOtherLetterWins() {
        assertEquals('t', SmartTouch.choose("ca", 'b', 't', score))      // 1000 >= 4 x 100
    }
    @Test fun neitherMakesAWordKeepsTheTouchedKey() {
        assertEquals('x', SmartTouch.choose("ca", 'x', 'z', score))
    }
    @Test fun notAPlainWordNoGuess() {
        assertEquals('r', SmartTouch.choose("c4", 'r', 't', score))
        assertEquals('r', SmartTouch.choose("a".repeat(41), 'r', 't', score))
    }
}
