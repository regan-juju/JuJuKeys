package com.reganbarua.jujukeys.keyboard

/**
 * Static layout data. English and Bengali share the same QWERTY key *positions*
 * (Bengali is typed phonetically using Latin keys), but Bengali keys additionally
 * carry a Bengali glyph hint for the base sound that key represents.
 */
object KeyboardLayouts {

    val englishRows: List<List<Char>> = listOf(
        listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
        listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
        listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
    )

    /** Bengali base-sound hint per Latin letter, shown as a small glyph above the key label. */
    val bengaliHint: Map<Char, String> = mapOf(
        'q' to "ক", 'w' to "ও", 'e' to "এ", 'r' to "র", 't' to "ত",
        'y' to "য়", 'u' to "উ", 'i' to "ই", 'o' to "ও", 'p' to "প",
        'a' to "আ", 's' to "স", 'd' to "দ", 'f' to "ফ", 'g' to "গ",
        'h' to "হ", 'j' to "জ", 'k' to "ক", 'l' to "ল",
        'z' to "য", 'x' to "ক্স", 'c' to "চ", 'v' to "ভ", 'b' to "ব", 'n' to "ন", 'm' to "ম"
    )

    val symbols1Rows: List<List<String>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "৳", "&", "@", "\""),
        listOf(".", ",", "?", "!", "'")
    )

    val symbols2Rows: List<List<String>> = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        listOf(".", ",", "?", "!", "'")
    )
}
