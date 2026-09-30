package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.clipboard.ClipItem

// ------------------------------------------------------------------ suggestion bar

/** iPhone suggestion bar: A / ক switches language, three words, ⌃⌄ opens many more. */
@Composable
internal fun SuggestionBar(state: KeyboardState, actions: KeyboardActions) {
    val bangla = state.language == Language.BANGLA
    val showChip = state.prefs.showLanguageKey
    val expanded = state.panel == Panel.SUGGESTIONS
    Row(
        Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bangla && showChip) LangChip("A") { actions.onToggleLanguage() }
        val clip = state.freshClip
        if (clip != null) {
            // Just copied → one tap pastes it (like Gboard)
            Row(
                Modifier.weight(1f).padding(horizontal = 8.dp).height(34.dp)
                    .clip(RoundedCornerShape(17.dp)).background(IosColors.key)
                    .clickable { actions.onClipPaste(clip) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ContentPaste, null, tint = IosColors.text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    clip.replace('\n', ' '), color = IosColors.text, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontFamily = fontOf(clip)
                )
            }
        } else {
            val words = if (state.prefs.showSuggestions) state.suggestions else emptyList()
            for (i in 0 until 3) {
                if (i > 0 && words.isNotEmpty()) Box(Modifier.width(1.dp).height(24.dp).background(IosColors.divider))
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
                            w, color = if (i == 0) IosColors.text else IosColors.suggestion,
                            fontSize = 18.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold,
                            fontFamily = fontOf(w), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
        // ⌃⌄ = more suggestions
        Column(
            Modifier.width(30.dp).fillMaxHeight().clickable {
                actions.onPanel(if (expanded) Panel.KEYS else Panel.SUGGESTIONS)
            },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, null, tint = IosColors.dim, modifier = Modifier.size(20.dp).padding(0.dp))
            Icon(Icons.Filled.KeyboardArrowDown, "আরও সাজেশন", tint = IosColors.dim, modifier = Modifier.size(20.dp))
        }
        if (!bangla && showChip) LangChip("ক") { actions.onToggleLanguage() }
    }
}

@Composable
private fun LangChip(text: String, onTap: () -> Unit) {
    PressBox(
        Modifier.size(width = 26.dp, height = 28.dp),
        shape = RoundedCornerShape(6.dp),
        color = IosColors.chip, pressedColor = IosColors.keyPressed,
        onTap = onTap
    ) {
        Text(text, color = IosColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = fontOf(text))
    }
}

