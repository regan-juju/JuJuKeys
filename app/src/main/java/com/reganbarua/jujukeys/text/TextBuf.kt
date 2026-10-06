package com.reganbarua.jujukeys.text

/**
 * Small helpers for the keyboard's own copy of the last typed characters. They work in whole
 * characters (code points), so an emoji such as 😀 — two Java chars — is never cut in half.
 */
object TextBuf {
    /** Removes the last character (a whole emoji counts as one). */
    fun dropLast(sb: StringBuilder) {
        if (sb.isEmpty()) return
        sb.setLength(Character.offsetByCodePoints(sb, sb.length, -1))
    }

    /** Keeps only the last [n] characters (code points). */
    fun keepLast(sb: StringBuilder, n: Int) {
        val count = Character.codePointCount(sb, 0, sb.length)
        if (count > n) sb.delete(0, Character.offsetByCodePoints(sb, 0, count - n))
    }
}
