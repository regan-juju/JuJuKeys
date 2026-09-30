package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.clipboard.ClipItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------ iPhone dark colours
// Measured from an iPhone 16 Pro Max dark-mode screenshot.

internal object IosColors {
    val bg = Color(0xFF212121)
    val key = Color(0xFF464646)
    val keyPressed = Color(0xFF6B6B6B)
    val text = Color.White
    val dim = Color(0xFF8A8A8A)
    val returnIcon = Color(0xFF8E8E8E)
    val suggestion = Color(0xFFA0A0A0)
    val divider = Color(0xFF3D3D3D)
    val chip = Color(0xFF5A5A5A)
    val blue = Color(0xFF0A84FF)
    val bluePressed = Color(0xFF409CFF)
    val keepYellow = Color(0xFFFBBC04)
    val card = Color(0xFF333333)
}

internal val KeyShape = RoundedCornerShape(5.5.dp)
private val BaseRowHeight = 50.dp      // 40dp key + 10dp gap, like iPhone
private val KeyHPad = 2.9.dp
private val KeyVPad = 5.dp
private const val BengaliDigits = "০১২৩৪৫৬৭৮৯"

/** Sound/vibration callback for every key press. */
internal val LocalKeyFeedback = staticCompositionLocalOf<(KeyKind) -> Unit> { {} }
/** Show the key-press bubble? (setting) */
internal val LocalPopupEnabled = staticCompositionLocalOf { true }
internal val LocalRowHeight = staticCompositionLocalOf { BaseRowHeight }

/** Height of the area used by keys / clipboard / emoji. */
internal fun keyAreaHeight(prefs: com.reganbarua.jujukeys.settings.KeyboardPrefs): Dp =
    BaseRowHeight * prefs.heightScale * (if (prefs.numberRow) 5 else 4)

// ------------------------------------------------------------------ keyboard

@Composable
fun KeyboardView(state: KeyboardState, clips: List<ClipItem>, actions: KeyboardActions) {
    val preview = remember { KeyPreview() }
    var rootWidth by remember { mutableStateOf(0f) }
    val prefs = state.prefs

    val baseConfig = LocalViewConfiguration.current
    val viewConfig = remember(baseConfig, prefs.longPressDelay) {
        object : ViewConfiguration by baseConfig {
            override val longPressTimeoutMillis: Long get() = prefs.longPressDelay.toLong()
        }
    }

    CompositionLocalProvider(
        LocalKeyFeedback provides { kind: KeyKind -> actions.onKeyFeedback(kind) },
        LocalPopupEnabled provides prefs.popup,
        LocalRowHeight provides BaseRowHeight * prefs.heightScale,
        LocalViewConfiguration provides viewConfig,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(IosColors.bg)
                .onGloballyPositioned { rootWidth = it.size.width.toFloat() }
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (state.translateOn) TranslateBar(state, actions) else SuggestionBar(state, actions)

                Box(Modifier.fillMaxWidth().height(keyAreaHeight(prefs) + 2.dp)) {
                    when (state.panel) {
                        Panel.KEYS -> KeysPanel(state, actions, preview)
                        Panel.CLIPBOARD -> ClipboardPanel(clips, actions)
                        Panel.EMOJI -> EmojiPanel(state, actions)
                    }
                }
                BottomStrip(state, actions)
            }
            KeyPreviewBubble(preview, rootWidth)
        }
    }
}

