package com.reganbarua.jujukeys.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.theme.JuJuColors

@Composable
fun TranslatePanel(
    modifier: Modifier = Modifier,
    sourceText: String,
    resultText: String,
    resultHint: String?,
    direction: TranslationDirection,
    onSwapDirection: () -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    onInsertResult: () -> Unit,
    onBackToLetters: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(254.dp)
            .background(JuJuColors.KeyboardBackground)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ABC",
                color = JuJuColors.KeyText,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onBackToLetters() }.padding(6.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (fromLabel, toLabel) = if (direction == TranslationDirection.BN_TO_EN) "বাংলা" to "English" else "English" to "বাংলা"
                Text(text = fromLabel, color = JuJuColors.SuggestionText, fontSize = 13.sp)
                Text(
                    text = "  ⇄  ",
                    color = JuJuColors.KeyBackgroundAccent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onSwapDirection() }
                )
                Text(text = toLabel, color = JuJuColors.SuggestionText, fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(JuJuColors.KeyBackgroundSpecial)
                .padding(10.dp)
        ) {
            Text(
                text = sourceText.ifEmpty { "Type a word, then tap Translate" },
                color = if (sourceText.isEmpty()) JuJuColors.KeyTextSecondary else JuJuColors.KeyText,
                fontSize = 15.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(JuJuColors.KeyBackgroundAccent)
                    .clickable { onTranslate() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Translate", color = JuJuColors.KeyText, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(JuJuColors.KeyBackgroundSpecial)
                    .clickable { onClear() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Clear", color = JuJuColors.KeyTextSecondary, fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(JuJuColors.KeyBackgroundSpecial)
                .clickable(enabled = resultText.isNotEmpty()) { onInsertResult() }
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = resultText.ifEmpty { resultHint ?: "" },
                    color = if (resultText.isEmpty()) JuJuColors.KeyTextSecondary else JuJuColors.KeyText,
                    fontSize = 15.sp
                )
                if (resultText.isNotEmpty()) {
                    Text("Tap to insert", color = JuJuColors.KeyTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
