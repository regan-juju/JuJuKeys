package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.clipboard.ClipItem

// ------------------------------------------------------------------ suggestion bar

/** iPhone suggestion bar: three words; ক (English) / A (Bangla) button switches language. */
@Composable
internal fun SuggestionBar(state: KeyboardState, actions: KeyboardActions) {
    val bangla = state.language == Language.BANGLA
    Row(
        Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bangla) LangChip("A") { actions.onToggleLanguage() }
        val words = state.suggestions
        for (i in 0 until 3) {
            if (i > 0) Box(Modifier.width(1.dp).height(26.dp).background(IosColors.divider))
            val w = words.getOrNull(i)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (w != null) Modifier.clickable { actions.onSuggestion(i) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (w != null) {
                    Text(
                        w, color = IosColors.suggestion, fontSize = 19.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        if (!bangla) LangChip("ক") { actions.onToggleLanguage() }
    }
}

@Composable
private fun LangChip(text: String, onTap: () -> Unit) {
    PressBox(
        Modifier.size(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = IosColors.chip, pressedColor = IosColors.keyPressed,
        onTap = onTap
    ) {
        Text(text, color = IosColors.text, fontSize = 19.sp)
    }
}

// ------------------------------------------------------------------ translate bar

/** Gboard-style translate: type here, the translation is written into the app. */
@Composable
internal fun TranslateBar(state: KeyboardState, actions: KeyboardActions) {
    val fromBangla = state.translateFrom == Language.BANGLA
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            LangPill(if (fromBangla) "বাংলা" else "ENGLISH")
            Icon(
                Icons.Filled.SwapHoriz, "উল্টাও", tint = IosColors.text,
                modifier = Modifier.padding(horizontal = 6.dp).size(30.dp).clickable { actions.onTranslateSwap() }
            )
            LangPill(if (fromBangla) "ENGLISH" else "বাংলা")
            Spacer(Modifier.weight(1f))
            SmallIcon(Icons.AutoMirrored.Filled.OpenInNew, "Google Translate অ্যাপ") { actions.onOpenTranslateApp() }
            Spacer(Modifier.width(6.dp))
            SmallIcon(Icons.Filled.Close, "বন্ধ") { actions.onTranslateToggle() }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, Color(0xFF8AB4F8), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val empty = state.translateInput.isEmpty()
            Text(
                if (empty) "অনুবাদ করার জন্য এখানে টাইপ করুন" else state.translateInput + "│",
                color = if (empty) IosColors.dim else IosColors.text,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // status: ONLINE ✓ / OFFLINE AVAILABLE
            if (state.online) Status(Icons.Filled.CheckCircle, "ONLINE", Color(0xFF81C995))
            if (state.offlineReady) Status(null, "OFFLINE", Color(0xFF8AB4F8))
            if (!state.online && !state.offlineReady) Status(Icons.Outlined.CloudOff, "NO MODEL", IosColors.dim)
        }
        if (state.translateStatus.isNotEmpty()) {
            Text(
                state.translateStatus, color = Color(0xFFFDD663), fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun LangPill(text: String) {
    Box(
        Modifier
            .background(IosColors.key, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(text, color = IosColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Status(icon: ImageVector?, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(" $text", color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, description: String, onTap: () -> Unit) {
    PressBox(
        Modifier.size(38.dp),
        shape = RoundedCornerShape(19.dp),
        color = IosColors.key, pressedColor = IosColors.keyPressed,
        onTap = onTap
    ) {
        Icon(icon, description, tint = IosColors.text, modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ clipboard panel

@Composable
internal fun ClipboardPanel(clips: List<ClipItem>, actions: KeyboardActions) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ক্লিপবোর্ড", color = IosColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton("Keep খুলুন", IosColors.keepYellow) { actions.onOpenKeep() }
            TextButton("মুছুন", IosColors.suggestion) { actions.onClipClear() }
            TextButton("ABC", IosColors.text) { actions.onPanel(Panel.KEYS) }
        }
        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "কিছু কপি করুন — এখানে জমা হবে।\n💡 বোতাম চাপলে লেখাটি Google Keep-এ সেভ হবে।",
                    color = IosColors.dim, fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(clips, key = { it.id }) { clip -> ClipRow(clip, actions) }
            }
        }
    }
}

@Composable
private fun ClipRow(clip: ClipItem, actions: KeyboardActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(IosColors.card, RoundedCornerShape(10.dp))
            .clickable { actions.onClipPaste(clip.text) }
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            clip.text, color = IosColors.text, fontSize = 15.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        ClipIcon(if (clip.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "পিন", IosColors.suggestion) {
            actions.onClipPin(clip.id)
        }
        ClipIcon(Icons.Filled.Lightbulb, "Google Keep-এ সেভ", IosColors.keepYellow) {
            actions.onClipToKeep(clip.text)
        }
        ClipIcon(Icons.Filled.Close, "মুছুন", IosColors.dim) { actions.onClipDelete(clip.id) }
    }
}

@Composable
private fun ClipIcon(icon: ImageVector, description: String, tint: Color, onTap: () -> Unit) {
    Box(
        Modifier.size(38.dp).clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun TextButton(text: String, color: Color, onTap: () -> Unit) {
    Text(
        text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onTap).padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

// ------------------------------------------------------------------ emoji panel

@Composable
internal fun EmojiPanel(actions: KeyboardActions) {
    var group by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton("ABC", IosColors.text) { actions.onPanel(Panel.KEYS) }
            LazyRow(Modifier.weight(1f)) {
                itemsIndexed(EmojiData.groups) { i, g ->
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(
                                if (i == group) IosColors.key else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { group = i },
                        contentAlignment = Alignment.Center
                    ) { Text(g.first, fontSize = 20.sp) }
                }
            }
            Box(
                Modifier.size(42.dp).clickable { actions.onBackspace() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Backspace", tint = IosColors.text, modifier = Modifier.size(24.dp))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(EmojiData.groups[group].second) { e ->
                Box(
                    Modifier.height(44.dp).clickable { actions.onText(e) },
                    contentAlignment = Alignment.Center
                ) { Text(e, fontSize = 27.sp) }
            }
        }
    }
}
