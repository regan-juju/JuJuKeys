package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.theme.JuJuColors

@Composable
fun SuggestionStrip(
    modifier: Modifier = Modifier,
    suggestions: List<String>,
    onSuggestionTap: (String) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(JuJuColors.KeyboardBackground),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        suggestions.take(3).forEachIndexed { index, word ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSuggestionTap(word) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    color = JuJuColors.SuggestionText,
                    fontSize = 15.sp,
                    fontWeight = if (index == 1) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