@Composable
private fun KeysPanel(state: KeyboardState, actions: KeyboardActions, preview: KeyPreview) {
    val bangla = state.language == Language.BANGLA
    val prefs = state.prefs
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        when (state.page) {
            Page.LETTERS -> {
                if (prefs.numberRow) {
                    KeyRow { KeyboardLayouts.symbolsRow1.forEach { CharKeyView(it, bangla, prefs.longPressSymbols, actions, preview) } }
                }
                val lp = prefs.longPressSymbols
                KeyRow { KeyboardLayouts.lettersRow1.forEach { CharKeyView(it, bangla, lp, actions, preview) } }
                KeyRow {
                    Spacer(Modifier.weight(0.5f))
                    KeyboardLayouts.lettersRow2.forEach { CharKeyView(it, bangla, lp, actions, preview) }
                    Spacer(Modifier.weight(0.5f))
                }
                KeyRow {
                    ShiftKey(state, actions)
                    Spacer(Modifier.weight(0.21f))
                    KeyboardLayouts.lettersRow3.forEach { CharKeyView(it, bangla, lp, actions, preview) }
                    Spacer(Modifier.weight(0.21f))
                    BackspaceKey(actions)
                }
            }
            Page.SYMBOLS, Page.MORE_SYMBOLS -> {
                val more = state.page == Page.MORE_SYMBOLS
                val r1 = if (more) KeyboardLayouts.moreRow1 else KeyboardLayouts.symbolsRow1
                val r2 = if (more) KeyboardLayouts.moreRow2 else KeyboardLayouts.symbolsRow2
                val r3 = if (more) KeyboardLayouts.moreRow3 else KeyboardLayouts.symbolsRow3
                if (prefs.numberRow) Spacer(Modifier.height(LocalRowHeight.current))
                KeyRow { r1.forEach { CharKeyView(it, bangla, false, actions, preview) } }
                KeyRow { r2.forEach { CharKeyView(it, bangla, false, actions, preview) } }
                KeyRow {
                    val label = if (more) (if (bangla) "১২৩" else "123") else "#+="
                    FuncKey(Modifier.weight(1.29f), onTap = {
                        actions.onPage(if (more) Page.SYMBOLS else Page.MORE_SYMBOLS)
                    }) { KeyLabel(label, 16) }
                    Spacer(Modifier.weight(0.21f))
                    r3.forEach { CharKeyView(it, bangla, false, actions, preview, weight = 1.4f) }
                    Spacer(Modifier.weight(0.21f))
                    BackspaceKey(actions)
                }
            }
        }
        BottomKeyRow(state, actions)
    }
}

// ------------------------------------------------------------------ rows & keys

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(LocalRowHeight.current).padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

private fun Modifier.keyPadding() = this.fillMaxHeight().padding(horizontal = KeyHPad, vertical = KeyVPad)

@Composable
private fun BottomKeyRow(state: KeyboardState, actions: KeyboardActions) {
    val bangla = state.language == Language.BANGLA
    val showEmoji = state.prefs.showEmojiKey
    KeyRow {
        val onLetters = state.page == Page.LETTERS
        FuncKey(Modifier.weight(1.24f), onTap = {
            actions.onPage(if (onLetters) Page.SYMBOLS else Page.LETTERS)
        }) {
            KeyLabel(if (!onLetters) "ABC" else if (bangla) "১২৩" else "123", 17)
        }
        if (showEmoji) {
            FuncKey(Modifier.weight(1.24f), onTap = { actions.onPanel(Panel.EMOJI) }) {
                Icon(Symbols.emoji, "Emoji", tint = IosColors.text, modifier = Modifier.size(26.dp))
            }
        }
        SpaceKey(Modifier.weight(if (showEmoji) 5.02f else 6.26f), bangla, actions)
        ReturnKey(Modifier.weight(2.5f), state.enterLabel, actions)
    }
}

