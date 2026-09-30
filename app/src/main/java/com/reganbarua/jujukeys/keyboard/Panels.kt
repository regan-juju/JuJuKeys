package com.reganbarua.jujukeys.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

// ------------------------------------------------------------------ top bar

/**
 * Top bar:  [ক / A]   suggestion  suggestion  suggestion   [⟵]
 *  • ক / A shows the language in use; tap = switch language.
 *  • Hold ক / A → the Keep clipboard and Translate buttons slide out (left → right).
 *  • ⟵ = backspace (hold to keep deleting), faded like a computer's Backspace key.
 */
@Composable
internal fun SuggestionBar(state: KeyboardState, actions: KeyboardActions, forceSuggestions: Boolean = false) {
    val bangla = state.language == Language.BANGLA
    var toolsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(toolsOpen) { if (toolsOpen) { delay(6000); toolsOpen = false } }
    val toolsVisible = remember { MutableTransitionState(false) }
    toolsVisible.targetState = toolsOpen

    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressBox(
            Modifier.size(width = 44.dp, height = 38.dp)
                .then(if (toolsOpen) Modifier.border(1.5.dp, IosColors.text.copy(alpha = 0.55f), RoundedCornerShape(10.dp)) else Modifier),
            shape = RoundedCornerShape(10.dp),
            color = IosColors.key, pressedColor = IosColors.keyPressed,
            onTap = { toolsOpen = false; actions.onToggleLanguage() },
            onLongPress = { toolsOpen = !toolsOpen }
        ) {
            Text(
                if (bangla) "ক" else "A", color = IosColors.text, fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = if (bangla) BanglaFont else null
            )
        }

        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (!toolsOpen) {
                val clip = state.freshClip
                if (clip != null && !forceSuggestions) {
                    // Just copied → one tap pastes it (like Gboard)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(34.dp)
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
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until 3) {
                            val w = words.getOrNull(i)
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .then(if (w != null) Modifier.clickable { actions.onSuggestion(i) } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (w != null) Text(
                                    w, color = if (i == 0) IosColors.text else IosColors.suggestion,
                                    fontSize = 17.5.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold,
                                    fontFamily = fontOf(w), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visibleState = toolsVisible,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    ToolButton(state.panel == Panel.CLIPBOARD, {
                        toolsOpen = false
                        actions.onPanel(if (state.panel == Panel.CLIPBOARD) Panel.KEYS else Panel.CLIPBOARD)
                    }) { KeepIcon() }
                    Spacer(Modifier.width(8.dp))
                    ToolButton(state.translateOn, { toolsOpen = false; actions.onTranslateToggle() }) {
                        Icon(Symbols.translate, "অনুবাদ", tint = IosColors.text, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }
        BackKey(actions)
    }
}

/** ⟵ Backspace in the top bar: faded long arrow, hold to keep deleting. */
@Composable
internal fun BackKey(actions: KeyboardActions) {
    PressBox(
        Modifier.size(width = 62.dp, height = 38.dp),
        shape = RoundedCornerShape(10.dp),
        color = IosColors.key, pressedColor = IosColors.keyPressed,
        onTap = { actions.onBackspace() },
        repeating = true,
        kind = KeyKind.DELETE
    ) {
        Icon(Symbols.longBack, "Backspace", tint = IosColors.text.copy(alpha = 0.5f), modifier = Modifier.size(width = 36.dp, height = 18.dp))
    }
}

@Composable
private fun KeepIcon() {
    Box(Modifier.size(24.dp)) {
        Icon(Symbols.clipboard, "Keep ক্লিপবোর্ড", tint = IosColors.text, modifier = Modifier.size(24.dp))
        Box(
            Modifier.align(Alignment.BottomEnd).size(12.dp)
                .background(IosColors.keepYellow, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Symbols.lightbulb, null, tint = Color.Black, modifier = Modifier.size(9.dp)) }
    }
}

@Composable
private fun ToolButton(active: Boolean, onTap: () -> Unit, content: @Composable () -> Unit) {
    PressBox(
        Modifier.size(width = 48.dp, height = 38.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (active) IosColors.blue.copy(alpha = 0.45f) else IosColors.key,
        pressedColor = IosColors.keyPressed,
        onTap = onTap
    ) { content() }
}

/** Many suggestions at once (the ⌃⌄ button). */
@Composable
internal fun MoreSuggestionsPanel(state: KeyboardState, actions: KeyboardActions) {
    val words = state.moreSuggestions
    val sheet = Modifier.fillMaxSize()
        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        .background(IosColors.panel)
        .border(0.7.dp, IosColors.glassEdge, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    if (words.isEmpty()) {
        Box(sheet, contentAlignment = Alignment.Center) {
            Text("লিখতে শুরু করুন — এখানে অনেক শব্দ দেখাবে", color = IosColors.dim, fontSize = 15.sp, fontFamily = BanglaFont)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = sheet,
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
    // Fixed height: the keyboard never grows/shrinks while translating (that made the app
    // behind re-layout on every result and felt slow).
    Column(Modifier.fillMaxWidth().height(102.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            LangPill(if (fromBangla) "বাংলা" else "ENGLISH")
            Icon(
                Icons.Filled.SwapHoriz, "উল্টাও", tint = IosColors.text,
                modifier = Modifier.padding(horizontal = 6.dp).size(30.dp).clickable { actions.onTranslateSwap() }
            )
            LangPill(if (fromBangla) "ENGLISH" else "বাংলা")
            // short status (downloading / error) sits here, so nothing changes size
            Text(
                state.translateStatus, color = Color(0xFFFDD663), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontFamily = fontOf(state.translateStatus),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
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

// ------------------------------------------------------------------ emoji

/** Emoji top bar: "ইমোজি খুঁজুন" search box + ⟵. */
@Composable
internal fun EmojiTopBar(actions: KeyboardActions) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(IosColors.chip.copy(alpha = 0.35f))
                .clickable { actions.onEmojiSearch(true) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Symbols.search, null, tint = IosColors.dim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("ইমোজি খুঁজুন (English)", color = IosColors.dim, fontSize = 15.sp, fontFamily = BanglaFont)
        }
        Spacer(Modifier.width(8.dp))
        BackKey(actions)
    }
}

/** While searching: [🔍 query ✕] [results …] [⟵] — the letter keys type into the search. */
@Composable
internal fun EmojiSearchBar(state: KeyboardState, actions: KeyboardActions) {
    val q = state.emojiSearch ?: ""
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.width(128.dp).height(36.dp).clip(RoundedCornerShape(18.dp))
                .background(IosColors.chip.copy(alpha = 0.35f)).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Symbols.search, null, tint = IosColors.dim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (q.isEmpty()) "খুঁজুন…" else "$q│", color = if (q.isEmpty()) IosColors.dim else IosColors.text,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fontOf(if (q.isEmpty()) "খ" else q),
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable { actions.onEmojiSearch(false) },
                contentAlignment = Alignment.Center
            ) { Icon(Symbols.close, "বন্ধ", tint = IosColors.dim, modifier = Modifier.size(18.dp)) }
        }
        LazyRow(Modifier.weight(1f).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            items(state.emojiResults) { e ->
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable { actions.onText(e) },
                    contentAlignment = Alignment.Center
                ) { Text(e, fontSize = 25.sp) }
            }
        }
        BackKey(actions)
    }
}

private class EmojiSection(val title: String, val icon: ImageVector, val items: List<EmojiItem>)

private val CategoryIcons: List<ImageVector> by lazy {
    listOf(
        Symbols.catSmileys, Symbols.catPeople, Symbols.catAnimals, Symbols.catFood, Symbols.catTravel,
        Symbols.catActivities, Symbols.catObjects, Symbols.catSymbols, Symbols.catFlags,
    )
}

/**
 * Every emoji (like the iPhone keyboard), one scrolling list with a heading per group.
 * Bottom row: ABC and the group icons (tap to jump). Hold an emoji → skin tones.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EmojiPanel(state: KeyboardState, actions: KeyboardActions) {
    @Suppress("UNUSED_VARIABLE") val loaded = state.emojiLoaded      // re-read when the list arrives
    val recent = if (state.prefs.recentEmoji) state.recentEmoji else emptyList()
    val sections = remember(recent, EmojiRepo.groups) {
        buildList {
            if (recent.isNotEmpty()) add(EmojiSection("সাম্প্রতিক", Symbols.catRecent, recent.map { EmojiItem(it, "", emptyList()) }))
            EmojiRepo.groups.forEachIndexed { i, g -> add(EmojiSection(g.name, CategoryIcons.getOrElse(i) { Symbols.catSymbols }, g.items)) }
        }
    }
    // index of each heading in the grid
    val headerIndex = remember(sections) {
        var n = 0
        sections.map { sec -> n.also { n += 1 + sec.items.size } }
    }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val current by remember(headerIndex) {
        derivedStateOf {
            val first = grid.firstVisibleItemIndex
            headerIndex.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    var tonePick by remember { mutableStateOf<EmojiItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (sections.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("ইমোজি লোড হচ্ছে…", color = IosColors.dim, fontSize = 15.sp, fontFamily = BanglaFont)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    state = grid,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    sections.forEach { sec ->
                        item(span = { GridItemSpan(8) }, contentType = 1) {
                            Text(
                                sec.title, color = IosColors.dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                fontFamily = BanglaFont, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(sec.items, contentType = { 2 }) { e ->
                            Box(
                                Modifier.height(42.dp).combinedClickable(
                                    onClick = { actions.onText(e.emoji) },
                                    onLongClick = { if (e.alternates.isNotEmpty()) tonePick = e }
                                ),
                                contentAlignment = Alignment.Center
                            ) { Text(e.emoji, fontSize = 27.sp) }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(IosColors.key)
                        .clickable { actions.onPanel(Panel.KEYS) }.padding(horizontal = 9.dp, vertical = 5.dp)
                ) { Text("ABC", color = IosColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Row(
                    Modifier.weight(1f).padding(start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sections.forEachIndexed { i, sec ->
                        val on = i == current
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (on) IosColors.key else Color.Transparent)
                                .clickable { scope.launch { grid.scrollToItem(headerIndex[i]) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(sec.icon, sec.title, tint = if (on) IosColors.text else IosColors.dim, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
        // skin-tone choice (hold an emoji)
        tonePick?.let { item ->
            Box(Modifier.fillMaxSize().clickable { tonePick = null })
            LazyRow(
                Modifier.align(Alignment.TopCenter).padding(8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)).background(IosColors.panel)
                    .border(0.7.dp, IosColors.glassEdge, RoundedCornerShape(14.dp)).padding(4.dp)
            ) {
                items(item.alternates) { alt ->
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                            .clickable { actions.onText(alt); tonePick = null },
                        contentAlignment = Alignment.Center
                    ) { Text(alt, fontSize = 28.sp) }
                }
            }
        }
    }
}
