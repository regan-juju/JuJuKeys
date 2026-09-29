package com.reganbarua.jujukeys

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.reganbarua.jujukeys.keyboard.KeyboardActions
import com.reganbarua.jujukeys.keyboard.KeyboardState
import com.reganbarua.jujukeys.keyboard.KeyboardView
import com.reganbarua.jujukeys.keyboard.Language
import com.reganbarua.jujukeys.keyboard.Page
import com.reganbarua.jujukeys.keyboard.ShiftState
import com.reganbarua.jujukeys.keyboard.ToolbarItem

/**
 * The system keyboard. Works in every app once enabled in Android settings.
 *
 * English: every letter is typed as a CAPITAL letter.
 * বাংলা:   letters are collected as a Roman word and converted with the Avro engine
 *          (bhalo → ভালো). Without Shift a key gives a small letter (t → ত);
 *          with Shift it gives a capital letter (T → ট).
 *
 * Privacy: nothing typed is stored, logged or sent anywhere.
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
    private val roman = StringBuilder()   // Roman letters of the Bangla word being typed
    private var lastShiftTap = 0L

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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
            setContent { KeyboardView(state, this@JuJuKeysInputMethodService) }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ editor lifecycle

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearWord()
        state.shift = ShiftState.OFF
        state.page = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.SYMBOLS
            else -> Page.LETTERS
        }
        state.enterLabel = enterLabelFor(info)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        commitWord()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user tapped somewhere else in the text: finish the word being typed.
        if (roman.isNotEmpty() &&
            (candidatesStart == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            roman.clear()
            updatePreview()
            currentInputConnection?.finishComposingText()
        }
    }

    // ------------------------------------------------------------------ key actions

    override fun onChar(c: Char) {
        val ic = currentInputConnection ?: return
        if (state.language == Language.ENGLISH) {
            ic.commitText(c.uppercaseChar().toString(), 1)   // English: always CAPITAL
            return
        }
        // বাংলা
        val ch = if (c.isLetter()) {
            if (state.shift == ShiftState.OFF) c.lowercaseChar() else c.uppercaseChar()
        } else c
        if (state.shift == ShiftState.ONCE && c.isLetter()) state.shift = ShiftState.OFF

        if (AvroPhonetic.isPhoneticChar(ch)) {
            roman.append(ch)
            val bangla = AvroPhonetic.convert(roman.toString())
            ic.setComposingText(bangla, 1)
            updatePreview(bangla)
        } else {
            commitWord()
            ic.commitText(ch.toString(), 1)
        }
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (roman.isNotEmpty()) {
            roman.setLength(roman.length - 1)
            if (roman.isEmpty()) {
                ic.commitText("", 1)
                updatePreview()
            } else {
                val bangla = AvroPhonetic.convert(roman.toString())
                ic.setComposingText(bangla, 1)
                updatePreview(bangla)
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    override fun onSpace() {
        commitWord()
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onEnter() {
        commitWord()
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendKeyChar('\n')
        }
    }

    override fun onShift() {
        if (state.language == Language.ENGLISH) {
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
        state.language = if (state.language == Language.BANGLA) Language.ENGLISH else Language.BANGLA
        state.shift = ShiftState.OFF
    }

    override fun onPage(page: Page) {
        state.page = page
    }

    override fun onToolbar(item: ToolbarItem) {
        when (item) {
            ToolbarItem.SETTINGS -> {
                val intent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            ToolbarItem.CLIPBOARD -> toast("ক্লিপবোর্ড ও Google Keep — ধাপ ২-এ আসছে")
            ToolbarItem.TRANSLATE -> toast("Google Translate (অনলাইন/অফলাইন) — ধাপ ২-এ আসছে")
            ToolbarItem.MIC -> toast("ভয়েস টাইপিং — ধাপ ২-এ আসছে")
            ToolbarItem.EMOJI -> toast("ইমোজি — ধাপ ২-এ আসছে")
            ToolbarItem.MENU -> toast("মেনু — ধাপ ২-এ আসছে")
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Puts the finished Bangla word into the text field. */
    private fun commitWord() {
        if (roman.isEmpty()) return
        val bangla = AvroPhonetic.convert(roman.toString())
        roman.clear()
        currentInputConnection?.commitText(bangla, 1)
        updatePreview()
    }

    private fun clearWord() {
        roman.clear()
        updatePreview()
    }

    private fun updatePreview(bangla: String = "") {
        state.romanPreview = roman.toString()
        state.banglaPreview = bangla
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return ""
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
            EditorInfo.IME_ACTION_DONE -> "DONE"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            else -> ""
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
