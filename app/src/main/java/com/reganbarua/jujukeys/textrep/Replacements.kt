package com.reganbarua.jujukeys.textrep

/**
 * One text replacement: typing [shortcut] (e.g. "omw") then space gives [phrase] ("On my way!").
 *
 * A [password] entry keeps its phrase ONLY encrypted in [enc] ([phrase] stays empty in memory and on
 * disk); it is used in password fields only, after the phone's lock (PIN / pattern / fingerprint).
 */
data class Rep(
    val id: Long,
    val shortcut: String,
    val phrase: String,
    val password: Boolean = false,
    val enc: String? = null,
)

/** Pure matching rules (no Android classes, so they are unit-tested). */
object ReplaceMatcher {
    const val MIN_SHORTCUT = 2

    /** A shortcut is at least 2 characters and has no spaces / line breaks. */
    fun validShortcut(s: String): Boolean = s.length >= MIN_SHORTCUT && s.none { it.isWhitespace() }

    /**
     * The entry whose shortcut equals the word just typed ([token], letters in any case).
     * Normal entries work in normal fields, password entries only in password fields — so a
     * password can never be typed into a chat by mistake.
     */
    fun find(token: String, reps: List<Rep>, passwordField: Boolean): Rep? {
        if (token.length < MIN_SHORTCUT) return null
        return reps.firstOrNull { it.shortcut.equals(token, ignoreCase = true) && it.password == passwordField }
    }

    /**
     * What goes into the text. A phrase that starts with a small letter follows the shortcut:
     * "Addr" gives "My address" (capital), "addr" gives "my address". A phrase that starts with a
     * capital stays as saved, and a password is NEVER changed.
     */
    fun applyCase(token: String, phrase: String, password: Boolean): String {
        if (password || phrase.isEmpty() || token.isEmpty()) return phrase
        val f = phrase[0]
        return if (f.isLowerCase() && token[0].isUpperCase()) f.uppercaseChar() + phrase.substring(1) else phrase
    }

    /** Is [shortcut] already used by another entry (case-insensitive)? */
    fun duplicate(reps: List<Rep>, shortcut: String, exceptId: Long? = null): Boolean =
        reps.any { it.id != exceptId && it.shortcut.equals(shortcut, ignoreCase = true) }

    /** The last word before the cursor: everything after the final space / line break. */
    fun lastToken(before: CharSequence): String = before.takeLastWhile { !it.isWhitespace() }.toString()

    /** Letter before "A–Z" sections in the list (anything else is "#"). */
    fun sectionOf(phrase: String): Char {
        val c = phrase.trimStart().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }
}
