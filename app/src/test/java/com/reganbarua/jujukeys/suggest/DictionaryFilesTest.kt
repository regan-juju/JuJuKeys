package com.reganbarua.jujukeys.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/** Checks the shipped word lists: format, no duplicates, sorted, counts printed for the report. */
class DictionaryFilesTest {
    private fun rows(name: String): List<Pair<String, Long>> =
        File("src/main/assets/$name").readLines(Charsets.UTF_8)
            .filter { !it.startsWith("#") }
            .map { line ->
                assertTrue("bad line in $name: $line", Regex("^[^\\t]+\\t\\d+$").matches(line))
                line.substringBefore('\t') to line.substringAfter('\t').toLong()
            }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    /** The extra list: word, 1, key — key must equal Suggester.skeleton(word), sorted by key. */
    private fun extraRows(): List<Triple<String, Long, String>> =
        File("src/main/assets/dict_bn_avro.txt").readLines(Charsets.UTF_8)
            .filter { !it.startsWith("#") }
            .map { line ->
                val p = line.split('\t')
                assertEquals("bad line: $line", 3, p.size)
                Triple(p[0], p[1].toLong(), p[2])
            }

    @Test fun extraListIsKeyedAndSorted() {
        val ex = extraRows()
        ex.forEach { (w, _, k) -> assertEquals("key out of date for $w", Suggester.skeleton(Suggester.nfc(w)), k) }
        assertTrue("not sorted by key", ex.zipWithNext().all { (a, b) -> a.third < b.third || (a.third == b.third && a.second >= b.second) })
    }

    @Test fun formatDuplicatesAndOrder() {
        val bn = rows("dict_bn.txt"); val av = extraRows().map { it.first to it.second }; val en = rows("dict_en.txt")
        val bnWords = (bn + av).map { nfc(it.first) }
        assertEquals("duplicate Bangla words", bnWords.size, bnWords.toSet().size)
        assertEquals("duplicate English words", en.size, en.map { it.first }.toSet().size)
        listOf(bn, en).forEach { list -> assertTrue("not sorted by frequency", list.zipWithNext().all { (a, b) -> a.second >= b.second }) }
        assertTrue(av.all { it.second >= 1L })
        (bn + av).forEach { (w, _) -> assertTrue("not Bangla: $w", w.all { it in 'ঀ'..'৿' || it == '‌' || it == '‍' }) }
        println("DICT dict_bn=${bn.size} dict_bn_avro=${av.size} bangla_total=${bnWords.size} english=${en.size}")
    }

    @Test fun suggestionsFindNewWords() {
        val s = Suggester(
            File("src/main/assets/dict_en.txt").readLines().asSequence(),
            File("src/main/assets/dict_bn.txt").readLines().asSequence()
        )
        s.loadExtra(File("src/main/assets/dict_bn_avro.txt").readLines().asSequence())
        assertEquals(333429, s.extraSize)
        assertTrue("জামানত" in s.bangla("জামানত", 6))
        assertTrue("ফুচকা" in s.bangla("ফুছকা", 6))
        assertTrue("bkash" in s.english("bka", 5))
        // iPhone-style fix: jemon → যেমন; real Avro words stay as they are
        assertEquals("যেমন", s.autoFix(com.reganbarua.jujukeys.bengali.AvroPhonetic.convert("jemon")))
        assertEquals("যদি", s.autoFix(com.reganbarua.jujukeys.bengali.AvroPhonetic.convert("jodi")))
        assertEquals("যখন", s.autoFix(com.reganbarua.jujukeys.bengali.AvroPhonetic.convert("jokhon")))
        assertEquals(null, s.autoFix(com.reganbarua.jujukeys.bengali.AvroPhonetic.convert("jol")))
        assertEquals(null, s.autoFix(com.reganbarua.jujukeys.bengali.AvroPhonetic.convert("zemon")))
        // common words still win over the new rare ones
        assertEquals("আমি", s.bangla("আমি", 6).first())
    }

    // ---- extra English words (dict_en_extra.txt)

    private fun enExtra(): List<String> =
        File("src/main/assets/dict_en_extra.txt").readLines(Charsets.UTF_8).filter { !it.startsWith("#") }.map { line ->
            assertTrue("bad line: $line", Regex("^[a-z]{2,20}\\t1$").matches(line))
            line.substringBefore('\t')
        }

    @Test fun englishExtraListIsCleanSortedAndNew() {
        val ex = enExtra()
        assertEquals(90311, ex.size)
        assertEquals("duplicates", ex.size, ex.toSet().size)
        assertTrue("not sorted", ex.zipWithNext().all { (a, b) -> a < b })
        val en = rows("dict_en.txt").map { it.first.lowercase() }.toSet()
        assertTrue("already in dict_en.txt", ex.none { it in en })
    }

    @Test fun englishExtraFillsAndNeverBeatsCommonWords() {
        val s = Suggester(
            File("src/main/assets/dict_en.txt").readLines().asSequence(),
            File("src/main/assets/dict_bn.txt").readLines().asSequence()
        )
        val beforeBe = s.english("be", 6); val beforeThe = s.english("the", 3)
        s.loadExtraEnglish(File("src/main/assets/dict_en_extra.txt").readLines().asSequence())
        assertEquals(90311, s.extraEnglishSize)
        // a real rare word now completes and is known (not a typo)
        assertTrue("stratigraphy" in s.english("stratig", 3))
        assertTrue(s.known("stratigraphy"))
        assertEquals(1L, s.englishFreq("stratigraphy"))
        // common words are untouched and still first
        assertEquals(beforeThe, s.english("the", 3))
        assertEquals(beforeBe, s.english("be", 6))
        // auto-correct of a common typo is unchanged
        assertEquals("the", s.correct("teh"))
    }

    @Test fun banglaExtraKeepsOldWordsAndAddsNewOnes() {
        val s = Suggester(
            File("src/main/assets/dict_en.txt").readLines().asSequence(),
            File("src/main/assets/dict_bn.txt").readLines().asSequence()
        )
        s.loadExtra(File("src/main/assets/dict_bn_avro.txt").readLines().asSequence())
        assertTrue(s.known("অ্যালার্ম"))
        assertTrue(s.banglaFreq("আভিজাত্যের") == 1L)
        // Wikipedia counts rank the extra words: the common one comes first
        val ex = extraRows().associate { it.first to it.second }
        assertTrue("ranking missing", ex.values.count { it > 1 } > 50_000)
        assertTrue("new frequent words missing", ex.size - 191366 > 100_000)
        assertTrue(ex.entries.filter { it.value > 1 }.all { it.value >= 1 })
    }
}
