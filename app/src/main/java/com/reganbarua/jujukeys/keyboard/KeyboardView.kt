package com.reganbarua.jujukeys.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ colours (dark iOS style)

private object KeyColors {
    val bgTop = Color(0xFF2A2F37)
    val bgBottom = Color(0xFF1A1E25)
    val letter = Color(0xFF505359)
    val letterPressed = Color(0xFF6E7279)
    val special = Color(0xFF2C2F34)
    val specialPressed = Color(0xFF474A50)
    val text = Color.White
    val hint = Color(0xFFB9BDC5)
    val dim = Color(0xFF8C9199)
    val blue = Color(0xFF1A73E8)
    val bluePressed = Color(0xFF4A90F0)
    val keepYellow = Color(0xFFFBBC04)
}

private val KeyShape = RoundedCornerShape(9.dp)
private val RowHeight = 56.dp

// ------------------------------------------------------------------ keyboard

@Composable
fun KeyboardView(state: KeyboardState, actions: KeyboardActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(KeyColors.bgTop, KeyColors.bgBottom)))
            .padding(bottom = 6.dp)
    ) {
        if (state.romanPreview.isNotEmpty()) PreviewStrip(state) else Toolbar(actions)

        when (state.page) {
            Page.LETTERS -> LetterRows(state, actions)
            Page.SYMBOLS -> SymbolRows(
                KeyboardLayouts.symbolsRow1, KeyboardLayouts.symbolsRow2, KeyboardLayouts.symbolsRow3,
                switchLabel = "#+=", switchTo = Page.MORE_SYMBOLS, actions = actions
            )
            Page.MORE_SYMBOLS -> SymbolRows(
                KeyboardLayouts.moreRow1, KeyboardLayouts.moreRow2, KeyboardLayouts.moreRow3,
                switchLabel = "123", switchTo = Page.SYMBOLS, actions = actions
            )
        }
        BottomRow(state, actions)
    }
}

// ------------------------------------------------------------------ top strip

@Composable
private fun Toolbar(actions: KeyboardActions) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarButton(Icons.Filled.GridView, "Menu") { actions.onToolbar(ToolbarItem.MENU) }
        ToolbarButton(null, "Clipboard", custom = {
            // Clipboard with a small Google-Keep-yellow bulb badge
            Box(Modifier.size(30.dp)) {
                Icon(Icons.Filled.ContentPaste, null, tint = KeyColors.text, modifier = Modifier.size(26.dp))
                Box(
                    Modifier.align(Alignment.BottomEnd).size(14.dp)
                        .background(KeyColors.keepYellow, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Color.Black, modifier = Modifier.size(10.dp))
                }
            }
        }) { actions.onToolbar(ToolbarItem.CLIPBOARD) }
        ToolbarButton(Icons.Filled.Translate, "Translate") { actions.onToolbar(ToolbarItem.TRANSLATE) }
        ToolbarButton(Icons.Filled.Settings, "Settings") { actions.onToolbar(ToolbarItem.SETTINGS) }
        ToolbarButton(Icons.Filled.Mic, "Voice") { actions.onToolbar(ToolbarItem.MIC) }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector?,
    description: String,
    custom: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    PressBox(
        modifier = Modifier.size(width = 52.dp, height = 42.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        pressedColor = KeyColors.specialPressed,
        onTap = onClick,
    ) {
        if (custom != null) custom()
        else if (icon != null) Icon(icon, description, tint = KeyColors.text, modifier = Modifier.size(28.dp))
    }
}

