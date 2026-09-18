package com.reganbarua.jujukeys

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
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
import com.reganbarua.jujukeys.bengali.BengaliPhoneticEngine
import com.reganbarua.jujukeys.clipboard.ClipboardHistoryStore
import com.reganbarua.jujukeys.clipboard.ClipboardManagerHelper
import com.reganbarua.jujukeys.keyboard.KeyboardActionListener
import com.reganbarua.jujukeys.keyboard.KeyboardLanguage
import com.reganbarua.jujukeys.keyboard.KeyboardPanel
import com.reganbarua.jujukeys.keyboard.KeyboardUiState
import com.reganbarua.jujukeys.keyboard.KeyboardView
import com.reganbarua.jujukeys.settings.PreferencesRepository
import com.reganbarua.jujukeys.settings.SettingsActivity
import com.reganbarua.jujukeys.suggestions.SuggestionEngine
import com.reganbarua.jujukeys.theme.JuJuKeysTheme
import com.reganbarua.jujukeys.translation.TranslateUiState
import com.reganbarua.jujukeys.translation.TranslationDirection
import com.reganbarua.jujukeys.translation.TranslationEngine
import com.reganbarua.jujukeys.translation.TranslationResult
import com.reganbarua.jujukeys.voice.MicPermissionActivity
import com.reganbarua.jujukeys.voice.MicPermissionBus
import com.reganbarua.jujukeys.voice.VoiceInputHelper
import com.reganbarua.jujukeys.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The real Android system keyboard. Registered in AndroidManifest.xml + res/xml/method.xml,
 * so once installed it appears under Settings > System > Languages & input > On-screen
 * keyboard, and can be selected as the active keyboard system-wide.
 *
 * This class is the ONLY place that touches [InputConnection]. Everything visual lives in
 * the `keyboard` / `emoji` / `clipboard` / `translation` packages and only ever calls back
 * through [KeyboardActionListener].
 */
class JuJuKeysInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    KeyboardActionListener {

    // --- Compose-in-a-Service plumbing -------------------------------------------------
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    // --- State ---------------------------------------------------------------------------
    private var uiState by mutableStateOf(KeyboardUiState())
    private var clipboardItems by mutableStateOf<List<String>>(emptyList())
    private var recentEmoji by mutableStateOf<List<String>>(emptyList())
    private var translateState by mutableStateOf(TranslateUiState())
    private var keySizeScale by mutableStateOf(1f)
    private var heightScale by mutableStateOf(1f)

    // Raw ASCII typed since the last word boundary, per language.
    private val bengaliRawBuffer = StringBuilder()
    private val englishWordBuffer = StringBuilder()

    private var isSensitiveField = false
    private var suggestionsEnabledPref = true
    private var autocorrectEnabledPref = true

    private lateinit var preferences: PreferencesRepository
    private lateinit var clipboardHelper: ClipboardManagerHelper
    private lateinit var voiceInputHelper: VoiceInputHelper

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        preferences = PreferencesRepository(this)
        clipboardHelper = ClipboardManagerHelper(this)
        voiceInputHelper = VoiceInputHelper(this)

