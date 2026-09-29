package com.reganbarua.jujukeys.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Language { BANGLA, ENGLISH }

/** OFF = small letters (Bangla), ONCE = next letter capital, LOCK = caps lock. */
enum class ShiftState { OFF, ONCE, LOCK }

enum class Page { LETTERS, SYMBOLS, MORE_SYMBOLS }

enum class ToolbarItem { MENU, CLIPBOARD, TRANSLATE, SETTINGS, MIC, EMOJI }

/** Everything the keyboard UI draws. Compose re-draws automatically when these change. */
class KeyboardState {
    var language by mutableStateOf(Language.BANGLA)
    var shift by mutableStateOf(ShiftState.OFF)
    var page by mutableStateOf(Page.LETTERS)

    /** Roman letters typed for the current Bangla word, and their Bangla preview. */
    var romanPreview by mutableStateOf("")
    var banglaPreview by mutableStateOf("")

    /** Label for the blue action key: "SEND", "GO", "SEARCH", … or "" for a new-line key. */
    var enterLabel by mutableStateOf("")
}

/** What the keyboard UI asks the service to do. */
interface KeyboardActions {
    fun onChar(c: Char)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onShift()
    fun onToggleLanguage()
    fun onPage(page: Page)
    fun onToolbar(item: ToolbarItem)
}
