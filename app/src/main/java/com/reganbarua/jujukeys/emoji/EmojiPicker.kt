package com.reganbarua.jujukeys.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.theme.JuJuColors

@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    recent: List<String>,
    onEmojiTap: (String) -> Unit,
    onBackToLetters: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JuJuColors.KeyboardBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clickable { onBackToLetters() },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "ABC",
                color = JuJuColors.KeyText,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 4.dp)
        ) {
            if (recent.isNotEmpty()) {
                items(recent) { emoji -> EmojiCell(emoji, onEmojiTap) }
            }
            EmojiData.categories.forEach { category ->
                items(category.emojis) { emoji -> EmojiCell(emoji, onEmojiTap) }
            }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, onTap: (String) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onTap(emoji) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}
