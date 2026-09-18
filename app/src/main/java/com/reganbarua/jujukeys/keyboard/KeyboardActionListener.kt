package com.reganbarua.jujukeys.keyboard

/**
 * Everything the Compose keyboard UI can ask the IME service to do.
 * The service is the only thing that touches InputConnection.
 */
interface KeyboardActionListener {
    /** A plain character key was tapped (already the correct case/glyph to send onward). */
    fun onLetterKey(raw: Char)
    fun onSpace()
    fun onBackspace()
    fun onBackspaceLongPressRepeat()
    fun onEnter()
    fun onSymbolOrPunctuation(text: String)
    fun onToggleShift()
    fun onGlobe()
    fun onSwitchToLetters()
    fun onSwitchToSymbols1()
    fun onSwitchToSymbols2()
    fun onSwitchToEmoji()
    fun onOpenClipboardPanel()
    fun onOpenTranslatePanel()
    fun onOpenSettings()
    fun onMicTapped()
    fun onEmojiSelected(emoji: String)
    fun onSuggestionSelected(word: String)
    fun onClipboardItemSelected(text: String)
    fun onClipboardClear()
    fun onShareToKeep(text: String)
}
