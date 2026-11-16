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
    @Suppress("UNUSED_PARAMETER") blobs: List<Blob>,
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
    /** Picture drawn behind the key pad (suggestion bar + key rows); the bottom strip stays plain [bg]. */
    @androidx.annotation.DrawableRes val bgImage: Int? = null,
    /** Dark veil over [bgImage] (0..255) so the letters stay readable. */
    val bgVeil: Int = 0x40,
    /** >0: blur what is behind the keyboard window by this many pixels (Android 12+, only where the phone allows it). */
    val blur: Int = 0,
    /** Blur glass: soft colour glows behind the glass keys, so the keyboard looks see-through whatever app is behind (Settings switch). */
    val glow: List<Blob> = emptyList(),
    /** true: [bgImage] fills the whole keyboard, bottom strip included. */
    val bgFull: Boolean = false,
    /** Colours of the 🌐 and 🎤 buttons (null = normal text colour). */
    val navGlobe: Color? = null,
    val navMic: Color? = null,
    /** >0: crisp glass keys (clear outline all round, bright top edge, soft drop shadow, clearer body). 1 = full strength. */
    val crisp: Float = 0f,
) {
    /** Flat background like the iPhone keyboard: no colour glows behind the keys (all themes). */
    val blobs: List<Blob> = emptyList()
}

