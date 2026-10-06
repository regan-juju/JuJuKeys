package com.reganbarua.jujukeys.keyboard

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A soft colour glow behind the glass keys: centre (fraction of width/height), colour, radius (fraction of width). */
internal class Blob(val x: Float, val y: Float, val color: Color, val r: Float)

/**
 * Keyboard themes. All follow the same rules: liquid-glass keys, plain faded return/search key,
 * thin line along the top edge. Exactly one light theme (সাদা গ্লাস); all others are dark.
 */
internal class KbTheme(
    val id: String,
    val name: String,
    val bg: Color,
    val blobs: List<Blob>,
    val key: Color,
    val keyPressed: Color,
    val fn: Color,
    val text: Color = Color.White,
    val dim: Color = Color(0xFF8A8A8A),
    val suggestion: Color = Color(0xFFC8C8C8),
    val faded: Color = Color(0x42AFAFAF),
    val fadedPressed: Color = Color(0x66AFAFAF),
    val fadedIcon: Color = Color(0x99FFFFFF),
    val divider: Color = Color(0x33FFFFFF),
    val chip: Color = Color(0x80909090),
    val card: Color = Color(0x558A8A8A),
    val bubble: Color = Color(0xFF5E5E5E),
    val shadow: Color = Color(0x2E000000),
    val glassTop: Color = Color(0x59FFFFFF),
    val edge: Color = Color(0x47FFFFFF),
    val panel: Color = Color(0xF21C1E1C),
    val nav: Int = 0xFF212121.toInt(),
    val light: Boolean = false,
    /** Rainbow theme: one colour per key column (letters); null = all keys use [key]. */
    val rainbow: List<Color>? = null,
    /** Colour for the 123 key in the rainbow theme. */
    val accent: Color? = null,
)

