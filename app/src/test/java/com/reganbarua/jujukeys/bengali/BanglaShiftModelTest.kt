package com.reganbarua.jujukeys.bengali

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaShiftModelTest {
    @Test fun tapCapitalisesOnlyTheNextLetter() {
        val s = BanglaShiftModel()
        s.press(); s.release(100)
        assertTrue(s.on)
        assertTrue(s.letter(600))      // T
        assertFalse(s.on)
        assertFalse(s.letter(900))     // t after it: small
    }

    @Test fun unusedShiftSwitchesOffAfterTwoSeconds() {
        val s = BanglaShiftModel()
        s.press(); s.release(0)
        assertFalse(s.expire(1999))
        assertTrue(s.on)
        assertTrue(s.expire(2000))
        assertFalse(s.on)
        assertFalse(s.letter(3000))
    }

    @Test fun letterAfterTwoSecondsIsSmallEvenWithoutTimerCallback() {
        val s = BanglaShiftModel()
        s.press(); s.release(0)
        assertFalse(s.letter(2500))
    }

    @Test fun holdCapitalisesEveryLetterUntilRelease() {
        val s = BanglaShiftModel()
        s.press()
        assertTrue(s.letter(100)); assertTrue(s.letter(300)); assertTrue(s.letter(5000))   // SH, held long
        assertTrue(s.on)
        s.release(6000)
        assertFalse(s.on)
        assertFalse(s.letter(6100))
    }

    @Test fun holdWithoutTypingActsLikeATap() {
        val s = BanglaShiftModel()
        s.press(); s.release(3000)
        assertTrue(s.on)
        assertTrue(s.letter(3500))
        assertFalse(s.letter(3700))
    }

    @Test fun noCapsLock() {
        val s = BanglaShiftModel()
        s.press(); s.release(50); s.press(); s.release(120)    // double tap
        assertTrue(s.letter(300))
        assertFalse(s.letter(400))
        assertFalse(s.letter(500))
    }

    @Test fun bOISHomz() {
        // typed b, Shift+O, Shift+I, Shift+S, Shift+H (each a tap), o, m, z  →  bOISHomz
        val s = BanglaShiftModel()
        val out = StringBuilder()
        var t = 0L
        for (c in "bOISHomz") {
            if (c.isUpperCase()) { s.press(); s.release(t + 60); t += 250 }
            out.append(if (s.letter(t)) c.uppercaseChar() else c.lowercaseChar())
            t += 220
        }
        assertEquals("bOISHomz", out.toString())
    }

    @Test fun resetClearsEverything() {
        val s = BanglaShiftModel()
        s.press(); s.reset()
        assertFalse(s.on); assertFalse(s.held)
    }
}