internal object Themes {
    val all: List<KbTheme> = listOf(
        KbTheme(
            "current", "Glass",
            bg = Color(0xF21C1C1E),
            blobs = listOf(
                Blob(0.18f, 0.80f, Color(0x38286E3C), 0.55f), Blob(0.62f, 0.72f, Color(0x29822D2D), 0.50f),
                Blob(0.88f, 0.28f, Color(0x1F3C3C8C), 0.50f), Blob(0.30f, 0.18f, Color(0x1A6E6432), 0.45f),
            ),
            // glass keys over a soft blurred grey picture behind the key pad; the bottom strip (🌐 ^ 🎤) stays plain dark
            bgImage = com.reganbarua.jujukeys.R.drawable.theme_glass, bgVeil = 0x6B,
            key = Color(0x1AFFFFFF), keyPressed = Color(0x66FFFFFF), fn = Color(0x1AFFFFFF),
            glassTop = Color(0xFFFFFFFF), edge = Color(0x59FFFFFF), crisp = 1f,
        ),
        KbTheme(
            "darkglass", "Dark glass",
            // same glass keypad, darker and less grey; coloured 🌐 and 🎤
            bg = Color(0xFF1A1A1C), blobs = emptyList(), bgImage = com.reganbarua.jujukeys.R.drawable.theme_dark, bgVeil = 0xA8,
            key = Color(0x1AFFFFFF), keyPressed = Color(0x4DFFFFFF), fn = Color(0x1AFFFFFF),
            glassTop = Color(0xFFFFFFFF), edge = Color(0x66FFFFFF), crisp = 0.75f,
            panel = Color(0xF21C1C1E), nav = 0xFF1A1A1C.toInt(),
            navGlobe = Color(0xFF4FC3FF), navMic = Color(0xFFFF8A6B),
        ),
        KbTheme(
            "noirglass", "Noir glass",
            // nearly black glass keypad, picture only a faint texture; coloured 🌐 and 🎤
            bg = Color(0xFF111113), blobs = emptyList(), bgImage = com.reganbarua.jujukeys.R.drawable.theme_dark, bgVeil = 0xCC,
            key = Color(0x1AFFFFFF), keyPressed = Color(0x44FFFFFF), fn = Color(0x1AFFFFFF),
            glassTop = Color(0xF0FFFFFF), edge = Color(0x59FFFFFF), crisp = 0.7f,
            panel = Color(0xF50E0E10), nav = 0xFF111113.toInt(),
            navGlobe = Color(0xFF5BE0B0), navMic = Color(0xFFFFB347),
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
            "amoled", "Black AMOLED",
            bg = Color(0xFF000000), blobs = emptyList(),
            key = Color(0x21FFFFFF), keyPressed = Color(0x55FFFFFF), fn = Color(0x14FFFFFF),
            dim = Color(0xFF9A9A9A), card = Color(0x33FFFFFF), bubble = Color(0xFF3A3A3A),
            glassTop = Color(0x9EFFFFFF), panel = Color(0xFF000000), nav = 0xFF000000.toInt(),
        ),
        KbTheme(
            "puredark", "Pure Dark",
            bg = Color(0xFF000000), blobs = emptyList(),
            key = Color(0xFF111113), keyPressed = Color(0xFF242428), fn = Color(0xFF0A0A0B),
            text = Color(0xFFE6E6E6), dim = Color(0xFF8A8A8E), suggestion = Color(0xFFB4B4B8),
            faded = Color(0x0DFFFFFF), fadedPressed = Color(0x22FFFFFF), fadedIcon = Color(0x80FFFFFF),
            divider = Color(0x1FFFFFFF), chip = Color(0x33FFFFFF), card = Color(0x22FFFFFF), bubble = Color(0xFF1C1C1E),
            shadow = Color(0x00000000), glassTop = Color(0x40FFFFFF), edge = Color(0x26FFFFFF),
            panel = Color(0xFF000000), nav = 0xFF000000.toInt(),
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
            "pattern", "Pattern glass",
            bg = Color(0xFF1C1C1E), blobs = emptyList(),
            // dark smoked-glass keys with a bright rim over a darkened picture (letters stay easy to read)
            key = Color(0xA0262628), keyPressed = Color(0xC84A4A50), fn = Color(0xB0202022),
            glassTop = Color(0xDEFFFFFF), edge = Color(0x59FFFFFF),
            card = Color(0x40FFFFFF), chip = Color(0x66FFFFFF), bubble = Color(0xFF3A3A3C),
            panel = Color(0xF21C1C1E), nav = 0xFF1C1C1E.toInt(),
            bgImage = com.reganbarua.jujukeys.R.drawable.theme_pattern, bgVeil = 0x96,
        ),
        KbTheme(
            "blur", "Blur glass",
            // see-through dark glass: the app behind the keyboard shows blurred through it (like the iPhone keyboard)
            bg = Color(0xD0141A28), blobs = emptyList(),   // dark navy veil: stays dark even over a white app (light-mode chat), like the demo
            // dark smoked-glass keys (chosen "middle" darkness), light blur so the apps behind stay faintly visible
            key = Color(0x4D08090D), keyPressed = Color(0x8C9A9AA2), fn = Color(0x3808090D),
            glassTop = Color(0xA8FFFFFF), edge = Color(0x40FFFFFF),
            card = Color(0x40FFFFFF), chip = Color(0x66FFFFFF), bubble = Color(0xFF3A3A3C),
            panel = Color(0xE61C1C1E), nav = 0xFF1C1C1E.toInt(),
            blur = 30,
            glow = listOf(
                Blob(0.10f, 0.12f, Color(0x8C7A3CFF), 0.36f), Blob(0.40f, 0.32f, Color(0x80FF7A1A), 0.34f),
                Blob(0.76f, 0.10f, Color(0x80E23A3A), 0.32f), Blob(0.97f, 0.42f, Color(0x8C1E7BFF), 0.34f),
                Blob(0.12f, 0.90f, Color(0x801FAE5A), 0.32f), Blob(0.84f, 0.92f, Color(0x80FFD21F), 0.30f),
            ),
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
    /** True while the phone is really blurring what is behind the keyboard (set by the keyboard service). */
    var blurOn = false
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
    fun glassBrush(t: KbTheme, top: Float, bottom: Float): Brush = glassBrushOf(t.glassTop.copy(alpha = t.glassTop.alpha * RIM), top, bottom)

    /** Bright white rim strength (1 = old look); a little softer, like the iPhone keyboard. */
    private const val RIM = 0.55f

    private fun glassBrushOf(g: Color, top: Float, bottom: Float): Brush = Brush.verticalGradient(
        0f to g,
        0.35f to g.copy(alpha = g.alpha * 0.36f),
        0.85f to g.copy(alpha = g.alpha * 0.18f),
        1f to g.copy(alpha = g.alpha * 0.34f),     // faint light along the bottom edge too
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
