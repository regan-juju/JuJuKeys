package com.reganbarua.jujukeys.textrep

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reganbarua.jujukeys.security.AuthActivity
import com.reganbarua.jujukeys.security.SensitiveGate
import kotlinx.coroutines.launch

private val Bg = Color(0xFF000000)
private val Card = Color(0xFF1C1C1E)
private val Sep = Color(0xFF38383A)
private val Blue = Color(0xFF0A84FF)
private val Gray = Color(0xFF8E8E93)
private val Red = Color(0xFFFF453A)
private val Green = Color(0xFF30D158)
private val White = Color(0xFFFFFFFF)

/**
 * iPhone-style "Text Replacement" screen (opened by the omw↓ button): a list with search, A–Z
 * sections and Edit / ＋, and an Add / Edit sheet with Phrase, Shortcut and a Password switch.
 * Password entries are masked here; opening one asks for the phone lock (fingerprint / PIN) first.
 */
class TextReplaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ReplaceStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue, surface = Card, background = Bg)) {
                TextReplaceScreen(store, onClose = { finish() }, setSecure = { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                })
            }
        }
    }
}

/** Open Add / Edit sheet. [id] null = new. [phrase] is the visible text (a saved password only after the lock). */
private class Sheet(val id: Long?, shortcut: String, phrase: String, password: Boolean, val hadPassword: Boolean) {
    var shortcut by mutableStateOf(shortcut)
    var phrase by mutableStateOf(phrase)
    var password by mutableStateOf(password)
    var reveal by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

private sealed class Row2 {
    class Head(val letter: Char) : Row2()
    class Entry(val rep: Rep) : Row2()
}

@Composable
private fun TextReplaceScreen(store: ReplaceStore, onClose: () -> Unit, setSecure: (Boolean) -> Unit) {
    val context = LocalContext.current
    val reps: SnapshotStateList<Rep> = remember { mutableStateListOf<Rep>().also { it.addAll(store.load()) } }
    var query by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var waitingFor by remember { mutableStateOf<Long?>(null) }     // password entry waiting for the phone lock

    fun reload() { reps.clear(); reps.addAll(store.load()) }

    // back from the lock screen: open the password entry (only if it was really unlocked)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                val id = waitingFor
                if (id != null) {
                    waitingFor = null
                    val rep = reps.firstOrNull { it.id == id }
                    if (rep != null && SensitiveGate.isUnlocked()) {
                        val plain = store.reveal(rep)
                        if (plain != null) sheet = Sheet(rep.id, rep.shortcut, plain, true, true)
                        else Toast.makeText(context, "Could not read this password entry", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val s = sheet
    LaunchedEffect(s?.password, s != null) { setSecure(s != null && s.password) }

    fun openRep(rep: Rep) {
        if (!rep.password) { sheet = Sheet(rep.id, rep.shortcut, rep.phrase, false, false); return }
        if (SensitiveGate.isUnlocked()) {
            val plain = store.reveal(rep)
            if (plain != null) sheet = Sheet(rep.id, rep.shortcut, plain, true, true)
            else Toast.makeText(context, "Could not read this password entry", Toast.LENGTH_LONG).show()
        } else { waitingFor = rep.id; AuthActivity.start(context) }
    }

    fun save(sh: Sheet) {
        val phrase: String? = if (sh.password && sh.hadPassword && sh.phrase.isEmpty()) null else sh.phrase
        when (store.save(sh.id, sh.shortcut, phrase, sh.password)) {
            ReplaceStore.Result.Ok -> { reload(); sheet = null }
            ReplaceStore.Result.BadShortcut -> sh.error = "Shortcut: at least 2 characters, no spaces"
            ReplaceStore.Result.Duplicate -> sh.error = "This shortcut is already used"
            ReplaceStore.Result.EmptyPhrase -> sh.error = "Phrase is empty"
            ReplaceStore.Result.CryptoFailed -> sh.error = "Could not encrypt it on this phone — not saved"
        }
    }

    BackHandler(enabled = sheet != null) { sheet = null }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        val sh = sheet
        if (sh == null) {
            // ---- top bar: ‹  Edit | title | ＋
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Blue, modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onClose() }.padding(8.dp))
                Text(if (editMode) "Done" else "Edit", color = Blue, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editMode = !editMode }.padding(horizontal = 8.dp, vertical = 8.dp))
                Spacer(Modifier.weight(1f))
                Text("Text Replacement", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("＋", color = Blue, fontSize = 30.sp,
                    modifier = Modifier.clip(CircleShape).clickable { sheet = Sheet(null, "", "", false, false) }.padding(horizontal = 12.dp))
            }
            // ---- search
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(38.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = Gray, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = Gray, fontSize = 16.sp)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Blue), modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            // ---- list with A–Z sections
            val shown = remember(reps.toList(), query) {
                val q = query.trim()
                reps.filter { r -> q.isEmpty() || r.shortcut.contains(q, true) || (!r.password && r.phrase.contains(q, true)) }
            }
            val rows: List<Row2> = remember(shown) {
                val out = ArrayList<Row2>()
                shown.groupBy { if (it.password) '#' else ReplaceMatcher.sectionOf(it.phrase) }
                    .toSortedMap(compareBy<Char> { if (it == '#') '￿' else it })
                    .forEach { (l, list) ->
                        out.add(Row2.Head(l))
                        list.sortedBy { it.phrase.lowercase() }.forEach { out.add(Row2.Entry(it)) }
                    }
                out
            }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) {
                    Text(
                        if (reps.isEmpty()) "No text replacements yet.\nTap ＋ to add one." else "No match",
                        color = Gray, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 28.dp)) {
                    items(rows) { r ->
                        when (r) {
                            is Row2.Head -> Text(r.letter.toString(), color = Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
                            is Row2.Entry -> EntryRow(r.rep, editMode, onOpen = { openRep(r.rep) }, onDelete = { store.delete(r.rep.id); reload() })
                        }
                    }
                }
                // A–Z index on the right
                val letters = rows.filterIsInstance<Row2.Head>().map { it.letter }
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    letters.forEach { l ->
                        Text(l.toString(), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                val idx = rows.indexOfFirst { it is Row2.Head && it.letter == l }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            }.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Text(
                "Type the shortcut, then space, and the phrase replaces it. Backspace right after brings the shortcut back. Password entries work only in password fields, after your phone lock.",
                color = Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)
            )
        } else {
            SheetUi(sh, onCancel = { sheet = null }, onSave = { save(sh) })
        }
    }
}

