package com.reganbarua.jujukeys.bengali

/**
 * Offline Avro-style phonetic engine: converts Roman (English-letter) input into Bengali.
 *
 *   ami → আমি, bhalo → ভালো, bangladesh → বাংলাদেশ, kromo → ক্রম, dhormo → ধর্ম
 *
 * Rule-based (no word list). Pure Kotlin, no Android dependency, so it is unit-testable.
 *
 * Case: like Avro, only the letters o i u d g j n r s t y z are case-sensitive
 * (e.g. t→ত, T→ট, n→ন, N→ণ). Every other letter is treated as lowercase.
 */
object AvroPhonetic {

    private const val HASANTA = "্"
    private const val CASE_SENSITIVE = "oiudgjnrstyz"

    private enum class Ctx { START, CONSONANT, VOWEL, REPH }

    private sealed class Tok {
        class Cons(val bn: String) : Tok()
        class Vowel(val independent: String, val kar: String) : Tok()
        /** Output as-is. [ctx] is the context after it. */
        class Plain(val bn: String, val ctx: Ctx) : Tok()
    }

    // ---------------------------------------------------------------- tables

    /** Single consonants: roman → Bengali (several romans may map to one letter). */
    private val consonants: List<Pair<String, String>> = listOf(
        "k" to "ক", "kh" to "খ", "q" to "ক",
        "g" to "গ", "gh" to "ঘ", "Ng" to "ঙ",
        "c" to "চ", "ch" to "ছ",
        "j" to "জ", "J" to "জ", "jh" to "ঝ", "NG" to "ঞ",
        "T" to "ট", "Th" to "ঠ", "D" to "ড", "Dh" to "ঢ", "N" to "ণ",
        "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
        "p" to "প", "ph" to "ফ", "f" to "ফ",
        "b" to "ব", "bh" to "ভ", "v" to "ভ", "m" to "ম",
        "z" to "য", "l" to "ল",
        "sh" to "শ", "S" to "শ", "Sh" to "ষ", "s" to "স", "h" to "হ",
        "R" to "ড়", "Rh" to "ঢ়", "Y" to "য়",
    )

    /** Conjuncts that are formed automatically when two consonants are typed together. */
    private val conjunctPairs: List<String> = listOf(
        "কক", "কট", "কত", "কম", "কল", "কষ", "কস",
        "গধ", "গন", "গম", "গল",
        "ঙক", "ঙখ", "ঙগ", "ঙঘ",
        "চচ", "চছ", "জজ", "জঝ", "জঞ", "ঞচ", "ঞছ", "ঞজ",
        "টট", "ডড", "ণট", "ণঠ", "ণড", "ণণ",
        "তত", "তথ", "তন", "তম", "দদ", "দধ", "দভ", "দম", "ধন", "ধম",
        "নত", "নথ", "নদ", "নধ", "নন", "নট", "নড", "নম", "নস",
        "পত", "পন", "পপ", "পল", "পস",
        "বজ", "বদ", "বধ", "বব", "বল",
        "মন", "মপ", "মফ", "মব", "মভ", "মম", "মল",
        "লক", "লগ", "লট", "লড", "লপ", "লফ", "লম", "লল",
        "শচ", "শন", "শম", "শল",
        "ষক", "ষট", "ষঠ", "ষণ", "ষপ", "ষফ", "ষম",
        "সক", "সখ", "সট", "সত", "সথ", "সন", "সপ", "সফ", "সম", "সল",
        "হন", "হম", "হল",
    )

    /** Hand-written specials (take priority over generated patterns). */
    private val specialConsonants: List<Pair<String, String>> = listOf(
        "x" to "ক্স",
        "kSh" to "ক্ষ", "kkh" to "ক্ষ",
        "gg" to "জ্ঞ",
        "ngk" to "ঙ্ক", "ngkh" to "ঙ্খ", "ngg" to "ঙ্গ", "nggh" to "ঙ্ঘ",
        "nc" to "ঞ্চ", "nch" to "ঞ্ছ", "nj" to "ঞ্জ", "njh" to "ঞ্ঝ",
    )

