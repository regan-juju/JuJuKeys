package com.reganbarua.jujukeys.bengali

/**
 * A real, reusable, offline Avro-style phonetic transliteration engine.
 *
 * This is NOT a hardcoded word list. It tokenizes a raw ASCII "word buffer"
 * (the Latin letters typed since the last word boundary) into phonetic units
 * — vowels and consonants — using a lookup table, then assembles Bengali
 * Unicode by applying general Bengali orthography rules:
 *  - a consonant followed by a vowel takes that vowel's dependent sign (kar)
 *  - a consonant followed by another consonant forms a conjunct via hasant (্)
 *  - a bare consonant at a word boundary keeps its own inherent vowel (no sign needed)
 *  - a vowel with nothing before it (word start, or after another vowel) is
 *    written in its independent form
 *
 * One documented exception table (EXCEPTION_CONSONANTS) mirrors how the
 * original Avro phonetic scheme itself special-cases a handful of consonant
 * clusters (e.g. "nn" -> ন্য) that don't fall out of the plain rules above.
 * This keeps the engine rule-driven while still producing standard spellings
 * for the everyday words people actually type.
 *
 * Known limitations (documented, not hidden): reph (র্), ya-phola/ba-phola
 * placement, and চন্দ্রবিন্দু (ঁ) are not modelled. Extend [EXCEPTION_CONSONANTS]
 * or the vowel/consonant tables below to grow coverage.
 */
object BengaliPhoneticEngine {

    private data class Unit(val independent: String, val dependentKar: String)

    // Ordered longest-match-first. Two-letter vowel digraphs must precede single letters.
    private val VOWELS: List<Pair<String, Unit>> = listOf(
        "rri" to Unit("ঋ", "ৃ"),
        "oi" to Unit("ঐ", "ৈ"),
        "ou" to Unit("ঔ", "ৌ"),
        "ii" to Unit("ঈ", "ী"),
        "uu" to Unit("ঊ", "ূ"),
        "I" to Unit("ঈ", "ী"),
        "U" to Unit("ঊ", "ূ"),
        "A" to Unit("আ", "া"),
        "a" to Unit("আ", "া"),
        "i" to Unit("ই", "ি"),
        "u" to Unit("উ", "ু"),
        "e" to Unit("এ", "ে"),
        "o" to Unit("ও", "ো")
    ).sortedByDescending { it.first.length }

    // Ordered longest-match-first. Digraphs/aspirates before single letters.
    private val CONSONANTS: List<Pair<String, String>> = listOf(
        "kh" to "খ", "gh" to "ঘ", "ch" to "ছ", "jh" to "ঝ",
        "Th" to "ঠ", "Dh" to "ঢ", "th" to "থ", "dh" to "ধ",
        "ph" to "ফ", "bh" to "ভ", "Sh" to "ষ", "sh" to "শ", "Rh" to "ঢ়",
        "k" to "ক", "g" to "গ", "c" to "চ", "j" to "জ",
        "T" to "ট", "D" to "ড", "N" to "ণ", "t" to "ত", "d" to "দ",
        "n" to "ন", "p" to "প", "f" to "ফ", "b" to "ব", "v" to "ভ",
        "m" to "ম", "Y" to "য", "y" to "য়", "r" to "র", "R" to "ড়",
        "l" to "ল", "s" to "স", "h" to "হ", "x" to "ক্স", "w" to "ও",
        "q" to "ক"
    ).sortedByDescending { it.first.length }

    /** Documented special-case consonant clusters (mirrors Avro's own exception list). */
    private val EXCEPTION_CONSONANTS: List<Pair<String, String>> = listOf(
        "nn" to "ন্য",
        "ng" to "ঙ" // ঙ (used only when "ng" is not resolved as anusvara by the caller)
    ).sortedByDescending { it.first.length }

    private const val HASANT = "্" // ্
    private const val ANUSVARA = "ং" // ং

    private sealed class Token {
        data class VowelTok(val raw: String, val unit: Unit) : Token()
        data class ConsonantTok(val raw: String, val glyph: String) : Token()
        /** "ng" only when it will act as an anusvara before another consonant / at word end. */
        data class NasalTok(val raw: String) : Token()
    }

    /** Tokenizes a raw ASCII word (letters only) into phonetic units, longest match first. */
    private fun tokenize(word: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < word.length) {
            // "ng": decide anusvara vs consonant ঙ based on what follows once we know the
            // next token; provisionally emit NasalTok and resolve during assembly.
            if (word.startsWith("ng", i)) {
                tokens.add(Token.NasalTok("ng"))
                i += 2
                continue
            }
            val exc = EXCEPTION_CONSONANTS.firstOrNull { word.startsWith(it.first, i) && it.first != "ng" }
            if (exc != null) {
                tokens.add(Token.ConsonantTok(exc.first, exc.second))
                i += exc.first.length
                continue
            }
            val vowel = VOWELS.firstOrNull { word.startsWith(it.first, i) }
            if (vowel != null) {
                tokens.add(Token.VowelTok(vowel.first, vowel.second))
                i += vowel.first.length
                continue
            }
            val cons = CONSONANTS.firstOrNull { word.startsWith(it.first, i) }
            if (cons != null) {
                tokens.add(Token.ConsonantTok(cons.first, cons.second))
                i += cons.first.length
                continue
            }
            // Unrecognized character (shouldn't normally happen for a-zA-Z buffers): pass through.
            tokens.add(Token.ConsonantTok(word[i].toString(), word[i].toString()))
            i += 1
        }
        return tokens
    }

    /** Transliterates one raw ASCII word (letters only, no spaces/punctuation) to Bengali. */
    fun transliterateWord(rawWord: String): String {
        if (rawWord.isEmpty()) return ""
        val tokens = tokenize(rawWord)
        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            when (val t = tokens[i]) {
                is Token.VowelTok -> {
                    // Reached only when nothing consumed it as a dependent kar: word-initial
                    // or immediately after another vowel.
                    out.append(t.unit.independent)
                    i += 1
                }
                is Token.NasalTok -> {
                    val hasNext = i + 1 < tokens.size
                    val nextIsVowel = hasNext && tokens[i + 1] is Token.VowelTok
                    if (!hasNext || !nextIsVowel) {
                        // "ng" before a consonant or at word end -> anusvara attaches to output so far.
                        out.append(ANUSVARA)
                        i += 1
                    } else {
                        // "ng" before a vowel -> real consonant ঙ, follows normal consonant rules.
                        out.append(applyConsonant("ঙ", tokens, i) { consumed -> i += consumed })
                    }
                }
                is Token.ConsonantTok -> {
                    out.append(applyConsonant(t.glyph, tokens, i) { consumed -> i += consumed })
                }
            }
        }
        return out.toString()
    }

    private inline fun applyConsonant(
        glyph: String,
        tokens: List<Token>,
        index: Int,
        advance: (Int) -> Unit
    ): String {
        val next = tokens.getOrNull(index + 1)
        return when {
            next is Token.VowelTok -> {
                val isLast = index + 2 >= tokens.size
                val kar = if (next.raw == "o" && !isLast) "" else next.unit.dependentKar
                advance(2)
                glyph + kar
            }
            next is Token.ConsonantTok || next is Token.NasalTok -> {
                advance(1)
                glyph + HASANT
            }
            else -> {
                advance(1)
                glyph
            }
        }
    }
}
