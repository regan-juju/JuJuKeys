package com.reganbarua.jujukeys.settings

import android.content.Context
import android.content.SharedPreferences

/** All keyboard settings (Gboard-style), read by the keyboard every time it opens. */
data class KeyboardPrefs(
    // ভাষা
    val defaultBangla: Boolean = true,
    // পছন্দসমূহ — কী
    val numberRow: Boolean = false,
    val showEmojiKey: Boolean = true,
    val showLanguageKey: Boolean = true,
    val showVoiceKey: Boolean = true,
    val boldKeys: Boolean = true,
    // পছন্দসমূহ — লেআউট
    val heightScale: Float = 1.0f,          // 0.9 ছোট / 1.0 মাঝারি / 1.1 বড় / 1.2 অনেক বড়
    // পছন্দসমূহ — কী চাপলে
    val sound: Boolean = false,
    val vibrate: Boolean = true,
    val vibrateStrength: Int = 0,           // 0 = সিস্টেম, 1 হালকা, 2 মাঝারি, 3 জোরে
    val popup: Boolean = true,
    val longPressSymbols: Boolean = true,
    val longPressDelay: Int = 300,          // ms
    // লেখা সংশোধন
    val showSuggestions: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val learnWords: Boolean = true,         // শিখে নেওয়া সাজেশন (ফোনেই থাকে)
    // ক্লিপবোর্ড
    val clipboardOn: Boolean = true,
    // ইমোজি
    val recentEmoji: Boolean = true,
)

object Prefs {
    const val FILE = "jujukeys_settings"

    fun sp(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): KeyboardPrefs {
        val p = sp(context)
        val d = KeyboardPrefs()
        return KeyboardPrefs(
            defaultBangla = p.getBoolean("default_bangla", d.defaultBangla),
            numberRow = p.getBoolean("number_row", d.numberRow),
            showEmojiKey = p.getBoolean("emoji_key", d.showEmojiKey),
            showLanguageKey = p.getBoolean("language_key", d.showLanguageKey),
            showVoiceKey = p.getBoolean("voice_key", d.showVoiceKey),
            boldKeys = p.getBoolean("bold_keys", d.boldKeys),
            heightScale = p.getFloat("height_scale", d.heightScale),
            sound = p.getBoolean("sound", d.sound),
            vibrate = p.getBoolean("vibrate", d.vibrate),
            vibrateStrength = p.getInt("vibrate_strength", d.vibrateStrength),
            popup = p.getBoolean("popup", d.popup),
            longPressSymbols = p.getBoolean("long_press_symbols", d.longPressSymbols),
            longPressDelay = p.getInt("long_press_delay", d.longPressDelay),
            showSuggestions = p.getBoolean("suggestions", d.showSuggestions),
            autoCapitalize = p.getBoolean("auto_cap", d.autoCapitalize),
            doubleSpacePeriod = p.getBoolean("double_space", d.doubleSpacePeriod),
            learnWords = p.getBoolean("learn_words", d.learnWords),
            clipboardOn = p.getBoolean("clipboard", d.clipboardOn),
            recentEmoji = p.getBoolean("recent_emoji", d.recentEmoji),
        )
    }

    fun setBoolean(context: Context, key: String, value: Boolean) = sp(context).edit().putBoolean(key, value).apply()
    fun setInt(context: Context, key: String, value: Int) = sp(context).edit().putInt(key, value).apply()
    fun setFloat(context: Context, key: String, value: Float) = sp(context).edit().putFloat(key, value).apply()

    /** Optional Google Cloud Translation API key for online translation. Empty = offline only. */
    fun cloudApiKey(context: Context): String = sp(context).getString("cloud_key", "") ?: ""
    fun setCloudApiKey(context: Context, key: String) {
        sp(context).edit().putString("cloud_key", key.trim()).apply()
    }

    /** Language used the last time (so the keyboard opens in it again). */
    fun lastLanguageBangla(context: Context): Boolean =
        sp(context).getBoolean("last_bangla", sp(context).getBoolean("default_bangla", true))
    fun setLastLanguageBangla(context: Context, bangla: Boolean) {
        sp(context).edit().putBoolean("last_bangla", bangla).apply()
    }

    // ---- personal dictionary (অভিধান)
    fun userWords(context: Context): List<String> =
        (sp(context).getString("user_words", "") ?: "").split('\n').filter { it.isNotBlank() }
    fun setUserWords(context: Context, words: List<String>) {
        sp(context).edit().putString("user_words", words.distinct().joinToString("\n")).apply()
    }

    // ---- recent emoji
    fun recentEmoji(context: Context): List<String> =
        (sp(context).getString("recent_emoji_list", "") ?: "").split(' ').filter { it.isNotBlank() }
    fun addRecentEmoji(context: Context, emoji: String) {
        val list = listOf(emoji) + recentEmoji(context).filter { it != emoji }
        sp(context).edit().putString("recent_emoji_list", list.take(32).joinToString(" ")).apply()
    }
    fun clearRecentEmoji(context: Context) = sp(context).edit().remove("recent_emoji_list").apply()
}
