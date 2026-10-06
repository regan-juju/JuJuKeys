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
    MAIN("JuJuKeys"), LANGUAGES("ভাষা"), PREFERENCES("পছন্দসমূহ"), THEME("থিম"),
    TEXT("সংশোধন ও সাজেশন"), VOICE("ভয়েস টাইপিং"), CLIPBOARD("ক্লিপবোর্ড"), TRANSLATE("অনুবাদ"),
    DICTIONARY("অভিধান"), EMOJI("ইমোজি"), PRIVACY("গোপনীয়তা"), ABOUT("সম্পর্কে"), TEST("লিখে দেখুন"),
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "পিছনে", tint = TextMain)
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
            confirmButton = { TextButton(onClick = { open = false }) { Text("বাতিল") } }
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
            Text("কীবোর্ড চালু করুন", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                if (!enabled) "১) তালিকায় JuJuKeys চালু (ON) করুন। Android সব কীবোর্ডের জন্য একটি সাধারণ সতর্কবার্তা দেখায় — JuJuKeys আপনার লেখা কোথাও পাঠায় না।"
                else "২) JuJuKeys বেছে নিন।",
                color = TextSub, fontSize = 14.sp
            )
            Button(
                onClick = {
                    if (!enabled) context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    else (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text(if (!enabled) "সেটিংস খুলুন" else "কীবোর্ড বাছাই করুন", color = Color(0xFF062E6F)) }
        }
    }
    Item("ভাষা", "বাংলা (অভ্র), ENGLISH", Icons.Outlined.Language) { open(Screen.LANGUAGES) }
    Item("পছন্দসমূহ", "কী, লেআউট, শব্দ ও কম্পন", Icons.Outlined.Tune) { open(Screen.PREFERENCES) }
    Item("থিম", "৮টি লিকুইড গ্লাস থিম", Icons.Outlined.Palette) { open(Screen.THEME) }
    Item("সংশোধন ও সাজেশন", "স্বয়ংক্রিয় সংশোধন, বড় হাতের অক্ষর, সাজেশন", Icons.Outlined.Spellcheck) { open(Screen.TEXT) }
    Item("ভয়েস টাইপিং", "Google ভয়েস টাইপিং", Icons.Outlined.Mic) { open(Screen.VOICE) }
    Item("ক্লিপবোর্ড", "ইতিহাস, পিন, Google Keep", Icons.Outlined.ContentPaste) { open(Screen.CLIPBOARD) }
    Item("অনুবাদ", "অফলাইন মডেল, অনলাইন API key", Icons.Outlined.Translate) { open(Screen.TRANSLATE) }
    Item("অভিধান", "নিজের শব্দ যোগ করুন", Icons.AutoMirrored.Outlined.MenuBook) { open(Screen.DICTIONARY) }
    Item("ইমোজি", "সাম্প্রতিক ইমোজি", Icons.Outlined.EmojiEmotions) { open(Screen.EMOJI) }
    Item("গোপনীয়তা", "কী ডেটা কোথাও পাঠানো হয় না", Icons.Outlined.Shield) { open(Screen.PRIVACY) }
    Item("লিখে দেখুন", "কীবোর্ড পরীক্ষা করুন", Icons.Outlined.Keyboard) { open(Screen.TEST) }
    Item("সম্পর্কে", "JuJuKeys সংস্করণ", Icons.Outlined.Info) { open(Screen.ABOUT) }
}

@Composable
private fun LanguagesPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("ইনস্টল করা ভাষা")
    Item("বাংলা", "অভ্র ফোনেটিক — ইংরেজি অক্ষরে লিখলে বাংলা (ami → আমি)")
    Item("ENGLISH", "QWERTY")
    Header("শুরুর ভাষা")
    ChoiceItem(
        "কীবোর্ড খুললে প্রথমে", listOf(true to "বাংলা", false to "ENGLISH"), p.defaultBangla
    ) {
        Prefs.setBoolean(context, "default_bangla", it)
        Prefs.setLastLanguageBangla(context, it)
        refresh()
    }
    Note("লেখার সময় ভাষা বদলাতে সাজেশন বারের ক / A বোতাম চাপুন।")
}

