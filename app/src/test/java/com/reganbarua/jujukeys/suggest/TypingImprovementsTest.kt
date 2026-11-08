package com.reganbarua.jujukeys.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.69: corrections that respect the keyboard, and suggestions that look at the previous word. */
class TypingImprovementsTest {
    @Test fun neighbourKeysAreRecognised() {
        assertTrue(KeyNeighbors.adjacent('g', 'h'))
        assertTrue(KeyNeighbors.adjacent('h', 'g'))
        assertTrue(KeyNeighbors.adjacent('q', 'w'))
        assertTrue(KeyNeighbors.adjacent('q', 'a'))      // lower row: column c and c + 1 of the row above
        assertTrue(KeyNeighbors.adjacent('w', 'a'))
        assertTrue(KeyNeighbors.adjacent('a', 'z'))
        assertTrue(KeyNeighbors.adjacent('s', 'z'))
        assertTrue(KeyNeighbors.adjacent('o', 'p'))
        assertTrue(KeyNeighbors.adjacent('G', 'h'))      // case does not matter
        assertFalse(KeyNeighbors.adjacent('g', 'j'))
        assertFalse(KeyNeighbors.adjacent('q', 'p'))
        assertFalse(KeyNeighbors.adjacent('q', 's'))
        assertFalse(KeyNeighbors.adjacent('a', 'a'))
        assertFalse(KeyNeighbors.adjacent('a', 'e'))     // two rows apart
        assertFalse(KeyNeighbors.adjacent('1', 'q'))
    }

    @Test fun adjacencyIsSymmetric() {
        for (a in 'a'..'z') for (b in 'a'..'z') assertEquals("$a$b", KeyNeighbors.adjacent(a, b), KeyNeighbors.adjacent(b, a))
    }

    @Test fun everyLetterHasBetweenTwoAndSixNeighbours() {
        for (a in 'a'..'z') {
            val n = ('a'..'z').count { KeyNeighbors.adjacent(a, it) }
            assertTrue("$a has $n", n in 2..6)
        }
    }

    private fun english() = Suggester(
        sequenceOf("hello\t1000", "jello\t1200", "the\t5000", "ten\t900", "help\t800", "world\t700"),
        sequenceOf("আমি\t500", "আমার\t400"),
    )

    @Test fun slipToNeighbourBeatsMoreFrequentFarKey() {
        // g is next to h, not next to j — "jello" is the commoner word but a far-away slip
        assertEquals("hello", english().correct("gello"))
    }

    @Test fun swapStillWorks() {
        assertEquals("the", english().correct("teh"))
    }

    @Test fun realWordIsLeftAlone() {
        assertNull(english().correct("hello"))
    }

    @Test fun farKeyCorrectionIsStillMadeWhenItIsTheOnlyCandidate() {
        val s = Suggester(sequenceOf("world\t700"), sequenceOf("আমি\t500"))
        assertEquals("world", s.correct("wxrld"))          // x is far from o, but nothing else fits
    }

    @Test fun banglaWordThatFollowedThePreviousWordComesFirst() {
        val s = Suggester(
            sequenceOf("the\t100"),
            sequenceOf("ভালো\t900", "ভাত\t5000", "ভাষা\t3000"),
        )
        val plain = s.bangla("ভা", 3)
        assertEquals("ভাত", plain[1])                         // no context: by frequency
        val withContext = s.bangla("ভা", 3, mapOf("ভালো" to 2))
        assertEquals("ভালো", withContext[1])                  // "আমি ভালো" was typed before
    }

    @Test fun englishWordThatFollowedThePreviousWordComesFirst() {
        val s = english()
        assertEquals("help", s.english("hel", 3, mapOf("help" to 1)).first())
        assertEquals("hello", s.english("hel", 3).first())
    }

    @Test fun learnerGivesNextCounts() {
        val l = Learner()
        l.learn("আমি", "ভালো"); l.learn("আমি", "ভালো"); l.learn("আমি", "তোমাকে")
        assertEquals(mapOf("ভালো" to 2, "তোমাকে" to 1), l.nextCounts("আমি"))
        assertTrue(l.nextCounts(null).isEmpty())
        assertTrue(l.nextCounts("অজানা").isEmpty())
        l.nextCounts("আমি").let { (it as MutableMap).clear() }   // a copy: the learner is untouched
        assertEquals(2, l.nextCounts("আমি")["ভালো"])
    }
}
