package com.reganbarua.jujukeys.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.reganbarua.jujukeys.keyboard.KbTheme
import com.reganbarua.jujukeys.keyboard.Themes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.reganbarua.jujukeys.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reganbarua.jujukeys.clipboard.ClipHistory
import com.reganbarua.jujukeys.translate.TranslateEngine

// Gboard dark settings colours
private val Bg = Color(0xFF1B1B1B)
private val TextMain = Color(0xFFE3E3E3)
private val TextSub = Color(0xFF9AA0A6)
private val Accent = Color(0xFFA8C7FA)

enum class Screen(val title: String) {
    MAIN("JuJuKeys"), LANGUAGES("Languages"), PREFERENCES("Preferences"), THEME("Themes"),
    TEXT("Text correction"), VOICE("Voice typing"), CLIPBOARD("Clipboard"), TRANSLATE("Translate"),
    DICTIONARY("Dictionary"), EMOJI("Emoji & stickers"), PRIVACY("Privacy"), ABOUT("About"), TEST("Try it"),
}

/** Gboard-style settings. [enabled]/[selected] show the setup banner until the keyboard is on. */
@Composable
fun SettingsApp(enabled: Boolean, selected: Boolean, onClose: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(Prefs.load(context)) }
    val refresh: () -> Unit = { prefs = Prefs.load(context) }

    BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(screen.title) { if (screen == Screen.MAIN) onClose() else screen = Screen.MAIN }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            when (screen) {
                Screen.MAIN -> MainList(enabled, selected) { screen = it }
                Screen.LANGUAGES -> LanguagesPage(prefs, refresh)
                Screen.PREFERENCES -> PreferencesPage(prefs, refresh)
                Screen.THEME -> ThemePage(prefs, refresh)
                Screen.TEXT -> TextPage(prefs, refresh)
                Screen.VOICE -> VoicePage(prefs, refresh)
                Screen.CLIPBOARD -> ClipboardPage(prefs, refresh)
                Screen.TRANSLATE -> TranslatePage()
                Screen.DICTIONARY -> DictionaryPage()
                Screen.EMOJI -> EmojiPage(prefs, refresh)
                Screen.PRIVACY -> PrivacyPage()
                Screen.ABOUT -> AboutPage()
                Screen.TEST -> TestPage()
            }
        }
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextMain)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextMain, fontSize = 22.sp)
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun Item(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = TextSub, fontSize = 13.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
private fun ToggleItem(title: String, subtitle: String? = null, key: String, value: Boolean, refresh: () -> Unit) {
    val context = LocalContext.current
    val flip = { Prefs.setBoolean(context, key, !value); refresh() }
    Item(title, subtitle, trailing = {
        Switch(
            checked = value, onCheckedChange = { flip() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent, checkedThumbColor = Color(0xFF062E6F),
                uncheckedTrackColor = Bg, uncheckedThumbColor = Color(0xFF8E918F),
                uncheckedBorderColor = Color(0xFF8E918F)
            )
        )
    }, onClick = flip)
}

/** A row that opens a radio-button dialog. */
@Composable
private fun <T> ChoiceItem(title: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Item(title, options.firstOrNull { it.first == current }?.second ?: "", onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (v, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(v); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = v == current, onClick = { onPick(v); open = false })
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = TextSub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun ActionButton(text: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) Accent else Color(0xFF3C4043))
    ) { Text(text, color = if (primary) Color(0xFF062E6F) else TextMain) }
}

// ------------------------------------------------------------------ pages