@Composable
private fun PreferencesPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("কী")
    ToggleItem("বোল্ড অক্ষর", "কী-র লেখা মোটা করে দেখাও", "bold_keys", p.boldKeys, refresh)
    ToggleItem("নম্বরের সারি", "অক্ষরের উপরে ১–০ সারি দেখাও", "number_row", p.numberRow, refresh)
    ToggleItem("ইমোজি বোতাম দেখাও", null, "emoji_key", p.showEmojiKey, refresh)
    ToggleItem("ভয়েস ইনপুট বোতাম", null, "voice_key", p.showVoiceKey, refresh)
    Header("লেআউট")
    ChoiceItem(
        "কীবোর্ডের উচ্চতা",
        listOf(0.9f to "ছোট", 1.0f to "মাঝারি", 1.1f to "বড়", 1.2f to "অনেক বড়"), p.heightScale
    ) { Prefs.setFloat(context, "height_scale", it); refresh() }
    Header("স্বয়ংক্রিয়ভাবে লুকানো")
    ToggleItem("কিছু না লিখলে কীবোর্ড লুকাও", "নির্দিষ্ট সময় কোনো কী না চাপলে কীবোর্ড নিজে থেকে নেমে যাবে", "auto_hide", p.autoHide, refresh)
    if (p.autoHide) {
        Text("কতক্ষণ পর লুকাবে", color = TextMain, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5 to "৫ সে.", 10 to "১০ সে.", 20 to "২০ সে.", 30 to "৩০ সে.").forEach { (sec, label) ->
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
        Note("আবার খুলতে: লেখার ঘরে একবার ট্যাপ করলেই কীবোর্ড ফিরে আসবে।")
    }
    Header("কী চাপলে")
    ToggleItem("কী চাপলে শব্দ", null, "sound", p.sound, refresh)
    ToggleItem("কী চাপলে কম্পন", null, "vibrate", p.vibrate, refresh)
    if (p.vibrate) {
        ChoiceItem(
            "কম্পনের জোর",
            listOf(0 to "সিস্টেম ডিফল্ট", 1 to "হালকা", 2 to "মাঝারি", 3 to "জোরে"), p.vibrateStrength
        ) { Prefs.setInt(context, "vibrate_strength", it); refresh() }
    }
    ToggleItem("কী চাপলে বড় করে দেখাও", null, "popup", p.popup, refresh)
    ToggleItem("লম্বা চাপলে চিহ্ন", "কী লম্বা চাপলে সংখ্যা/চিহ্ন", "long_press_symbols", p.longPressSymbols, refresh)
    ChoiceItem(
        "লম্বা চাপের সময়",
        listOf(200 to "২০০ মি.সে.", 300 to "৩০০ মি.সে.", 400 to "৪০০ মি.সে.", 500 to "৫০০ মি.সে."), p.longPressDelay
    ) { Prefs.setInt(context, "long_press_delay", it); refresh() }
}

@Composable
private fun ThemePage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    Header("থিম")
    Note("একটি থিম বেছে নিন — কীবোর্ড সাথে সাথে বদলে যাবে। সব থিমে লিকুইড গ্লাস কী; শুধু একটি সাদা, বাকিগুলো ডার্ক।")
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
    Header("স্বয়ংক্রিয় সংশোধন")
    ToggleItem("স্বয়ংক্রিয় সংশোধন", "লেখার সময় ভুল ইংরেজি বানান ঠিক করো (স্পেস চাপলে)", "auto_correct", p.autoCorrect, refresh)
    ToggleItem("স্বয়ংক্রিয় বড় হাতের অক্ষর", "ENGLISH-এ প্রতিটি বাক্যের প্রথম অক্ষর বড় হাতের", "auto_cap", p.autoCapitalize, refresh)
    ToggleItem("দুবার স্পেসে দাঁড়ি/ফুলস্টপ", "বাংলায় ।  ENGLISH-এ .", "double_space", p.doubleSpacePeriod, refresh)
    Header("সাজেশন")
    ToggleItem("আপত্তিকর শব্দ সাজেস্ট করো না", null, "block_offensive", p.blockOffensive, refresh)
    ToggleItem("সাজেশন বার", "সাজেশন ও অন্যান্য বোতাম দেখাও", "suggestions", p.showSuggestions, refresh)
    ToggleItem("শব্দের সাজেশন", "লেখার সময় সাজেশন বারে শব্দ দেখাও", "word_suggestions", p.wordSuggestions, refresh)
    ToggleItem("পরের শব্দের সাজেশন", "আগের শব্দ দেখে পরের শব্দ আন্দাজ করো", "next_word", p.nextWordSuggestions, refresh)
    ToggleItem("আমার লেখা থেকে শেখো", "ভালো সাজেশনের জন্য — শুধু এই ফোনে থাকে", "learn_words", p.learnWords, refresh)
    ActionButton("শেখা শব্দ মুছুন", primary = false) { Prefs.setBoolean(context, "clear_learned", true) }
    Note("বানান ও ব্যাকরণ যাচাই (লাল/নীল দাগ) Android-এর আলাদা \"spell checker\" সেবা — কীবোর্ড সেটি দেয় না। ফোনের Settings → Languages & input → Spell checker থেকে চালু করতে পারেন।")
}