/** While a Bangla word is being typed: shows "bhalo → ভালো". */
@Composable
private fun PreviewStrip(state: KeyboardState) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(state.romanPreview, color = KeyColors.dim, fontSize = 16.sp, maxLines = 1)
        Text("  →  ", color = KeyColors.dim, fontSize = 16.sp)
        Text(
            state.banglaPreview, color = KeyColors.text, fontSize = 22.sp,
            fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

// ------------------------------------------------------------------ rows

@Composable
private fun LetterRows(state: KeyboardState, actions: KeyboardActions) {
    KeyRow {
        KeyboardLayouts.lettersRow1.forEach { LetterKey(it, actions) }
    }
    KeyRow {
        Spacer(Modifier.weight(0.5f))
        KeyboardLayouts.lettersRow2.forEach { LetterKey(it, actions) }
        Spacer(Modifier.weight(0.5f))
    }
    KeyRow {
        ShiftKey(state, actions)
        Spacer(Modifier.weight(0.1f))
        KeyboardLayouts.lettersRow3.forEach { LetterKey(it, actions) }
        Spacer(Modifier.weight(0.1f))
        BackspaceKey(actions)
    }
}

@Composable
private fun SymbolRows(
    row1: List<CharKey>, row2: List<CharKey>, row3: List<CharKey>,
    switchLabel: String, switchTo: Page, actions: KeyboardActions,
) {
    KeyRow { row1.forEach { LetterKey(it, actions) } }
    KeyRow { row2.forEach { LetterKey(it, actions) } }
    KeyRow {
        SpecialKey(Modifier.weight(1.5f), onTap = { actions.onPage(switchTo) }) {
            KeyText(switchLabel, 17)
        }
        Spacer(Modifier.weight(0.3f))
        row3.forEach { LetterKey(it, actions, weight = 1.3f) }
        Spacer(Modifier.weight(0.2f))
        BackspaceKey(actions)
    }
}

@Composable
private fun BottomRow(state: KeyboardState, actions: KeyboardActions) {
    KeyRow {
        // ?123 / ABC
        val onLetters = state.page == Page.LETTERS
        SpecialKey(
            Modifier.weight(1.5f), shape = RoundedCornerShape(50),
            onTap = { actions.onPage(if (onLetters) Page.SYMBOLS else Page.LETTERS) }
        ) { KeyText(if (onLetters) "?123" else "ABC", 18) }

        // comma, long-press = emoji
        SpecialKey(
            Modifier.weight(1f),
            onTap = { actions.onChar(',') },
            onLongPress = { actions.onToolbar(ToolbarItem.EMOJI) }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.EmojiEmotions, "Emoji", tint = KeyColors.text, modifier = Modifier.size(18.dp))
                Text(",", color = KeyColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // globe: বাংলা ⇄ ENGLISH
        SpecialKey(Modifier.weight(1f), onTap = { actions.onToggleLanguage() }) {
            Icon(Icons.Filled.Language, "Language", tint = KeyColors.text, modifier = Modifier.size(28.dp))
        }

        // space bar: active language bright, the other dim
        PressBox(
            Modifier.weight(4f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp),
            color = KeyColors.letter, pressedColor = KeyColors.letterPressed,
            onTap = { actions.onSpace() }
        ) {
            val bangla = state.language == Language.BANGLA
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (bangla) KeyColors.text else KeyColors.dim)) { append("বাংলা") }
                    withStyle(SpanStyle(color = KeyColors.dim)) { append(" / ") }
                    withStyle(SpanStyle(color = if (!bangla) KeyColors.text else KeyColors.dim)) { append("ENGLISH") }
                },
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
        }

        // full stop (দাঁড়ি in Bangla)
        SpecialKey(Modifier.weight(1f), onTap = { actions.onChar('.') }) {
            KeyText(if (state.language == Language.BANGLA) "।" else ".", 22)
        }

        // blue action key
        PressBox(
            Modifier.weight(1.5f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp),
            shape = RoundedCornerShape(22.dp),
            color = KeyColors.blue, pressedColor = KeyColors.bluePressed,
            onTap = { actions.onEnter() }
        ) {
            if (state.enterLabel.isEmpty()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardReturn, "Enter", tint = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(
                        state.enterLabel, color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ keys

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun RowScope.LetterKey(key: CharKey, actions: KeyboardActions, weight: Float = 1f) {
    PressBox(
        Modifier.weight(weight).fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp),
        color = KeyColors.letter, pressedColor = KeyColors.letterPressed,
        onTap = { actions.onChar(key.label) },
        onLongPress = key.hint?.let { h -> { actions.onChar(h) } }
    ) {
        KeyText(key.label.toString(), 24, FontWeight.Medium)
        if (key.hint != null) {
            Text(
                key.hint.toString(), color = KeyColors.hint, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun RowScope.ShiftKey(state: KeyboardState, actions: KeyboardActions) {
    // English is always capital, so it is shown as caps-lock on.
    val shown = if (state.language == Language.ENGLISH) ShiftState.LOCK else state.shift
    val lit = shown != ShiftState.OFF
    PressBox(
        Modifier.weight(1.4f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp),
        color = if (lit) Color.White else KeyColors.special,
        pressedColor = if (lit) Color(0xFFDDDDDD) else KeyColors.specialPressed,
        onTap = { actions.onShift() }
    ) {
        val icon = when (shown) {
            ShiftState.OFF -> Icons.Outlined.ArrowUpward
            ShiftState.ONCE -> Icons.Filled.ArrowUpward
            ShiftState.LOCK -> Icons.Filled.KeyboardCapslock
        }
        Icon(icon, "Shift", tint = if (lit) Color.Black else KeyColors.text, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun RowScope.BackspaceKey(actions: KeyboardActions) {
    PressBox(
        Modifier.weight(1.4f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp),
        color = KeyColors.special, pressedColor = KeyColors.specialPressed,
        onTap = { actions.onBackspace() },
        repeating = true
    ) {
        Icon(Icons.AutoMirrored.Outlined.Backspace, "Backspace", tint = KeyColors.text, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun SpecialKey(
    modifier: Modifier,
    shape: Shape = KeyShape,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    PressBox(
        modifier.fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp),
        shape = shape, color = KeyColors.special, pressedColor = KeyColors.specialPressed,
        onTap = onTap, onLongPress = onLongPress, content = content
    )
}

@Composable
private fun KeyText(text: String, sizeSp: Int, weight: FontWeight = FontWeight.SemiBold) {
    Text(text, color = KeyColors.text, fontSize = sizeSp.sp, fontWeight = weight, maxLines = 1)
}

/**
 * A pressable rounded box. Tap, optional long-press, optional auto-repeat while held
 * (used by backspace). Gives a light haptic tick on every press.
 */
@Composable
private fun PressBox(
    modifier: Modifier,
    shape: Shape = KeyShape,
    color: Color,
    pressedColor: Color,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    repeating: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val hasLongPress = onLongPress != null

    val gestures = if (repeating) {
        Modifier.pointerInput(Unit) {
            coroutineScope {
                val scope = this
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    tap()
                    val repeatJob = scope.launch {
                        delay(400)
                        while (true) { tap(); delay(55) }
                    }
                    waitForUpOrCancellation()
                    repeatJob.cancel()
                    pressed = false
                }
            }
        }
    } else {
        Modifier.pointerInput(hasLongPress) {
            val longHandler: ((Offset) -> Unit)? =
                if (hasLongPress) ({ _: Offset -> longPress?.invoke(); Unit }) else null
            detectTapGestures(
                onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = longHandler,
                onTap = { tap() }
            )
        }
    }

    val shadowMod = if (color == Color.Transparent) Modifier else Modifier.shadow(1.dp, shape)
    Box(
        modifier
            .then(shadowMod)
            .clip(shape)
            .background(if (pressed) pressedColor else color)
            .then(gestures),
        contentAlignment = Alignment.Center,
        content = content
    )
}
