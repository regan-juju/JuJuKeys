package com.reganbarua.jujukeys

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
import com.reganbarua.jujukeys.clipboard.KeepLedger
import com.reganbarua.jujukeys.keyboard.EmojiRepo
import com.reganbarua.jujukeys.keyboard.KeyKind
import com.reganbarua.jujukeys.keyboard.Themes
import com.reganbarua.jujukeys.security.AuthActivity
import com.reganbarua.jujukeys.security.SensitiveAction
import com.reganbarua.jujukeys.security.SensitiveGate
import com.reganbarua.jujukeys.clipboard.ClipItem
import android.os.PersistableBundle
import com.reganbarua.jujukeys.keyboard.KeyboardActions
import com.reganbarua.jujukeys.keyboard.KeyboardState
import com.reganbarua.jujukeys.keyboard.KeyboardView
import com.reganbarua.jujukeys.keyboard.Language
import com.reganbarua.jujukeys.keyboard.Page
import com.reganbarua.jujukeys.keyboard.Panel
import com.reganbarua.jujukeys.keyboard.ShiftState
import com.reganbarua.jujukeys.settings.Prefs
import com.reganbarua.jujukeys.suggest.Learner
import com.reganbarua.jujukeys.text.TextBuf
import com.reganbarua.jujukeys.suggest.RomanAliases
import com.reganbarua.jujukeys.suggest.Suggester
import com.reganbarua.jujukeys.translate.TranslateEngine
import java.io.File
import java.util.concurrent.Executors