/** Many suggestions at once (the ⌃⌄ button). */
@Composable
internal fun MoreSuggestionsPanel(state: KeyboardState, actions: KeyboardActions) {
    val words = state.moreSuggestions
    if (words.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("লিখতে শুরু করুন — এখানে অনেক শব্দ দেখাবে", color = IosColors.dim, fontSize = 15.sp, fontFamily = BanglaFont)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(words) { w ->
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(8.dp)).background(IosColors.key)
                    .clickable { actions.onSuggestionWord(w) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    w, color = IosColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = fontOf(w), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
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
                .border(1.dp, IosColors.lightBlue, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val empty = state.translateInput.isEmpty()
            val shown = if (empty) "অনুবাদ করার জন্য এখানে টাইপ করুন" else state.translateInput + "│"
            Text(
                shown, color = if (empty) IosColors.dim else IosColors.text,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fontOf(shown),
                modifier = Modifier.weight(1f)
            )
            if (state.online) Status(Icons.Filled.CheckCircle, "ONLINE", Color(0xFF81C995))
            if (state.offlineReady) Status(null, "OFFLINE", IosColors.lightBlue)
            if (!state.online && !state.offlineReady) Status(Icons.Outlined.CloudOff, "NO MODEL", IosColors.dim)
        }
        if (state.translateStatus.isNotEmpty()) {
            Text(
                state.translateStatus, color = Color(0xFFFDD663), fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontFamily = fontOf(state.translateStatus),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
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
        Text(text, color = IosColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontOf(text))
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
        shape = CircleShape,
        color = IosColors.key, pressedColor = IosColors.keyPressed,
        onTap = onTap
    ) {
        Icon(icon, description, tint = IosColors.text, modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ clipboard panel (Gboard style)

/**
 * ← ক্লিপবোর্ড   [on/off] [✎ = save everything to Google Keep as one note]
 * সাম্প্রতিক / পিন করা হয়েছে — two-column tiles. Tap = paste, long-press = pin/Keep/delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClipboardPanel(state: KeyboardState, clips: List<ClipItem>, actions: KeyboardActions) {
    val enabled = state.prefs.clipboardOn
    var selected by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallIcon(Icons.AutoMirrored.Filled.ArrowBack, "ফিরে যান") { actions.onPanel(Panel.KEYS) }
            Spacer(Modifier.width(12.dp))
            Text("ক্লিপবোর্ড", color = IosColors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = BanglaFont)
            Spacer(Modifier.weight(1f))
            SmallIcon(if (enabled) Icons.Outlined.ToggleOn else Icons.Outlined.ToggleOff, "ক্লিপবোর্ড চালু/বন্ধ") {
                actions.onClipEnabled(!enabled)
            }
            Spacer(Modifier.width(8.dp))
            SmallIcon(Icons.Outlined.Edit, "সব Google Keep-এ সেভ") { actions.onClipAllToKeep() }
        }

        val sel = clips.firstOrNull { it.id == selected }
        if (sel != null) {
            // actions for the long-pressed item
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip(if (sel.pinned) "আনপিন" else "পিন", if (sel.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin) {
                    actions.onClipPin(sel.id); selected = null
                }
                ActionChip("Keep", null, IosColors.keepYellow) { actions.onClipToKeep(sel.text); selected = null }
                ActionChip("মুছুন", Icons.Filled.Delete) { actions.onClipDelete(sel.id); selected = null }
                Spacer(Modifier.weight(1f))
                ActionChip("বাতিল", null) { selected = null }
            }
        }

        if (!enabled) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("ক্লিপবোর্ড বন্ধ আছে — উপরের টগল চাপলে চালু হবে", color = IosColors.dim, fontSize = 15.sp, fontFamily = BanglaFont)
            }
        } else if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "কিছু কপি করুন — সাথে সাথে এখানে আসবে।\n✎ চাপলে সব লেখা Google Keep-এ এক পাতায় সেভ হবে।",
                    color = IosColors.dim, fontSize = 15.sp, fontFamily = BanglaFont
                )
            }
        } else {
            ClipGrid(clips, selected, actions) { selected = it }
        }
    }
}

@Composable
private fun ClipGrid(clips: List<ClipItem>, selected: Long?, actions: KeyboardActions, onSelect: (Long) -> Unit) {
        val recent = clips.filter { !it.pinned }
        val pinned = clips.filter { it.pinned }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) { SectionTitle("সাম্প্রতিক") }
                items(recent, key = { it.id }) { c -> ClipTile(c, c.id == selected, actions) { onSelect(c.id) } }
            }
            if (pinned.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) { SectionTitle("পিন করা হয়েছে") }
                items(pinned, key = { it.id }) { c -> ClipTile(c, c.id == selected, actions) { onSelect(c.id) } }
            }
        }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = IosColors.dim, fontSize = 14.sp, fontFamily = BanglaFont, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipTile(clip: ClipItem, isSelected: Boolean, actions: KeyboardActions, onLongPress: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) IosColors.keyPressed else IosColors.card)
            .combinedClickable(onClick = { actions.onClipPaste(clip.text) }, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            clip.text, color = IosColors.text, fontSize = 15.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis, fontFamily = fontOf(clip.text)
        )
        if (clip.pinned) {
            Icon(
                Icons.Filled.PushPin, null, tint = IosColors.dim,
                modifier = Modifier.size(12.dp).align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun ActionChip(text: String, icon: ImageVector?, tint: Color = IosColors.text, onTap: () -> Unit) {
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(IosColors.key)
            .clickable(onClick = onTap).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontOf(text))
    }
}

// ------------------------------------------------------------------ emoji panel

@Composable
internal fun EmojiPanel(state: KeyboardState, actions: KeyboardActions) {
    val recent = if (state.prefs.recentEmoji) state.recentEmoji else emptyList()
    val groups = if (recent.isNotEmpty()) listOf("🕘" to recent) + EmojiData.groups else EmojiData.groups
    var group by remember { mutableIntStateOf(0) }
    if (group >= groups.size) group = 0
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ABC", color = IosColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { actions.onPanel(Panel.KEYS) }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            LazyRow(Modifier.weight(1f)) {
                itemsIndexed(groups) { i, g ->
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
                Icon(Symbols.backspace, "Backspace", tint = IosColors.text, modifier = Modifier.size(24.dp))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(groups[group].second) { e ->
                Box(
                    Modifier.height(44.dp).clickable { actions.onText(e) },
                    contentAlignment = Alignment.Center
                ) { Text(e, fontSize = 27.sp) }
            }
        }
    }
}
