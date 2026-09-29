package com.reganbarua.jujukeys.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

enum class Language { BANGLA, ENGLISH }

/** OFF = small letters (Bangla), ONCE = next letter capital, LOCK = caps lock. */
enum class ShiftState { OFF, ONCE, LOCK }

enum class Page { LETTERS, SYMBOLS, MORE_SYMBOLS }

/** What fills the key area: the keys, the clipboard list or the emoji grid. */
enum class Panel { KEYS, CLIPBOARD, EMOJI }

/** Everything the keyboard UI draws. Compose re-draws automatically when these change. */
class KeyboardState {
    var language by mutableStateOf(Language.BANGLA)
    var shift by mutableStateOf(ShiftState.OFF)
    var page by mutableStateOf(Page.LETTERS)
    var panel by mutableStateOf(Panel.KEYS)

    /** Three words in the suggestion bar. */
    var suggestions by mutableStateOf<List<String>>(emptyList())

    /** Label for the action key: "Send", "Go", "Search", … or "" for a plain return key. */
    var enterLabel by mutableStateOf("")

    // ---- Translate mode (Gboard style: type in the box, translation goes into the app)
    var translateOn by mutableStateOf(false)
    var translateFrom by mutableStateOf(Language.BANGLA)
    var translateInput by mutableStateOf("")
    var translateStatus by mutableStateOf("")
    var online by mutableStateOf(false)
    var offlineReady by mutableStateOf(false)
}

/** Key-press bubble shown above a letter key (like iPhone). */
class KeyPreview {
    var text by mutableStateOf<String?>(null)
    var bounds by mutableStateOf(Rect.Zero)
}

/** What the keyboard UI asks the service to do. */
interface KeyboardActions {
    fun onChar(c: Char)
    fun onText(text: String)
    fun onBackspace()
    fun onSpace()
    fun onCursorMove(steps: Int)
    fun onEnter()
    fun onShift()
    fun onToggleLanguage()
    fun onShowImePicker()
    fun onPage(page: Page)
    fun onPanel(panel: Panel)
    fun onSuggestion(index: Int)
    fun onVoice()

    // translate
    fun onTranslateToggle()
    fun onTranslateSwap()
    fun onOpenTranslateApp()

    // clipboard
    fun onClipPaste(text: String)
    fun onClipPin(id: Long)
    fun onClipDelete(id: Long)
    fun onClipToKeep(text: String)
    fun onOpenKeep()
    fun onClipClear()
}
