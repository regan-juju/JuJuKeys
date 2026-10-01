package com.reganbarua.jujukeys.suggest

import com.reganbarua.jujukeys.bengali.AvroPhonetic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RomanAliasesTest {
    private val aliases = RomanAliases(File("src/main/assets/aliases_bn.txt").readLines().asSequence())

    @Test fun exactSpellings() {
        assertEquals(listOf("চট্টগ্রাম"), aliases.lookup("chottogram"))
        assertEquals(listOf("বিলাইছড়ি"), aliases.lookup("bilaichori"))
        assertEquals(listOf("বিলাইছড়ি"), aliases.lookup("Belaichari"))
        assertEquals(listOf("ঋণ"), aliases.lookup("rin"))
    }

    @Test fun prefixOnlyWhenLongEnough() {
        assertTrue(aliases.lookup("chot").isEmpty())            // ছোট must not be pushed down
        assertTrue("চট্টগ্রাম" in aliases.lookup("chott"))
        assertTrue("বিলাইছড়ি" in aliases.lookup("bilaic"))
    }

    @Test fun avroResultStaysFirst() {
        val avro = AvroPhonetic.convert("chottogram")
        val merged = RomanAliases.merge(listOf(avro, "x"), aliases.lookup("chottogram"))
        assertEquals(avro, merged[0])
        assertEquals("চট্টগ্রাম", merged[1])
        // nothing changes when there is no alias
        assertEquals(listOf("আমি", "আমার"), RomanAliases.merge(listOf("আমি", "আমার"), aliases.lookup("ami")))
    }

    @Test fun everyAliasTargetIsRealBangla() {
        assertTrue(aliases.size > 50)
        File("src/main/assets/aliases_bn.txt").readLines().filter { '\t' in it && !it.startsWith("#") }.forEach {
            val w = it.substringAfter('\t')
            assertTrue(it, w.all { c -> c in 'ঀ'..'৿' })
            assertFalse(it, it.substringBefore('\t').any { c -> c.isUpperCase() })
        }
    }
}
