package com.reganbarua.jujukeys.sticker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private val Accent = Color(0xFFA8C7FA)
private val Bg = Color(0xFF1B1B1B)
private val Card = Color(0xFF262626)
private val Sub = Color(0xFFB0B0B0)
private const val ROOT_NAME = "সব স্টিকার"

/**
 * "Sticker profiles": profiles (folders) inside profiles, any depth, hundreds are fine.
 * Make / rename / move / reorder / delete profiles, search them by name, choose which stickers a
 * profile holds and arrange them. Opened from the keyboard (hold a sticker → "প্রোফাইল…") with
 * EXTRA_STICKER it just asks which profiles that one sticker belongs to.
 */
class StickerProfilesActivity : ComponentActivity() {
    private val io = Executors.newSingleThreadExecutor()

    private var tree by mutableStateOf(ProfileTree())
    private var all by mutableStateOf<List<String>>(emptyList())
    private var current by mutableStateOf<String?>(null)
    private var assign by mutableStateOf<String?>(null)
    private var loaded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        reload()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Color(0xFF2B2B2B))) {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    val a = assign
                    if (a != null) AssignScreen(a) else BrowseScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); readIntent(intent); reload() }
    override fun onResume() { super.onResume(); reload() }      // e.g. back from adding stickers
    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    private fun readIntent(i: Intent?) {
        current = i?.getStringExtra(EXTRA_PROFILE)
        assign = i?.getStringExtra(EXTRA_STICKER)
    }

    private fun reload() = io.execute {
        StickerStore.seed(this)
        val list = StickerStore.list(this)
        val t = ProfileStore.load(this).onlyExisting(list.toHashSet())
        runOnUiThread { all = list; tree = t; if (current != null && !t.exists(current)) current = null; loaded = true }
    }

    /** Runs a change in the background and shows the result. */
    private fun change(f: (ProfileTree) -> ProfileTree) = io.execute {
        val t = ProfileStore.update(this, f).onlyExisting(all.toHashSet())
        runOnUiThread { tree = t }
    }

    private fun stickersHere(): List<String> = current?.let { tree[it]?.stickers } ?: all

    // ================================================================= browse

    @Composable
    private fun BrowseScreen() {
        var query by remember { mutableStateOf("") }
        var nameDialog by remember { mutableStateOf<Pair<String?, String>?>(null) }   // (profileId or null=new, start text)
        var moveFor by remember { mutableStateOf<String?>(null) }
        var deleteFor by remember { mutableStateOf<String?>(null) }
        var addPicker by remember { mutableStateOf(false) }
        var copyPicker by remember { mutableStateOf(false) }
        var arrange by remember { mutableStateOf(false) }
        var picked by remember { mutableStateOf<String?>(null) }
        var selecting by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var confirmDeleteStickers by remember { mutableStateOf(false) }

        fun open(id: String?) { current = id; query = ""; arrange = false; picked = null; selecting = false; selected = emptySet() }
        BackHandler(enabled = current != null || query.isNotEmpty() || selecting || arrange) {
            when {
                query.isNotEmpty() -> query = ""
                selecting -> { selecting = false; selected = emptySet() }
                arrange -> { arrange = false; picked = null }
                else -> open(tree[current]?.parent)
            }
        }

        val here = current
        val stickers = stickersHere()
        val children = tree.children(here)

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            full {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (here == null) finish() else open(tree[here]?.parent) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tree[here]?.name ?: "Sticker profiles", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tree.profiles.size} profiles · ${all.size} stickers", color = Sub, fontSize = 13.sp)
                    }
                }
            }
            full { Breadcrumb(here) { open(it) } }
            full {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search profiles by name") }, modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotBlank()) {
                val hits = tree.search(query)
                full { Text(if (hits.isEmpty()) "No profile found" else "${hits.size} found", color = Sub, fontSize = 13.sp) }
                hits.forEach { p -> full { ProfileRow(p, showPath = true, onOpen = { open(p.id) }, menu = null) } }
                return@LazyVerticalGrid
            }

            // ---- profiles inside this one
            full {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (here == null) "Profiles (${children.size})" else "Profiles inside (${children.size})", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nameDialog = null to "" }) {
                        Icon(Icons.Filled.CreateNewFolder, null, tint = Accent); Spacer(Modifier.width(6.dp)); Text("New profile", color = Accent)
                    }
                }
            }
            if (children.isEmpty()) full { Text("No profiles here yet. “New profile” makes one; profiles can hold more profiles.", color = Sub, fontSize = 13.sp) }
            children.forEachIndexed { i, p ->
                full {
                    ProfileRow(p, showPath = false, onOpen = { open(p.id) }, menu = listOf(
                        "Rename" to { nameDialog = p.id to p.name },
                        "Move to…" to { moveFor = p.id },
                    ) + (if (i > 0) listOf("Move up" to { change { it.shift(p.id, -1) } }) else emptyList()) +
                        (if (i < children.lastIndex) listOf("Move down" to { change { it.shift(p.id, +1) } }) else emptyList()) +
                        listOf("Delete" to { deleteFor = p.id }))
                }
            }

            // ---- stickers of this profile
            full {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (here == null) "$ROOT_NAME (${stickers.size})" else "Stickers in this profile (${stickers.size})",
                        color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (here != null) Chip("Choose stickers") { addPicker = true }
                        Chip("From gallery") { StickerAddActivity.start(this@StickerProfilesActivity, here) }
                        Chip(if (arrange) "Arranging — done" else "Arrange", on = arrange) { arrange = !arrange; picked = null; selecting = false; selected = emptySet() }
                        Chip(if (selecting) "Selecting — cancel" else "Select", on = selecting) { selecting = !selecting; selected = emptySet(); arrange = false; picked = null }
                    }
                    if (arrange) Text(
                        if (picked == null) "Tap a sticker, then tap where it should go." else "Now tap the sticker it should go before — or tap it again to cancel.",
                        color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                    if (picked != null) TextButton(onClick = {
                        val n = picked!!; picked = null
                        if (here == null) io.execute { StickerStore.moveBefore(this@StickerProfilesActivity, n, null); reload() } else change { it.moveSticker(here, n, null) }
                    }) { Text("Move it to the end", color = Accent) }
                }
            }
            if (stickers.isEmpty() && loaded) full {
                Text(if (here == null) "No stickers yet." else "This profile has no stickers. “Choose stickers” adds ones you already have; “From gallery” makes new ones.", color = Sub, fontSize = 13.sp)
            }
            items(stickers, key = { it }) { name ->
                val isPicked = picked == name; val isSel = name in selected
                val isCover = here != null && tree.coverOf(here) == name && tree[here]?.cover == name
                StickerTile(name, highlighted = isPicked || isSel, badge = if (isSel) "✓" else if (isCover) "Cover" else null) {
                    when {
                        arrange && picked == null -> picked = name
                        arrange && isPicked -> picked = null
                        arrange -> {
                            val n = picked!!; picked = null
                            if (here == null) io.execute { StickerStore.moveBefore(this@StickerProfilesActivity, n, name); reload() } else change { it.moveSticker(here, n, name) }
                        }
                        selecting -> selected = if (isSel) selected - name else selected + name
                        else -> startActivity(Intent(this@StickerProfilesActivity, StickerProfilesActivity::class.java).putExtra(EXTRA_STICKER, name).putExtra(EXTRA_PROFILE, here))
                    }
                }
            }
        }

        // ---- bottom bar while selecting
        if (selecting) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().background(Color(0xF2202020)).padding(12.dp)) {
                Text("${selected.size} selected", color = Color.White, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All") { selected = stickers.toSet() }
                    Chip("Add to profile…", enabled = selected.isNotEmpty()) { copyPicker = true }
                    if (here != null) {
                        Chip("Move to top", enabled = selected.isNotEmpty()) {
                            val s = stickers.filter { it in selected }; change { it.addStickers(here, s) }; selecting = false; selected = emptySet()
                        }
                        Chip("Set as cover", enabled = selected.size == 1) { val s = selected.first(); change { it.setCover(here, s) }; selecting = false; selected = emptySet() }
                        Chip("Remove from profile", enabled = selected.isNotEmpty()) {
                            val s = selected; change { t -> s.fold(t) { acc, n -> acc.removeSticker(here, n) } }; selecting = false; selected = emptySet()
                        }
                    } else Chip("Delete from phone…", enabled = selected.isNotEmpty()) { confirmDeleteStickers = true }
                }
            }
        }

        // ---- dialogs
        nameDialog?.let { (id, start) ->
            NameDialog(if (id == null) "New profile" + (here?.let { " inside “${tree[it]?.name}”" } ?: "") else "Rename profile", start,
                onDone = { name ->
                    nameDialog = null
                    if (id == null) io.execute { ProfileStore.create(this@StickerProfilesActivity, name, here); reload() } else change { it.rename(id, name) }
                }, onDismiss = { nameDialog = null })
        }
        moveFor?.let { id ->
            ProfilePickerDialog(tree, "Move “${tree[id]?.name}” into…", allowNone = true, noneLabel = "Top level",
                exclude = tree.descendants(id) + id, onPick = { to -> moveFor = null; change { it.move(id, to) } }, onDismiss = { moveFor = null })
        }
        deleteFor?.let { id ->
            val inner = tree.descendants(id).size
            AlertDialog(onDismissRequest = { deleteFor = null },
                title = { Text("Delete “${tree[id]?.name}”?") },
                text = { Text((if (inner > 0) "The $inner profile(s) inside it are deleted too. " else "") + "The stickers themselves stay on the phone (in “$ROOT_NAME” and other profiles).") },
                confirmButton = { TextButton(onClick = { deleteFor = null; change { it.delete(id) } }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
                dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } })
        }
        if (addPicker && here != null) StickerChooser(here) { addPicker = false }
        if (copyPicker) ProfilePickerDialog(tree, "Add ${selected.size} sticker(s) to…", allowNone = false, exclude = setOfNotNull(here),
            onPick = { to -> copyPicker = false; val s = stickers.filter { it in selected }; if (to != null) change { it.addStickers(to, s) }; selecting = false; selected = emptySet() },
            onDismiss = { copyPicker = false })
        if (confirmDeleteStickers) AlertDialog(onDismissRequest = { confirmDeleteStickers = false },
            title = { Text("Delete ${selected.size} sticker(s) from the phone?") },
            text = { Text("They are removed from every profile. This cannot be undone.") },
            confirmButton = { TextButton(onClick = {
                val s = selected; confirmDeleteStickers = false; selecting = false; selected = emptySet()
                io.execute { s.forEach { StickerStore.delete(this@StickerProfilesActivity, it) }; reload() }
            }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { confirmDeleteStickers = false }) { Text("Cancel") } })
    }

    /** Full-screen choice of existing stickers for a profile (ticks = already in it). */
    @Composable
    private fun StickerChooser(profile: String, close: () -> Unit) {
        var chosen by remember { mutableStateOf(tree[profile]?.stickers?.toSet().orEmpty()) }
        AlertDialog(
            onDismissRequest = close,
            title = { Text("Stickers in “${tree[profile]?.name}”") },
            text = {
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 460.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(all, key = { it }) { n ->
                        val on = n in chosen
                        StickerTile(n, highlighted = on, badge = if (on) "✓" else null) { chosen = if (on) chosen - n else chosen + n }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val before = tree[profile]?.stickers.orEmpty()
                    val added = all.filter { it in chosen && it !in before }
                    val removed = before.filter { it !in chosen }
                    change { t -> removed.fold(t.addStickers(profile, added)) { acc, n -> acc.removeSticker(profile, n) } }
                    close()
                }) { Text("Save (${chosen.size})") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
        )
    }

    // ================================================================= one sticker → its profiles

    @Composable
    private fun AssignScreen(sticker: String) {
        var chosen by remember(sticker, loaded) { mutableStateOf(tree.profilesContaining(sticker)) }
        var query by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf<String?>(null) }
        BackHandler { finish() }
        val rows = remember(tree, query) {
            if (query.isBlank()) tree.flat() else tree.search(query).map { it to 0 }
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                StickerImage(sticker, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Profiles for this sticker", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Tick every profile it should be in (${chosen.size} ticked)", color = Sub, fontSize = 13.sp)
                }
            }
            OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search profiles") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
                if (rows.isEmpty()) item { Text(if (query.isBlank()) "No profiles yet — make one below." else "No profile found", color = Sub, modifier = Modifier.padding(12.dp)) }
                items(rows, key = { it.first.id }) { (p, depth) ->
                    val on = p.id in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - p.id else chosen + p.id }.padding(start = (depth * 18).dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(on, { chosen = if (on) chosen - p.id else chosen + p.id })
                        Icon(Icons.Filled.Folder, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (query.isBlank()) p.name else tree.path(p.id).joinToString(" › ") { it.name }, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { newName = "" }, modifier = Modifier.weight(1f)) { Text("New profile", color = Accent) }
                Button(onClick = {
                    val ids = chosen
                    io.execute { ProfileStore.update(this@StickerProfilesActivity) { it.setMembership(sticker, ids) }; runOnUiThread { finish() } }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Icon(Icons.Filled.Check, null, tint = Color(0xFF062E6F)); Spacer(Modifier.width(6.dp)); Text("Save", color = Color(0xFF062E6F))
                }
            }
        }
        newName?.let { start ->
            NameDialog("New profile (top level)", start, onDone = { name ->
                newName = null
                io.execute {
                    val id = ProfileStore.create(this@StickerProfilesActivity, name, null)
                    val t = ProfileStore.load(this@StickerProfilesActivity).onlyExisting(all.toHashSet())
                    runOnUiThread { tree = t; chosen = chosen + id }
                }
            }, onDismiss = { newName = null })
        }
    }

    // ================================================================= small parts

    @Composable
    private fun Breadcrumb(id: String?, open: (String?) -> Unit) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Crumb(ROOT_NAME, last = id == null) { open(null) }
            tree.path(id).forEachIndexed { i, p ->
                Text("  ›  ", color = Sub, fontSize = 14.sp)
                Crumb(p.name, last = i == tree.path(id).lastIndex) { open(p.id) }
            }
        }
    }

    @Composable
    private fun Crumb(text: String, last: Boolean, onClick: () -> Unit) =
        Text(text, color = if (last) Color.White else Accent, fontSize = 14.sp, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp))

    @Composable
    private fun ProfileRow(p: StickerProfile, showPath: Boolean, onOpen: () -> Unit, menu: List<Pair<String, () -> Unit>>?) {
        var open by remember { mutableStateOf(false) }
        val inner = tree.children(p.id).size
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).clickable(onClick = onOpen).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cover = tree.coverOf(p.id)
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                if (cover != null) StickerImage(cover, Modifier.fillMaxSize().padding(2.dp)) else Icon(Icons.Filled.Folder, null, tint = Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (showPath) tree.path(p.id).joinToString(" › ") { it.name } else p.name, color = Color.White, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${p.stickers.size} stickers" + if (inner > 0) " · $inner profiles inside" else "", color = Sub, fontSize = 13.sp)
            }
            if (menu != null) Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Sub) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, act) -> DropdownMenuItem(text = { Text(label, color = if (label == "Delete") Color(0xFFFF6B6B) else Color.White) }, onClick = { open = false; act() }) }
                }
            }
        }
    }

    @Composable
    private fun StickerTile(name: String, highlighted: Boolean, badge: String?, onClick: () -> Unit) {
        Box(
            Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (highlighted) Color(0xFF34465E) else Card)
                .then(if (highlighted) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            StickerImage(name, Modifier.fillMaxSize().padding(4.dp))
            if (badge != null) Text(badge, color = Color(0xFF062E6F), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(CircleShape).background(Accent).padding(horizontal = 6.dp, vertical = 1.dp))
        }
    }

    @Composable
    private fun Chip(text: String, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
        Text(text, color = if (!enabled) Sub.copy(alpha = 0.5f) else if (on) Color(0xFF062E6F) else Accent, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Accent else Color.Transparent)
                .border(1.dp, if (enabled) Accent else Sub.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
    }

    private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(content: @Composable () -> Unit) =
        item(span = { GridItemSpan(maxLineSpan) }) { content() }

    companion object {
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_STICKER = "sticker"

        fun start(ctx: Context, profile: String? = null, sticker: String? = null) = ctx.startActivity(
            Intent(ctx, StickerProfilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (profile != null) putExtra(EXTRA_PROFILE, profile); if (sticker != null) putExtra(EXTRA_STICKER, sticker) }
        )
    }
}

