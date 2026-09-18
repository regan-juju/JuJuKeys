package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.R
import com.reganbarua.jujukeys.clipboard.ClipboardPanel
import com.reganbarua.jujukeys.emoji.EmojiPicker
import com.reganbarua.jujukeys.theme.JuJuColors
import com.reganbarua.jujukeys.translation.TranslateUiState
import com.reganbarua.jujukeys.translation.TranslatePanel

/**
 * Top-level keyboard composable. Pure UI: every user action is forwarded to
 * [KeyboardActionListener]; this file never touches InputConnection directly.
 */
@Composable
fun KeyboardView(
    state: KeyboardUiState,
    clipboardItems: List<String>,
    recentEmoji: List<String>,
    translateState: TranslateUiState,
    listener: KeyboardActionListener,
    keySizeScale: Float = 1f,
    heightScale: Float = 1f
) {
    val rowHeight = (46 * heightScale).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JuJuColors.KeyboardBackground)
            .padding(bottom = 4.dp)
    ) {
        KeyboardToolbar(
            onClipboard = listener::onOpenClipboardPanel,
            onTranslate = listener::onOpenTranslatePanel,
            onSettings = listener::onOpenSettings,
            onMic = listener::onMicTapped
        )

        when (state.panel) {
            KeyboardPanel.CLIPBOARD -> ClipboardPanel(
                items = clipboardItems,
                onItemTap = listener::onClipboardItemSelected,
                onItemShareToKeep = listener::onShareToKeep,
                onClear = listener::onClipboardClear,
                onBackToLetters = listener::onSwitchToLetters
            )

            KeyboardPanel.TRANSLATE -> TranslatePanel(
                sourceText = translateState.sourceText,
                resultText = translateState.resultText,
                resultHint = translateState.hint,
                direction = translateState.direction,
                onSwapDirection = translateState.onSwapDirection,
                onTranslate = translateState.onTranslate,
                onClear = translateState.onClear,
                onInsertResult = translateState.onInsertResult,
                onBackToLetters = listener::onSwitchToLetters
            )

            KeyboardPanel.EMOJI -> EmojiPicker(
                recent = recentEmoji,
                onEmojiTap = listener::onEmojiSelected,
                onBackToLetters = listener::onSwitchToLetters
            )

            else -> {
                SuggestionStrip(suggestions = state.suggestions, onSuggestionTap = listener::onSuggestionSelected)
                LettersOrSymbols(state = state, listener = listener, rowHeight = rowHeight)
            }
        }
    }
}

@Composable
private fun LettersOrSymbols(
    state: KeyboardUiState,
    listener: KeyboardActionListener,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp)) {
        when (state.panel) {
            KeyboardPanel.SYMBOLS_1 -> SymbolRows(
                rows = KeyboardLayouts.symbols1Rows,
                rowHeight = rowHeight,
                listener = listener,
                showMoreSymbols = true
            )
            KeyboardPanel.SYMBOLS_2 -> SymbolRows(
                rows = KeyboardLayouts.symbols2Rows,
                rowHeight = rowHeight,
                listener = listener,
                showMoreSymbols = false
            )
            else -> LetterRows(state = state, listener = listener, rowHeight = rowHeight)
        }

        BottomRow(state = state, listener = listener, rowHeight = rowHeight)
    }
}

@Composable
private fun LetterRows(
    state: KeyboardUiState,
    listener: KeyboardActionListener,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    val isBengali = state.language == KeyboardLanguage.BENGALI
    val shiftOn = isBengali && state.bengaliShiftOn

    // Row 1
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        KeyboardLayouts.englishRows[0].forEach { ch ->
            LetterKey(ch, isBengali, shiftOn, Modifier.weight(1f), listener)
        }
    }
    // Row 2 (inset like a real QWERTY middle row)
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        Row(Modifier.weight(0.5f)) {}
        KeyboardLayouts.englishRows[1].forEach { ch ->
            LetterKey(ch, isBengali, shiftOn, Modifier.weight(1f), listener)
        }
        Row(Modifier.weight(0.5f)) {}
    }
    // Row 3: shift (Bengali only) + letters + backspace
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        if (isBengali) {
            KeyboardKey(
                modifier = Modifier.weight(1.4f),
                label = "⇧", // ⇧ shift glyph; toggles case for phonetic key distinctions (t vs T)
                fontSize = 18.sp,
                background = if (shiftOn) JuJuColors.KeyBackgroundAccent else JuJuColors.KeyBackgroundSpecial,
                contentDescription = "Shift"
            ) {
                listener.onToggleShift()
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1.4f))
        }
        KeyboardLayouts.englishRows[2].forEach { ch ->
            LetterKey(ch, isBengali, shiftOn, Modifier.weight(1f), listener)
        }
        KeyboardIconKey(
            modifier = Modifier.weight(1.4f),
            iconRes = R.drawable.ic_backspace,
            contentDescription = stringResource(R.string.cd_backspace),
            onTap = listener::onBackspace
        )
    }
}

