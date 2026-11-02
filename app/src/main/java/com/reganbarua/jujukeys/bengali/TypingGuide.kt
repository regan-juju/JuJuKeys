package com.reganbarua.jujukeys.bengali

/**
 * Data for Settings -> "Bangla typing guide": conjuncts (যুক্তাক্ষর) and the English letters that make them.
 * Pure Kotlin. TypingGuideTest checks that every roman code really gives its Bengali through [AvroPhonetic.convert],
 * so the guide can never show a code the keyboard does not understand.
 */
object TypingGuide {
    /** Bengali conjunct to the roman letters you type. */
    val conjuncts: List<Pair<String, String>> = listOf(
        "ক্ষ" to "kkh",
        "জ্ঞ" to "gg",
        "ক্ষ্ম" to "kkhm",
        "ঙ্ক" to "Ngk",
        "ঞ্চ" to "nc",
        "ঞ্জ" to "nj",
        "ন্ট" to "nT",
        "ণ্ড" to "ND",
        "ক্ত" to "kt",
        "ক্র" to "kr",
        "গ্ন" to "gn",
        "গ্র" to "gr",
        "চ্ছ" to "cch",
        "জ্জ" to "jj",
        "ট্ট" to "TT",
        "ত্ত" to "tt",
        "ত্র" to "tr",
        "দ্দ" to "dd",
        "দ্ধ" to "ddh",
        "দ্ম" to "dm",
        "দ্র" to "dr",
        "ন্ত" to "nt",
        "ন্দ" to "nd",
        "ন্ধ" to "ndh",
        "প্র" to "pr",
        "ব্দ" to "bd",
        "ব্র" to "br",
        "ম্প" to "mp",
        "ম্ব" to "mb",
        "ম্ভ" to "mbh",
        "ল্প" to "lp",
        "শ্র" to "shr",
        "ষ্ট" to "ShT",
        "স্ক" to "sk",
        "স্ত" to "st",
        "স্থ" to "sth",
        "স্প" to "sp",
        "স্ম" to "sm",
        "হ্ম" to "hm",
        "ন্ত্র" to "ntr",
        "ন্দ্র" to "ndr",
        "স্ত্র" to "str",
        "স্থ্য" to "sthy"
    )

    /** What the keyboard shows after typing the first [n] letters of [roman]. */
    fun partial(roman: String, n: Int): String = AvroPhonetic.convert(roman.take(n.coerceIn(0, roman.length)))
}
