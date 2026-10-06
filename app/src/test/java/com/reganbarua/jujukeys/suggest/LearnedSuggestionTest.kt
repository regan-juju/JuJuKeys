package com.reganbarua.jujukeys.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Words the user types often show up — even when they are not in the dictionary. */
class LearnedSuggestionTest {
    private fun suggester() = Suggester(
        sequenceOf("hello\t100", "help\t80", "the\t1000"),
        sequenceOf("আমি\t500", "আমার\t400", "বিলাস\t50"),
    )

    @Test fun banglaWordNotInDictionaryIsSuggested() {
        val s = suggester()
        assertFalse(s.bangla("বিলা", 5).contains("বিলাইছড়ি"))
        s.learnedCounts = mapOf("বিলাইছড়ি" to 2)
        assertTrue(s.bangla("বিলা", 5).contains("বিলাইছড়ি"))
        assertFalse(s.known("বিলাইছড়ি"))
        assertTrue(s.known("আমি"))
    }

    @Test fun englishWordTypedOftenComesEarly() {
        val s = suggester()
        s.learnedCounts = mapOf("helmand" to 4)
        assertTrue(s.english("hel", 3).contains("helmand"))
    }

    @Test fun favouritesForAnEmptyBar() {
        val s = suggester()
        s.learnedCounts = mapOf("কিস্তি" to 9, "সমিতি" to 5, "ঋণ" to 7, "একবার" to 1, "loan" to 6)
        assertEquals(listOf("কিস্তি", "ঋণ", "সমিতি"), s.favourites(true, 3))
        assertEquals(listOf("loan"), s.favourites(false, 3))
    }

    @Test fun learnerCountsWords() {
        val l = Learner()
        l.learn(null, "বিলাইছড়ি"); assertEquals(1, l.count("বিলাইছড়ি"))
        l.learn("আমি", "বিলাইছড়ি"); assertEquals(2, l.count("বিলাইছড়ি"))
    }
}
