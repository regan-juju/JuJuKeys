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

internal fun rainbowFor(seed: Int): Color = PressRainbow[((seed % PressRainbow.size) + PressRainbow.size) % PressRainbow.size]

/** Light around and below the key — drawn BEFORE the key body (outside its edge). */
internal fun DrawScope.drawGlowBehind(r: Rect, corner: Float, c: Color, strong: Boolean) {
    val d = density
    if (strong) {
        // big halo: three soft layers
        for ((grow, a) in listOf(9f to 0.22f, 6f to 0.38f, 3f to 0.62f)) {
            val g = r.inflate(grow * d).translate(0f, 2.5f * d)
            drawRoundRect(c.copy(alpha = a), g.topLeft, g.size, CornerRadius(corner + grow * d))
        }
    } else {
        for ((grow, a) in listOf(5f to 0.30f, 2.5f to 0.62f)) {
            val g = r.inflate(grow * d).translate(0f, 2.5f * d)
            drawRoundRect(c.copy(alpha = a), g.topLeft, g.size, CornerRadius(corner + grow * d))
        }
    }
}

/** Light inside the key: fills from the bottom and runs up the border — drawn OVER the key body. */
internal fun DrawScope.drawGlowInside(r: Rect, corner: Float, c: Color, strong: Boolean) {
    val cr = CornerRadius(corner, corner)
    val fill = if (strong) listOf(0f to c.copy(alpha = 0.40f), 0.45f to c.copy(alpha = 0.65f), 1f to c.copy(alpha = 0.95f))
               else listOf(0f to Color.Transparent, 0.30f to Color.Transparent, 1f to c.copy(alpha = 0.80f))
    drawRoundRect(Brush.verticalGradient(*fill.toTypedArray(), startY = r.top, endY = r.bottom), r.topLeft, r.size, cr)
    drawRoundRect(
        Brush.verticalGradient(0f to c.copy(alpha = if (strong) 0.85f else 0.55f), 0.6f to c.copy(alpha = 0.85f), 1f to c,
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
    private val color = rainbowFor(System.identityHashCode(this) / 7)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                val now = when (i) {
                    is PressInteraction.Press -> true
                    is PressInteraction.Release, is PressInteraction.Cancel -> false
                    else -> pressed
                }
                if (now != pressed) { pressed = now; invalidateDraw() }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (pressed) {
            val r = Rect(Offset.Zero, size)
            val corner = 8f * density
            drawRoundRect(color.copy(alpha = 0.22f), r.topLeft, r.size, CornerRadius(corner))
            drawGlowInside(r, corner, color, strong = false)
        }
        drawContent()
    }
}