internal object Themes {
    val all: List<KbTheme> = listOf(
        KbTheme(
            "current", "Glass",
            bg = Color(0xF0171917),
            blobs = listOf(
                Blob(0.18f, 0.80f, Color(0x38286E3C), 0.55f), Blob(0.62f, 0.72f, Color(0x29822D2D), 0.50f),
                Blob(0.88f, 0.28f, Color(0x1F3C3C8C), 0.50f), Blob(0.30f, 0.18f, Color(0x1A6E6432), 0.45f),
            ),
            key = Color(0x668C8C8C), keyPressed = Color(0xA8B4B4B4), fn = Color(0x38707070), glassTop = Color(0xA6FFFFFF),
        ),
        KbTheme(
            "deep", "Deep liquid glass",
            bg = Color(0xF5121412),
            blobs = listOf(
                Blob(0.18f, 0.80f, Color(0x33286E3C), 0.55f), Blob(0.62f, 0.72f, Color(0x26822D2D), 0.50f),
                Blob(0.88f, 0.28f, Color(0x1F3C3C8C), 0.50f),
            ),
            key = Color(0x2EFFFFFF), keyPressed = Color(0x66FFFFFF), fn = Color(0x1AFFFFFF),
            glassTop = Color(0xC4FFFFFF), panel = Color(0xF5161816),
        ),
        KbTheme(
            "midnight", "Midnight blue",
            bg = Color(0xF50B1020),
            blobs = listOf(
                Blob(0.20f, 0.85f, Color(0x592850C8), 0.60f), Blob(0.80f, 0.25f, Color(0x407838DC), 0.60f),
                Blob(0.55f, 0.60f, Color(0x2E1E8CC8), 0.50f),
            ),
            key = Color(0x3D96B4FF), keyPressed = Color(0x7396B4FF), fn = Color(0x245A78DC),
            dim = Color(0xFF9FB0D8), suggestion = Color(0xFFC8D4F0), glassTop = Color(0xB8FFFFFF),
            card = Color(0x4D5A78DC), chip = Color(0x805A78DC), bubble = Color(0xFF3A4A78),
            panel = Color(0xF50E1428), nav = 0xFF0B1020.toInt(),
        ),
        KbTheme(
            "emerald", "Emerald green",
            bg = Color(0xF507140E),
            blobs = listOf(
                Blob(0.15f, 0.80f, Color(0x5914A05A), 0.60f), Blob(0.85f, 0.30f, Color(0x40147878), 0.60f),
                Blob(0.50f, 0.55f, Color(0x1F78C850), 0.50f),
            ),
            key = Color(0x33A0FFC8), keyPressed = Color(0x66A0FFC8), fn = Color(0x1F3CA06E),
            dim = Color(0xFF9FD8B8), suggestion = Color(0xFFC8ECD8), glassTop = Color(0xB8FFFFFF),
            card = Color(0x4D3CA06E), chip = Color(0x803CA06E), bubble = Color(0xFF2E5A44),
            panel = Color(0xF50A1A12), nav = 0xFF07140E.toInt(),
        ),
        KbTheme(
            "aurora", "Aurora purple",
            bg = Color(0xF5140A1C),
            blobs = listOf(
                Blob(0.15f, 0.80f, Color(0x52C83CA0), 0.60f), Blob(0.85f, 0.25f, Color(0x526E46E6), 0.60f),
                Blob(0.55f, 0.60f, Color(0x243CA0DC), 0.50f),
            ),
            key = Color(0x38E6AAFF), keyPressed = Color(0x6BE6AAFF), fn = Color(0x1F9650C8),
            dim = Color(0xFFD0B0E8), suggestion = Color(0xFFE6D2F2), glassTop = Color(0xB8FFFFFF),
            card = Color(0x4D9650C8), chip = Color(0x809650C8), bubble = Color(0xFF5A3A78),
            panel = Color(0xF5180E22), nav = 0xFF140A1C.toInt(),
        ),
        KbTheme(
            "amoled", "Black AMOLED",
            bg = Color(0xFF000000), blobs = emptyList(),
            key = Color(0x21FFFFFF), keyPressed = Color(0x55FFFFFF), fn = Color(0x14FFFFFF),
            dim = Color(0xFF9A9A9A), card = Color(0x33FFFFFF), bubble = Color(0xFF3A3A3A),
            glassTop = Color(0x9EFFFFFF), panel = Color(0xFF000000), nav = 0xFF000000.toInt(),
        ),
        KbTheme(
            "rainbow", "Rainbow glass",
            bg = Color(0xF716161C),
            blobs = listOf(Blob(0.15f, 0.85f, Color(0x1FF03C3C), 0.55f), Blob(0.85f, 0.20f, Color(0x1F505ADC), 0.55f)),
            key = Color(0x59605F68), keyPressed = Color(0x998A8A92), fn = Color(0x33505058),
            dim = Color(0xFFA0A0A8), glassTop = Color(0xC4FFFFFF), card = Color(0x55605F68),
            bubble = Color(0xFF4A4A52), panel = Color(0xF71C1C22), nav = 0xFF16161C.toInt(),
            rainbow = listOf(
                Color(0xC8F03A3A), Color(0xC8F2603A), Color(0xC8F28A1E), Color(0xC8E6B414), Color(0xC82DB84D),
                Color(0xC817B5AE), Color(0xC82E9BE0), Color(0xC81E6FF0), Color(0xC85059D6), Color(0xC89B4DE0),
            ),
            accent = Color(0xC8E0606A),
        ),
        KbTheme(
            "white", "White glass",
            bg = Color(0xF5D6D8DC),
            blobs = listOf(Blob(0.20f, 0.80f, Color(0x5996C8FF), 0.60f), Blob(0.80f, 0.25f, Color(0x59FFC8DC), 0.60f)),
            key = Color(0xF2FFFFFF), keyPressed = Color(0xFFC4C7CC), fn = Color(0x99FFFFFF),
            text = Color(0xFF111111), dim = Color(0xFF666666), suggestion = Color(0xFF333333),
            faded = Color(0x17000000), fadedPressed = Color(0x2E000000), fadedIcon = Color(0x99000000),
            divider = Color(0x22000000), chip = Color(0x33000000), card = Color(0xB3FFFFFF),
            bubble = Color(0xFFFFFFFF), shadow = Color(0x24000000), glassTop = Color(0xE6FFFFFF),
            edge = Color(0x24000000), panel = Color(0xF7E4E6EA), nav = 0xFFD6D8DC.toInt(), light = true,
        ),
    )