@Composable
private fun MainList(enabled: Boolean, selected: Boolean, open: (Screen) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.logo), "JuJuKeys",
            modifier = Modifier.size(96.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text("JuJuKeys", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
    if (!enabled || !selected) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth()
                .background(Color(0xFF2B2F36), RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Turn on the keyboard", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                if (!enabled) "1) Turn JuJuKeys ON in the list. Android shows the same general warning for every keyboard — JuJuKeys never sends what you type anywhere."
                else "2) Choose JuJuKeys.",
                color = TextSub, fontSize = 14.sp
            )
            Button(
                onClick = {
                    if (!enabled) context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    else (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text(if (!enabled) "Open settings" else "Choose keyboard", color = Color(0xFF062E6F)) }
        }
    }
    Item("Languages", "Bangla (Avro), English", Icons.Outlined.Language) { open(Screen.LANGUAGES) }
    Item("Preferences", "Keys, layout, sound & vibration", Icons.Outlined.Tune) { open(Screen.PREFERENCES) }
    Item("Themes", "8 liquid glass themes", Icons.Outlined.Palette) { open(Screen.THEME) }
    Item("Text correction", "Auto-correct, capitals, suggestions", Icons.Outlined.Spellcheck) { open(Screen.TEXT) }
    Item("Voice typing", "Google voice typing", Icons.Outlined.Mic) { open(Screen.VOICE) }
    Item("Clipboard", "History, pin, Google Keep", Icons.Outlined.ContentPaste) { open(Screen.CLIPBOARD) }
    Item("Translate", "Languages, offline models, online API key", Icons.Outlined.Translate) { open(Screen.TRANSLATE) }
    Item("Dictionary", "Your words (added automatically)", Icons.AutoMirrored.Outlined.MenuBook) { open(Screen.DICTIONARY) }
    Item("Emoji & stickers", "Recent emoji, add your stickers", Icons.Outlined.EmojiEmotions) { open(Screen.EMOJI) }
    Item("Privacy", "Your typing is never sent anywhere", Icons.Outlined.Shield) { open(Screen.PRIVACY) }
    Item("Try it", "Test the keyboard", Icons.Outlined.Keyboard) { open(Screen.TEST) }
    Item("About", "JuJuKeys version", Icons.Outlined.Info) { open(Screen.ABOUT) }
}

@Composable
private fun LanguagesPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("Installed languages")
    Item("Bangla", "Avro phonetic — type in English letters, get Bangla (ami → আমি)")
    Item("English", "QWERTY")
    Header("Starting language")
    ChoiceItem(
        "When the keyboard opens", listOf(true to "Bangla", false to "English"), p.defaultBangla
    ) {
        Prefs.setBoolean(context, "default_bangla", it)
        Prefs.setLastLanguageBangla(context, it)
        refresh()
    }
    Note("To switch language while typing, tap the ক / A key on the suggestion bar.")
}

@Composable
private fun PreferencesPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("Keys")
    ToggleItem("Bold letters", "Show key labels in bold", "bold_keys", p.boldKeys, refresh)
    ToggleItem("Number row", "Show a 1–0 row above the letters", "number_row", p.numberRow, refresh)
    ToggleItem("Photo / emoji key", null, "emoji_key", p.showEmojiKey, refresh)
    ToggleItem("Voice input key", null, "voice_key", p.showVoiceKey, refresh)
    Header("Layout")
    ChoiceItem(
        "Keyboard height",
        listOf(1.0f to "Small", 1.1f to "Normal", 1.2f to "Large", 1.3f to "Extra large"), p.heightScale
    ) { Prefs.setFloat(context, "height_scale", it); refresh() }
    Header("Auto-hide")
    ToggleItem("Hide the keyboard when idle", "The keyboard closes by itself if no key is pressed for a while", "auto_hide", p.autoHide, refresh)
    if (p.autoHide) {
        Text("Hide after", color = TextMain, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5 to "5 s", 10 to "10 s", 20 to "20 s", 30 to "30 s").forEach { (sec, label) ->
                val on = p.autoHideSeconds == sec
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) Accent else Color.Transparent)
                        .border(1.dp, if (on) Accent else Color(0xFF5F6368), RoundedCornerShape(10.dp))
                        .clickable { Prefs.setInt(context, "auto_hide_seconds", sec); refresh() },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (on) Color(0xFF062E6F) else TextMain, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        Note("To bring it back, just tap the text field once.")
    }
    Header("Key press")
    ToggleItem("Sound on key press", null, "sound", p.sound, refresh)
    ToggleItem("Vibrate on key press", null, "vibrate", p.vibrate, refresh)
    if (p.vibrate) {
        ChoiceItem(
            "Vibration strength",
            listOf(0 to "System default", 1 to "Light", 2 to "Medium", 3 to "Strong"), p.vibrateStrength
        ) { Prefs.setInt(context, "vibrate_strength", it); refresh() }
    }
    ToggleItem("Pop-up on key press", null, "popup", p.popup, refresh)
    ToggleItem("Long press for symbols", "Hold a key for its number / symbol", "long_press_symbols", p.longPressSymbols, refresh)
    ChoiceItem(
        "Long press delay",
        listOf(200 to "200 ms", 300 to "300 ms", 400 to "400 ms", 500 to "500 ms"), p.longPressDelay
    ) { Prefs.setInt(context, "long_press_delay", it); refresh() }
}

