package com.reganbarua.jujukeys.settings

import android.content.Context
import android.content.SharedPreferences
import com.reganbarua.jujukeys.security.CryptoBox

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
    val showSuggestions: Boolean = true,    // সাজেশন বার
    val wordSuggestions: Boolean = true,    // লেখার সময় শব্দ
    val nextWordSuggestions: Boolean = true,
    val autoCorrect: Boolean = false,
    val blockOffensive: Boolean = false,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val learnWords: Boolean = true,         // শিখে নেওয়া সাজেশন (ফোনেই থাকে)
    // ক্লিপবোর্ড
    val clipboardOn: Boolean = true,
    val saveSensitive: Boolean = false,     // keep text the copying app marked private (locked)
    // ইমোজি
    val recentEmoji: Boolean = true,
    // থিম
    val theme: String = "current",
    // কিছু না লিখলে কীবোর্ড লুকাও
    val autoHide: Boolean = true,
    val autoHideSeconds: Int = 10,
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
            wordSuggestions = p.getBoolean("word_suggestions", d.wordSuggestions),
            nextWordSuggestions = p.getBoolean("next_word", d.nextWordSuggestions),
            autoCorrect = p.getBoolean("auto_correct", d.autoCorrect),
            blockOffensive = p.getBoolean("block_offensive", d.blockOffensive),
            autoCapitalize = p.getBoolean("auto_cap", d.autoCapitalize),
            doubleSpacePeriod = p.getBoolean("double_space", d.doubleSpacePeriod),
            learnWords = p.getBoolean("learn_words", d.learnWords),
            clipboardOn = p.getBoolean("clipboard", d.clipboardOn),
            saveSensitive = p.getBoolean("save_sensitive", d.saveSensitive),
            recentEmoji = p.getBoolean("recent_emoji", d.recentEmoji),
            theme = p.getString("theme", d.theme) ?: d.theme,
            autoHide = p.getBoolean("auto_hide", d.autoHide),
            autoHideSeconds = p.getInt("auto_hide_seconds", d.autoHideSeconds),
        )
    }

    fun setBoolean(context: Context, key: String, value: Boolean) = sp(context).edit().putBoolean(key, value).apply()
    fun setInt(context: Context, key: String, value: Int) = sp(context).edit().putInt(key, value).apply()
    fun setString(context: Context, key: String, value: String) = sp(context).edit().putString(key, value).apply()
    fun setFloat(context: Context, key: String, value: Float) = sp(context).edit().putFloat(key, value).apply()

    /**
     * The API key lives ENCRYPTED (Android Keystore, see [CryptoBox]) in its own file,
     * "jujukeys_secret", which is also left out of Google backup / phone transfer.
     */
    private fun secret(context: Context): SharedPreferences =
        context.getSharedPreferences("jujukeys_secret", Context.MODE_PRIVATE)

    /**
     * Moves an old plain-text key (v1.0.15 and earlier kept it in the settings file) into the
     * encrypted form. Plain copies are removed only after the encrypted copy is written and
     * reads back correctly; if anything fails, the old copy stays and this runs again later.
     */
    fun migrateSecrets(context: Context) {
        val s = secret(context)
        val plain = sp(context).getString("cloud_key", null) ?: s.getString("cloud_key", null) ?: return
        if (s.getString("cloud_key_enc", null) == null) {
            val e = CryptoBox.encryptVerified(plain) ?: return
            if (!s.edit().putString("cloud_key_enc", e).commit()) return
        }
        if (CryptoBox.decrypt(s.getString("cloud_key_enc", "") ?: "") != null) {
            sp(context).edit().remove("cloud_key").commit()
            s.edit().remove("cloud_key").commit()
        }
    }

    /** Optional Google Cloud Translation API key for online translation. Empty = offline only. */
    fun cloudApiKey(context: Context): String {
        migrateSecrets(context)
        val s = secret(context)
        s.getString("cloud_key_enc", null)?.let { return CryptoBox.decrypt(it) ?: "" }
        return s.getString("cloud_key", null) ?: sp(context).getString("cloud_key", "") ?: ""
    }

    /** Saves (encrypted) or, with an empty key, deletes. False = could not encrypt, nothing saved. */
    fun setCloudApiKey(context: Context, key: String): Boolean {
        val k = key.trim()
        val s = secret(context)
        if (k.isEmpty()) {
            s.edit().remove("cloud_key_enc").remove("cloud_key").commit()
            sp(context).edit().remove("cloud_key").commit()
            return true
        }
        val e = CryptoBox.encryptVerified(k) ?: return false
        s.edit().putString("cloud_key_enc", e).remove("cloud_key").commit()
        sp(context).edit().remove("cloud_key").commit()
        return true
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

    // ---- the one Google Keep note used for the clipboard
    fun keepNoteCreated(context: Context) = sp(context).getBoolean("keep_note_created", false)
    fun setKeepNoteCreated(context: Context, v: Boolean) = sp(context).edit().putBoolean("keep_note_created", v).apply()
    fun keepSentIds(context: Context): Set<Long> =
        (sp(context).getString("keep_sent", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.toSet()
    fun setKeepSentIds(context: Context, ids: Set<Long>) =
        sp(context).edit().putString("keep_sent", ids.joinToString(",")).apply()

    /**
     * Items handed to Keep but NOT yet confirmed by the user. They count as "sent" only after
     * the user taps "হ্যাঁ, সেভ হয়েছে" — Keep cannot tell another app whether a note was saved.
     */
    fun keepPendingIds(context: Context): Set<Long> =
        (sp(context).getString("keep_pending", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.toSet()
    fun keepPendingCreate(context: Context) = sp(context).getBoolean("keep_pending_create", false)
    fun setKeepPending(context: Context, ids: Set<Long>, creating: Boolean) = sp(context).edit()
        .putString("keep_pending", ids.joinToString(",")).putBoolean("keep_pending_create", creating).apply()
    fun clearKeepPending(context: Context) =
        sp(context).edit().remove("keep_pending").remove("keep_pending_create").apply()

    // ---- recent emoji
    fun recentEmoji(context: Context): List<String> =
        (sp(context).getString("recent_emoji_list", "") ?: "").split(' ').filter { it.isNotBlank() }
    fun addRecentEmoji(context: Context, emoji: String) {
        val list = listOf(emoji) + recentEmoji(context).filter { it != emoji }
        sp(context).edit().putString("recent_emoji_list", list.take(32).joinToString(" ")).apply()
    }
    fun clearRecentEmoji(context: Context) = sp(context).edit().remove("recent_emoji_list").apply()
}