    private val vowels: List<Pair<String, Tok.Vowel>> = listOf(
        "a" to Tok.Vowel("আ", "া"),
        "i" to Tok.Vowel("ই", "ি"),
        "I" to Tok.Vowel("ঈ", "ী"), "ee" to Tok.Vowel("ঈ", "ী"),
        "u" to Tok.Vowel("উ", "ু"), "oo" to Tok.Vowel("উ", "ু"),
        "U" to Tok.Vowel("ঊ", "ূ"),
        "e" to Tok.Vowel("এ", "ে"),
        "OI" to Tok.Vowel("ঐ", "ৈ"),
        "O" to Tok.Vowel("ও", "ো"),
        "OU" to Tok.Vowel("ঔ", "ৌ"),
        "rri" to Tok.Vowel("ঋ", "ৃ"),
    )

    private val others: List<Pair<String, Tok.Plain>> = listOf(
        "ng" to Tok.Plain("ং", Ctx.VOWEL),
        ":" to Tok.Plain("ঃ", Ctx.VOWEL),
        "^" to Tok.Plain("ঁ", Ctx.VOWEL), // context kept, see convert()
        "t``" to Tok.Plain("ৎ", Ctx.VOWEL),
        "." to Tok.Plain("।", Ctx.START),
        ".." to Tok.Plain(".", Ctx.START),
        "$" to Tok.Plain("৳", Ctx.START),
        ",," to Tok.Plain("্‌", Ctx.START),
        "0" to Tok.Plain("০", Ctx.START), "1" to Tok.Plain("১", Ctx.START),
        "2" to Tok.Plain("২", Ctx.START), "3" to Tok.Plain("৩", Ctx.START),
        "4" to Tok.Plain("৪", Ctx.START), "5" to Tok.Plain("৫", Ctx.START),
        "6" to Tok.Plain("৬", Ctx.START), "7" to Tok.Plain("৭", Ctx.START),
        "8" to Tok.Plain("৮", Ctx.START), "9" to Tok.Plain("৯", Ctx.START),
    )

    private val patterns: Map<String, Tok>
    private val maxLen: Int

    init {
        val map = LinkedHashMap<String, Tok>()
        // Generated conjuncts first, so single letters / specials can override collisions.
        for (pair in conjunctPairs) {
            val first = pair.substring(0, 1)
            val second = pair.substring(1, 2)
            val romans1 = consonants.filter { it.second == first }.map { it.first }
            val romans2 = consonants.filter { it.second == second }.map { it.first }
            for (r1 in romans1) for (r2 in romans2) {
                map[r1 + r2] = Tok.Cons(first + HASANTA + second)
            }
        }
        // Single consonants win over any colliding generated conjunct (e.g. "sh" is শ, not স্হ).
        for ((r, bn) in consonants) map[r] = Tok.Cons(bn)
        for ((r, bn) in specialConsonants) map[r] = Tok.Cons(bn)
        for ((r, v) in vowels) map[r] = v
        for ((r, p) in others) map[r] = p
        patterns = map
        maxLen = map.keys.maxOf { it.length }
    }

    // ---------------------------------------------------------------- helpers

    /** Avro case rule: only the letters in [CASE_SENSITIVE] keep their capital form. */
    fun normalize(input: String): String = buildString(input.length) {
        for (ch in input) {
            if (ch.isLetter() && ch.isUpperCase() && ch.lowercaseChar() !in CASE_SENSITIVE) {
                append(ch.lowercaseChar())
            } else append(ch)
        }
    }

    /** True when the output ends in a conjunct cluster such as ্ম, ্ত, ্র. */
    private fun endsWithCluster(out: StringBuilder): Boolean =
        out.length >= 2 && out[out.length - 2].toString() == HASANTA

