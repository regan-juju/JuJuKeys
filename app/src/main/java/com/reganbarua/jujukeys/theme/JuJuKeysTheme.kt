package com.reganbarua.jujukeys.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette matched to the uploaded reference image (dark iPhone-style keyboard).
object JuJuColors {
    val KeyboardBackground = Color(0xFF1C1C1E)
    val KeyBackground = Color(0xFF3A3A3C)
    val KeyBackgroundPressed = Color(0xFF545456)
    val KeyBackgroundSpecial = Color(0xFF2C2C2E)
    val KeyBackgroundSpecialPressed = Color(0xFF48484A)
    val KeyBackgroundAccent = Color(0xFF0A84FF)
    val KeyBackgroundAccentPressed = Color(0xFF3D9BFF)
    val KeyText = Color(0xFFFFFFFF)
    val KeyTextSecondary = Color(0xFF8E8E93)
    val ToolbarIcon = Color(0xFFE5E5EA)
    val SuggestionText = Color(0xFFE5E5EA)
    val Divider = Color(0xFF38383A)
}

private val JuJuDarkColorScheme = darkColorScheme(
    primary = JuJuColors.KeyBackgroundAccent,
    background = JuJuColors.KeyboardBackground,
    surface = JuJuColors.KeyboardBackground,
    onBackground = JuJuColors.KeyText,
    onSurface = JuJuColors.KeyText
)

@Composable
fun JuJuKeysTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JuJuDarkColorScheme,
        content = content
    )
}
