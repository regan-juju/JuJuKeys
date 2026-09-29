package com.reganbarua.jujukeys

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.reganbarua.jujukeys.bengali.AvroPhonetic
import com.reganbarua.jujukeys.clipboard.ClipHistory
import com.reganbarua.jujukeys.keyboard.KeyboardActions
import com.reganbarua.jujukeys.keyboard.KeyboardState
import com.reganbarua.jujukeys.keyboard.KeyboardView
import com.reganbarua.jujukeys.keyboard.Language
import com.reganbarua.jujukeys.keyboard.Page
import com.reganbarua.jujukeys.keyboard.Panel
import com.reganbarua.jujukeys.keyboard.ShiftState
import com.reganbarua.jujukeys.settings.Prefs
import com.reganbarua.jujukeys.suggest.Suggester
import com.reganbarua.jujukeys.translate.TranslateEngine
import java.util.concurrent.Executors

/**
 * The system keyboard (iPhone look, Gboard behaviour). Works in every app once enabled.
 *
 * ENGLISH: letters are typed as CAPITAL letters (except in password fields).
 * বাংলা:   Avro phonetic (bhalo → ভালো). No Shift = small letter (t → ত), Shift = capital (T → ট).
 * Extras:  word suggestions, clipboard history + Google Keep, Google-quality translation
 *          (offline ML Kit / online Cloud API), emoji, voice typing, space-bar cursor slide.
 *
 * Privacy: typed text is never logged or uploaded. Only text typed into the translate box
 * is translated (on the phone offline, or by Google if the user saved an API key).
 */
class JuJuKeysInputMethodService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, KeyboardActions {

    // ---- Compose needs these owners; an InputMethodService does not provide them itself.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val state = KeyboardState()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var clips: ClipHistory
    private lateinit var translator: TranslateEngine
    @Volatile private var suggester: Suggester? = null

    private val roman = StringBuilder()        // Roman letters of the Bangla word being typed
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    private var passwordField = false
    private var suggestionSeq = 0

    // ---- translate box
    private val tCommitted = StringBuilder()
    private var tComposing = ""
    private var translateSeq = 0
    private val translateRunnable = Runnable { runTranslation() }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { readClipboard() }

    // ================================================================== lifecycle

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        state.language = if (Prefs.lastLanguageBangla(this)) Language.BANGLA else Language.ENGLISH
        clips = ClipHistory(this)
        translator = TranslateEngine(this)
        clipboard().addPrimaryClipChangedListener(clipListener)