@Composable
private fun ThemePage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("Themes")
    Note("Pick a theme — the keyboard changes at once. Every theme has liquid glass keys; one is white, the rest are dark.")
    Themes.all.forEach { t ->
        val selected = t.id == p.theme
        Column(
            Modifier.fillMaxWidth()
                .clickable { Prefs.setString(context, "theme", t.id); refresh() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = { Prefs.setString(context, "theme", t.id); refresh() })
                Text(t.name, color = TextMain, fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
            ThemePreview(t, selected)
        }
    }
}

/** A small picture of the keyboard in theme [t]. */
@Composable
private fun ThemePreview(t: KbTheme, selected: Boolean) {
    Canvas(
        Modifier.fillMaxWidth().height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(14.dp)) else Modifier)
    ) {
        val w = size.width; val h = size.height
        drawRect(t.bg.copy(alpha = 1f))
        t.blobs.forEach { b ->
            drawRect(Brush.radialGradient(listOf(b.color, Color.Transparent), Offset(w * b.x, h * b.y), w * b.r))
        }
        val gap = 4.dp.toPx(); val kh = (h - gap * 5) / 4f
        val cr = CornerRadius(4.dp.toPx())
        val glass = Brush.verticalGradient(0f to t.glassTop, 0.4f to Color.Transparent)
        fun key(x: Float, y: Float, kw: Float, color: Color) {
            drawRoundRect(color, Offset(x, y), Size(kw, kh), cr)
            if (color != t.faded) drawRoundRect(glass, Offset(x, y), Size(kw, kh), cr, style = Stroke(1f))
        }
        // top bar: ক · · · ⟵
        val y0 = gap
        key(gap * 2, y0, kh * 1.1f, t.key)
        key(w - gap * 2 - kh * 1.6f, y0, kh * 1.6f, t.key)
        for (i in 0 until 3) drawRoundRect(t.text.copy(alpha = if (i == 0) 0.9f else 0.5f),
            Offset(w * (0.3f + i * 0.14f), y0 + kh * 0.4f), Size(w * 0.08f, kh * 0.2f), cr)
        // two rows of letters
        for (r in 0 until 2) {
            val n = 10 - r
            val kw = (w - gap * 2 - gap * 10) / 10f
            val start = gap + (10 - n) * (kw + gap) / 2f
            for (i in 0 until n) key(start + i * (kw + gap), gap + (r + 1) * (kh + gap), kw, t.rainbow?.let { it[i % it.size] } ?: t.key)
        }
        // bottom: 123 · space · return (faded)
        val y3 = gap + 3 * (kh + gap)
        val unit = (w - gap * 4) / 10f
        key(gap, y3, unit * 1.5f, t.accent ?: t.key)
        key(gap * 2 + unit * 1.5f, y3, unit * 5.5f, t.key)
        key(gap * 3 + unit * 7f, y3, unit * 3f, t.faded)
    }
}

