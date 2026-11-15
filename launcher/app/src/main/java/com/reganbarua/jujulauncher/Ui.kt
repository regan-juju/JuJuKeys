package com.reganbarua.jujulauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Glass = Color(0x33FFFFFF)
private val Panel = Color(0xE61C1C1E)
private val Label = TextStyle(color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)

// ---------------------------------------------------------------- icons
@Composable
fun AppIcon(st: LauncherState, app: AppInfo, size: Dp) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val ver = st.iconVersion
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(Icons.peek(app, px), app.key, px, ver) {
        value = Icons.peek(app, px) ?: withContext(Dispatchers.Default) { Icons.get(ctx, app, px) }
    }
    Box(Modifier.size(size)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

@Composable
fun FolderIcon(st: LauncherState, f: FolderItem, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Color(0x66FFFFFF)).padding(size * 0.1f)) {
        val mini = size * 0.25f
        val gap = size * 0.04f
        Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.align(Alignment.Center)) {
            for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until 3) {
                    val a = f.keys.getOrNull(r * 3 + c)?.let { st.byKey[it] }
                    if (a != null) AppIcon(st, a, mini) else Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- state holders
private data class DragInfo(val item: Item, val pos: Offset, val size: IntSize)

private class MenuOption(val title: String, val action: () -> Unit)

@Composable
private fun OptionsMenu(expanded: Boolean, onDismiss: () -> Unit, options: List<MenuOption>) {
    DropdownMenu(expanded, onDismiss, containerColor = Color(0xF02C2C2E), shape = RoundedCornerShape(14.dp)) {
        options.forEach { o ->
            DropdownMenuItem(
                text = { Text(o.title, color = if (o.title.startsWith("Uninstall") || o.title.startsWith("Remove")) Color(0xFFFF6961) else Color.White, fontSize = 15.sp) },
                onClick = { onDismiss(); o.action() },
            )
        }
    }
}

// ---------------------------------------------------------------- root
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(st: LauncherState) {
    val layout = st.layout
    val homePages = layout.pages.size
    val pager = rememberPagerState(pageCount = { homePages + 1 })
    var edit by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(IntSize(1, 1)) }
    var dockTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var dockLeft by remember { mutableFloatStateOf(0f) }
    var dockWidth by remember { mutableFloatStateOf(1f) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(st.homeSignal) {
        if (st.homeSignal > 0) {
            edit = false; openFolder = null; showSearch = false; showSettings = false; menuFor = null
            pager.animateScrollToPage(0)
        }
    }
    LaunchedEffect(st.message) { if (st.message != null) { delay(2200); st.message = null } }
    LaunchedEffect(homePages) { if (pager.currentPage > homePages) pager.scrollToPage(homePages) }

    BackHandler(enabled = openFolder != null || showSearch || showSettings || edit) {
        when {
            showSettings -> showSettings = false
            showSearch -> showSearch = false
            openFolder != null -> openFolder = null
            edit -> edit = false
        }
    }

    // edge auto-scroll while dragging
    val dragX = drag?.let { it.pos.x + it.size.width / 2f }
    LaunchedEffect(dragX != null, dragX?.let { (it / 120f).toInt() }) {
        val x = dragX ?: return@LaunchedEffect
        val w = gridSize.width.toFloat() + gridOrigin.x * 2
        delay(550)
        if (drag == null) return@LaunchedEffect
        if (x > w - 50 && pager.currentPage < homePages - 1) pager.animateScrollToPage(pager.currentPage + 1)
        else if (x < 50 && pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1)
    }

    fun finishDrag() {
        val d = drag ?: return
        drag = null
        val center = d.pos + Offset(d.size.width / 2f, d.size.height / 2f)
        val cellW = gridSize.width / Ops.COLS.toFloat()
        val cellH = gridSize.height / Ops.ROWS.toFloat()
        if (center.y > dockTop - 10f && d.item is AppItem) {
            val idx = ((center.x - dockLeft) / (dockWidth / Ops.DOCK)).toInt().coerceIn(0, Ops.DOCK - 1)
            st.setLayout(Ops.toDock(st.layout, d.item.id, idx)); return
        }
        val page = pager.currentPage.coerceAtMost(homePages - 1)
        val lx = center.x - gridOrigin.x; val ly = center.y - gridOrigin.y
        val col = (lx / cellW).toInt().coerceIn(0, Ops.COLS - 1)
        val row = (ly / cellH).toInt().coerceIn(0, Ops.ROWS - 1)
        val idx = row * Ops.COLS + col
        val shown = st.layout.pages[page]
        val target = shown.getOrNull(idx)
        val cx = gridOrigin.x + col * cellW + cellW / 2; val cy = gridOrigin.y + row * cellH + cellH / 2
        val near = kotlin.math.abs(center.x - cx) < cellW * 0.28f && kotlin.math.abs(center.y - cy) < cellH * 0.28f
        val merge = if (target != null && target.id != d.item.id && near && d.item is AppItem) target.id else null
        st.setLayout(Ops.drop(st.layout, d.item, page, idx, merge))
    }

    Box(Modifier.fillMaxSize()) {
        // wallpaper
        st.wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        // soft dark veil so labels stay readable on bright wallpapers
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x40000000), Color(0x00000000), Color(0x55000000)))))

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            // top bar (edit mode)
            Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp)) {
                if (edit) {
                    Pill("Settings", Modifier.align(Alignment.CenterStart)) { showSettings = true }
                    Pill("Done", Modifier.align(Alignment.CenterEnd), bold = true) { edit = false }
                }
            }
            // pages
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { gridOrigin = it.positionInRoot(); gridSize = it.size }
                    .pointerInput(edit) {
                        if (!edit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total > 140f) showSearch = true },
                                onDragCancel = {},
                            ) { _, dy -> total += dy }
                        }
                    }
            ) {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 6, userScrollEnabled = drag == null) { p ->
                    if (p < homePages) {
                        PageGrid(
                            st, layout.pages[p], edit, drag?.item?.id, menuFor,
                            onMenu = { menuFor = it },
                            onEdit = { edit = true },
                            onOpenFolder = { openFolder = it },
                            onDragStart = { item, pos, size -> drag = DragInfo(item, pos, size) },
                            onDrag = { delta -> drag = drag?.copy(pos = drag!!.pos + delta) },
                            onDragEnd = { finishDrag() },
                        )
                    } else {
                        LibraryPage(st, onMenu = { menuFor = it }, menuFor = menuFor)
                    }
                }
            }
            // page dots
            Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..homePages) {
                    val on = pager.currentPage == i
                    Box(Modifier.padding(horizontal = 4.dp).size(7.dp).clip(CircleShape).background(if (on) Color.White else Color(0x66FFFFFF)))
                }
            }
            // search pill
            Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x40000000)).clickable { showSearch = true }
                        .padding(horizontal = 26.dp, vertical = 7.dp)
                ) { Text("Search", color = Color.White, fontSize = 13.sp) }
            }
            // dock
            Dock(st, edit, drag?.item?.id, menuFor, { menuFor = it }, { edit = true },
                onPlaced = { top, left, w -> dockTop = top; dockLeft = left; dockWidth = w },
                onDragStart = { item, pos, size -> drag = DragInfo(item, pos, size) },
                onDrag = { delta -> drag = drag?.copy(pos = drag!!.pos + delta) },
                onDragEnd = { finishDrag() })
            Spacer(Modifier.height(6.dp))
        }

        // dragged item
        drag?.let { d ->
            val density = LocalDensity.current
            Box(Modifier.offset { IntOffset(d.pos.x.toInt(), d.pos.y.toInt()) }
                .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
                .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.95f }) {
                CellContent(st, d.item, showLabel = st.showLabels)
            }
        }

        // overlays
        openFolder?.let { id ->
            val f = layout.pages.flatten().firstOrNull { it.id == id } as? FolderItem
            if (f == null) openFolder = null else FolderOverlay(st, f, onClose = { openFolder = null })
        }
        if (showSearch) SearchOverlay(st, onClose = { showSearch = false })
        if (showSettings) SettingsOverlay(st, onClose = { showSettings = false })

        st.message?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp).clip(RoundedCornerShape(50)).background(Color(0xE62C2C2E)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(it, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, bold: Boolean = false, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- cell + grid
@Composable
private fun CellContent(st: LauncherState, item: Item, showLabel: Boolean) {
    val iconSize = 58.dp * st.iconScale
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (item) {
            is AppItem -> st.byKey[item.key]?.let { a ->
                AppIcon(st, a, iconSize)
                if (showLabel) { Spacer(Modifier.height(4.dp)); Text(a.label, style = Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp)) }
            }
            is FolderItem -> {
                FolderIcon(st, item, iconSize)
                if (showLabel) { Spacer(Modifier.height(4.dp)); Text(item.name, style = Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp)) }
            }
        }
    }
}

