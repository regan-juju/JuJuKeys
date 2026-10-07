package com.reganbarua.jujukeys.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.R
import com.reganbarua.jujukeys.clipboard.ClipItem
import com.reganbarua.jujukeys.settings.KeyboardPrefs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Noto Sans Bengali, bundled with the app, for all Bangla text on the keyboard. */
internal val BanglaFont = FontFamily(
    Font(R.font.noto_bengali_regular, FontWeight.Normal),
    Font(R.font.noto_bengali_medium, FontWeight.Medium),
    Font(R.font.noto_bengali_semibold, FontWeight.SemiBold),
    Font(R.font.noto_bengali_bold, FontWeight.Bold),
)

/** Bangla text → Noto Sans Bengali; other text keeps the phone's normal font. */
internal fun fontOf(text: String): FontFamily? =
    if (text.any { it in 'ঀ'..'৿' }) BanglaFont else null

internal val KeyShape = RoundedCornerShape(8.dp)
private val PillShape = RoundedCornerShape(50)
// Row = key + gap. 49.1 × Normal(1.1) = 54 dp: key ≈ 36 × 45 dp with a 9 dp gap between rows.
private val BaseRowHeight = 49.1.dp
// Gaps around each key: big, nearly square keys (≈36 × 45 dp at Normal) with small gaps.
// Shared by letters, symbols and the number pad. Touches in a gap go to the nearest key.
internal val KeyHPad = 1.4.dp
internal val KeyVPad = 4.5.dp
private const val BengaliDigits = "০১২৩৪৫৬৭৮৯"

/** Sound/vibration callback for every key press. */
internal val LocalKeyFeedback = staticCompositionLocalOf<(KeyKind) -> Unit> { {} }
internal val LocalPopupEnabled = staticCompositionLocalOf { true }
internal val LocalRowHeight = staticCompositionLocalOf { BaseRowHeight }
internal val LocalKeyWeight = staticCompositionLocalOf { FontWeight.Medium }

/** Height of the area used by keys / clipboard / emoji / suggestions. */
internal fun keyAreaHeight(prefs: KeyboardPrefs): Dp =
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
    val feedback = remember(actions) { { kind: KeyKind -> actions.onKeyFeedback(kind) } }

    val theme = Themes.byId(prefs.theme)
    IosColors.apply(theme)

    // key(theme): a theme change rebuilds the keyboard once so every part picks up the new colours
    key(theme.id) {
    CompositionLocalProvider(
        LocalKeyFeedback provides feedback,
        LocalPopupEnabled provides prefs.popup,
        LocalRowHeight provides BaseRowHeight * prefs.heightScale,
        LocalViewConfiguration provides viewConfig,
        LocalKeyWeight provides if (prefs.boldKeys) FontWeight.Medium else FontWeight.Normal,   // "হালকা বোল্ড"
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .drawWithCache {
                    // soft colour glow behind the glass keys (drawn once per size)
                    val w = size.width; val h = size.height
                    val bg = theme.bg
                    val blobs = theme.blobs.map { b ->
                        // a little stronger colour behind the glass, so the keys look see-through
                        val c = b.color.copy(alpha = (b.color.alpha * 1.6f).coerceAtMost(0.55f))
                        Brush.radialGradient(listOf(c, Color.Transparent), Offset(w * b.x, h * b.y), w * b.r)
                    }
                    onDrawBehind {
                        drawRect(bg)
                        blobs.forEach { drawRect(it) }
                    }
                }
                .border(1.dp, IosColors.topEdge, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .onGloballyPositioned { rootWidth = it.size.width.toFloat() }
        ) {
            Column(Modifier.fillMaxWidth()) {
                val searching = state.emojiSearch != null
                when {
                    searching -> EmojiSearchBar(state, actions)
                    state.panel == Panel.EMOJI -> EmojiTopBar(actions)
                    state.translateOn -> {
                        TranslateBar(state, actions)
                        SuggestionBar(state, actions, forceSuggestions = true)   // suggestions while translating too
                    }
                    else -> SuggestionBar(state, actions)
                }

                Box(Modifier.fillMaxWidth().height(keyAreaHeight(prefs) + 2.dp)) {
                    when (state.panel) {
                        Panel.KEYS, Panel.SUGGESTIONS -> KeysPanel(state, actions, preview)
                        Panel.CLIPBOARD -> ClipboardPanel(state, clips, actions)
                        Panel.EMOJI -> if (searching) KeysPanel(state, actions, preview) else EmojiPanel(state, actions)
                    }
                    // more suggestions rise from the bottom (the faded arrow in the bottom strip)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.panel == Panel.SUGGESTIONS,
                        enter = slideInVertically(tween(180)) { it } + fadeIn(tween(120)),
                        exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(120))
                    ) { MoreSuggestionsPanel(state, actions) }
                    if (state.translateOn && state.translatePicker) LanguagePicker(state, actions)
                }
                BottomStrip(state, actions)
            }
            KeyPreviewBubble(preview, rootWidth)
        }
    }
    }
}