@Composable
private fun VoicePage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    ToggleItem("ভয়েস ইনপুট বোতাম", "কীবোর্ডের নিচে 🎤", "voice_key", p.showVoiceKey, refresh)
    Note("🎤 চাপলে Google ভয়েস টাইপিং চালু হয় (Gboard বা Google অ্যাপ লাগবে)। বলা শেষে আবার JuJuKeys-এ ফিরে আসবে।")
    ActionButton("ফোনের কীবোর্ড সেটিংস", primary = false) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
}

@Composable
private fun ClipboardPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    ToggleItem("ক্লিপবোর্ড", "কপি করা লেখা কীবোর্ডে জমা রাখো", "clipboard", p.clipboardOn, refresh)
    Note("কিছু কপি করলেই কীবোর্ডের ক্লিপবোর্ডে চলে আসে, লেখা হুবহু (স্পেস ও লাইনসহ) থাকে। কোনো লেখা লম্বা চাপলে পিন / কপি / Keep / মুছুন। ইতিহাস এনক্রিপ্ট করে শুধু এই ফোনে রাখা হয় (Android Keystore), ফোনের ব্যাকআপে যায় না।")
    Header("সংবেদনশীল লেখা (পাসওয়ার্ড, OTP …)")
    ToggleItem(
        "সংবেদনশীল লেখাও রাখো",
        "যে অ্যাপ থেকে কপি করছেন সেটি লেখাকে 'গোপন' চিহ্নিত করলে (পাসওয়ার্ড ম্যানেজার, ব্যাংক অ্যাপ) — চালু থাকলে সেটাও ইতিহাসে থাকবে",
        "save_sensitive", p.saveSensitive, refresh
    )
    Note("• এনক্রিপ্ট করে রাখা হয়; ইতিহাসে লেখা ঢাকা থাকে (🔒 ••••)।\n" +
        "• দেখা, বসানো, কপি বা Keep-এ পাঠানোর আগে ফোনের লক (PIN/প্যাটার্ন/আঙুলের ছাপ) চাইবে; একবার দিলে ১ মিনিট খোলা থাকে।\n" +
        "• নিজে থেকে কখনো Keep, অনুবাদ বা অন্য কোথাও যায় না; শব্দ শেখায়ও ব্যবহার হয় না।\n" +
        "• পাসওয়ার্ডের ঘরে আপনি যা টাইপ করেন তা কখনো জমা হয় না — শুধু আপনি নিজে কপি করলে এই নিয়মে রাখা হয়।\n" +
        "• ফোনে স্ক্রিন লক না থাকলে এগুলো খোলা যাবে না।\n" +
        "• সতর্কতা: কেউ আপনার ফোনের PIN জানলে এগুলো দেখতে পারবে।")
    ActionButton("সব সংবেদনশীল লেখা মুছুন", primary = false) {
        ClipHistory(context).clearSensitive()
        Prefs.sp(context).edit().putLong("clip_changed", System.currentTimeMillis()).apply()
    }
    ActionButton("Google Keep খুলুন") { ClipHistory.openKeep(context) }
    Header("Google Keep-এ পাঠানো (নিজে থেকে সিঙ্ক হয় না)")
    Note("Keep অন্য অ্যাপকে কোনো নোটে নিজে থেকে লেখা যোগ করতে দেয় না, আর সেভ হলো কিনা জানায়ও না। তাই এভাবে কাজ করে:\n" +
        "১. প্রথমবার ✎ চাপলে Keep-এ \"JuJuKeys ক্লিপবোর্ড\" নোট সব লেখাসহ খোলে — Keep-এ 'Save' চাপুন।\n" +
        "২. পরে ✎ চাপলে শুধু নতুন লেখাগুলো কপি হয় ও Keep খোলে — ওই নোট খুলে লম্বা চেপে 'Paste' করুন।\n" +
        "৩. কীবোর্ডে ফিরে \"Keep-এ সেভ হয়েছে?\" প্রশ্নে 'হ্যাঁ' চাপুন। 'না' চাপলে লেখাগুলো পরে আবার পাঠানো যাবে।")
    var sentCount by remember { mutableStateOf(Prefs.keepSentIds(context).size) }
    Item("পাঠানো হয়েছে বলে চিহ্নিত", if (sentCount == 0) "কিছু নেই" else "${sentCount}টি লেখা")
    ActionButton("সব লেখা আবার পাঠানোর জন্য চিহ্ন মুছুন", primary = false) {
        Prefs.setKeepSentIds(context, emptySet()); Prefs.clearKeepPending(context); sentCount = 0
    }
    ActionButton("Keep-এর নোট নতুন করে শুরু করুন", primary = false) {
        Prefs.setKeepNoteCreated(context, false); Prefs.setKeepSentIds(context, emptySet()); Prefs.clearKeepPending(context); sentCount = 0
    }
    ActionButton("ইতিহাস মুছুন (পিন করা ছাড়া)", primary = false) {
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
    LaunchedEffect(Unit) { engine.checkOfflineModel { modelReady = it } }

    Header("অফলাইন")
    Item(
        "বাংলা ⇄ ENGLISH মডেল",
        when (modelReady) { true -> "✓ ফোনে আছে — ইন্টারনেট ছাড়াই অনুবাদ হবে"; false -> "নেই (~৩০MB, একবারই নামাতে হবে)"; null -> "দেখা হচ্ছে…" }
    )
    ActionButton(if (busy) "নামানো হচ্ছে…" else "অফলাইন মডেল নামান", enabled = !busy && modelReady != true) {
        busy = true; message = ""
        engine.downloadOfflineModel { ok, err ->
            busy = false; modelReady = ok
            message = if (ok) "" else "হয়নি: ${err ?: "ইন্টারনেট দেখুন"}"
        }
    }
    if (message.isNotEmpty()) Note(message)
    Header("অনলাইন (ঐচ্ছিক)")
    Note("Google Cloud Translation API key দিলে ইন্টারনেট থাকলে সেটি দিয়ে অনুবাদ হবে; না দিলে অফলাইন মডেলই চলবে।")
    OutlinedTextField(
        value = key, onValueChange = { key = it }, singleLine = true,
        placeholder = { Text("API key") },
        visualTransformation = PasswordVisualTransformation(),     // key is never shown on screen
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    )
    ActionButton("Key সেভ করুন", primary = false) {
        message = if (Prefs.setCloudApiKey(context, key)) "সেভ হয়েছে (এনক্রিপ্ট করে)" else "সেভ হয়নি — ফোনের নিরাপদ চাবিঘর (Keystore) কাজ করছে না"
    }
    ActionButton("Key মুছুন", primary = false) { key = ""; Prefs.setCloudApiKey(context, ""); message = "Key মুছে ফেলা হয়েছে — এখন শুধু অফলাইন অনুবাদ" }
    Note("Key এনক্রিপ্ট করে শুধু এই ফোনে রাখা হয় (Android Keystore), ফোনের ব্যাকআপে যায় না।")
}