@Composable
private fun jiggle(on: Boolean, phase: Int): Float {
    if (!on) return 0f
    val t = rememberInfiniteTransition(label = "j")
    val v by t.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(110 + (phase % 4) * 14, easing = LinearEasing), RepeatMode.Reverse), label = "r")
    return v
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageGrid(
    st: LauncherState, items: List<Item>, edit: Boolean, draggingId: String?, menuFor: String?,
    onMenu: (String?) -> Unit, onEdit: () -> Unit, onOpenFolder: (String) -> Unit,
    onDragStart: (Item, Offset, IntSize) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = maxWidth / Ops.COLS
        val ch = maxHeight / Ops.ROWS
        val density = LocalDensity.current
        items.forEachIndexed { i, item ->
            key(item.id) {
                val target = IntOffset(with(density) { (cw * (i % Ops.COLS)).roundToPx() }, with(density) { (ch * (i / Ops.COLS)).roundToPx() })
                val off by animateIntOffsetAsState(target, tween(180), label = "o")
                var rootPos by remember { mutableStateOf(Offset.Zero) }
                var cellSize by remember { mutableStateOf(IntSize.Zero) }
                val rot = jiggle(edit && draggingId != item.id, i)
                Box(
                    Modifier.offset { off }.size(cw, ch)
                        .onGloballyPositioned { rootPos = it.positionInRoot(); cellSize = it.size }
                        .graphicsLayer { rotationZ = rot; alpha = if (draggingId == item.id) 0f else 1f }
                        .then(
                            if (edit) Modifier
                                .pointerInput(item.id) { detectTapGestures(onTap = { if (item is FolderItem) onOpenFolder(item.id) }) }
                                .pointerInput(item.id) {
                                    var cur = Offset.Zero
                                    detectDragGestures(
                                        onDragStart = { cur = rootPos; onDragStart(item, rootPos, cellSize) },
                                        onDragEnd = onDragEnd, onDragCancel = onDragEnd,
                                    ) { change, amt -> change.consume(); onDrag(amt) }
                                }
                            else Modifier.combinedClickable(
                                onClick = {
                                    when (item) {
                                        is AppItem -> st.byKey[item.key]?.let { st.launch(it) }
                                        is FolderItem -> onOpenFolder(item.id)
                                    }
                                },
                                onLongClick = { onMenu(item.id) },
                            )
                        )
                ) {
                    CellContent(st, item, st.showLabels)
                    if (edit && item is AppItem) {
                        Box(
                            Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 6.dp).size(22.dp).clip(CircleShape)
                                .background(Color(0xFF8E8E93)).clickable { st.setLayout(Ops.hide(st.layout, item.key)) },
                            contentAlignment = Alignment.Center,
                        ) { Text("–", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    ItemMenu(st, item, menuFor == item.id, onEdit) { onMenu(null) }
                }
            }
        }
    }
}

@Composable
private fun ItemMenu(st: LauncherState, item: Item, open: Boolean, onEdit: () -> Unit, dismiss: () -> Unit) {
    if (!open) return
    val opts = mutableListOf(MenuOption("Edit Home Screen", onEdit))
    when (item) {
        is AppItem -> st.byKey[item.key]?.let { a ->
            opts += MenuOption("Change Icon") { st.pickIconFor(a.pkg) }
            opts += MenuOption("App Info") { st.appInfo(a) }
            opts += MenuOption("Remove from Home Screen") { st.setLayout(Ops.hide(st.layout, item.key)) }
            opts += MenuOption("Uninstall") { st.uninstall(a) }
        }
        is FolderItem -> opts += MenuOption("Rename") { /* handled in folder view */ }
    }
    OptionsMenu(true, dismiss, opts)
}

// ---------------------------------------------------------------- dock
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    st: LauncherState, edit: Boolean, draggingId: String?, menuFor: String?, onMenu: (String?) -> Unit, onEdit: () -> Unit,
    onPlaced: (Float, Float, Float) -> Unit,
    onDragStart: (Item, Offset, IntSize) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(30.dp)).background(Color(0x40FFFFFF))
                .onGloballyPositioned { val p = it.positionInRoot(); onPlaced(p.y, p.x, it.size.width.toFloat()) },
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            st.layout.dock.forEachIndexed { i, key ->
                val a = st.byKey[key] ?: return@forEachIndexed
                key(key) {
                    var rootPos by remember { mutableStateOf(Offset.Zero) }
                    var cellSize by remember { mutableStateOf(IntSize.Zero) }
                    val rot = jiggle(edit && draggingId != key, i)
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .onGloballyPositioned { rootPos = it.positionInRoot(); cellSize = it.size }
                            .graphicsLayer { rotationZ = rot; alpha = if (draggingId == key) 0f else 1f }
                            .then(
                                if (edit) Modifier.pointerInput(key) {
                                    detectDragGestures(
                                        onDragStart = { onDragStart(AppItem(key), rootPos, cellSize) },
                                        onDragEnd = onDragEnd, onDragCancel = onDragEnd,
                                    ) { change, amt -> change.consume(); onDrag(amt) }
                                } else Modifier.combinedClickable(onClick = { st.launch(a) }, onLongClick = { onMenu("dock:$key") })
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(st, a, 58.dp * st.iconScale)
                        if (menuFor == "dock:$key") OptionsMenu(true, { onMenu(null) }, listOf(
                            MenuOption("Edit Home Screen", onEdit),
                            MenuOption("Change Icon") { st.pickIconFor(a.pkg) },
                            MenuOption("App Info") { st.appInfo(a) },
                            MenuOption("Remove from Dock") { st.setLayout(Ops.addToHome(st.layout.copy(dock = st.layout.dock - key), key)) },
                        ))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- app library
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPage(st: LauncherState, onMenu: (String?) -> Unit, menuFor: String?) {
    var query by remember { mutableStateOf("") }
    var openCat by remember { mutableStateOf<String?>(null) }
    val groups = remember(st.apps) { st.apps.groupBy { st.categoryName(it.category) }.toList().sortedBy { it.first } }
    BackHandler(enabled = openCat != null) { openCat = null }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        SearchField(query, "App Library", { query = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Spacer(Modifier.height(6.dp))
        val list = when {
            query.isNotBlank() -> st.apps.filter { it.label.contains(query, true) }
            openCat != null -> groups.firstOrNull { it.first == openCat }?.second ?: emptyList()
            else -> null
        }
        if (list != null) {
            if (openCat != null && query.isBlank()) Text("‹ ${openCat}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { openCat = null }.padding(vertical = 8.dp))
            AppRows(st, list, onMenu, menuFor)
        } else {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(groups.chunked(2).size) { r ->
                    val row = groups.chunked(2)[r]
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (name, apps) ->
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x59FFFFFF)).clickable { openCat = name }.padding(12.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        for (rr in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                            for (cc in 0 until 2) {
                                                val a = apps.getOrNull(rr * 2 + cc)
                                                Box(Modifier.weight(1f).aspectRatio(1f)) { if (a != null) AppIcon(st, a, 54.dp) }
                                            }
                                        }
                                    }
                                }
                                Text(name, style = Label.copy(fontSize = 12.sp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRows(st: LauncherState, list: List<AppInfo>, onMenu: (String?) -> Unit, menuFor: String?) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        items(list.size) { i ->
            val a = list[i]
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { st.launch(a) }, onLongClick = { onMenu("lib:${a.key}") }).padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(st, a, 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (menuFor == "lib:${a.key}") OptionsMenu(true, { onMenu(null) }, listOf(
                    MenuOption("Add to Home Screen") { st.setLayout(Ops.addToHome(st.layout, a.key)) },
                    MenuOption("Change Icon") { st.pickIconFor(a.pkg) },
                    MenuOption("App Info") { st.appInfo(a) },
                    MenuOption("Uninstall") { st.uninstall(a) },
                ))
            }
        }
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, focus: FocusRequester? = null) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(hint, color = Color(0xB3FFFFFF), fontSize = 16.sp)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        )
    }
}

// ---------------------------------------------------------------- folder
@Composable
private fun FolderOverlay(st: LauncherState, f: FolderItem, onClose: () -> Unit) {
    var name by remember(f.id) { mutableStateOf(f.name) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
        st.setLayout(Ops.rename(st.layout, f.id, name.ifBlank { "Folder" })); onClose()
    }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xCC3A3A3C)).padding(20.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTextField(name, { name = it.take(24) }, singleLine = true, cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
            val apps = f.keys.mapNotNull { st.byKey[it] }
            apps.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { a ->
                        Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.combinedClickableCompat(onClick = { onClose(); st.launch(a) }, onLong = { menuKey = a.key }),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(st, a, 58.dp * st.iconScale)
                                Spacer(Modifier.height(4.dp))
                                Text(a.label, style = Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 78.dp))
                            }
                            if (menuKey == a.key) OptionsMenu(true, { menuKey = null }, listOf(
                                MenuOption("Remove from Folder") { st.setLayout(Ops.leaveFolder(st.layout, f.id, a.key)); if (f.keys.size <= 2) onClose() },
                                MenuOption("Change Icon") { st.pickIconFor(a.pkg) },
                                MenuOption("App Info") { st.appInfo(a) },
                            ))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLong: () -> Unit) = this.combinedClickable(onClick = onClick, onLongClick = onLong)

// ---------------------------------------------------------------- spotlight search
@Composable
private fun SearchOverlay(st: LauncherState, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val res = if (q.isBlank()) emptyList() else st.apps.filter { it.label.contains(q, true) }.sortedBy { !it.label.startsWith(q, true) }.take(30)
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).windowInsetsPadding(WindowInsets.ime).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(q, "Search", { q = it }, Modifier.weight(1f), fr)
                Spacer(Modifier.width(12.dp))
                Text("Cancel", color = Color(0xFF4DA3FF), fontSize = 16.sp, modifier = Modifier.clickable { onClose() })
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                items(res.size) { i ->
                    val a = res[i]
                    Row(Modifier.fillMaxWidth().clickable { onClose(); st.launch(a) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(st, a, 44.dp); Spacer(Modifier.width(14.dp))
                        Text(a.label, color = Color.White, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- settings
@Composable
private fun SettingsOverlay(st: LauncherState, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF000000)).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Done", color = Color(0xFF4DA3FF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onClose() })
        }
        androidx.compose.foundation.rememberScrollState().let { sc ->
            Column(Modifier.weight(1f).verticalScroll(sc).padding(horizontal = 16.dp)) {
                Group("Wallpaper") {
                    Row2("Choose picture") { st.pickWallpaper() }
                    Row2("Use system wallpaper") { st.resetWallpaper() }
                }
                Group("Icons") {
                    Row2("Add icons (PNG files or a ZIP)") { st.pickIcons() }
                    Row2("Custom icons in use: ${st.customIconCount()}") {}
                    Row2("Reset all custom icons") { st.resetIcons() }
                }
                Group("Home Screen") {
                    Column(Modifier.padding(14.dp)) {
                        Text("Icon size", color = Color.White, fontSize = 16.sp)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Small" to 0.85f, "Medium" to 1f, "Large" to 1.12f).forEach { (n, v) ->
                                val on = kotlin.math.abs(st.iconScale - v) < 0.01f
                                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF0A84FF) else Color(0xFF3A3A3C)).clickable { st.updateIconScale(v) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(n, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    Row2(if (st.showLabels) "Names under icons: On" else "Names under icons: Off") { st.updateLabels(!st.showLabels) }
                    Row2("Reset Home Screen layout") { st.resetLayout(); onClose() }
                }
                Group("Phone") {
                    Row2("Make JuJu Launcher the default home app") { st.defaultHomeSettings() }
                    Text("To go back: Settings › Apps › Default apps › Home app → choose your old launcher.",
                        color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
                Text("Status bar, battery icon and lock screen belong to the phone system, not to a launcher. They need a MIUI theme.",
                    color = Color(0xFF8E8E93), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = Color(0xFF8E8E93), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1E)), content = content)
}

@Composable
private fun Row2(text: String, onClick: () -> Unit) {
    Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp))
}