@Composable
private fun TextPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("Auto-correction")
    ToggleItem("Auto-correct", "Fix English spelling mistakes when you press space", "auto_correct", p.autoCorrect, refresh)
    ToggleItem("Auto-capitalisation", "English only: first letter of every sentence is a capital (never in Bangla)", "auto_cap", p.autoCapitalize, refresh)
    ToggleItem("Double-space full stop", "Bangla ।  English .", "double_space", p.doubleSpacePeriod, refresh)
    Header("Suggestions")
    ToggleItem("Block offensive words", null, "block_offensive", p.blockOffensive, refresh)
    ToggleItem("Suggestion bar", "Show suggestions and buttons", "suggestions", p.showSuggestions, refresh)
    ToggleItem("Word suggestions", "Show words on the suggestion bar while typing", "word_suggestions", p.wordSuggestions, refresh)
    ToggleItem("Next-word suggestions", "Guess the next word from the previous one", "next_word", p.nextWordSuggestions, refresh)
    ToggleItem("Learn from my typing", "Your most-typed words come first — stays on this phone", "learn_words", p.learnWords, refresh)
    ActionButton("Clear learned words", primary = false) { Prefs.setBoolean(context, "clear_learned", true) }
    Note("Red / blue underlines come from Android's separate spell checker, not the keyboard. Turn it on in phone Settings → Languages & input → Spell checker.")
}

@Composable
private fun VoicePage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    ToggleItem("Voice input key", "🎤 under the keyboard", "voice_key", p.showVoiceKey, refresh)
    Note("🎤 starts Google voice typing (needs Gboard or the Google app). When you finish speaking you come back to JuJuKeys.")
    ActionButton("Phone keyboard settings", primary = false) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
}

@Composable
private fun ClipboardPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    ToggleItem("Clipboard", "Keep copied text in the keyboard", "clipboard", p.clipboardOn, refresh)
    Note("Anything you copy appears in the keyboard clipboard exactly as copied (spaces and lines too). Pin button at the top: tap it, choose items, then Done. Hold an item: copy / Keep / delete. History is encrypted and stays on this phone (Android Keystore); it is not in phone backups.")
    Header("Sensitive text (passwords, OTP …)")
    ToggleItem(
        "Keep sensitive text too",
        "When the app you copy from marks text as private (password managers, bank apps) — if on, it is kept in history too",
        "save_sensitive", p.saveSensitive, refresh
    )
    Note("• Stored encrypted; hidden in the history (🔒 ••••).\n" +
        "• Viewing, pasting, copying or sending to Keep asks for the phone lock (PIN / pattern / fingerprint); unlocked for 1 minute.\n" +
        "• Never goes to Keep, translation or anywhere else by itself; never used for learning words.\n" +
        "• What you type in password fields is never stored — only text you copy yourself follows this rule.\n" +
        "• Without a screen lock on the phone these cannot be opened.\n" +
        "• Warning: anyone who knows your phone PIN can see them.")
    ActionButton("Delete all sensitive text", primary = false) {
        ClipHistory(context).clearSensitive()
        Prefs.sp(context).edit().putLong("clip_changed", System.currentTimeMillis()).apply()
    }
    ActionButton("Open Google Keep") { ClipHistory.openKeep(context) }
    Header("Sending to Google Keep (not an automatic sync)")
    Note("Keep does not let other apps add to a note, nor say whether it was saved. So it works like this:\n" +
        "1. First ✎ opens a new Keep note \"JuJuKeys ক্লিপবোর্ড\" with everything — tap 'Save' in Keep.\n" +
        "2. Later ✎ copies only the new items and opens Keep — open that note, hold and 'Paste'.\n" +
        "3. Back in the keyboard answer 'হ্যাঁ' to \"Keep-এ সেভ হয়েছে?\". 'না' means they can be sent again later.")
    var sentCount by remember { mutableStateOf(Prefs.keepSentIds(context).size) }
    Item("Marked as sent", if (sentCount == 0) "None" else "$sentCount items")
    ActionButton("Clear the sent marks (send all again)", primary = false) {
        Prefs.setKeepSentIds(context, emptySet()); Prefs.clearKeepPending(context); sentCount = 0
    }
    ActionButton("Start a new Keep note", primary = false) {
        Prefs.setKeepNoteCreated(context, false); Prefs.setKeepSentIds(context, emptySet()); Prefs.clearKeepPending(context); sentCount = 0
    }
    ActionButton("Clear history (except pinned)", primary = false) {
        ClipHistory(context).clearUnpinned()
        Prefs.sp(context).edit().putLong("clip_changed", System.currentTimeMillis()).apply()
    }
}

