package com.reganbarua.jujukeys.keyboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.reganbarua.jujukeys.settings.KeyboardPrefs

enum class Language { BANGLA, ENGLISH }

/** OFF = small letters, ONCE = next letter capital, LOCK = caps lock. */
enum class ShiftState { OFF, ONCE, LOCK }

/** LETTERS → (?123) NUMPAD → (!?#) SYMBOLS → (=\<) MORE_SYMBOLS; (১২ ৩৪) goes back to NUMPAD. */
enum class Page { LETTERS, NUMPAD, SYMBOLS, MORE_SYMBOLS }

/** What fills the key area. */
enum class Panel { KEYS, CLIPBOARD, EMOJI, SUGGESTIONS }

/** Which sound a key press makes. */
enum class KeyKind { NORMAL, DELETE, SPACE, RETURN }

/** Everything the keyboard UI draws. Compose re-draws only the parts that read a changed value. */
@Stable
class KeyboardState {
    var language by mutableStateOf(Language.BANGLA)
    var shift by mutableStateOf(ShiftState.OFF)
    var page by mutableStateOf(Page.LETTERS)
    var panel by mutableStateOf(Panel.KEYS)

    /** Settings chosen in the app (Gboard-style settings screen). */
    var prefs by mutableStateOf(KeyboardPrefs())
    var recentEmoji by mutableStateOf<List<String>>(emptyList())

    /** Three words in the suggestion bar, and the long list for the ⌃⌄ panel. */
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var moreSuggestions by mutableStateOf<List<String>>(emptyList())

    /** Just-copied text, shown as a chip in the suggestion bar (like Gboard). */
    var freshClip by mutableStateOf<String?>(null)

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
@Stable
class KeyPreview {
    var text by mutableStateOf<String?>(null)
    var bounds by mutableStateOf(Rect.Zero)
}

/** What the keyboard UI asks the service to do. */
@Stable
interface KeyboardActions {
    fun onChar(c: Char)
    /** Long-press: replace the character just typed with [c]. */
    fun onLongChar(c: Char)
    /** Emoji: typed as-is and remembered as recent. */
    fun onText(text: String)
    /** Number pad / symbol keys: typed exactly as shown. */
    fun onRawText(text: String)
    fun onBackspace()
    fun onSpace()
    fun onCursorMove(steps: Int)
    fun onEnter()
    fun onShift()
    fun onToggleLanguage()
    fun onShowImePicker()
    fun onOpenSettings()
    /** Sound / vibration for a key press, following the settings. */
    fun onKeyFeedback(kind: KeyKind)
    fun onPage(page: Page)
    fun onPanel(panel: Panel)
    fun onSuggestion(index: Int)
    fun onSuggestionWord(word: String)
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
    fun onClipAllToKeep()
    fun onClipEnabled(on: Boolean)
    fun onOpenKeep()
    fun onClipClear()
}
