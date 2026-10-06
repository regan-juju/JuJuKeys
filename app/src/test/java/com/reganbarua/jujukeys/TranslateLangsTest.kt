package com.reganbarua.jujukeys

import com.reganbarua.jujukeys.translate.TranslateLangs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateLangsTest {
    @Test fun userFavouritesComeFirst() {
        val fromBn = TranslateLangs.targetsFor("bn").map { it.code }
        assertEquals(listOf("de", "ru", "ja", "pt", "en"), fromBn.take(5))
        assertFalse("bn" in fromBn)
        val fromEn = TranslateLangs.targetsFor("en").map { it.code }
        assertEquals(listOf("de", "ru", "ja", "pt", "bn"), fromEn.take(5))
        assertFalse("en" in fromEn)
        assertEquals(fromBn.size, fromBn.toSet().size)            // no duplicates
    }

    @Test fun labelsAndOnlineOnly() {
        assertEquals("ENGLISH", TranslateLangs.label("en"))
        assertEquals("Deutsch", TranslateLangs.label("de"))
        assertFalse(TranslateLangs.byCode("ne").offline)
        assertTrue(TranslateLangs.byCode("ja").offline)
        assertTrue(TranslateLangs.all.size > 55)
    }
}