// ===================================================================== shared with StickerAddActivity

@Composable
internal fun StickerImage(name: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { runCatching { StickerStore.thumb(ctx, name)?.asImageBitmap() }.getOrNull() }
    }
    bmp?.let { Image(it, null, modifier, contentScale = ContentScale.Fit) }
}

@Composable
internal fun NameDialog(title: String, start: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true, placeholder = { Text("Name, e.g. পরিবার") }) },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Pick one profile from the whole tree (indented), with a name search. */
@Composable
internal fun ProfilePickerDialog(
    tree: ProfileTree, title: String, allowNone: Boolean, noneLabel: String = "None",
    exclude: Set<String> = emptySet(), onPick: (String?) -> Unit, onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val rows = remember(tree, query) {
        (if (query.isBlank()) tree.flat() else tree.search(query).map { it to 0 }).filter { it.first.id !in exclude }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search profiles") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 6.dp)) {
                    if (allowNone && query.isBlank()) item {
                        Text(noneLabel, color = Accent, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 10.dp))
                        HorizontalDivider()
                    }
                    if (rows.isEmpty()) item { Text(if (tree.profiles.isEmpty()) "No profiles yet" else "No profile found", color = Sub, modifier = Modifier.padding(vertical = 10.dp)) }
                    items(rows, key = { it.first.id }) { (p, depth) ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(start = (depth * 16).dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, null, tint = Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (query.isBlank()) p.name else tree.path(p.id).joinToString(" › ") { it.name }, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