    private fun isVowelLetter(c: Char) = c in "aeiouAEIOU"
    private fun isConsonantLetter(c: Char) = c.isLetter() && c.code < 128 && !isVowelLetter(c)

    // ---------------------------------------------------------------- convert

    fun convert(raw: String): String {
        val s = normalize(raw)
        val out = StringBuilder()
        var ctx = Ctx.START
        var lastWasR = false
        var i = 0

        while (i < s.length) {
            // 1) longest table match
            var matched: Tok? = null
            var len = 0
            for (l in minOf(maxLen, s.length - i) downTo 1) {
                val t = patterns[s.substring(i, i + l)]
                if (t != null) { matched = t; len = l; break }
            }
            val c = s[i]

            // 2) context-sensitive single letters (only when nothing longer matched)
            if (len <= 1 && c in "oryZw`") {
                when (c) {
                    'o' -> {
                        val atWordEnd = i + 1 >= s.length || !s[i + 1].isLetter()
                        when (ctx) {
                            // Word-final o after a single consonant is written: bhalo → ভালো.
                            // After a conjunct it stays inherent: dhormo → ধর্ম, golpo → গল্প.
                            Ctx.CONSONANT -> if (atWordEnd && !endsWithCluster(out)) out.append("ো")
                            else -> out.append(if (ctx == Ctx.START) "অ" else "ও")
                        }
                        ctx = Ctx.VOWEL
                    }
                    'r' -> {
                        val next = s.getOrNull(i + 1)
                        when {
                            ctx == Ctx.CONSONANT && !lastWasR -> { out.append(HASANTA + "র"); ctx = Ctx.CONSONANT }
                            next != null && isConsonantLetter(next) && next != 'r' && ctx != Ctx.START ->
                                { out.append("র" + HASANTA); ctx = Ctx.REPH }       // dhormo → ধর্ম
                            else -> { out.append("র"); ctx = Ctx.CONSONANT }
                        }
                        lastWasR = true
                        i++
                        continue
                    }
                    'y' -> {
                        when (ctx) {
                            Ctx.CONSONANT -> out.append(HASANTA + "য")   // byabosha → ব্যাবসা
                            Ctx.REPH -> out.append("য")                   // karyo → কার্য
                            else -> out.append("য়")
                        }
                        ctx = Ctx.CONSONANT
                    }
                    'Z' -> {
                        out.append(if (ctx == Ctx.CONSONANT) HASANTA + "য" else "য")
                        ctx = Ctx.CONSONANT
                    }
                    'w' -> {
                        if (ctx == Ctx.CONSONANT) {
                            out.append(HASANTA + "ব"); ctx = Ctx.CONSONANT
                        } else if (s.getOrNull(i + 1) == 'a') {
                            out.append("ওয়া"); ctx = Ctx.VOWEL; i += 2; lastWasR = false; continue
                        } else {
                            out.append("ও"); ctx = Ctx.VOWEL
                        }
                    }
                    '`' -> { /* separator: prevents conjuncts, outputs nothing */ }
                }
                lastWasR = false
                i++
                continue
            }

            // 3) table token
            when (val t = matched) {
                is Tok.Cons -> { out.append(t.bn); ctx = Ctx.CONSONANT }
                is Tok.Vowel -> {
                    out.append(if (ctx == Ctx.CONSONANT) t.kar else t.independent)
                    ctx = Ctx.VOWEL
                }
                is Tok.Plain -> {
                    out.append(t.bn)
                    if (t.bn != "ঁ") ctx = t.ctx   // chandrabindu does not change context
                }
                null -> { out.append(c); ctx = Ctx.START; len = 1 }  // unknown char: pass through
            }
            lastWasR = false
            i += len
        }
        return out.toString()
    }

    /** Characters that belong to a word being typed phonetically in Bengali mode. */
    fun isPhoneticChar(c: Char): Boolean =
        (c.isLetter() && c.code < 128) || c.isDigit() || c in ".:^`$"
}
