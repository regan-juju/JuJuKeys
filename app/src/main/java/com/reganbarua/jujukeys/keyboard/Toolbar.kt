package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reganbarua.jujukeys.R
import com.reganbarua.jujukeys.theme.JuJuColors

/** Top toolbar shown above the keyboard: Clipboard / Translate / Settings / Mic. */
@Composable
fun KeyboardToolbar(
    modifier: Modifier = Modifier,
    onClipboard: () -> Unit,
    onTranslate: () -> Unit,
    onSettings: () -> Unit,
    onMic: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(JuJuColors.KeyboardBackground)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KeyboardIconKey(
            modifier = Modifier.padding(vertical = 4.dp),
            iconRes = R.drawable.ic_clipboard,
            contentDescription = "Clipboard",
            onTap = onClipboard
        )
        KeyboardIconKey(
            modifier = Modifier.padding(vertical = 4.dp),
            iconRes = R.drawable.ic_translate,
            contentDescription = "Translate",
            onTap = onTranslate
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        KeyboardIconKey(
            modifier = Modifier.padding(vertical = 4.dp),
            iconRes = R.drawable.ic_settings,
            contentDescription = "Settings",
            onTap = onSettings
        )
        KeyboardIconKey(
            modifier = Modifier.padding(vertical = 4.dp),
            iconRes = R.drawable.ic_mic,
            contentDescription = "Voice input",
            onTap = onMic
        )
    }
}
