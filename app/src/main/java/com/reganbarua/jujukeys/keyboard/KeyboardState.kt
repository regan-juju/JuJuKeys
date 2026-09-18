package com.reganbarua.jujukeys.keyboard

enum class KeyboardLanguage { ENGLISH, BENGALI }

enum class KeyboardPanel {
    LETTERS,
    SYMBOLS_1,
    SYMBOLS_2,
    EMOJI,
    CLIPBOARD,
    TRANSLATE
}

/**
 * Single source of truth for what the keyboard is currently showing.
 * Held in the IME service and passed down into the Compose UI.
 */
data class KeyboardUiState(
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val panel: KeyboardPanel = KeyboardPanel.LETTERS,
    /** Only meaningful in Bengali letters mode: shift affects the *case* of the next
     * phonetic key (e.g. "t" -> ত vs "T" -> ট), since Bengali phonetic rules are
     * case-sensitive. English is always forced uppercase regardless of this flag. */
    val bengaliShiftOn: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val editorActionLabel: String? = null // e.g. "Send", "Go", "Search" from EditorInfo
)
