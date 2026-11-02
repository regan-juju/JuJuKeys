package com.reganbarua.jujukeys.bengali

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingGuideTest {
    @Test fun everyConjunctInTheGuideIsTypedByItsCode() {
        for ((bn, roman) in TypingGuide.conjuncts) assertEquals("code $roman", bn, AvroPhonetic.convert(roman))
    }

    @Test fun theGuideHasNoDuplicateCodes() {
        val codes = TypingGuide.conjuncts.map { it.second }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test fun partialTypingShowsTheStepsAndEndsOnTheConjunct() {
        assertEquals("", TypingGuide.partial("kkh", 0))
        assertEquals("ক", TypingGuide.partial("kkh", 1))
        assertEquals("ক্ষ", TypingGuide.partial("kkh", 3))
        assertTrue(TypingGuide.conjuncts.size >= 40)
    }
}