    fun byId(id: String): KbTheme = all.firstOrNull { it.id == id } ?: all[0]
}

/** Current colours, read everywhere. Changed by [IosColors.apply] when the theme changes. */
internal object IosColors {
    var theme: KbTheme = Themes.all[0]; private set
    var bg = theme.bg; private set
    var faded = theme.faded; private set
    var fadedPressed = theme.fadedPressed; private set
    var fadedIcon = theme.fadedIcon; private set
    var key = theme.key; private set
    var keyPressed = theme.keyPressed; private set
    var fn = theme.fn; private set
    var text = theme.text; private set
    var dim = theme.dim; private set
    var suggestion = theme.suggestion; private set
    var divider = theme.divider; private set
    var chip = theme.chip; private set
    var card = theme.card; private set
    var bubble = theme.bubble; private set
    var shadow = theme.shadow; private set
    var panel = theme.panel; private set
    val blue = Color(0xFF0A84FF)
    val bluePressed = Color(0xFF409CFF)
    val lightBlue = Color(0xFF8AB4F8)
    val lightBlueText = Color(0xFF062E6F)
    val keepYellow = Color(0xFFFBBC04)

    /** Thin line along the keyboard's top edge — separates it from the app. */
    var topEdge: Brush = edgeBrush(theme); private set
    /** Thin bright top edge that makes a key look like glass. */
    var glassEdge: Brush = glassBrush(theme); private set

    private fun edgeBrush(t: KbTheme) = Brush.verticalGradient(0f to t.edge, 0.04f to Color.Transparent, 1f to Color.Transparent)
    private fun glassBrush(t: KbTheme) = glassBrush(t, 0f, Float.POSITIVE_INFINITY)

    /** Glass rim for ONE key from [top] to [bottom]: bright top edge, faint ring all round — same on every row. */
    fun glassBrush(t: KbTheme, top: Float, bottom: Float): Brush = Brush.verticalGradient(
        0f to t.glassTop,
        0.35f to t.glassTop.copy(alpha = t.glassTop.alpha * 0.36f),
        0.85f to t.glassTop.copy(alpha = t.glassTop.alpha * 0.18f),
        1f to t.glassTop.copy(alpha = t.glassTop.alpha * 0.34f),     // faint light along the bottom edge too
        startY = top, endY = bottom
    )

    /** Rainbow theme: colour number [i] (wraps round); null for every other theme. */
    fun rainbowAt(i: Int): Color? = theme.rainbow?.let { it[((i % it.size) + it.size) % it.size] }

    /** [base] normally; in the rainbow theme the colour number [i]. */
    fun keyOr(i: Int, base: Color): Color = rainbowAt(i) ?: base

    /** Pressed look of a coloured key: a little lighter. */
    fun pressedOf(c: Color): Color = androidx.compose.ui.graphics.lerp(c, Color.White, 0.30f).copy(alpha = 0.95f)

    /** Whole rainbow left → right (space bar). */
    fun rainbowBrush(): Brush? = theme.rainbow?.let { Brush.horizontalGradient(it) }

    fun apply(t: KbTheme) {
        if (t === theme) return
        theme = t
        bg = t.bg; faded = t.faded; fadedPressed = t.fadedPressed; fadedIcon = t.fadedIcon
        key = t.key; keyPressed = t.keyPressed; fn = t.fn; text = t.text; dim = t.dim
        suggestion = t.suggestion; divider = t.divider; chip = t.chip; card = t.card
        bubble = t.bubble; shadow = t.shadow; panel = t.panel
        topEdge = edgeBrush(t); glassEdge = glassBrush(t)
    }
}