@Composable
private fun TranslatePage() {
    val context = LocalContext.current
    val engine = remember { TranslateEngine(context) }
    var modelReady by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(Prefs.cloudApiKey(context)) }
    LaunchedEffect(Unit) { engine.checkOfflineModel("bn", "en") { modelReady = it } }

    Header("Offline")
    Item(
        "Bangla ⇄ English model",
        when (modelReady) { true -> "✓ On the phone — translates without internet"; false -> "Not downloaded (~30 MB, once)"; null -> "Checking…" }
    )
    ActionButton(if (busy) "Downloading…" else "Download offline model", enabled = !busy && modelReady != true) {
        busy = true; message = ""
        engine.downloadOfflineModel("bn", "en") { ok, err ->
            busy = false; modelReady = ok
            message = if (ok) "" else "Failed: ${err ?: "check the internet"}"
        }
    }
    if (message.isNotEmpty()) Note(message)
    Header("Languages")
    Note("You type in Bangla or English; tap the language on the translate bar (e.g. ENGLISH ▾) to translate into German, Russian, Japanese, Portuguese and about 55 more. Each offline language downloads once (~30 MB) the first time you use it. Nepali, Burmese, Punjabi and Sinhala work online only (API key).")
    Note("How to translate: write everything in the box, then tap the photo button (top right) — or press Enter. The whole text is translated at once.")
    Header("Online (optional)")
    Note("With your own Google Cloud Translation API key, translation uses it when online; without it the offline model is used.")
    OutlinedTextField(
        value = key, onValueChange = { key = it }, singleLine = true,
        placeholder = { Text("API key") },
        visualTransformation = PasswordVisualTransformation(),     // key is never shown on screen
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    )
    ActionButton("Save key", primary = false) {
        message = if (Prefs.setCloudApiKey(context, key)) "Saved (encrypted)" else "Not saved — the phone's secure key store (Keystore) is not working"
    }
    ActionButton("Delete key", primary = false) { key = ""; Prefs.setCloudApiKey(context, ""); message = "Key deleted — offline translation only" }
    Note("The key is encrypted and stays on this phone (Android Keystore); not in phone backups.")
}

@Composable
private fun DictionaryPage() {
    val context = LocalContext.current
    var words by remember { mutableStateOf(Prefs.userWords(context)) }
    val auto = remember { Prefs.autoWords(context) }
    var autoOn by remember { mutableStateOf(Prefs.sp(context).getBoolean("auto_dictionary", true)) }
    var input by remember { mutableStateOf("") }
    Item("Add new words automatically", "A word you type twice that the dictionary does not know (a name, a place) is added here", trailing = {
        Switch(checked = autoOn, onCheckedChange = { autoOn = it; Prefs.setBoolean(context, "auto_dictionary", it) })
    }) { autoOn = !autoOn; Prefs.setBoolean(context, "auto_dictionary", autoOn) }
    Note("Your words come first in the suggestions. Bangla or English both work. A word you delete here is never added again by itself.")
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            placeholder = { Text("e.g. বিলাইছড়ি") }, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                val w = input.trim()
                if (w.isNotEmpty()) { words = (listOf(w) + words).distinct(); Prefs.setUserWords(context, words); input = "" }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) { Text("Add", color = Color(0xFF062E6F)) }
    }
    Header("My words (${words.size})")
    words.forEach { w ->
        Item(w, if (w in auto) "added automatically" else null, trailing = {
            Icon(Icons.Filled.Close, "Delete", tint = TextSub, modifier = Modifier.size(22.dp).clickable {
                words = words - w; Prefs.setUserWords(context, words); Prefs.addRemovedWord(context, w)
            })
        })
    }
}