@Composable
private fun LetterKey(
    ch: Char,
    isBengali: Boolean,
    shiftOn: Boolean,
    modifier: Modifier,
    listener: KeyboardActionListener
) {
    val displayChar = if (isBengali) {
        if (shiftOn) ch.uppercaseChar() else ch
    } else {
        ch.uppercaseChar() // English is ALWAYS uppercase, regardless of any shift state
    }
    val hint = if (isBengali) KeyboardLayouts.bengaliHint[ch] else null

    KeyboardKey(
        modifier = modifier,
        label = displayChar.toString(),
        subLabel = hint,
        fontSize = 18.sp
    ) {
        listener.onLetterKey(displayChar)
    }
}

@Composable
private fun SymbolRows(
    rows: List<List<String>>,
    rowHeight: androidx.compose.ui.unit.Dp,
    listener: KeyboardActionListener,
    showMoreSymbols: Boolean
) {
    rows.forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth().height(rowHeight)) {
            if (rowIndex == rows.lastIndex) {
                // Toggle between the two symbol pages: "1/2" on page 1 goes to page 2 (more
                // symbols), "1/2" on page 2 goes back to page 1.
                KeyboardKey(
                    modifier = Modifier.weight(1.4f),
                    label = if (showMoreSymbols) "1/2" else "2/2",
                    fontSize = 13.sp,
                    background = JuJuColors.KeyBackgroundSpecial
                ) {
                    if (showMoreSymbols) listener.onSwitchToSymbols2() else listener.onSwitchToSymbols1()
                }
            }
            row.forEach { sym ->
                KeyboardKey(modifier = Modifier.weight(1f), label = sym, fontSize = 18.sp) {
                    listener.onSymbolOrPunctuation(sym)
                }
            }
            if (rowIndex == rows.lastIndex) {
                KeyboardIconKey(
                    modifier = Modifier.weight(1.4f),
                    iconRes = R.drawable.ic_backspace,
                    contentDescription = stringResource(R.string.cd_backspace),
                    onTap = listener::onBackspace
                )
            }
        }
    }
}

@Composable
private fun BottomRow(
    state: KeyboardUiState,
    listener: KeyboardActionListener,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    val spacebarLabel = when {
        state.panel == KeyboardPanel.SYMBOLS_1 || state.panel == KeyboardPanel.SYMBOLS_2 -> "space"
        state.language == KeyboardLanguage.BENGALI -> stringResource(R.string.spacebar_bengali)
        else -> stringResource(R.string.spacebar_english)
    }

    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        val symbolsLabel = if (state.panel == KeyboardPanel.SYMBOLS_1 || state.panel == KeyboardPanel.SYMBOLS_2) "ABC" else "?123"
        KeyboardKey(
            modifier = Modifier.weight(1.3f),
            label = symbolsLabel,
            fontSize = 14.sp,
            background = JuJuColors.KeyBackgroundSpecial
        ) {
            if (symbolsLabel == "ABC") listener.onSwitchToLetters() else listener.onSwitchToSymbols1()
        }

        KeyboardIconKey(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_emoji,
            contentDescription = stringResource(R.string.cd_emoji),
            onTap = listener::onSwitchToEmoji
        )

        KeyboardIconKey(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_globe,
            contentDescription = stringResource(R.string.cd_globe),
            onTap = listener::onGlobe
        )

        KeyboardKey(
            modifier = Modifier.weight(4f),
            label = spacebarLabel,
            fontSize = 14.sp,
            background = JuJuColors.KeyBackgroundSpecial
        ) {
            listener.onSpace()
        }

        KeyboardKey(
            modifier = Modifier.weight(1f),
            label = ".",
            fontSize = 18.sp,
            background = JuJuColors.KeyBackgroundSpecial
        ) {
            listener.onSymbolOrPunctuation(".")
        }

        KeyboardIconKey(
            modifier = Modifier.weight(1.3f),
            iconRes = R.drawable.ic_send,
            contentDescription = "Enter",
            background = JuJuColors.KeyBackgroundAccent,
            onTap = listener::onEnter
        )
    }
}