@Composable
private fun DictionaryPage() {
    val context = LocalContext.current
    var words by remember { mutableStateOf(Prefs.userWords(context)) }
    var input by remember { mutableStateOf("") }
    Note("নিজের শব্দ (নাম, জায়গা ইত্যাদি) যোগ করুন — লেখার সময় সাজেশনে আগে আসবে। বাংলা বা ENGLISH দুটোই চলবে।")
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            placeholder = { Text("যেমন: বিলাইছড়ি") }, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                val w = input.trim()
                if (w.isNotEmpty()) { words = (listOf(w) + words).distinct(); Prefs.setUserWords(context, words); input = "" }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) { Text("যোগ", color = Color(0xFF062E6F)) }
    }
    Header("আমার শব্দ (${words.size})")
    words.forEach { w ->
        Item(w, trailing = {
            Icon(Icons.Filled.Close, "মুছুন", tint = TextSub, modifier = Modifier.size(22.dp).clickable {
                words = words - w; Prefs.setUserWords(context, words)
            })
        })
    }
}

@Composable
private fun EmojiPage(p: KeyboardPrefs, refresh: () -> Unit) {
    val context = LocalContext.current
    ToggleItem("ইমোজি বোতাম দেখাও", null, "emoji_key", p.showEmojiKey, refresh)
    ToggleItem("সাম্প্রতিক ইমোজি", "সম্প্রতি ব্যবহার করা ইমোজি আগে দেখাও", "recent_emoji", p.recentEmoji, refresh)
    ActionButton("সাম্প্রতিক ইমোজি মুছুন", primary = false) { Prefs.clearRecentEmoji(context) }
}