@Composable
private fun RowScope.CharKeyView(
    key: CharKey,
    bangla: Boolean,
    longPress: Boolean,
    actions: KeyboardActions,
    preview: KeyPreview,
    weight: Float = 1f,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val popup = LocalPopupEnabled.current
    val shown = key.label.let { c ->
        if (bangla && c in '0'..'9') BengaliDigits[c - '0'].toString()
        else if (bangla && c == '.') "।"
        else c.uppercaseChar().toString()        // labels are always CAPITAL
    }
    PressBox(
        Modifier
            .weight(weight)
            .keyPadding()
            .onGloballyPositioned { bounds = it.boundsInRoot() },
        color = IosColors.key, pressedColor = IosColors.keyPressed,
        onTap = { actions.onChar(key.label) },
        onLongPress = if (longPress) key.hint?.let { h -> { actions.onChar(h) } } else null,
        onPressChange = { down ->
            if (!popup) return@PressBox
            if (down) { preview.text = shown; preview.bounds = bounds }
            else if (preview.text == shown) preview.text = null
        }
    ) {
        Text(shown, color = IosColors.text, fontSize = 22.sp, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun RowScope.ShiftKey(state: KeyboardState, actions: KeyboardActions) {
    FuncKey(Modifier.weight(1.29f), onTap = { actions.onShift() }) {
        val icon = when (state.shift) {
            ShiftState.OFF -> IosIcons.shiftOff
            ShiftState.ONCE -> IosIcons.shiftOn
            ShiftState.LOCK -> IosIcons.capsLock
        }
        Icon(icon, "Shift", tint = IosColors.text, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun RowScope.BackspaceKey(actions: KeyboardActions) {
    PressBox(
        Modifier.weight(1.29f).keyPadding(),
        color = IosColors.key, pressedColor = IosColors.keyPressed,
        onTap = { actions.onBackspace() },
        repeating = true,
        kind = KeyKind.DELETE
    ) {
        Icon(Symbols.backspace, "Backspace", tint = IosColors.text, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun ReturnKey(modifier: Modifier, label: String, actions: KeyboardActions) {
    val action = label.isNotEmpty()
    PressBox(
        modifier.keyPadding(),
        color = if (action) IosColors.blue else IosColors.key,
        pressedColor = if (action) IosColors.bluePressed else IosColors.keyPressed,
        onTap = { actions.onEnter() },
        kind = KeyKind.RETURN
    ) {
        if (action) {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Normal, maxLines = 1)
        } else {
            Icon(Symbols.keyboardReturn, "Return", tint = IosColors.returnIcon, modifier = Modifier.size(26.dp))
        }
    }
}

/** Space bar: tap = space, slide left/right = move the cursor (like iPhone / Gboard). */
@Composable
private fun SpaceKey(modifier: Modifier, bangla: Boolean, actions: KeyboardActions) {
    var pressed by remember { mutableStateOf(false) }
    val feedback = LocalKeyFeedback.current
    val act by rememberUpdatedState(actions)
    Box(
        modifier
            .keyPadding()
            .clip(KeyShape)
            .background(if (pressed) IosColors.keyPressed else IosColors.key)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    feedback(KeyKind.SPACE)
                    var dragging = false
                    var acc = 0f
                    val stepPx = 9.dp.toPx()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        acc += change.positionChange().x
                        if (!dragging && abs(acc) > viewConfiguration.touchSlop) {
                            dragging = true
                            acc = 0f
                        }
                        if (dragging) {
                            val steps = (acc / stepPx).toInt()
                            if (steps != 0) {
                                act.onCursorMove(steps)
                                acc -= steps * stepPx
                            }
                            change.consume()
                        }
                    }
                    pressed = false
                    if (!dragging) act.onSpace()
                }
            },
    ) {
        Text(
            if (bangla) "ক" else "EN BN",
            color = IosColors.dim, fontSize = 10.5.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 3.dp)
        )
    }
}

@Composable
internal fun FuncKey(
    modifier: Modifier,
    shape: Shape = KeyShape,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    PressBox(
        modifier.keyPadding(),
        shape = shape, color = IosColors.key, pressedColor = IosColors.keyPressed,
        onTap = onTap, onLongPress = onLongPress, content = content
    )
}

@Composable
internal fun KeyLabel(text: String, sizeSp: Int, weight: FontWeight = FontWeight.Normal) {
    Text(text, color = IosColors.text, fontSize = sizeSp.sp, fontWeight = weight, maxLines = 1)
}

// ------------------------------------------------------------------ bottom strip

/** iPhone's strip under the keys: 🌐 left, 🎤 right — Keep clipboard and Translate in the middle. */
@Composable
private fun BottomStrip(state: KeyboardState, actions: KeyboardActions) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🌐 = settings (long-press: choose another keyboard)
        StripButton(onTap = { actions.onOpenSettings() }, onLongPress = { actions.onShowImePicker() }) {
            Icon(Symbols.globe, "সেটিংস", tint = IosColors.text, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.weight(1f))

        if (state.prefs.clipboardOn) {
            val clipOpen = state.panel == Panel.CLIPBOARD
            StripButton(
                active = clipOpen,
                onTap = { actions.onPanel(if (clipOpen) Panel.KEYS else Panel.CLIPBOARD) }
            ) {
                Box(Modifier.size(30.dp)) {
                    Icon(Symbols.clipboard, "ক্লিপবোর্ড", tint = IosColors.text, modifier = Modifier.size(28.dp))
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 1.dp).size(14.dp)
                            .background(IosColors.keepYellow, RoundedCornerShape(3.5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Symbols.lightbulb, null, tint = Color.Black, modifier = Modifier.size(11.dp))
                    }
                }
            }
            Spacer(Modifier.width(30.dp))
        }
        StripButton(active = state.translateOn, onTap = { actions.onTranslateToggle() }) {
            Icon(Symbols.translate, "অনুবাদ", tint = IosColors.text, modifier = Modifier.size(28.dp))
        }

        Spacer(Modifier.weight(1f))
        if (state.prefs.showVoiceKey) {
            StripButton(onTap = { actions.onVoice() }) {
                Icon(Symbols.mic, "ভয়েস", tint = IosColors.text, modifier = Modifier.size(32.dp))
            }
        } else {
            Spacer(Modifier.width(52.dp))
        }
    }
}

@Composable
private fun StripButton(
    active: Boolean = false,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    PressBox(
        Modifier.size(width = 52.dp, height = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (active) IosColors.blue.copy(alpha = 0.35f) else Color.Transparent,
        pressedColor = IosColors.keyPressed,
        onTap = onTap, onLongPress = onLongPress, content = content
    )
}

// ------------------------------------------------------------------ key preview bubble

@Composable
private fun KeyPreviewBubble(preview: KeyPreview, rootWidth: Float) {
    val text = preview.text ?: return
    val b = preview.bounds
    if (b == Rect.Zero) return
    val density = LocalDensity.current
    with(density) {
        val w = b.width + 14.dp.toPx()
        val h = b.height * 1.35f
        var x = b.center.x - w / 2f
        if (rootWidth > 0f) x = x.coerceIn(0f, rootWidth - w)
        val y = (b.bottom - h - b.height * 0.55f).coerceAtLeast(0f)
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(w.toDp(), h.toDp())
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .background(IosColors.keyPressed, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 32.sp)
        }
    }
}

// ------------------------------------------------------------------ pressable box

/**
 * A pressable rounded box. Tap, optional long-press, optional auto-repeat while held
 * (backspace). Sound/vibration on every press, following the settings.
 */
@Composable
internal fun PressBox(
    modifier: Modifier,
    shape: Shape = KeyShape,
    color: Color,
    pressedColor: Color,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    repeating: Boolean = false,
    kind: KeyKind = KeyKind.NORMAL,
    onPressChange: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val feedback = LocalKeyFeedback.current
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val pressChange by rememberUpdatedState(onPressChange)
    val hasLongPress = onLongPress != null

    val gestures = if (repeating) {
        Modifier.pointerInput(Unit) {
            coroutineScope {
                val scope = this
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    feedback(kind)
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
                    pressChange?.invoke(true)
                    feedback(kind)
                    tryAwaitRelease()
                    pressed = false
                    pressChange?.invoke(false)
                },
                onLongPress = longHandler,
                onTap = { tap() }
            )
        }
    }

    Box(
        modifier
            .clip(shape)
            .background(if (pressed) pressedColor else color)
            .then(gestures),
        contentAlignment = Alignment.Center,
        content = content
    )
}
