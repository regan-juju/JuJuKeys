package com.reganbarua.jujukeys.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PairsAndSlipsTest {
    @Test fun bigramsLoadAndRank() {
        val b = StaticBigrams()
        b.load("আমি\tভালো তোমাকে যাব\nতুমি\tকেমন\n\nbad line without tab")
        assertEquals(2, b.size)
        assertEquals(listOf("ভালো", "তোমাকে"), b.next("আমি", 2))
        assertEquals(mapOf("ভালো" to 5, "তোমাকে" to 4, "যাব" to 3), b.ranks("আমি"))
        assertEquals(emptyList<String>(), b.next("অজানা", 3))
        assertEquals(emptyList<String>(), b.next(null, 3))
        assertEquals(emptyMap<String, Int>(), b.ranks(""))
    }

    @Test fun learnerRemembersOnlySingleLetterSlips() {
        val l = Learner()
        l.learnSlip("hrllo", "hello")       // r → e : one letter
        l.learnSlip("hrllo", "hello")
        l.learnSlip("teh", "the")           // two letters differ → not a slip
        l.learnSlip("cat", "cats")          // different length
        l.learnSlip("abc", "abc")           // same word
        l.learnSlip("a1c", "abc")           // not letters only
        assertEquals(mapOf("re" to 2), l.snapshotSlips())
    }

    @Test fun slipsSurviveSaveAndLoadAndClear() {
        val l = Learner()
        l.learnSlip("gello", "hello"); l.learn("hello", "world")
        val l2 = Learner(); l2.load(l.serialize())
        assertEquals(mapOf("gh" to 1), l2.snapshotSlips())
        assertEquals(1, l2.count("world"))
        l2.clear()
        assertTrue(l2.snapshotSlips().isEmpty())
    }

    @Test fun ownSlipsRaiseTheWeightButOnlyAfterTwice() {
        assertEquals(KeyNeighbors.FAR_KEY, KeyNeighbors.replaceWeight('g', 'p', mapOf("gp" to 1)), 1e-9)
        val two = KeyNeighbors.replaceWeight('g', 'p', mapOf("gp" to 2))
        assertTrue(two > KeyNeighbors.FAR_KEY && two <= 1.0)
        assertEquals(1.0, KeyNeighbors.replaceWeight('g', 'p', mapOf("gp" to 9)), 1e-9)       // a far key never beats a neighbour
        assertEquals(1.0, KeyNeighbors.replaceWeight('g', 'h', mapOf("gh" to 1)), 1e-9)
        assertEquals(KeyNeighbors.MAX_BOOST, KeyNeighbors.replaceWeight('g', 'h', mapOf("gh" to 50)), 1e-9)
    }

    @Test fun shippedPairListIsWellFormed() {
        val lines = File("src/main/assets/bigram_bn.txt").readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertTrue("too few lines: ${lines.size}", lines.size > 10_000)
        val bangla = Regex("^[\\u0980-\\u09FF\\u200c\\u200d]+$")
        val seen = HashSet<String>()
        for (line in lines) {
            val p = line.split('\t')
            assertEquals("bad line: $line", 2, p.size)
            assertTrue("repeated word: ${p[0]}", seen.add(p[0]))
            assertTrue("not Bangla: ${p[0]}", bangla.matches(p[0]))
            val next = p[1].split(' ')
            assertTrue("1..5 next words: $line", next.size in 1..5)
            next.forEach { assertTrue("not Bangla: $it", bangla.matches(it)) }
        }
        val b = StaticBigrams(); b.load(lines.joinToString("\n"))
        assertTrue(b.next("আমি", 5).isNotEmpty())
    }
}