        serviceScope.launch { preferences.suggestionsEnabled.collect { suggestionsEnabledPref = it } }
        serviceScope.launch { preferences.autocorrectEnabled.collect { autocorrectEnabledPref = it } }
        serviceScope.launch { preferences.keySizeScale.collect { keySizeScale = it } }
        serviceScope.launch { preferences.keyboardHeightScale.collect { heightScale = it } }
        serviceScope.launch { preferences.recentEmoji.collect { recentEmoji = it } }
        serviceScope.launch {
            MicPermissionBus.events.collect { granted ->
                if (granted) startVoiceInput() else toast(getString(R.string.mic_permission_needed))
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@JuJuKeysInputMethodService)
            setViewTreeViewModelStoreOwner(this@JuJuKeysInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@JuJuKeysInputMethodService)
            setContent {
                JuJuKeysTheme {
                    KeyboardView(
                        state = uiState,
                        clipboardItems = clipboardItems,
                        recentEmoji = recentEmoji,
                        translateState = translateState,
                        listener = this@JuJuKeysInputMethodService,
                        keySizeScale = keySizeScale,
                        heightScale = heightScale
                    )
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        bengaliRawBuffer.clear()
        englishWordBuffer.clear()

        isSensitiveField = isPasswordVariation(info)

        val actionLabel = editorActionLabel(info)
        uiState = uiState.copy(panel = KeyboardPanel.LETTERS, suggestions = emptyList(), editorActionLabel = actionLabel)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voiceInputHelper.cancel()
        finishComposingSafely()
    }

    override fun onDestroy() {
        voiceInputHelper.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- EditorInfo helpers ---------------------------------------------------------------

    private fun isPasswordVariation(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun editorActionLabel(info: EditorInfo?): String? {
        info ?: return null
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> null
        }
    }

    // --- Small InputConnection helpers -----------------------------------------------------

    private fun ic(): InputConnection? = currentInputConnection

    private fun finishComposingSafely() {
        if (bengaliRawBuffer.isNotEmpty()) {
            ic()?.finishComposingText()
            bengaliRawBuffer.clear()
        }
    }

    private fun refreshSuggestions() {
        if (!suggestionsEnabledPref || isSensitiveField) {
            uiState = uiState.copy(suggestions = emptyList())
            return
        }
        val currentWord = when (uiState.language) {
            KeyboardLanguage.ENGLISH -> englishWordBuffer.toString()
            KeyboardLanguage.BENGALI -> BengaliPhoneticEngine.transliterateWord(bengaliRawBuffer.toString())
        }
        uiState = uiState.copy(suggestions = SuggestionEngine.suggestionsFor(uiState.language, currentWord))
    }

    // --- KeyboardActionListener -------------------------------------------------------------

    override fun onLetterKey(raw: Char) {
        val connection = ic() ?: return
        when (uiState.language) {
            KeyboardLanguage.ENGLISH -> {
                // Spec requirement: English typed output is ALWAYS uppercase, enforced here
                // before commitText — not just in the key label.
                val upper = raw.uppercaseChar()
                connection.commitText(upper.toString(), 1)
                englishWordBuffer.append(upper)
            }
            KeyboardLanguage.BENGALI -> {
                bengaliRawBuffer.append(raw)
                val transliterated = BengaliPhoneticEngine.transliterateWord(bengaliRawBuffer.toString())
                connection.setComposingText(transliterated, 1)
                if (uiState.bengaliShiftOn) {
                    uiState = uiState.copy(bengaliShiftOn = false) // single-shot shift, like standard mobile keyboards
                }
            }
        }
        refreshSuggestions()
    }

    override fun onSpace() {
        val connection = ic() ?: return
        when (uiState.language) {
            KeyboardLanguage.ENGLISH -> {
                maybeAutocorrectCurrentWord(connection)
                connection.commitText(" ", 1)
                englishWordBuffer.clear()
            }
            KeyboardLanguage.BENGALI -> {
                connection.finishComposingText()
                connection.commitText(" ", 1)
                bengaliRawBuffer.clear()
            }
        }
        uiState = uiState.copy(suggestions = emptyList())
    }

    private fun maybeAutocorrectCurrentWord(connection: InputConnection) {
        if (!autocorrectEnabledPref || isSensitiveField) return
        val current = englishWordBuffer.toString()
        val corrected = SuggestionEngine.autocorrectFor(current) ?: return
        connection.deleteSurroundingText(current.length, 0)
        connection.commitText(corrected, 1) // already uppercase, per EnglishDictionary
    }

    override fun onBackspace() {
        val connection = ic() ?: return
        when (uiState.language) {
            KeyboardLanguage.ENGLISH -> {
                if (englishWordBuffer.isNotEmpty()) englishWordBuffer.deleteCharAt(englishWordBuffer.length - 1)
                connection.deleteSurroundingText(1, 0)
            }
            KeyboardLanguage.BENGALI -> {
                if (bengaliRawBuffer.isNotEmpty()) {
                    bengaliRawBuffer.deleteCharAt(bengaliRawBuffer.length - 1)
                    if (bengaliRawBuffer.isEmpty()) {
                        connection.setComposingText("", 1)
                        connection.finishComposingText()
                    } else {
                        val transliterated = BengaliPhoneticEngine.transliterateWord(bengaliRawBuffer.toString())
                        connection.setComposingText(transliterated, 1)
                    }
                } else {
                    connection.deleteSurroundingText(1, 0)
                }
            }
        }
        refreshSuggestions()
    }

    override fun onBackspaceLongPressRepeat() {
        onBackspace()
    }

    override fun onEnter() {
        val connection = ic() ?: return
        finishComposingSafely()
        val actionId = currentInputEditorInfo?.let { it.imeOptions and EditorInfo.IME_MASK_ACTION } ?: EditorInfo.IME_ACTION_NONE
        val hasNoEnterFlag = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED && !hasNoEnterFlag) {
            connection.performEditorAction(actionId)
        } else {
            connection.commitText("\n", 1)
        }
        englishWordBuffer.clear()
        uiState = uiState.copy(suggestions = emptyList())
    }

    override fun onSymbolOrPunctuation(text: String) {
        val connection = ic() ?: return
        finishComposingSafely()
        connection.commitText(text, 1)
        englishWordBuffer.clear()
        uiState = uiState.copy(suggestions = emptyList())
    }

    override fun onToggleShift() {
        uiState = uiState.copy(bengaliShiftOn = !uiState.bengaliShiftOn)
    }

    override fun onGlobe() {
        finishComposingSafely()
        englishWordBuffer.clear()
        val next = if (uiState.language == KeyboardLanguage.ENGLISH) KeyboardLanguage.BENGALI else KeyboardLanguage.ENGLISH
        uiState = uiState.copy(language = next, panel = KeyboardPanel.LETTERS, suggestions = emptyList(), bengaliShiftOn = false)
        serviceScope.launch { preferences.setLastLanguage(next.name) }
    }

    override fun onSwitchToLetters() {
        uiState = uiState.copy(panel = KeyboardPanel.LETTERS)
    }

    override fun onSwitchToSymbols1() {
        finishComposingSafely()
        uiState = uiState.copy(panel = KeyboardPanel.SYMBOLS_1)
    }

    override fun onSwitchToSymbols2() {
        uiState = uiState.copy(panel = KeyboardPanel.SYMBOLS_2)
    }

    override fun onSwitchToEmoji() {
        finishComposingSafely()
        uiState = uiState.copy(panel = KeyboardPanel.EMOJI)
    }

    override fun onOpenClipboardPanel() {
        finishComposingSafely()
        if (!isSensitiveField) clipboardHelper.syncFromSystemClipboard()
        clipboardItems = ClipboardHistoryStore.all()
        uiState = uiState.copy(panel = KeyboardPanel.CLIPBOARD)
    }

    override fun onOpenTranslatePanel() {
        finishComposingSafely()
        translateState = TranslateUiState(
            direction = translateState.direction,
            onSwapDirection = ::swapTranslateDirection,
            onTranslate = ::runTranslate,
            onClear = ::clearTranslate,
            onInsertResult = ::insertTranslateResult
        )
        uiState = uiState.copy(panel = KeyboardPanel.TRANSLATE)
    }

    override fun onOpenSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onMicTapped() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceInput()
        } else {
            val intent = Intent(this, MicPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun startVoiceInput() {
        if (!voiceInputHelper.isAvailable()) {
            toast(getString(R.string.mic_unavailable))
            return
        }
        voiceInputHelper.start(uiState.language) { state ->
            when (state) {
                is VoiceState.Result -> {
                    finishComposingSafely()
                    ic()?.commitText(state.text + " ", 1)
                }
                is VoiceState.Error -> if (state.message != "no_match") toast(getString(R.string.mic_unavailable))
                else -> Unit
            }
        }
    }

    override fun onEmojiSelected(emoji: String) {
        finishComposingSafely()
        ic()?.commitText(emoji, 1)
        serviceScope.launch { preferences.pushRecentEmoji(emoji) }
    }

    override fun onSuggestionSelected(word: String) {
        val connection = ic() ?: return
        when (uiState.language) {
            KeyboardLanguage.ENGLISH -> {
                connection.deleteSurroundingText(englishWordBuffer.length, 0)
                connection.commitText("$word ", 1)
                englishWordBuffer.clear()
            }
            KeyboardLanguage.BENGALI -> {
                connection.setComposingText(word, 1)
                connection.finishComposingText()
                connection.commitText(" ", 1)
                bengaliRawBuffer.clear()
            }
        }
        uiState = uiState.copy(suggestions = emptyList())
    }

    override fun onClipboardItemSelected(text: String) {
        ic()?.commitText(text, 1)
        uiState = uiState.copy(panel = KeyboardPanel.LETTERS)
    }

    override fun onClipboardClear() {
        ClipboardHistoryStore.clear()
        clipboardItems = emptyList()
    }

    override fun onShareToKeep(text: String) {
        clipboardHelper.shareToKeepOrSharesheet(text)
    }

    // --- Translation panel callbacks --------------------------------------------------------

    private fun swapTranslateDirection() {
        val newDirection = if (translateState.direction == TranslationDirection.BN_TO_EN) {
            TranslationDirection.EN_TO_BN
        } else {
            TranslationDirection.BN_TO_EN
        }
        translateState = translateState.copy(direction = newDirection, resultText = "", hint = null)
    }

    private fun runTranslate() {
        // Source text for translation is whatever the user has just composed/typed; simplest
        // reliable source in an IME is the current composing/committed word buffer.
        val source = when (translateState.direction) {
            TranslationDirection.BN_TO_EN -> BengaliPhoneticEngine.transliterateWord(bengaliRawBuffer.toString())
                .ifEmpty { translateState.sourceText }
            TranslationDirection.EN_TO_BN -> englishWordBuffer.toString().ifEmpty { translateState.sourceText }
        }
        translateState = translateState.copy(sourceText = source)
        serviceScope.launch {
            when (val result = TranslationEngine.translate(source, translateState.direction)) {
                is TranslationResult.Success -> translateState = translateState.copy(resultText = result.text, hint = null)
                TranslationResult.NoMatch -> translateState = translateState.copy(resultText = "", hint = getString(R.string.translation_no_match))
                TranslationResult.OnlineUnavailable -> translateState = translateState.copy(resultText = "", hint = getString(R.string.translation_unavailable_online))
            }
        }
    }

    private fun clearTranslate() {
        translateState = translateState.copy(sourceText = "", resultText = "", hint = null)
    }

    private fun insertTranslateResult() {
        if (translateState.resultText.isEmpty()) return
        finishComposingSafely()
        ic()?.commitText(translateState.resultText + " ", 1)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
