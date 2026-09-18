package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.reganbarua.jujukeys.theme.JuJuColors

/** A single tappable keyboard key with an iPhone-style rounded background and press state. */
@Composable
fun KeyboardKey(
    modifier: Modifier = Modifier,
    label: String,
    subLabel: String? = null,
    background: Color = JuJuColors.KeyBackground,
    pressedBackground: Color = JuJuColors.KeyBackgroundPressed,
    textColor: Color = JuJuColors.KeyText,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    contentDescription: String? = null,
    onTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) pressedBackground else background)
            .clickable(interactionSource = interactionSource, indication = null) { onTap() }
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center
    ) {
        if (subLabel != null) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = subLabel, color = JuJuColors.KeyTextSecondary, fontSize = 11.sp)
                Text(text = label, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Medium)
            }
        } else {
            Text(text = label, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun KeyboardIconKey(
    modifier: Modifier = Modifier,
    iconRes: Int,
    background: Color = JuJuColors.KeyBackgroundSpecial,
    pressedBackground: Color = JuJuColors.KeyBackgroundSpecialPressed,
    tint: Color = JuJuColors.ToolbarIcon,
    contentDescription: String,
    onTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) pressedBackground else background)
            .clickable(interactionSource = interactionSource, indication = null) { onTap() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint)
        )
    }
}
