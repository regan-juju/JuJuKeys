package com.reganbarua.jujukeys.textrep

import android.content.Context
import com.reganbarua.jujukeys.security.CryptoBox
import org.json.JSONArray
import org.json.JSONObject

/**
 * The replacement list, kept on the phone only (not in backups). Normal phrases are stored as
 * written; a password entry is stored ONLY encrypted (Android Keystore, [CryptoBox]).
 * The very first time, one entry is added: omw → On my way!
 */
class ReplaceStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    sealed class Result {
        object Ok : Result()
        object BadShortcut : Result()
        object Duplicate : Result()
        object EmptyPhrase : Result()
        object CryptoFailed : Result()
    }

    @Synchronized
    fun load(): List<Rep> {
        val list = parse(prefs.getString("list", null))
        if (!prefs.getBoolean("seeded", false)) {
            val seeded = list + Rep(System.currentTimeMillis(), "omw", "On my way!")
            write(seeded)
            prefs.edit().putBoolean("seeded", true).apply()
            return seeded
        }
        return list
    }

    /** The real phrase: for a password entry it is decrypted (only call after the phone lock). */
    fun reveal(rep: Rep): String? = if (rep.password) rep.enc?.let { CryptoBox.decrypt(it) } else rep.phrase

    /** [phrase] null while editing a password entry = keep the saved password. */
    @Synchronized
    fun save(id: Long?, shortcutRaw: String, phrase: String?, password: Boolean): Result {
        val shortcut = shortcutRaw.trim()
        if (!ReplaceMatcher.validShortcut(shortcut)) return Result.BadShortcut
        val list = load().toMutableList()
        if (ReplaceMatcher.duplicate(list, shortcut, id)) return Result.Duplicate
        val old = id?.let { i -> list.firstOrNull { it.id == i } }
        val rep: Rep = if (password) {
            val enc = if (phrase == null) old?.enc else {
                if (phrase.isEmpty()) return Result.EmptyPhrase
                CryptoBox.encryptVerified(phrase) ?: return Result.CryptoFailed
            }
            if (enc == null) return Result.EmptyPhrase
            Rep(old?.id ?: System.currentTimeMillis(), shortcut, "", true, enc)
        } else {
            val p = phrase ?: old?.phrase ?: ""
            if (p.isBlank()) return Result.EmptyPhrase
            Rep(old?.id ?: System.currentTimeMillis(), shortcut, p, false, null)
        }
        if (old != null) list[list.indexOf(old)] = rep else list.add(rep)
        write(list)
        return Result.Ok
    }

    @Synchronized
    fun delete(id: Long) { write(load().filter { it.id != id }) }

    private fun write(list: List<Rep>) {
        val arr = JSONArray()
        for (r in list) arr.put(JSONObject().apply {
            put("id", r.id); put("s", r.shortcut); put("p", if (r.password) "" else r.phrase)
            put("pw", r.password); if (r.enc != null) put("e", r.enc)
        })
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun parse(json: String?): List<Rep> {
        if (json.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Rep(o.getLong("id"), o.getString("s"), o.optString("p", ""), o.optBoolean("pw", false), o.optString("e", "").ifEmpty { null })
            }
        }.getOrDefault(emptyList())
    }

    companion object { const val FILE = "jujukeys_textrep" }
}