@Composable
private fun EntryRow(rep: Rep, editMode: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editMode) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(Red).clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Remove, "Delete", tint = White, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        if (rep.password) {
            Icon(Icons.Filled.Lock, null, tint = Color(0xFFFFD60A), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("••••••••••", color = White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
        } else {
            Text(rep.phrase.replace('\n', ' '), color = White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(rep.shortcut, color = Gray, fontSize = 15.sp, maxLines = 1)
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun SheetUi(sh: Sheet, onCancel: () -> Unit, onSave: () -> Unit) {
    val canSave = sh.shortcut.isNotBlank() && (sh.phrase.isNotEmpty() || (sh.password && sh.hadPassword))
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Cancel", color = Blue, fontSize = 17.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCancel() }.padding(8.dp))
        Spacer(Modifier.weight(1f))
        Text(if (sh.id == null) "New Text Replacement" else "Edit Text Replacement", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("Save", color = if (canSave) Blue else Gray, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).then(if (canSave) Modifier.clickable { onSave() } else Modifier).padding(8.dp))
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)) {
            // Phrase
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                Text("Phrase", color = White, fontSize = 16.sp, modifier = Modifier.width(88.dp).padding(top = 2.dp))
                Box(Modifier.weight(1f)) {
                    if (sh.phrase.isEmpty()) Text(
                        if (sh.password && sh.hadPassword) "•••••••• (leave empty = unchanged)" else "Phrase",
                        color = Color(0xFF6C6C70), fontSize = 16.sp
                    )
                    BasicTextField(
                        value = sh.phrase, onValueChange = { sh.phrase = it; sh.error = null },
                        textStyle = TextStyle(color = White, fontSize = 16.sp), cursorBrush = SolidColor(Blue),
                        maxLines = 5,
                        visualTransformation = if (sh.password && !sh.reveal) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = if (sh.password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sh.password) {
                    Icon(
                        if (sh.reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show / hide",
                        tint = Gray, modifier = Modifier.size(26.dp).clip(CircleShape).clickable { sh.reveal = !sh.reveal }.padding(2.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Sep))
            // Shortcut
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Shortcut", color = White, fontSize = 16.sp, modifier = Modifier.width(88.dp))
                Box(Modifier.weight(1f)) {
                    if (sh.shortcut.isEmpty()) Text("Shortcut", color = Color(0xFF6C6C70), fontSize = 16.sp)
                    BasicTextField(
                        value = sh.shortcut, onValueChange = { sh.shortcut = it.filter { c -> !c.isWhitespace() }; sh.error = null },
                        singleLine = true, textStyle = TextStyle(color = White, fontSize = 16.sp), cursorBrush = SolidColor(Blue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Sep))
            // Password switch
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Color(0xFFFFD60A), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Password", color = White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = sh.password, onCheckedChange = { sh.password = it; sh.reveal = false; sh.error = null },
                    colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = White)
                )
            }
        }
        sh.error?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
        Text(
            if (sh.password)
                "Password on: stored encrypted on this phone only, hidden in the list, works only in password fields and only after your fingerprint / PIN. Case is never changed."
            else
                "Shortcut: at least 2 characters, no spaces. A phrase that starts with a small letter gets a capital when you type the shortcut with a capital.",
            color = Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}