@Composable
private fun EmojiPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    ToggleItem("Photo / emoji key", null, "emoji_key", p.showEmojiKey, refresh)
    ToggleItem("Recent emoji", "Show recently used emoji first", "recent_emoji", p.recentEmoji, refresh)
    ActionButton("Clear recent emoji", primary = false) { Prefs.clearRecentEmoji(context) }
    Header("Stickers")
    Note("Tap the photo key on the keyboard to open your stickers. Tap a sticker to send it; hold it to share / save to gallery / move to top / put in profiles / delete. No limit on how many.")
    ActionButton("Add stickers (from gallery)") { com.reganbarua.jujukeys.sticker.StickerAddActivity.start(context) }
    ActionButton("Sticker profiles (folders)") { com.reganbarua.jujukeys.sticker.StickerProfilesActivity.start(context) }
    Note("Profiles are folders for stickers, and can hold more profiles (any depth, hundreds are fine). One sticker can be in many profiles — the picture is stored only once. In the keyboard the top line shows where you are (সব স্টিকার › …); tap a profile to open it, hold it to edit.")
    Note("Ordinary photos can get the background removed and a white border (on the phone, Google ML Kit). A sheet of many stickers is split into separate stickers. You can also share pictures from the gallery to \"JuJuKeys স্টিকার\".")
}

@Composable
private fun PrivacyPage() {
    Note("• What you type is never stored, logged or sent anywhere.")
    Note("• Clipboard history and the API key are encrypted and stay on this phone. Text an app marks as private is kept only if you turn that on (hidden, opened with the phone lock). Clipboard, API key and learned words are not in phone backups.")
    Note("• Nothing is learned in fields an app marks as private (incognito).")
    Note("• Translation runs offline on the phone. Only if you add your own API key does the text in the translate box go to Google.")
    Note("• With \"Learn from my typing\" on, the words you use stay only on this phone (Text correction → Clear learned words). New words typed twice go into your Dictionary.")
    Note("• Stickers stay on this phone; background removal happens on the phone too. Stickers are not in Google backups.")
    Note("• Internet is needed only to download the translation models and the sticker background model (once).")
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.logo), "JuJuKeys", modifier = Modifier.size(120.dp).clip(CircleShape))
    }
    Item("JuJuKeys", "Version $version")
    Item("Made by", "Regan Barua (রিগ্যান বড়ুয়া)")
    val sp = Prefs.sp(context)
    val ms = sp.getLong("bench_dict_ms", -1)
    Item(
        "Dictionary on this phone",
        if (ms < 0) "Open the keyboard once to see the measured time here"
        else "Loads in $ms ms · ${sp.getInt("bench_words_bn", 0)} Bangla · ${sp.getInt("bench_words_en", 0)} English words"
    )
    Header("Sources & licences")
    Item("Word frequency list", "FrequencyWords — github.com/hermitdave/FrequencyWords (rev 525f9b5, content/2018) · CC BY-SA 4.0")
    Item("Extra Bangla words", "Avro Phonetic dictionary — github.com/sarim/ibus-avro (rev dd521a1) · Mozilla Public License 2.0 · assets/dict_bn_avro.txt")
    Item("Emoji list", "Google emoji-metadata — github.com/googlefonts/emoji-metadata (rev 173b9b2) · Apache 2.0")
    Item("Bangla font", "Noto Sans Bengali · SIL Open Font License 1.1")
    Item("Translation", "Google ML Kit")
    Item("Sticker background removal", "Google ML Kit Subject Segmentation (beta) — runs on the phone")
    Note("Details: github.com/regan-juju/JuJuKeys → THIRD_PARTY_NOTICES.md")
}

@Composable
private fun TestPage() {
    var text by remember { mutableStateOf("") }
    Note("Type here to test the keyboard.")
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp),
        placeholder = { Text("Type here…") }
    )
}
