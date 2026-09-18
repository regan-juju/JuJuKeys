package com.reganbarua.jujukeys.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.theme.JuJuColors

@Composable
fun ClipboardPanel(
    modifier: Modifier = Modifier,
    items: List<String>,
    onItemTap: (String) -> Unit,
    onItemShareToKeep: (String) -> Unit,
    onClear: () -> Unit,
    onBackToLetters: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(254.dp)
            .background(JuJuColors.KeyboardBackground)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ABC",
                color = JuJuColors.KeyText,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { onBackToLetters() }
                    .padding(6.dp)
            )
            Text(
                text = "Clear",
                color = JuJuColors.KeyTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { onClear() }
                    .padding(6.dp)
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("Copied text will appear here", color = JuJuColors.KeyTextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn {
                items(items) { text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(JuJuColors.KeyBackgroundSpecial)
                            .padding(vertical = 4.dp)
                            .clickable { onItemTap(text) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = text,
                            color = JuJuColors.KeyText,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                        Text(
                            text = "Keep",
                            color = JuJuColors.KeyBackgroundAccent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { onItemShareToKeep(text) }
                                .padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
