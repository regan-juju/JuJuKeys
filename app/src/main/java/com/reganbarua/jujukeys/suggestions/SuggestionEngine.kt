package com.reganbarua.jujukeys.suggestions

import com.reganbarua.jujukeys.keyboard.KeyboardLanguage

/**
 * Produces the suggestion-strip words and a simple autocorrect pick for the current
 * in-progress word. Entirely offline/local — no network calls, no keystroke logging.
 */
object SuggestionEngine {

    fun suggestionsFor(language: KeyboardLanguage, currentWord: String): List<String> =
        when (language) {
            KeyboardLanguage.ENGLISH -> EnglishDictionary.suggestionsFor(currentWord)
            KeyboardLanguage.BENGALI -> BengaliDictionary.suggestionsFor(currentWord)
        }

    /**
     * Very small edit-distance-1 autocorrect: if the typed word isn't a known word but is
     * one letter away from exactly one known word, offer that as the top suggestion.
     * Only applies to English (output must stay uppercase); Bengali relies on the
     * suggestion strip instead, to avoid unexpectedly rewriting composed text.
     */
    fun autocorrectFor(currentWordUpper: String): String? {
        if (currentWordUpper.length < 3) return null
        if (EnglishDictionary.words.contains(currentWordUpper)) return null
        val candidates = EnglishDictionary.words.filter { isEditDistanceOne(it, currentWordUpper) }
        return candidates.singleOrNull()
    }

    private fun isEditDistanceOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        var i = 0
        var j = 0
        var edits = 0
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) {
                i++; j++
            } else {
                edits++
                if (edits > 1) return false
                if (shorter.length == longer.length) {
                    i++; j++
                } else {
                    j++
                }
            }
        }
        if (j < longer.length) edits += (longer.length - j)
        return edits <= 1
    }
}