        // Load the dictionaries in the background.
        worker.execute {
            runCatching {
                val en = assets.open("dict_en.txt").bufferedReader().readLines()
                val bn = assets.open("dict_bn.txt").bufferedReader().readLines()
                suggester = Suggester(en.asSequence(), bn.asSequence())
            }
            main.post { refreshSuggestions() }
        }
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@JuJuKeysInputMethodService)
            setViewTreeViewModelStoreOwner(this@JuJuKeysInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@JuJuKeysInputMethodService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { KeyboardView(state, clips.items, this@JuJuKeysInputMethodService) }
        }
    }

    override fun onDestroy() {
        clipboard().removePrimaryClipChangedListener(clipListener)
        translator.close()
        worker.shutdown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting && state.translateOn) stopTranslate()
        roman.clear()
        state.shift = ShiftState.OFF
        state.panel = Panel.KEYS
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = (cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        state.page = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.SYMBOLS
            else -> Page.LETTERS
        }
        state.enterLabel = enterLabelFor(info)
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (state.translateOn) stopTranslate() else commitWord()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (state.translateOn) return
        // The user tapped somewhere else in the text: finish the word being typed.
        if (roman.isNotEmpty() &&
            (candidatesStart == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            roman.clear()
            currentInputConnection?.finishComposingText()
        }
        refreshSuggestions()
    }

    // ================================================================== where text goes

    /** Text goes into the app, or into the translate box while translate is on. */
    private fun sinkSetComposing(text: String) {
        if (state.translateOn) { tComposing = text; translateInputChanged() }
        else currentInputConnection?.setComposingText(text, 1)
    }

    private fun sinkCommit(text: String) {
        if (state.translateOn) {
            tComposing = ""              // the committed text replaces the composing word
            tCommitted.append(text)
            translateInputChanged()
        } else {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun sinkDeleteBefore() {
        if (state.translateOn) {
            if (tCommitted.isNotEmpty()) {
                val cut = Character.offsetByCodePoints(tCommitted, tCommitted.length, -1)
                tCommitted.setLength(cut)
                translateInputChanged()
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    // ================================================================== keys

    override fun onChar(c: Char) {
        if (state.language == Language.ENGLISH || passwordField) {
            val out = when {
                !c.isLetter() -> c
                passwordField -> if (state.shift == ShiftState.OFF) c.lowercaseChar() else c.uppercaseChar()
                else -> c.uppercaseChar()                       // English: always CAPITAL
            }
            if (passwordField && state.shift == ShiftState.ONCE && c.isLetter()) state.shift = ShiftState.OFF
            sinkCommit(out.toString())
            refreshSuggestions()
            return
        }
        // বাংলা
        val ch = if (c.isLetter()) {
            if (state.shift == ShiftState.OFF) c.lowercaseChar() else c.uppercaseChar()
        } else c
        if (state.shift == ShiftState.ONCE && c.isLetter()) state.shift = ShiftState.OFF

        if (AvroPhonetic.isPhoneticChar(ch)) {
            roman.append(ch)
            sinkSetComposing(AvroPhonetic.convert(roman.toString()))
        } else {
            commitWord()
            sinkCommit(ch.toString())
        }
        refreshSuggestions()
    }

    override fun onText(text: String) {
        commitWord()
        sinkCommit(text)
        refreshSuggestions()
    }

    override fun onBackspace() {
        if (roman.isNotEmpty()) {
            roman.setLength(roman.length - 1)
            if (roman.isEmpty()) {
                if (state.translateOn) { tComposing = ""; translateInputChanged() }
                else currentInputConnection?.commitText("", 1)
            } else {
                sinkSetComposing(AvroPhonetic.convert(roman.toString()))
            }
        } else {
            sinkDeleteBefore()
        }
        refreshSuggestions()
    }

    override fun onSpace() {
        val now = SystemClock.uptimeMillis()
        val hadWord = roman.isNotEmpty()
        commitWord()
        val ic = currentInputConnection
        // Double space → full stop (। in Bangla), like iPhone.
        if (!state.translateOn && !hadWord && ic != null && now - lastSpaceTime < 700) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' &&
                !before[0].isWhitespace() && before[0] !in ".।,!?"
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (state.language == Language.BANGLA) "। " else ". ", 1)
                lastSpaceTime = 0L
                refreshSuggestions()
                return
            }
        }
        sinkCommit(" ")
        lastSpaceTime = now
        refreshSuggestions()
    }

    override fun onCursorMove(steps: Int) {
        if (state.translateOn) return
        commitWord()
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    override fun onEnter() {
        if (state.translateOn) {
            // keep the translation in the app, clear the box, then send / new line
            main.removeCallbacks(translateRunnable)
            roman.clear(); tCommitted.setLength(0); tComposing = ""
            state.translateInput = ""
            currentInputConnection?.finishComposingText()
        } else {
            commitWord()
        }
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendKeyChar('\n')
        }
        refreshSuggestions()
    }

    override fun onShift() {
        if (state.language == Language.ENGLISH && !passwordField) {
            toast("ENGLISH সবসময় বড় হাতের অক্ষরে লেখে")
            return
        }
        val now = SystemClock.uptimeMillis()
        state.shift = when {
            state.shift == ShiftState.LOCK -> ShiftState.OFF
            now - lastShiftTap < 350 -> ShiftState.LOCK          // double tap = caps lock
            state.shift == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
    }

    override fun onToggleLanguage() {
        commitWord()
        setLanguage(if (state.language == Language.BANGLA) Language.ENGLISH else Language.BANGLA)
        if (state.translateOn) {
            state.translateFrom = state.language
            clearTranslateBox()
        }
    }

    private fun setLanguage(lang: Language) {
        state.language = lang
        state.shift = ShiftState.OFF
        Prefs.setLastLanguageBangla(this, lang == Language.BANGLA)
        refreshSuggestions()
    }

    override fun onShowImePicker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onPage(page: Page) {
        state.page = page
    }

    override fun onPanel(panel: Panel) {
        commitWord()
        if (panel == Panel.CLIPBOARD) readClipboard()
        state.panel = panel
    }

    // ================================================================== suggestions

    override fun onSuggestion(index: Int) {
        val word = state.suggestions.getOrNull(index) ?: return
        if (state.language == Language.BANGLA && !passwordField) {
            roman.clear()
            sinkCommit("$word ")                 // replaces the composing word
        } else {
            val cur = currentEnglishWord()
            val ic = currentInputConnection
            if (cur.isNotEmpty()) ic?.deleteSurroundingText(cur.length, 0)
            ic?.commitText("$word ", 1)
        }
        refreshSuggestions()
    }

    private fun currentEnglishWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString() ?: return ""
        return before.takeLastWhile { (it.isLetter() && it.code < 128) || it == '\'' }
    }

    private fun refreshSuggestions() {
        if (passwordField) { state.suggestions = emptyList(); return }
        val seq = ++suggestionSeq
        val bangla = state.language == Language.BANGLA
        val romanNow = roman.toString()
        val englishWord = if (!bangla && !state.translateOn) currentEnglishWord() else ""
        val s = suggester

        if (bangla && romanNow.isEmpty()) { state.suggestions = listOf("আমি", "সে", "আপনি"); return }
        if (!bangla && englishWord.isEmpty()) { state.suggestions = listOf("I", "THE", "I'M"); return }
        if (bangla) state.suggestions = listOf(AvroPhonetic.convert(romanNow))
        if (s == null) return

        worker.execute {
            val list = if (bangla) s.bangla(AvroPhonetic.convert(romanNow))
            else s.english(englishWord).map { it.uppercase() }
            main.post { if (seq == suggestionSeq) state.suggestions = list }
        }
    }

    // ================================================================== voice

    override fun onVoice() {
        commitWord()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Voice keyboards (Google voice typing) register themselves as "shortcut" IMEs.
        var target = imm.shortcutInputMethodsAndSubtypes.entries.firstOrNull()
            ?.let { it.key to it.value.firstOrNull() }
        if (target == null) {
            for (imi in imm.enabledInputMethodList) {
                for (i in 0 until imi.subtypeCount) {
                    val st = imi.getSubtypeAt(i)
                    if (st.mode == "voice") { target = imi to st; break }
                }
                if (target != null) break
            }
        }
        if (target == null) {
            toast("ভয়েস টাইপিং পাওয়া যায়নি — Gboard বা Google অ্যাপের ভয়েস টাইপিং চালু করুন")
            return
        }
        val (info, subtype) = target
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (subtype != null) switchInputMethod(info.id, subtype) else switchInputMethod(info.id)
        } else {
            val token = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            imm.setInputMethodAndSubtype(token, info.id, subtype)
        }
    }

    // ================================================================== clipboard

    private fun clipboard() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun readClipboard() {
        runCatching {
            val clip = clipboard().primaryClip ?: return
            if (clip.itemCount == 0) return
            if (Build.VERSION.SDK_INT >= 33 &&
                clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            ) return   // passwords etc. are not kept
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            clips.add(text)
        }
    }

    override fun onClipPaste(text: String) {
        commitWord()
        sinkCommit(text)
        refreshSuggestions()
    }

    override fun onClipPin(id: Long) = clips.togglePin(id)
    override fun onClipDelete(id: Long) = clips.delete(id)
    override fun onClipClear() = clips.clearUnpinned()

    override fun onClipToKeep(text: String) {
        if (ClipHistory.sendToKeep(this, text)) toast("Google Keep-এ সেভ করতে 'Save' চাপুন")
        else toast("Google Keep ইনস্টল নেই — Play Store খোলা হলো")
    }

    override fun onOpenKeep() {
        if (!ClipHistory.openKeep(this)) toast("Google Keep ইনস্টল নেই — Play Store খোলা হলো")
    }

    // ================================================================== translate

    override fun onTranslateToggle() {
        if (state.translateOn) { stopTranslate(); return }
        commitWord()
        state.panel = Panel.KEYS
        state.translateOn = true
        state.translateFrom = state.language
        state.translateStatus = ""
        clearTranslateBox()
        state.online = translator.isOnline()
        translator.checkOfflineModel { ready ->
            state.offlineReady = ready
            if (!ready && translator.isOnline()) {
                state.translateStatus = "অফলাইন মডেল নামানো হচ্ছে (একবারই, ~৩০MB)…"
                translator.downloadOfflineModel { ok, err ->
                    state.offlineReady = ok
                    state.translateStatus = if (ok) "অফলাইন অনুবাদ প্রস্তুত ✓" else "মডেল নামানো যায়নি: ${err ?: ""}"
                }
            } else if (!ready) {
                state.translateStatus = "অফলাইন মডেল নেই — একবার ইন্টারনেট চালু করুন"
            }
        }
    }

    override fun onTranslateSwap() {
        val next = if (state.translateFrom == Language.BANGLA) Language.ENGLISH else Language.BANGLA
        state.translateFrom = next
        setLanguage(next)
        clearTranslateBox()
    }

    override fun onOpenTranslateApp() {
        val text = (tCommitted.toString() + tComposing).trim()
        TranslateEngine.openTranslateApp(this, text)
    }

    private fun stopTranslate() {
        main.removeCallbacks(translateRunnable)
        roman.clear()
        tCommitted.setLength(0); tComposing = ""
        state.translateInput = ""
        state.translateOn = false
        state.translateStatus = ""
        currentInputConnection?.finishComposingText()   // keep the translation in the app
        refreshSuggestions()
    }

    private fun clearTranslateBox() {
        main.removeCallbacks(translateRunnable)
        roman.clear()
        tCommitted.setLength(0); tComposing = ""
        state.translateInput = ""
        currentInputConnection?.setComposingText("", 1)
    }

    private fun translateInputChanged() {
        state.translateInput = tCommitted.toString() + tComposing
        main.removeCallbacks(translateRunnable)
        main.postDelayed(translateRunnable, 450)
    }

    private fun runTranslation() {
        val raw = (tCommitted.toString() + tComposing).trim()
        val seq = ++translateSeq
        if (raw.isEmpty()) {
            currentInputConnection?.setComposingText("", 1)
            return
        }
        val toEnglish = state.translateFrom == Language.BANGLA
        val text = if (toEnglish) raw else raw.lowercase()   // English box is all-caps
        state.online = translator.isOnline()
        translator.translate(
            text, toEnglish,
            status = { state.translateStatus = it },
            onResult = { result, error ->
                if (seq != translateSeq || !state.translateOn) return@translate
                if (result != null) {
                    currentInputConnection?.setComposingText(result.text, 1)
                    state.translateStatus = if (result.online) "অনলাইন (Google Cloud)" else "অফলাইন (Google ML Kit)"
                    if (!result.online) state.offlineReady = true
                } else {
                    state.translateStatus = error ?: "অনুবাদ হয়নি"
                }
            }
        )
    }

    // ================================================================== helpers

    /** Puts the finished Bangla word into the app (or the translate box). */
    private fun commitWord() {
        if (roman.isEmpty()) return
        val bangla = AvroPhonetic.convert(roman.toString())
        roman.clear()
        if (state.translateOn) {
            tComposing = ""
            tCommitted.append(bangla)
            translateInputChanged()
        } else {
            currentInputConnection?.commitText(bangla, 1)
        }
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return ""
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            else -> ""
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
