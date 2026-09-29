package com.reganbarua.jujukeys.keyboard

/** A character key: [label] is shown big, [hint] small in the corner and typed on long-press. */
data class CharKey(val label: Char, val hint: Char? = null)

object KeyboardLayouts {

    // Letter rows with the long-press hints shown in the design.
    val lettersRow1 = "QWERTYUIOP".zip("1234567890").map { (l, h) -> CharKey(l, h) }
    val lettersRow2 = "ASDFGHJKL".zip("@#\$_&-+()").map { (l, h) -> CharKey(l, h) }
    val lettersRow3 = "ZXCVBNM".zip("*\"':;!?").map { (l, h) -> CharKey(l, h) }

    // iOS-style "123" page.
    val symbolsRow1 = "1234567890".map { CharKey(it) }
    val symbolsRow2 = "-/:;()৳&@\"".map { CharKey(it) }
    val symbolsRow3 = ".,?!'".map { CharKey(it) }

    // iOS-style "#+=" page.
    val moreRow1 = "[]{}#%^*+=".map { CharKey(it) }
    val moreRow2 = "_\\|~<>€£¥•".map { CharKey(it) }
    val moreRow3 = ".,?!'".map { CharKey(it) }
}