@Composable
private fun PrivacyPage() {
    Note("• আপনি যা টাইপ করেন তা কোথাও জমা রাখা, লগ করা বা পাঠানো হয় না।")
    Note("• ক্লিপবোর্ডের ইতিহাস ও API key এনক্রিপ্ট করে শুধু এই ফোনে থাকে। অ্যাপ যে লেখাকে গোপন চিহ্নিত করে, তা শুধু আপনি সেটিংসে চালু করলে রাখা হয় (ঢাকা অবস্থায়, ফোনের লক দিয়ে খোলে)। ক্লিপবোর্ড, API key আর শেখা শব্দ ফোনের ব্যাকআপে যায় না।")
    Note("• কোনো অ্যাপ যে ঘরকে গোপন (Incognito) বলে চিহ্নিত করে, সেখানে লেখা শব্দ শেখা হয় না।")
    Note("• অনুবাদ অফলাইনে ফোনেই হয়। শুধু আপনি নিজে API key দিলে, অনুবাদের বক্সের লেখাটুকু Google-এ যায়।")
    Note("• \"আমার লেখা থেকে শেখো\" চালু থাকলে কোন শব্দের পর কোন শব্দ লেখেন তা শুধু এই ফোনে জমা থাকে (লেখা সংশোধন → শেখা শব্দ মুছুন)।")
    Note("• ইন্টারনেট লাগে শুধু অনুবাদের মডেল নামাতে (একবার)।")
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.logo), "JuJuKeys", modifier = Modifier.size(120.dp).clip(CircleShape))
    }
    Item("JuJuKeys", "সংস্করণ $version")
    Item("তৈরি করেছেন", "রিগ্যান বড়ুয়া")
    val sp = Prefs.sp(context)
    val ms = sp.getLong("bench_dict_ms", -1)
    Item(
        "এই ফোনে অভিধান",
        if (ms < 0) "কীবোর্ড একবার খুললে এখানে মাপা সময় দেখাবে"
        else "লোড হতে ${ms} মি.সে. · বাংলা ${sp.getInt("bench_words_bn", 0)}টি · ইংরেজি ${sp.getInt("bench_words_en", 0)}টি শব্দ"
    )
    Header("উৎস ও লাইসেন্স")
    Item("শব্দের ঘনত্বের তালিকা", "FrequencyWords — github.com/hermitdave/FrequencyWords (rev 525f9b5, content/2018) · CC BY-SA 4.0")
    Item("অতিরিক্ত বাংলা শব্দ", "Avro Phonetic অভিধান — github.com/sarim/ibus-avro (rev dd521a1) · Mozilla Public License 2.0 · assets/dict_bn_avro.txt")
    Item("ইমোজি তালিকা", "Google emoji-metadata — github.com/googlefonts/emoji-metadata (rev 173b9b2) · Apache 2.0")
    Item("বাংলা ফন্ট", "Noto Sans Bengali · SIL Open Font License 1.1")
    Item("অনুবাদ", "Google ML Kit")
    Note("বিস্তারিত: github.com/regan-juju/JuJuKeys → THIRD_PARTY_NOTICES.md")
}

@Composable
private fun TestPage() {
    var text by remember { mutableStateOf("") }
    Note("এখানে লিখে কীবোর্ড পরীক্ষা করুন।")
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp),
        placeholder = { Text("এখানে টাইপ করুন…") }
    )
}
