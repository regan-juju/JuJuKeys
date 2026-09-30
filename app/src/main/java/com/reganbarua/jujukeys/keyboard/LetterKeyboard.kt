package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The letter keyboard, drawn on ONE canvas (like Gboard / the Android keyboard) instead of
 * ~35 separate UI pieces. A key press only repaints — nothing is rebuilt — so typing is fast.
 * Handles several fingers at once, long-press, backspace repeat and space-bar cursor slide.
 */
private enum class KType { CHAR, SHIFT, BACKSPACE, SYMBOLS, EMOJI, SPACE, RETURN, GAP }

private class GKey(
    val type: KType,
    val weight: Float,
    val label: String = "",
    val ch: Char = ' ',
    val hint: Char? = null,
)

@Composable
internal fun LetterKeyboard(state: KeyboardState, actions: KeyboardActions, preview: KeyPreview) {
    val prefs = state.prefs
    val bangla = state.language == Language.BANGLA
    val showEmoji = prefs.showEmojiKey
    val longPressOn = prefs.longPressSymbols

    // ---- layout (rebuilt only when language / settings change, not on key presses)
    val rows: List<List<GKey>> = remember(bangla, showEmoji, prefs.numberRow, longPressOn) {
        fun chars(list: List<CharKey>, lp: Boolean) = list.map { k ->
            val shown = if (bangla && k.label in '0'..'9') "০১২৩৪৫৬৭৮৯"[k.label - '0'].toString()
            else k.label.uppercaseChar().toString()
            GKey(KType.CHAR, 1f, shown, k.label, if (lp) k.hint else null)
        }
        buildList {
            if (prefs.numberRow) add(chars(KeyboardLayouts.symbolsRow1, false))
            add(chars(KeyboardLayouts.lettersRow1, longPressOn))
            add(listOf(GKey(KType.GAP, 0.5f)) + chars(KeyboardLayouts.lettersRow2, longPressOn) + GKey(KType.GAP, 0.5f))
            add(
                listOf(GKey(KType.SHIFT, 1.29f), GKey(KType.GAP, 0.21f)) +
                    chars(KeyboardLayouts.lettersRow3, longPressOn) +
                    listOf(GKey(KType.GAP, 0.21f), GKey(KType.BACKSPACE, 1.29f))
            )
            add(
                buildList {
                    add(GKey(KType.SYMBOLS, 1.24f, if (bangla) "১২৩" else "123"))
                    if (showEmoji) add(GKey(KType.EMOJI, 1.24f))
                    add(GKey(KType.SPACE, if (showEmoji) 5.02f else 6.26f, if (bangla) "ক" else "EN BN"))
                    add(GKey(KType.RETURN, 2.5f))
                }
            )
        }
    }
    val flat = remember(rows) { rows.flatten() }

    val density = LocalDensity.current
    val rowH = LocalRowHeight.current
    val weight = LocalKeyWeight.current
    val popupOn = LocalPopupEnabled.current
    val feedback = LocalKeyFeedback.current
    val keyPadH = with(density) { 2.6.dp.toPx() }
    val keyPadV = with(density) { 5.dp.toPx() }
    val sidePad = with(density) { 3.dp.toPx() }
    val corner = with(density) { 8.dp.toPx() }
    val rowPx = with(density) { rowH.toPx() }

    // ---- text & icons prepared once
    val measurer = rememberTextMeasurer(cacheSize = 80)
    val labelStyle = remember(weight) { TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = weight) }
    val smallStyle = remember(weight) { TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = weight) }
    val hintStyle = remember { TextStyle(color = IosColors.dim, fontSize = 10.5.sp) }
    val bubbleStyle = remember(weight) { TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = weight) }
    val layouts: Map<String, TextLayoutResult> = remember(flat, labelStyle) {
        buildMap {
            flat.forEach { k ->
                when (k.type) {
                    KType.CHAR -> {
                        put("c:" + k.label, measurer.measure(k.label, labelStyle.copy(fontFamily = fontOf(k.label))))
                        put("b:" + k.label, measurer.measure(k.label, bubbleStyle.copy(fontFamily = fontOf(k.label))))
                        k.hint?.let { h -> put("b:$h", measurer.measure(h.toString(), bubbleStyle)) }
                    }
                    KType.SYMBOLS -> put("s", measurer.measure(k.label, smallStyle.copy(fontFamily = fontOf(k.label))))
                    KType.SPACE -> put("space", measurer.measure(k.label, hintStyle.copy(fontFamily = fontOf(k.label))))
                    else -> {}
                }
            }
        }
    }
    val shiftPainter = rememberVectorPainter(
        when (state.shift) {
            ShiftState.OFF -> IosIcons.shiftOff
            ShiftState.ONCE -> IosIcons.shiftOn
            ShiftState.LOCK -> IosIcons.capsLock
        }
    )
    val backPainter = rememberVectorPainter(Symbols.backspace)
    val emojiPainter = rememberVectorPainter(Symbols.emoji)
    val returnPainter = rememberVectorPainter(Symbols.keyboardReturn)
    val searchPainter = rememberVectorPainter(Symbols.search)
    // key-press bubble: drawn on this same canvas (no UI rebuild on each press = faster)
    var bubble by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // ---- geometry
    var size by remember { mutableStateOf(Size.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val geometry = remember(rows, size, rowPx) {
        val keyRects = ArrayList<Rect>()     // what is drawn
        val hitRects = ArrayList<Rect>()     // what is touchable (includes the gaps around a key)
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (size.width - 2 * sidePad) / total
            var x = sidePad
            val top = r * rowPx
            row.forEach { k ->
                val w = unit * k.weight
                hitRects.add(Rect(x, top, x + w, top + rowPx))
                keyRects.add(Rect(x + keyPadH, top + keyPadV, x + w - keyPadH, top + rowPx - keyPadV))
                x += w
            }
        }
        keyRects to hitRects
    }

    val pressed = remember { mutableStateListOf<Int>() }
    val act by rememberUpdatedState(actions)
    val longDelay by rememberUpdatedState(prefs.longPressDelay.toLong())
    val enterLabel = state.enterLabel

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(rowH * rows.size)
            .onGloballyPositioned { size = Size(it.size.width.toFloat(), it.size.height.toFloat()); origin = it.positionInRoot() }
            .pointerInput(flat, geometry) {
                pressed.clear()
                val (keyRects, hitRects) = geometry
                fun hit(p: Offset): Int {
                    for (i in hitRects.indices) {
                        if (flat[i].type != KType.GAP && hitRects[i].contains(p)) return i
                    }
                    // touches in a gap go to the nearest key in that row
                    var best = -1; var bestD = Float.MAX_VALUE
                    for (i in hitRects.indices) {
                        if (flat[i].type == KType.GAP) continue
                        val r = hitRects[i]
                        if (p.y < r.top || p.y > r.bottom) continue
                        val d = abs(r.center.x - p.x)
                        if (d < bestD) { bestD = d; best = i }
                    }
                    return best
                }
                class Touch(val key: Int, var job: Job? = null, var dragging: Boolean = false, var acc: Float = 0f)
                val touches = HashMap<PointerId, Touch>()
                val stepPx = 9.dp.toPx()
                coroutineScope {
                    val scope = this
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                if (c.changedToDown()) {
                                    val i = hit(c.position)
                                    if (i < 0) continue
                                    c.consume()
                                    val k = flat[i]
                                    val t = Touch(i)
                                    touches[c.id] = t
                                    pressed.add(i)
                                    feedback(
                                        when (k.type) {
                                            KType.BACKSPACE -> KeyKind.DELETE
                                            KType.SPACE -> KeyKind.SPACE
                                            KType.RETURN -> KeyKind.RETURN
                                            else -> KeyKind.NORMAL
                                        }
                                    )
                                    when (k.type) {
                                        KType.CHAR -> {
                                            act.onChar(k.ch)                     // types instantly on touch
                                            if (popupOn) bubble = i to k.label
                                            val h = k.hint
                                            if (h != null) t.job = scope.launch {
                                                delay(longDelay)
                                                act.onLongChar(h)
                                                if (popupOn) bubble = t.key to h.toString()
                                            }
                                        }
                                        KType.SHIFT -> act.onShift()
                                        KType.BACKSPACE -> {
                                            act.onBackspace()
                                            t.job = scope.launch {
                                                delay(400)
                                                while (true) { act.onBackspace(); delay(55) }
                                            }
                                        }
                                        else -> {}
                                    }
                                } else if (c.changedToUp()) {
                                    val t = touches.remove(c.id) ?: continue
                                    c.consume()
                                    t.job?.cancel()
                                    pressed.remove(t.key)
                                    val k = flat[t.key]
                                    when (k.type) {
                                        KType.CHAR -> if (bubble?.first == t.key) bubble = null
                                        KType.SYMBOLS -> act.onPage(Page.SYMBOLS)
                                        KType.EMOJI -> act.onPanel(Panel.EMOJI)
                                        KType.SPACE -> if (!t.dragging) act.onSpace()
                                        KType.RETURN -> act.onEnter()
                                        else -> {}
                                    }
                                } else if (c.pressed) {
                                    val t = touches[c.id] ?: continue
                                    if (flat[t.key].type == KType.SPACE) {
                                        t.acc += c.positionChange().x
                                        if (!t.dragging && abs(t.acc) > viewConfiguration.touchSlop) {
                                            t.dragging = true; t.acc = 0f
                                        }
                                        if (t.dragging) {
                                            val steps = (t.acc / stepPx).toInt()
                                            if (steps != 0) { act.onCursorMove(steps); t.acc -= steps * stepPx }
                                            c.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val (keyRects, _) = geometry
        val iconPx = 25.dp.toPx()
        for (i in flat.indices) {
            val k = flat[i]
            if (k.type == KType.GAP) continue
            val r = keyRects.getOrNull(i) ?: continue
            val down = i in pressed
            val cr = CornerRadius(corner, corner)
            if (k.type == KType.RETURN) {
                // return / search: always plain and faded (like iPhone), never coloured
                drawRoundRect(if (down) IosColors.fadedPressed else IosColors.faded, r.topLeft, r.size, cr)
            } else {
                // glass key: soft shadow, see-through body, bright top edge; pressed keys swell a little
                val kr = if (down) r.inflate(2.dp.toPx()) else r
                drawRoundRect(Color(0x2E000000), kr.topLeft + Offset(0f, 1.dp.toPx()), kr.size, cr)
                drawRoundRect(if (down) IosColors.keyPressed else IosColors.key, kr.topLeft, kr.size, cr)
                drawRoundRect(GlassEdge, kr.topLeft, kr.size, cr, style = Stroke(width = 0.8.dp.toPx()))
            }
            when (k.type) {
                KType.CHAR -> layouts["c:" + k.label]?.let { drawCentered(it, r) }
                KType.SYMBOLS -> layouts["s"]?.let { drawCentered(it, r) }
                KType.SPACE -> layouts["space"]?.let {
                    drawText(it, topLeft = Offset(r.right - it.size.width - 8.dp.toPx(), r.bottom - it.size.height - 2.dp.toPx()))
                }
                KType.RETURN -> drawIcon(
                    if (enterLabel == "search") searchPainter else returnPainter,
                    r, iconPx + 1.dp.toPx(), IosColors.fadedIcon
                )
                KType.SHIFT -> drawIcon(shiftPainter, r, iconPx, Color.White)
                KType.BACKSPACE -> drawIcon(backPainter, r, iconPx, Color.White)
                KType.EMOJI -> drawIcon(emojiPainter, r, iconPx + 1.dp.toPx(), Color.White)
                KType.GAP -> {}
            }
        }
        // bubble above the pressed letter
        bubble?.let { (i, label) ->
            val r = keyRects.getOrNull(i) ?: return@let
            val l = layouts["b:$label"] ?: return@let
            val w = r.width + 14.dp.toPx()
            val h = r.height * 1.35f
            val x = (r.center.x - w / 2f).coerceIn(0f, size.width - w)
            val y = r.bottom - h - r.height * 0.55f
            val cr = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            drawRoundRect(Color(0x66000000), Offset(x, y + 2.dp.toPx()), Size(w, h), cr)
            drawRoundRect(Color(0xFF5E5E5E), Offset(x, y), Size(w, h), cr)
            drawRoundRect(GlassEdge, Offset(x, y), Size(w, h), cr, style = Stroke(width = 0.8.dp.toPx()))
            drawText(l, topLeft = Offset(x + w / 2f - l.size.width / 2f, y + h / 2f - l.size.height / 2f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCentered(l: TextLayoutResult, r: Rect) {
    drawText(l, topLeft = Offset(r.center.x - l.size.width / 2f, r.center.y - l.size.height / 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIcon(
    painter: androidx.compose.ui.graphics.painter.Painter, r: Rect, px: Float, color: Color,
) {
    translate(r.center.x - px / 2f, r.center.y - px / 2f) {
        with(painter) { draw(Size(px, px), colorFilter = ColorFilter.tint(color)) }
    }
}
