package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Press light: the key being pressed glows in its own rainbow colour, from the bottom up to its border.
 * Return and backspace glow more strongly. Used by every key and button in the keyboard.
 */
internal val PressRainbow = listOf(
    Color(0xFFFF4D4D), Color(0xFFFF8A3C), Color(0xFFFFD23C), Color(0xFF7BE04A), Color(0xFF2FD38A),
    Color(0xFF2FC8FF), Color(0xFF4D7DFF), Color(0xFF9A5AFF), Color(0xFFE05AFF), Color(0xFFFF5AA8),
)

/** A quick tap lasts only ~1/20 s — too short to see. The light stays a moment and fades out after release. */
internal const val GlowHoldMs = 90L
internal const val GlowFadeMs = 380

internal fun rainbowFor(seed: Int): Color = PressRainbow[((seed % PressRainbow.size) + PressRainbow.size) % PressRainbow.size]

/** Light around and below the key — drawn BEFORE the key body (outside its edge). */
internal fun DrawScope.drawGlowBehind(r: Rect, corner: Float, c: Color, strong: Boolean, level: Float = 1f) {
    val d = density
    if (strong) {
        // big halo: three soft layers
        for ((grow, a) in listOf(9f to 0.22f, 6f to 0.38f, 3f to 0.62f)) {
            val g = r.inflate(grow * d).translate(0f, 2.5f * d)
            drawRoundRect(c.copy(alpha = a * level), g.topLeft, g.size, CornerRadius(corner + grow * d))
        }
    } else {
        for ((grow, a) in listOf(5f to 0.30f, 2.5f to 0.62f)) {
            val g = r.inflate(grow * d).translate(0f, 2.5f * d)
            drawRoundRect(c.copy(alpha = a * level), g.topLeft, g.size, CornerRadius(corner + grow * d))
        }
    }
}

/** Light inside the key: fills from the bottom and runs up the border — drawn OVER the key body. */
internal fun DrawScope.drawGlowInside(r: Rect, corner: Float, c: Color, strong: Boolean, level: Float = 1f) {
    val L = level.coerceIn(0f, 1f)
    val cr = CornerRadius(corner, corner)
    val fill = if (strong) listOf(0f to c.copy(alpha = (0.40f) * L), 0.45f to c.copy(alpha = (0.65f) * L), 1f to c.copy(alpha = (0.95f) * L))
               else listOf(0f to Color.Transparent, 0.30f to Color.Transparent, 1f to c.copy(alpha = (0.80f) * L))
    drawRoundRect(Brush.verticalGradient(*fill.toTypedArray(), startY = r.top, endY = r.bottom), r.topLeft, r.size, cr)
    drawRoundRect(
        Brush.verticalGradient(0f to c.copy(alpha = (if (strong) 0.85f else 0.55f) * L), 0.6f to c.copy(alpha = (0.85f) * L), 1f to c.copy(alpha = L),
            startY = r.top, endY = r.bottom),
        r.topLeft, r.size, cr, style = Stroke(width = (if (strong) 2.2f else 1.6f) * density)
    )
}

/**
 * The same light for every plain `clickable` in the keyboard (translate bar, language list, panels…):
 * provided as the keyboard's LocalIndication.
 */
internal object RainbowIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RainbowNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}

private class RainbowNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed = false
    private var level = 0f
    private val color = rainbowFor(System.identityHashCode(this) / 7)
    private val fade = androidx.compose.animation.core.Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                val now = when (i) {
                    is PressInteraction.Press -> true
                    is PressInteraction.Release, is PressInteraction.Cancel -> false
                    else -> pressed
                }
                if (now != pressed) {
                    pressed = now
                    if (now) { fade.snapTo(1f); level = 1f; invalidateDraw() }
                    else coroutineScope.launch {
                        kotlinx.coroutines.delay(GlowHoldMs)
                        if (!pressed) fade.animateTo(0f, androidx.compose.animation.core.tween(GlowFadeMs)) { level = value; invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (level > 0.01f) {
            val r = Rect(Offset.Zero, size)
            val corner = 8f * density
            drawRoundRect(color.copy(alpha = 0.22f * level), r.topLeft, r.size, CornerRadius(corner))
            drawGlowInside(r, corner, color, strong = false, level = level)
        }
        drawContent()
    }
}

/** 1 while pressed; after release it stays a moment and fades to 0 (so a quick tap is visible). */
@androidx.compose.runtime.Composable
internal fun rememberGlowLevel(pressed: Boolean): Float {
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) a.snapTo(1f)
        else if (a.value > 0f) {
            kotlinx.coroutines.delay(GlowHoldMs)
            a.animateTo(0f, androidx.compose.animation.core.tween(GlowFadeMs))
        }
    }
    return a.value
}