/**
 * The system keyboard (iPhone look, Gboard behaviour). Works in every app once enabled.
 *
 * ENGLISH: key labels are CAPITAL; typing is normal (small letters, Shift / auto-capital = capital).
 * বাংলা:   Avro phonetic (bhalo → ভালো). No Shift = small letter (t → ত), Shift = capital (T → ট).
 *
 * Speed: a key types the moment it is touched, and nothing on the key-press path waits for
 * the app (the current word, capitals and suggestions are all tracked here; dictionary
 * look-ups run on a background thread).
 *
 * Privacy: typed text is never uploaded. Learned words stay in the app's private storage.
 * Only text typed into the translate box is translated (on the phone, or by Google if the
 * user saved an API key).
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
    /** Background thread at low priority, so dictionary work never slows the keys. */
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "jujukeys-worker")
    }

    private lateinit var clips: ClipHistory
    private lateinit var translator: TranslateEngine
    @Volatile private var suggester: Suggester? = null
    @Volatile private var aliases: RomanAliases? = null
    private val learner = Learner()
    private var learnedSinceSave = 0

    private val roman = StringBuilder()        // Roman letters of the Bangla word being typed
    /** iPhone-style fix for the current word (jemon → যেমন), valid only for [autoFor]. */
    private var autoWord: String? = null
    private var autoFor = ""
    private val enWord = StringBuilder()       // English word being typed (kept here: no app round-trip)
    private val recent = StringBuilder()       // last few characters we typed (for auto-capital, double space)
    private var lastWord: String? = null       // previous word, for next-word suggestions
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    private var lastEditTime = 0L
    private var passwordField = false
    private var fieldWantsCaps = false
    /** The app asked for no learning here (incognito / private fields). */
    private var noLearn = false
    private var suggestionSeq = 0
    private var ignoreNextClip = false

    // ---- translate box
    private val tCommitted = StringBuilder()
    private var tComposing = ""
    private var translateSeq = 0
    private var translating = false          // one translation at a time — no pile-up
    private var translatePending = false
    private var lastTranslation = ""
    private val translateRunnable = Runnable { runTranslation() }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { readClipboard(fresh = true) }
    private val clearFreshClip = Runnable { state.freshClip = null }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "clip_changed") clips.reload() else loadPrefs()
    }

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    // ================================================================== lifecycle

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        loadPrefs()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
        state.language = if (Prefs.lastLanguageBangla(this)) Language.BANGLA else Language.ENGLISH
        clips = ClipHistory(this)
        translator = TranslateEngine(this)
        clipboard().addPrimaryClipChangedListener(clipListener)

        // Dictionaries and learned words load in the background.
        worker.execute { runCatching { Prefs.migrateSecrets(this) } }   // plain API key → encrypted
        worker.execute {
            runCatching { learner.load(learnedFile().readText()) }
            runCatching {
                val t0 = SystemClock.elapsedRealtime()
                val en = assets.open("dict_en.txt").bufferedReader().readLines()
                val bn = assets.open("dict_bn.txt").bufferedReader().readLines()
                suggester = Suggester(en.asSequence(), bn.asSequence()).also {
                    it.setUserWords(Prefs.userWords(this))
                    it.learnedCounts = learner.snapshotCounts()
                }
                aliases = runCatching {
                    RomanAliases(assets.open("aliases_bn.txt").bufferedReader().readLines().asSequence())
                }.getOrNull()
                // measured on THIS phone — shown in settings → সম্পর্কে
                val ms = SystemClock.elapsedRealtime() - t0
                Prefs.sp(this).edit()
                    .putLong("bench_dict_ms", ms)
                    .putInt("bench_words_bn", bn.count { '\t' in it })
                    .putInt("bench_words_en", en.count { '\t' in it })
                    .apply()
            }
            // The 141k rare Bangla words come a few seconds later, at the lowest priority, so
            // opening the keyboard does not cause a burst of work (less heat).
            main.postDelayed({ worker.execute { loadExtraWords() } }, 5000)
            main.post { refreshSuggestions() }
        }
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@JuJuKeysInputMethodService)
            setViewTreeViewModelStoreOwner(this@JuJuKeysInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@JuJuKeysInputMethodService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { KeyboardView(state, clips.items, this@JuJuKeysInputMethodService) }
        }
        // Any touch on the keyboard restarts the "hide after N seconds" timer.
        return object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) bumpAutoHide()
                return super.dispatchTouchEvent(ev)
            }
        }.apply { addView(compose) }
    }

    // ================================================================== auto-hide

    private val autoHideRunnable = Runnable {
        if (state.prefs.autoHide && isInputViewShown) requestHideSelf(0)
    }

    /** Restart the timer: the keyboard hides itself after N seconds without a key press. */
    private fun bumpAutoHide() {
        main.removeCallbacks(autoHideRunnable)
        if (state.prefs.autoHide) main.postDelayed(autoHideRunnable, state.prefs.autoHideSeconds.coerceAtLeast(3) * 1000L)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        darkNavigationBar()
    }

    /** Navigation bar under the keyboard follows the theme (dark, or light for সাদা গ্লাস). */
    @Suppress("DEPRECATION")
    private fun darkNavigationBar() {
        val w = window?.window ?: return
        val t = Themes.byId(state.prefs.theme)
        w.navigationBarColor = t.nav
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val v = w.decorView
            v.systemUiVisibility = if (t.light) v.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else v.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    private fun loadPrefs() {
        val old = state.prefs
        state.prefs = Prefs.load(this)
        if (old.theme != state.prefs.theme) darkNavigationBar()
        if (old.autoHide != state.prefs.autoHide || old.autoHideSeconds != state.prefs.autoHideSeconds) {
            if (isInputViewShown) bumpAutoHide() else main.removeCallbacks(autoHideRunnable)
        }
        state.recentEmoji = Prefs.recentEmoji(this)
        suggester?.setUserWords(Prefs.userWords(this))
        if (Prefs.sp(this).getBoolean("clear_learned", false)) {
            Prefs.sp(this).edit().remove("clear_learned").apply()
            learner.clear(); learnedSinceSave = 1; saveLearned()
        }
    }

    override fun onDestroy() {
        clipboard().removePrimaryClipChangedListener(clipListener)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        saveLearned()
        translator.close()
        worker.shutdown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting && state.translateOn) stopTranslate()
        roman.clear(); enWord.clear(); lastWord = null
        state.shift = ShiftState.OFF
        state.panel = Panel.KEYS
        state.emojiSearch = null
        state.keepConfirm = KeepLedger.pendingCount(this)
        refreshSensitiveLock()
        // came back from the phone-lock screen: do what the user asked for
        SensitiveGate.takePending()?.let { (id, action) ->
            clips.find(id)?.let { item ->
                if (action == SensitiveAction.VIEW) state.panel = Panel.CLIPBOARD
                else main.post { runClipAction(item, action) }
            }
        }
        bumpAutoHide()
        noLearn = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = (cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        // English: capital at the start of every sentence — except where it would be wrong.
        fieldWantsCaps = cls == InputType.TYPE_CLASS_TEXT && !passwordField &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI
        state.page = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.NUMPAD
            else -> Page.LETTERS
        }
        state.enterLabel = enterLabelFor(info)
        state.typing = false    // top bar shows clipboard & translate until the user writes
        readRecentFromApp()     // one read when the field opens, not on every key
        updateCaps()
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        main.removeCallbacks(autoHideRunnable)
        state.emojiSearch = null
        if (state.translateOn) stopTranslate() else { commitWord(); endEnglishWord() }
        saveLearned()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (state.translateOn) return
        // Our own typing also moves the cursor — ignore that (it is already tracked here).
        if (SystemClock.uptimeMillis() - lastEditTime < 500) return
        bumpAutoHide()      // the user is working in the text (tap, select, paste…)
        // The user tapped somewhere else in the text: start fresh.
        if (roman.isNotEmpty()) {
            roman.clear()
            currentInputConnection?.finishComposingText()
        }
        enWord.clear(); lastWord = null
        readRecentFromApp()
        if (recent.isEmpty()) state.typing = false
        updateCaps()
        refreshSuggestions()
    }

    // ================================================================== where text goes

    private fun markEdit() { lastEditTime = SystemClock.uptimeMillis() }

    private fun rememberTyped(text: String) {
        recent.append(text)
        TextBuf.keepLast(recent, 8)          // last 8 characters (an emoji counts as one)
    }

    private fun readRecentFromApp() {
        recent.setLength(0)
        currentInputConnection?.getTextBeforeCursor(8, 0)?.let { recent.append(it) }
    }

    /** Text goes into the app, or into the translate box while translate is on. */
    private fun sinkSetComposing(text: String) {
        if (state.translateOn) { tComposing = text; translateInputChanged() }
        else { markEdit(); currentInputConnection?.setComposingText(text, 1) }
    }

    private fun sinkCommit(text: String) {
        if (state.translateOn) {
            tComposing = ""              // the committed text replaces the composing word
            tCommitted.append(text)
            translateInputChanged()
        } else {
            markEdit()
            currentInputConnection?.commitText(text, 1)
            rememberTyped(text)
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
            markEdit()
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            TextBuf.dropLast(recent)            // a whole emoji, not half of it
        }
    }

    // ================================================================== keys

    override fun onChar(c: Char) {
        state.emojiSearch?.let { q -> setEmojiQuery(q + c.lowercaseChar()); return }
        state.freshClip = null
        state.typing = true
        if (state.language == Language.ENGLISH || passwordField) {
            // Key labels are CAPITAL, but typing is normal: small letters, Shift = capital.
            val out = when {
                !c.isLetter() -> c
                state.shift == ShiftState.OFF -> c.lowercaseChar()
                else -> c.uppercaseChar()
            }
            if (state.shift == ShiftState.ONCE && c.isLetter()) state.shift = ShiftState.OFF
            if (out.isLetter() || (out == '\'' && enWord.isNotEmpty())) {
                sinkCommit(out.toString())
                enWord.append(out)
            } else {
                endEnglishWord()
                sinkCommit(out.toString())
                updateCaps()
            }
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

    override fun onLongChar(c: Char) {
        if (state.emojiSearch != null) return
        onBackspace()        // remove the letter typed on touch-down
        val shown = if (state.language == Language.BANGLA && c in '0'..'9') "০১২৩৪৫৬৭৮৯"[c - '0'] else c
        onRawText(shown.toString())
    }

    override fun onText(text: String) {
        if (state.prefs.recentEmoji && !noLearn) {
            Prefs.addRecentEmoji(this, text)
            state.recentEmoji = Prefs.recentEmoji(this)
        }
        onRawText(text)
    }

    override fun onRawText(text: String) {
        state.freshClip = null
        state.typing = true
        commitWord()
        endEnglishWord()
        sinkCommit(text)
        updateCaps()
        refreshSuggestions()
    }

    override fun onBackspace() {
        val q = state.emojiSearch
        if (q != null && q.isNotEmpty()) { setEmojiQuery(q.dropLast(1)); return }
        if (roman.isNotEmpty()) {
            roman.setLength(roman.length - 1)
            if (roman.isEmpty()) {
                if (state.translateOn) { tComposing = ""; translateInputChanged() }
                else { markEdit(); currentInputConnection?.commitText("", 1) }
            } else {
                sinkSetComposing(AvroPhonetic.convert(roman.toString()))
            }
        } else {
            if (enWord.isNotEmpty()) enWord.setLength(enWord.length - 1)
            sinkDeleteBefore()
            // Field emptied? Then show the clipboard / translate buttons again.
            if (recent.isEmpty() && !state.translateOn) {
                val before = currentInputConnection?.getTextBeforeCursor(1, 0)
                if (before.isNullOrEmpty()) state.typing = false else recent.append(before)
            }
            updateCaps()
        }
        refreshSuggestions()
    }

    override fun onSpace() {
        state.emojiSearch?.let { q -> if (q.isNotEmpty() && !q.endsWith(" ")) setEmojiQuery("$q "); return }
        val now = SystemClock.uptimeMillis()
        val hadWord = roman.isNotEmpty() || enWord.isNotEmpty()
        commitWord()
        autoCorrectWord()
        endEnglishWord()
        // Double space → full stop (। in Bangla), like iPhone.
        if (state.prefs.doubleSpacePeriod && !state.translateOn && !hadWord && now - lastSpaceTime < 700 &&
            recent.length >= 2 && recent[recent.length - 1] == ' ' &&
            !recent[recent.length - 2].isWhitespace() && recent[recent.length - 2] !in ".।,!?"
        ) {
            markEdit()
            currentInputConnection?.deleteSurroundingText(1, 0)
            TextBuf.dropLast(recent)
            sinkCommit(if (state.language == Language.BANGLA) "। " else ". ")
            lastSpaceTime = 0L
            updateCaps()
            refreshSuggestions()
            return
        }
        sinkCommit(" ")
        lastSpaceTime = now
        updateCaps()
        refreshSuggestions()
    }

    override fun onCursorMove(steps: Int) {
        if (state.translateOn) return
        commitWord()
        enWord.clear(); lastWord = null
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
            endEnglishWord()
        }
        markEdit()
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
            state.typing = false          // sent → buttons come back
            recent.setLength(0)
        } else {
            sendKeyChar('\n')
            rememberTyped("\n")
        }
        lastWord = null
        updateCaps()
        refreshSuggestions()
    }

    override fun onShift() {
        val now = SystemClock.uptimeMillis()
        state.shift = when {
            state.shift == ShiftState.LOCK -> ShiftState.OFF
            now - lastShiftTap < 350 -> ShiftState.LOCK          // double tap = caps lock
            state.shift == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
    }

    /** ENGLISH: capital letter at the start of a sentence — worked out here, no app round-trip. */
    private fun updateCaps() {
        if (state.language != Language.ENGLISH || state.translateOn || passwordField || state.shift == ShiftState.LOCK) return
        if (!state.prefs.autoCapitalize || !fieldWantsCaps) {
            if (state.shift == ShiftState.ONCE && enWord.isEmpty()) state.shift = ShiftState.OFF
            return
        }
        val t = recent.toString()
        val trimmed = t.trimEnd(' ')
        val start = t.isEmpty() || t.endsWith("\n") ||
            (t.endsWith(" ") && (trimmed.isEmpty() || trimmed.last() in ".?!।"))
        state.shift = if (start && enWord.isEmpty()) ShiftState.ONCE else ShiftState.OFF
    }

    override fun onToggleLanguage() {
        commitWord()
        endEnglishWord()
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
        updateCaps()
        refreshSuggestions()
    }

    override fun onOpenSettings() {
        commitWord()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onKeyFeedback(kind: KeyKind) {
        val p = state.prefs
        if (p.sound) {
            val fx = when (kind) {
                KeyKind.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyKind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyKind.RETURN -> AudioManager.FX_KEYPRESS_RETURN
                KeyKind.NORMAL -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, -1f)
        }
        if (p.vibrate) {
            if (p.vibrateStrength == 0) {
                window?.window?.decorView?.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } else {
                vibrate(p.vibrateStrength)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(strength: Int) {
        val v = vibrator ?: return
        val ms = when (strength) { 1 -> 8L; 2 -> 15L; else -> 25L }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = when (strength) { 1 -> 60; 2 -> 140; else -> 255 }
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            v.vibrate(ms)
        }
    }

    override fun onShowImePicker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onPage(page: Page) {
        if (page != Page.LETTERS) { commitWord(); endEnglishWord() }
        state.page = page
    }

    private fun loadExtraWords() {
        val s = suggester ?: return
        if (s.extraSize > 0) return
        runCatching {
            val t0 = SystemClock.elapsedRealtime()
            assets.open("dict_bn_avro.txt").bufferedReader().useLines { s.loadExtra(it) }
            Prefs.sp(this).edit()
                .putLong("bench_extra_ms", SystemClock.elapsedRealtime() - t0)
                .putInt("bench_words_bn", Prefs.sp(this).getInt("bench_words_bn", 0) + s.extraSize)
                .apply()
        }
    }

    /** Emoji list is read only the first time the emoji panel opens (not at every start). */
    private fun ensureEmoji() {
        if (state.emojiLoaded) return
        worker.execute {
            EmojiRepo.load(this)
            main.post { state.emojiLoaded = true }
        }
    }

    override fun onPanel(panel: Panel) {
        if (panel == Panel.EMOJI) ensureEmoji()
        if (panel == Panel.CLIPBOARD) refreshSensitiveLock()
        if (state.emojiSearch != null) { onEmojiSearch(false); if (panel == Panel.EMOJI) return }
        if (panel != Panel.SUGGESTIONS) { commitWord(); endEnglishWord() }
        if (panel == Panel.CLIPBOARD) readClipboard(fresh = false)
        state.panel = panel
    }

    // ================================================================== emoji search

    override fun onEmojiSearch(open: Boolean) {
        if (open) {
            commitWord(); endEnglishWord()
            state.page = Page.LETTERS
            state.shift = ShiftState.OFF
            state.panel = Panel.EMOJI
            setEmojiQuery("")
        } else {
            state.emojiSearch = null
            state.emojiResults = emptyList()
        }
    }

    private fun setEmojiQuery(q: String) {
        state.emojiSearch = q
        state.emojiResults = EmojiRepo.search(q)
    }

    // ================================================================== suggestions

    override fun onSuggestion(index: Int) {
        state.suggestions.getOrNull(index)?.let { onSuggestionWord(it) }
    }

    override fun onSuggestionWord(word: String) {
        if (state.translateOn) {
            // put the chosen word into the translate box
            if (roman.isNotEmpty()) roman.clear()
            else if (enWord.isNotEmpty() && tCommitted.length >= enWord.length) {
                tCommitted.setLength(tCommitted.length - enWord.length)
            }
            enWord.clear()
            sinkCommit("$word ")
            if (state.panel == Panel.SUGGESTIONS) state.panel = Panel.KEYS
            refreshSuggestions()
            return
        }
        if (state.language == Language.BANGLA && !passwordField) {
            roman.clear()
            sinkCommit("$word ")                 // replaces the composing word
        } else {
            if (enWord.isNotEmpty()) {
                markEdit()
                currentInputConnection?.deleteSurroundingText(enWord.length, 0)
                enWord.clear()
            }
            sinkCommit("$word ")
        }
        learn(word)
        if (state.panel == Panel.SUGGESTIONS) state.panel = Panel.KEYS
        updateCaps()
        refreshSuggestions()
    }

    /** Remember the word (on the phone) for better and next-word suggestions. */
    private fun learn(word: String) {
        val w = word.trim()
        if (w.isEmpty() || passwordField) return
        if (state.prefs.learnWords && !noLearn) {
            learner.learn(lastWord, w)
            if (++learnedSinceSave >= 15) saveLearned()
        }
        lastWord = w
    }

    /** Auto-correction (setting): fix an English typo when space is pressed. */
    private fun autoCorrectWord() {
        if (!state.prefs.autoCorrect || state.translateOn || passwordField || enWord.isEmpty()) return
        val typed = enWord.toString()
        val fix = suggester?.correct(typed) ?: return
        val out = matchCase(fix, typed)
        markEdit()
        currentInputConnection?.deleteSurroundingText(typed.length, 0)
        currentInputConnection?.commitText(out, 1)
        enWord.setLength(0); enWord.append(out)
    }

    private fun endEnglishWord() {
        if (enWord.isEmpty()) return
        learn(enWord.toString().lowercase())
        enWord.clear()
    }

    private fun learnedFile() = File(filesDir, "learned_words.txt")

    private fun saveLearned() {
        if (learnedSinceSave == 0) return
        learnedSinceSave = 0
        val text = learner.serialize()
        suggester?.learnedCounts = learner.snapshotCounts()
        worker.execute { runCatching { learnedFile().writeText(text) } }
    }

    private fun refreshSuggestions() {
        if (passwordField) {
            state.suggestions = emptyList(); state.moreSuggestions = emptyList(); return
        }
        val seq = ++suggestionSeq
        val bangla = state.language == Language.BANGLA
        val romanNow = roman.toString()
        val typed = enWord.toString()
        val prev = lastWord
        val s = suggester

        val p = state.prefs
        if ((romanNow.isNotEmpty() || typed.isNotEmpty()) && !p.wordSuggestions) {
            state.suggestions = if (bangla && romanNow.isNotEmpty()) listOf(AvroPhonetic.convert(romanNow)) else emptyList()
            state.moreSuggestions = state.suggestions
            return
        }
        if (romanNow.isEmpty() && typed.isEmpty() && !p.nextWordSuggestions) {
            state.suggestions = emptyList(); state.moreSuggestions = emptyList(); return
        }
        worker.execute {
            val raw: List<String> = when {
                bangla && romanNow.isNotEmpty() -> {
                    val conv = AvroPhonetic.convert(romanNow)
                    val list0 = s?.bangla(conv, 18) ?: listOf(conv)
                    // jemon → যেমন first (Avro's জেমন stays as the 2nd choice)
                    val fix = if (romanNow.length >= 3) s?.autoFix(conv) else null
                    val base = if (fix != null) listOf(fix) + list0.filter { it != fix } else list0
                    if (fix != null) main.post {
                        if (seq == suggestionSeq && roman.toString() == romanNow) {
                            autoWord = fix; autoFor = romanNow
                            sinkSetComposing(fix)
                        }
                    }
                    // chottogram → চট্টগ্রাম … as an extra suggestion; the Avro result stays first
                    RomanAliases.merge(base, aliases?.lookup(romanNow) ?: emptyList())
                }
                !bangla && typed.isNotEmpty() ->
                    (s?.english(typed, 18) ?: emptyList()).map { matchCase(it, typed) }
                else -> nextWordList(bangla, prev, s)
            }
            val list = if (p.blockOffensive) raw.filter { it.lowercase() !in OFFENSIVE } else raw
            main.post {
                if (seq == suggestionSeq) {
                    state.suggestions = list.take(3)
                    state.moreSuggestions = list
                }
            }
        }
    }

    /** Nothing typed yet: learned next words first, then the most common words. */
    private fun nextWordList(bangla: Boolean, prev: String?, s: Suggester?): List<String> {
        val fits = { w: String -> if (bangla) w.any { it in 'ঀ'..'৿' } else w.all { it.code < 128 } }
        val out = LinkedHashSet<String>()
        if (prev != null) learner.nextWords(prev, 12).filter(fits).forEach { out.add(it) }
        if (bangla) {
            if (out.isEmpty()) listOf("আমি", "সে", "আপনি").forEach { out.add(it) }
            s?.topBangla?.forEach { if (out.size < 18) out.add(it) }
        } else {
            if (out.isEmpty()) listOf("I", "The", "I'm").forEach { out.add(it) }
            s?.topEnglish?.forEach { if (out.size < 18) out.add(if (it == "i" || it.startsWith("i'")) it.replaceFirstChar { c -> c.uppercaseChar() } else it) }
        }
        return out.toList()
    }

    /** "hel" → hello, "Hel" → Hello, "HEL" → HELLO */
    private fun matchCase(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
        word == "i" || word.startsWith("i'") -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    companion object {
        /** Small list hidden when "আপত্তিকর শব্দ সাজেস্ট করো না" is on. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private const val NO_SENSITIVE_TRANSLATE = "সংবেদনশীল লেখা অনুবাদের বক্সে দেওয়া যায় না (অনলাইনে যেতে পারে)"

        private val OFFENSIVE = setOf(
            "fuck", "fucking", "fucked", "shit", "bitch", "bastard", "asshole", "dick", "pussy", "cunt",
            "whore", "slut", "motherfucker", "damn", "crap", "fucker", "bullshit",
            "মাগি", "খানকি", "চুদি", "চোদা", "বাল", "শালা", "হারামি", "কুত্তা", "শুয়োর",
        )
    }

    // ================================================================== voice

    override fun onVoice() {
        commitWord()
        endEnglishWord()
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
            val token = window?.window?.attributes?.token ?: return
            @Suppress("DEPRECATION")
            imm.setInputMethodAndSubtype(token, info.id, subtype)
        }
    }

    // ================================================================== clipboard

    private fun clipboard() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Called the moment something is copied — it shows up in the clipboard right away. */
    private fun readClipboard(fresh: Boolean) {
        if (ignoreNextClip && fresh) { ignoreNextClip = false; return }
        if (!state.prefs.clipboardOn) return
        runCatching {
            val clip = clipboard().primaryClip ?: return
            if (clip.itemCount == 0) return
            // The copying app marked it private (passwords, OTPs …): kept only if the user turned
            // that on in settings — then stored encrypted, preview hidden, phone lock needed.
            val sensitive = clip.description.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true
            if (sensitive && !state.prefs.saveSensitive) return
            // kept exactly as copied — spaces, tabs and line breaks included
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isBlank()) return
            clips.add(text, sensitive)
            if (fresh && !sensitive) {
                state.freshClip = text
                main.removeCallbacks(clearFreshClip)
                main.postDelayed(clearFreshClip, 60_000)
            }
        }
    }

    override fun onClipPaste(text: String) {
        state.freshClip = null
        state.typing = true
        commitWord()
        endEnglishWord()
        sinkCommit(text)
        updateCaps()
        refreshSuggestions()
    }

    override fun onClipTap(id: Long) = clipAction(id, SensitiveAction.PASTE)
    override fun onClipAction(id: Long, action: SensitiveAction) = clipAction(id, action)

    private fun clipAction(id: Long, action: SensitiveAction) {
        val item = clips.find(id) ?: return
        if (item.sensitive && !SensitiveGate.isUnlocked()) {
            if (action == SensitiveAction.PASTE && state.translateOn) { toast(NO_SENSITIVE_TRANSLATE); return }
            SensitiveGate.setPending(id, action)
            AuthActivity.start(this)          // phone PIN / fingerprint, then back here
            return
        }
        runClipAction(item, action)
    }

    private fun runClipAction(item: ClipItem, action: SensitiveAction) {
        when (action) {
            SensitiveAction.PASTE -> {
                // never into the translate box: that text may be sent online
                if (item.sensitive && state.translateOn) { toast(NO_SENSITIVE_TRANSLATE); return }
                onClipPaste(item.text)
            }
            SensitiveAction.COPY -> {
                ignoreNextClip = true
                val data = ClipData.newPlainText("JuJuKeys", item.text)
                if (item.sensitive) data.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
                clipboard().setPrimaryClip(data)
                toast("কপি হয়েছে")
            }
            SensitiveAction.KEEP -> onClipToKeep(item.text)          // only when the user picks it
            SensitiveAction.VIEW -> state.panel = Panel.CLIPBOARD
        }
    }

    private val relockRunnable = Runnable { state.sensitiveUnlocked = SensitiveGate.isUnlocked() }
    private fun refreshSensitiveLock() {
        state.sensitiveUnlocked = SensitiveGate.isUnlocked()
        main.removeCallbacks(relockRunnable)
        if (state.sensitiveUnlocked) main.postDelayed(relockRunnable, SensitiveGate.UNLOCK_MS + 200)
    }

    override fun onClipPin(id: Long) = clips.togglePin(id)
    override fun onClipDelete(id: Long) = clips.delete(id)
    override fun onClipClear() = clips.clearUnpinned()

    override fun onClipEnabled(on: Boolean) {
        Prefs.setBoolean(this, "clipboard", on)
        loadPrefs()
        if (on) readClipboard(fresh = false)
    }

    override fun onClipToKeep(text: String) {
        if (ClipHistory.sendToKeep(this, text)) toast("Google Keep-এ সেভ করতে 'Save' চাপুন")
        else toast("Google Keep ইনস্টল নেই — Play Store খোলা হলো")
    }

    /**
     * ✎ = everything goes to ONE fixed Keep note ("JuJuKeys ক্লিপবোর্ড").
     * First time: that note is created with everything in it.
     * After that: only the NEW items are copied and Keep opens, so they can be pasted into
     * the same note (Google does not let other apps edit a Keep note directly).
     */
    /**
     * ✎ = hand the clipboard to ONE Keep note ("JuJuKeys ক্লিপবোর্ড"). This is NOT an automatic
     * sync: Keep does not let other apps add to a note, nor tell them whether it was saved.
     * First time: Keep's "new note" screen opens with everything in it.
     * After that: only the NEW items are copied and Keep opens, to paste into that note.
     * Nothing counts as "sent" until the user answers "হ্যাঁ" to "Keep-এ সেভ হয়েছে?".
     */
    override fun onClipAllToKeep() {
        // sensitive items never go along automatically — only one by one, after the phone lock
        val items = clips.items.filter { !it.sensitive }
        if (items.isEmpty()) { toast("পাঠানোর মতো লেখা নেই (সংবেদনশীল লেখা আলাদা করে পাঠাতে হয়)"); return }
        if (!ClipHistory.isKeepInstalled(this)) {
            ClipHistory.openKeep(this)          // opens the Play Store page
            toast("Google Keep ইনস্টল নেই — Play Store খোলা হলো। কিছুই পাঠানো হয়নি")
            return
        }
        if (!Prefs.keepNoteCreated(this)) {
            if (ClipHistory.sendAllToKeep(this, items)) {
                KeepLedger.handOff(this, items.map { it.id }.toSet(), creatingNote = true)
                state.keepConfirm = items.size
                toast("Keep-এ নতুন নোট খুলেছে — 'Save' চাপুন, তারপর কীবোর্ডে ফিরে 'হ্যাঁ' চাপুন")
            } else toast("Google Keep খোলা যায়নি — কিছুই পাঠানো হয়নি")
            return
        }
        val fresh = KeepLedger.unsent(this, items)
        if (fresh.isEmpty()) {
            toast("নতুন লেখা নেই — আপনি 'হ্যাঁ' বলা সব লেখা আগেই পাঠানো। আবার পাঠাতে: সেটিংস → ক্লিপবোর্ড")
            return
        }
        ignoreNextClip = true
        clipboard().setPrimaryClip(ClipData.newPlainText("JuJuKeys", fresh.joinToString("\n\n") { it.text }))
        KeepLedger.handOff(this, fresh.map { it.id }.toSet(), creatingNote = false)
        state.keepConfirm = fresh.size
        ClipHistory.openKeep(this)
        toast("${fresh.size}টি নতুন লেখা কপি হয়েছে — Keep-এ 'JuJuKeys ক্লিপবোর্ড' নোটে লম্বা চেপে Paste করুন, তারপর কীবোর্ডে ফিরে 'হ্যাঁ' চাপুন")
    }

    override fun onKeepConfirm(saved: Boolean) {
        KeepLedger.confirm(this, saved)
        if (!saved) toast("ঠিক আছে — লেখাগুলো পাঠানো হয়নি ধরা হলো, পরে ✎ চাপলে আবার যাবে")
        state.keepConfirm = 0
    }

    override fun onOpenKeep() {
        if (!ClipHistory.openKeep(this)) toast("Google Keep ইনস্টল নেই — Play Store খোলা হলো")
    }

    // ================================================================== translate

    override fun onTranslateToggle() {
        if (state.translateOn) { stopTranslate(); return }
        commitWord()
        endEnglishWord()
        state.panel = Panel.KEYS
        state.translateOn = true
        state.translateFrom = state.language
        state.translateStatus = ""
        clearTranslateBox()
        state.online = translator.isOnline()
        translator.warmUp()          // load the model now, so the first translation is quick
        translator.checkOfflineModel { ready ->
            state.offlineReady = ready
            if (!ready && translator.isOnline()) {
                state.translateStatus = "অফলাইন মডেল নামানো হচ্ছে (একবারই, ~৩০MB)…"
                translator.downloadOfflineModel { ok, err ->
                    state.offlineReady = ok
                    state.translateStatus = if (ok) "অফলাইন অনুবাদ প্রস্তুত ✓" else "মডেল নামানো যায়নি: ${err ?: ""}"
                    if (ok) translator.warmUp()
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
        lastTranslation = ""; translatePending = false
        roman.clear()
        tCommitted.setLength(0); tComposing = ""
        state.translateInput = ""
        state.translateOn = false
        state.translateStatus = ""
        translator.release()                          // free the ML Kit models' memory
        markEdit()
        currentInputConnection?.finishComposingText()   // keep the translation in the app
        refreshSuggestions()
    }

    private fun clearTranslateBox() {
        main.removeCallbacks(translateRunnable)
        lastTranslation = ""
        roman.clear()
        tCommitted.setLength(0); tComposing = ""
        state.translateInput = ""
        markEdit()
        currentInputConnection?.setComposingText("", 1)
    }

    private fun translateInputChanged() {
        state.translateInput = tCommitted.toString() + tComposing
        main.removeCallbacks(translateRunnable)
        main.postDelayed(translateRunnable, 400)   // translate once typing pauses — keeps typing smooth
    }

    private fun runTranslation() {
        if (translating) { translatePending = true; return }
        val raw = (tCommitted.toString() + tComposing).trim()
        val seq = ++translateSeq
        if (raw.isEmpty()) {
            lastTranslation = ""
            markEdit()
            currentInputConnection?.setComposingText("", 1)
            return
        }
        val toEnglish = state.translateFrom == Language.BANGLA
        translating = true
        translator.translate(
            raw, toEnglish,
            status = { state.translateStatus = it },
            onResult = { result, error ->
                translating = false
                if (translatePending) {          // text changed while we were busy → translate the latest once
                    translatePending = false
                    main.post(translateRunnable)
                }
                if (seq != translateSeq || !state.translateOn) return@translate
                if (result != null && result.text != lastTranslation) {
                    lastTranslation = result.text
                    markEdit()
                    currentInputConnection?.setComposingText(result.text, 1)
                    if (state.translateStatus.isNotEmpty()) state.translateStatus = ""
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
        val r = roman.toString()
        val bangla = autoWord?.takeIf { autoFor == r } ?: AvroPhonetic.convert(r)
        autoWord = null
        roman.clear()
        if (state.translateOn) {
            tComposing = ""
            tCommitted.append(bangla)
            translateInputChanged()
        } else {
            markEdit()
            currentInputConnection?.commitText(bangla, 1)
            rememberTyped(bangla)
            learn(bangla)
        }
    }

    /** The return key is always plain; only search fields get a 🔍 icon. */
    private fun enterLabelFor(info: EditorInfo): String =
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
            (info.imeOptions and EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEARCH
        ) "search" else ""

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