@Composable
private fun KeysPanel(state: KeyboardState, actions: KeyboardActions, preview: KeyPreview) {
    val bangla = state.language == Language.BANGLA
    val prefs = state.prefs
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        when (state.page) {
            Page.LETTERS -> LetterKeyboard(state, actions, preview)
            Page.NUMPAD -> {
                if (prefs.numberRow) Spacer(Modifier.height(LocalRowHeight.current))
                NumberPad(state.enterLabel, actions)
            }
            Page.SYMBOLS, Page.MORE_SYMBOLS -> {
                if (prefs.numberRow) Spacer(Modifier.height(LocalRowHeight.current))
                SymbolPage(state.page == Page.MORE_SYMBOLS, bangla, state.enterLabel, actions, preview)
            }
        }
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
private fun RowScope.BackspaceKey(actions: KeyboardActions, weight: Float = 1.29f, color: Color = IosColors.key) {
    PressBox(
        Modifier.weight(weight).keyPadding(),
        color = color, pressedColor = if (IosColors.theme.rainbow != null) IosColors.pressedOf(color) else IosColors.keyPressed,
        onTap = { actions.onBackspace() },
        repeating = true,
        kind = KeyKind.DELETE
    ) {
        Icon(Symbols.backspace, "Backspace", tint = IosColors.text, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun ReturnKey(
    modifier: Modifier, label: String, actions: KeyboardActions,
    @Suppress("UNUSED_PARAMETER") pill: Boolean, rainbow: Int = 7,
) {
    // Plain and faded (like iPhone): no colour, no text; 🔍 in search fields.
    // Only the rainbow theme colours it, like every other key there.
    val rc = IosColors.rainbowAt(rainbow)
    PressBox(
        modifier.keyPadding(),
        color = rc ?: IosColors.faded, pressedColor = rc?.let { IosColors.pressedOf(it) } ?: IosColors.fadedPressed,
        onTap = { actions.onEnter() },
        kind = KeyKind.RETURN
    ) {
        Icon(
            if (label == "search") Symbols.search else Symbols.keyboardReturn, "Return",
            tint = if (rc != null) IosColors.text else IosColors.fadedIcon, modifier = Modifier.size(26.dp)
        )
    }
}

/** Space bar: tap = space, slide left/right = move the cursor (like iPhone / Gboard). */
@Composable
private fun SpaceKey(modifier: Modifier, label: String, actions: KeyboardActions, centered: Boolean = false) {
    var pressed by remember { mutableStateOf(false) }
    val feedback = LocalKeyFeedback.current
    val act by rememberUpdatedState(actions)
    Box(
        modifier
            .keyPadding()
            .clip(KeyShape)
            .then(
                IosColors.rainbowBrush()?.takeIf { !pressed }?.let { Modifier.background(it, alpha = 0.8f) }
                    ?: Modifier.background(if (pressed) IosColors.keyPressed else IosColors.key)
            )
            .border(0.8.dp, IosColors.glassEdge, KeyShape)
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
        if (centered) {
            Text(
                label, color = IosColors.suggestion, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = fontOf(label), modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Text(
                label, color = IosColors.dim, fontSize = 10.5.sp, fontFamily = fontOf(label),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 3.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ number pad (?123)

/** Gboard-style number pad: + − * / column, 1–9 grid, % ␣ ⌫ column, bottom ABC , !?# 0 = . ↵ */
@Composable
private fun NumberPad(enterLabel: String, actions: KeyboardActions) {
    val rowH = LocalRowHeight.current
    Row(Modifier.fillMaxWidth().height(rowH * 3).padding(horizontal = 3.dp)) {
        // left column: + − * /
        Column(
            Modifier.weight(1.3f).fillMaxHeight().padding(horizontal = KeyHPad, vertical = KeyVPad)
                .clip(RoundedCornerShape(8.dp)).background(IosColors.keyOr(4, IosColors.fn))
        ) {
            listOf("+" to "+", "−" to "-", "*" to "*", "/" to "/").forEach { (shown, typed) ->
                PressBox(
                    Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent, pressedColor = IosColors.keyPressed,
                    onTap = { actions.onRawText(typed) }, fireOnDown = true
                ) { KeyLabel(shown, 22.sp) }
            }
        }
        Column(Modifier.weight(5.2f).fillMaxHeight()) {
            val rows = listOf("123", "456", "789")
            val right: List<@Composable RowScope.() -> Unit> = listOf(
                { NumKey("%", actions, IosColors.keyOr(9, IosColors.fn), 20.sp) },
                {
                    PressBox(
                        Modifier.weight(1f).keyPadding(), color = IosColors.keyOr(5, IosColors.fn), pressedColor = IosColors.keyPressed,
                        onTap = { actions.onRawText(" ") }, fireOnDown = true, kind = KeyKind.SPACE
                    ) { Icon(Symbols.spaceBar, "Space", tint = IosColors.text, modifier = Modifier.size(26.dp)) }
                },
                { BackspaceKey(actions, 1f, IosColors.keyOr(0, IosColors.fn)) },
            )
            rows.forEachIndexed { i, digits ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    digits.forEach { d -> NumKey(d.toString(), actions, IosColors.keyOr(d - '1', IosColors.key), 28.sp) }
                    right[i]()
                }
            }
        }
    }
    KeyRow {
        // , !?# = . were half-width keys; ABC / 0 / return give some room so they are ~35% bigger
        // (the row still adds up to 6.5 like the rows above).
        FuncKey(Modifier.weight(1.0f), shape = PillShape, color = IosColors.theme.accent ?: IosColors.fn, onTap = { actions.onPage(Page.LETTERS) }) {
            KeyLabel("ABC", 16.sp)
        }
        NumKey(",", actions, IosColors.keyOr(1, IosColors.fn), 26.sp, 0.875f)
        FuncKey(Modifier.weight(0.875f), color = IosColors.keyOr(3, IosColors.fn), onTap = { actions.onPage(Page.SYMBOLS) }) {
            KeyLabel("!?#", 17.sp)
        }
        NumKey("0", actions, IosColors.keyOr(9, IosColors.key), 28.sp, 1.0f)
        NumKey("=", actions, IosColors.keyOr(6, IosColors.fn), 24.sp, 0.875f)
        NumKey(".", actions, IosColors.keyOr(8, IosColors.fn), 28.sp, 0.875f)
        ReturnKey(Modifier.weight(1.0f), enterLabel, actions, pill = true, rainbow = 7)
    }
}

@Composable
private fun RowScope.NumKey(text: String, actions: KeyboardActions, color: Color, size: TextUnit, weight: Float = 1f) {
    PressBox(
        Modifier.weight(weight).keyPadding(),
        color = color, pressedColor = if (IosColors.theme.rainbow != null) IosColors.pressedOf(color) else IosColors.keyPressed,
        onTap = { actions.onRawText(text) }, fireOnDown = true
    ) { KeyLabel(text, size) }
}

// ------------------------------------------------------------------ symbols (!?#)

/** Gboard-style symbol page; "১২ ৩৪" goes back to the number pad. */
@Composable
private fun SymbolPage(more: Boolean, bangla: Boolean, enterLabel: String, actions: KeyboardActions, preview: KeyPreview) {
    val digits = if (bangla) "১২৩৪৫৬৭৮৯০" else "1234567890"
    val r1 = if (more) "~ ` | • √ π ÷ × ¶ ∆".split(' ') else digits.map { it.toString() }
    val r2 = if (more) "£ € ¥ ^ ° = { } \\ %".split(' ') else "@ # ৳ _ & - + ( ) /".split(' ')
    val r3 = if (more) "© ® ™ ✓ [ ] < >".split(' ') else "* \" ' : ; ! ?".split(' ')
    KeyRow { r1.forEachIndexed { i, t -> SymKey(t, actions, preview, color = IosColors.keyOr(i, IosColors.key)) } }
    KeyRow { r2.forEachIndexed { i, t -> SymKey(t, actions, preview, color = IosColors.keyOr(i, IosColors.key)) } }
    KeyRow {
        FuncKey(Modifier.weight(1.4f), color = IosColors.keyOr(0, IosColors.fn), onTap = {
            actions.onPage(if (more) Page.SYMBOLS else Page.MORE_SYMBOLS)
        }) { KeyLabel(if (more) "?123" else "=\\<", 15.sp) }
        r3.forEachIndexed { i, t -> SymKey(t, actions, preview, if (more) 0.9f else 1f, IosColors.keyOr(i + 1, IosColors.key)) }
        BackspaceKey(actions, 1.4f, IosColors.keyOr(9, IosColors.fn))
    }
    KeyRow {
        FuncKey(Modifier.weight(1.3f), shape = PillShape, color = IosColors.theme.accent ?: IosColors.fn, onTap = { actions.onPage(Page.LETTERS) }) {
            KeyLabel("ABC", 16.sp)
        }
        NumKey(",", actions, IosColors.keyOr(1, IosColors.fn), 20.sp, 0.8f)
        FuncKey(Modifier.weight(0.9f), color = IosColors.keyOr(3, IosColors.fn), onTap = { actions.onPage(Page.NUMPAD) }) {
            Text(
                if (bangla) "১ ২\n৩ ৪" else "1 2\n3 4", color = IosColors.text, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, lineHeight = 13.sp, fontFamily = if (bangla) BanglaFont else null
            )
        }
        SpaceKey(Modifier.weight(4.2f), if (bangla) "ক" else "EN BN", actions)   // plain, like the letters page
        NumKey(".", actions, IosColors.keyOr(8, IosColors.fn), 20.sp, 0.8f)
        ReturnKey(Modifier.weight(1.3f), enterLabel, actions, pill = true)
    }
}

@Composable
private fun RowScope.SymKey(
    text: String, actions: KeyboardActions, preview: KeyPreview, weight: Float = 1f, color: Color = IosColors.key,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val popup = LocalPopupEnabled.current
    PressBox(
        Modifier.weight(weight).keyPadding().onGloballyPositioned { bounds = it.boundsInRoot() },
        color = color, pressedColor = if (IosColors.theme.rainbow != null) IosColors.pressedOf(color) else IosColors.keyPressed,
        onTap = { actions.onRawText(text) }, fireOnDown = true,
        onPressChange = { down ->
            if (popup) {
                if (down) { preview.text = text; preview.bounds = bounds }
                else if (preview.text == text) preview.text = null
            }
        }
    ) { KeyLabel(text, 21.sp) }
}

// ------------------------------------------------------------------ shared keys

@Composable
internal fun FuncKey(
    modifier: Modifier,
    shape: Shape = KeyShape,
    color: Color = IosColors.key,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    fireOnDown: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    PressBox(
        modifier.keyPadding(),
        shape = shape, color = color, pressedColor = IosColors.keyPressed,
        onTap = onTap, onLongPress = onLongPress, fireOnDown = fireOnDown, content = content
    )
}

@Composable
internal fun KeyLabel(text: String, size: TextUnit) {
    Text(
        text, color = IosColors.text, fontSize = size, fontWeight = LocalKeyWeight.current,
        fontFamily = fontOf(text), maxLines = 1
    )
}

// ------------------------------------------------------------------ bottom strip

/** Strip under the keys: 🌐 · faded ⌃ (more suggestions rise up) ·························· 🎤 */
@Composable
private fun BottomStrip(state: KeyboardState, actions: KeyboardActions) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🌐 = switch to the next keyboard only (hold: list of keyboards). Settings is in the top bar.
        StripButton(onTap = { actions.onNextKeyboard() }, onLongPress = { actions.onShowImePicker() }) {
            Icon(Symbols.globe, "কীবোর্ড বদলান", tint = IosColors.text, modifier = Modifier.size(32.dp))
        }
        if (state.prefs.showSuggestions && state.emojiSearch == null) {
            Spacer(Modifier.width(20.dp))
            val open = state.panel == Panel.SUGGESTIONS
            StripButton(onTap = { actions.onPanel(if (open) Panel.KEYS else Panel.SUGGESTIONS) }) {
                Icon(
                    if (open) Symbols.arrowDown else Symbols.arrowUp, "আরও সাজেশন",
                    tint = IosColors.text.copy(alpha = 0.42f), modifier = Modifier.size(30.dp)
                )
            }
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
                .background(IosColors.bubble, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = IosColors.text, fontSize = 32.sp, fontWeight = LocalKeyWeight.current, fontFamily = fontOf(text))
        }
    }
}

// ------------------------------------------------------------------ pressable box

/**
 * A pressable rounded box. [fireOnDown] types the instant the finger touches (no waiting
 * for release — feels instant). Optional long-press and auto-repeat (backspace).
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
    fireOnDown: Boolean = false,
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
        Modifier.pointerInput(hasLongPress, fireOnDown) {
            val longHandler: ((Offset) -> Unit)? =
                if (hasLongPress) ({ _: Offset -> longPress?.invoke(); Unit }) else null
            detectTapGestures(
                onPress = {
                    if (fireOnDown) tap()           // type immediately on touch
                    pressed = true
                    pressChange?.invoke(true)
                    feedback(kind)
                    tryAwaitRelease()
                    pressed = false
                    pressChange?.invoke(false)
                },
                onLongPress = longHandler,
                onTap = if (fireOnDown) null else ({ _: Offset -> tap(); Unit })
            )
        }
    }

    val glass = color.alpha > 0.1f && color != Color.Transparent && color != IosColors.faded
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) pressedColor else color)
            .then(if (glass) Modifier.drawWithCache {
                // liquid glass: lighter top, diagonal light sheen (same as the letter keys)
                val body = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.09f), 1f to Color.Transparent)
                val sheen = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.16f), 0.42f to Color.Transparent, 1f to Color.White.copy(alpha = 0.05f),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
                onDrawBehind { drawRect(body); drawRect(sheen) }
            } else Modifier)
            .then(if (glass) Modifier.border(0.8.dp, IosColors.glassEdge, shape) else Modifier)
            .then(gestures),
        contentAlignment = Alignment.Center,
        content = content
    )
}
