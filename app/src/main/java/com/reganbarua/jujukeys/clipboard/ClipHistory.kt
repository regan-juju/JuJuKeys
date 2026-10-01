package com.reganbarua.jujukeys.clipboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.reganbarua.jujukeys.security.CryptoBox
import org.json.JSONArray
import org.json.JSONObject

/** [sensitive] = the copying app marked it private (passwords, OTPs…): preview hidden, unlock needed. */
data class ClipItem(val id: Long, val text: String, val pinned: Boolean, val sensitive: Boolean = false)

/**
 * Clipboard history kept on the phone only (never uploaded), stored ENCRYPTED with a key in the
 * Android Keystore ([CryptoBox]). Pinned items stay; others are limited to the newest [MAX_UNPINNED].
 *
 * Old versions stored the list as plain text ("items"); it is moved into the encrypted form the
 * first time it is read, and the plain copy is removed only after the encrypted copy has been
 * written and read back successfully.
 */
class ClipHistory(private val context: Context) {

    val items = mutableStateListOf<ClipItem>()
    private val prefs = context.getSharedPreferences("jujukeys_clipboard", Context.MODE_PRIVATE)

    init { load() }

    /**
     * Keeps the copied text EXACTLY as it was (leading/trailing spaces, tabs and line breaks
     * included). A trimmed copy is used only to skip empty text and to spot duplicates.
     */
    fun add(text: String, sensitive: Boolean = false) {
        if (text.isBlank()) return
        val norm = text.trim()
        val existing = items.firstOrNull { it.text.trim() == norm }
        if (existing != null) {
            if (items.indexOf(existing) == 0 && existing.text == text && existing.sensitive == sensitive) return
            items.remove(existing)
            // newest copy wins, same id; once marked sensitive it stays sensitive
            items.add(0, existing.copy(text = text, sensitive = existing.sensitive || sensitive))
        } else {
            items.add(0, ClipItem(System.currentTimeMillis(), text, false, sensitive))
        }
        trim()
        save()
    }

    fun togglePin(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(pinned = !items[i].pinned); save() }
    }

    fun delete(id: Long) {
        items.removeAll { it.id == id }
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }

    /** Removes every sensitive item (pinned ones too). */
    fun clearSensitive() {
        items.removeAll { it.sensitive }
        save()
    }

    fun find(id: Long): ClipItem? = items.firstOrNull { it.id == id }

    private fun trim() {
        var unpinned = 0
        val it = items.listIterator()
        while (it.hasNext()) {
            val c = it.next()
            if (!c.pinned && ++unpinned > MAX_UNPINNED) it.remove()
        }
    }

    /** Re-read from storage (the settings screen may have changed it). */
    fun reload() {
        items.clear()
        load()
    }

    private fun parse(json: String): List<ClipItem> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ClipItem(o.getLong("id"), o.getString("text"), o.optBoolean("pinned"), o.optBoolean("sensitive"))
        }
    }.getOrDefault(emptyList())

    private fun toJson(list: List<ClipItem>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("pinned", it.pinned).put("sensitive", it.sensitive))
        }
        return arr.toString()
    }

    private fun load() {
        val enc = prefs.getString(KEY_ENC, null)
        if (enc != null) {
            val json = CryptoBox.decrypt(enc)
            if (json != null) items.addAll(parse(json))
            else {
                // Cannot be read any more (Keystore key gone, e.g. after a factory-level reset).
                // Keep the blob aside instead of overwriting it, and start a fresh history.
                prefs.edit().putString(KEY_UNREADABLE, enc).remove(KEY_ENC).commit()
            }
        }
        val legacy = prefs.getString(KEY_PLAIN, null) ?: return
        val old = parse(legacy)
        old.forEach { o -> if (items.none { it.id == o.id }) items.add(o) }
        // migrate: write encrypted, read it back, and only then delete the plain copy
        val e = CryptoBox.encryptVerified(toJson(items))
        if (e != null && prefs.edit().putString(KEY_ENC, e).commit() && prefs.getString(KEY_ENC, null) == e) {
            prefs.edit().remove(KEY_PLAIN).commit()
        }
        // else: the plain copy stays untouched, so nothing is lost; migration is retried next time
    }

    /** Saved only in encrypted form. If the Keystore fails, nothing new is written in plain text. */
    private fun save() {
        val e = CryptoBox.encrypt(toJson(items)) ?: return
        prefs.edit().putString(KEY_ENC, e).apply()
    }

    companion object {
        const val MAX_UNPINNED = 40
        const val KEY_PLAIN = "items"                 // old, unencrypted (migrated away)
        const val KEY_ENC = "items_enc"
        const val KEY_UNREADABLE = "items_enc_unreadable"
        const val KEEP_PACKAGE = "com.google.android.keep"

        /** Opens Google Keep's "save note" screen with [text] filled in. */
        fun sendToKeep(context: Context, text: String): Boolean {
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .setPackage(KEEP_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent); true
            } catch (e: ActivityNotFoundException) {
                openKeepInStore(context); false
            }
        }

        const val KEEP_NOTE_TITLE = "JuJuKeys ক্লিপবোর্ড"

        /**
         * Opens Keep's "save note" screen with EVERYTHING in the clipboard as one note
         * (one page). Pinned items first, then recent.
         */
        fun sendAllToKeep(context: Context, items: List<ClipItem>): Boolean {
            val ordered = items.filter { it.pinned } + items.filter { !it.pinned }
            val text = ordered.joinToString("\n\n") { it.text }
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, KEEP_NOTE_TITLE)
                .putExtra(Intent.EXTRA_TITLE, KEEP_NOTE_TITLE)
                .putExtra(Intent.EXTRA_TEXT, text)
                .setPackage(KEEP_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent); true
            } catch (e: ActivityNotFoundException) {
                openKeepInStore(context); false
            }
        }

        fun isKeepInstalled(context: Context): Boolean =
            context.packageManager.getLaunchIntentForPackage(KEEP_PACKAGE) != null

        /** Opens the Google Keep app. */
        fun openKeep(context: Context): Boolean {
            val launch = context.packageManager.getLaunchIntentForPackage(KEEP_PACKAGE)
            return if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            } else {
                openKeepInStore(context); false
            }
        }

        private fun openKeepInStore(context: Context) {
            val uri = Uri.parse("https://play.google.com/store/apps/details?id=$KEEP_PACKAGE")
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
