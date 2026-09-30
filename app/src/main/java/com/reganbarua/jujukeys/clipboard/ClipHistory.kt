package com.reganbarua.jujukeys.clipboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class ClipItem(val id: Long, val text: String, val pinned: Boolean)

/**
 * Clipboard history kept on the phone only (never uploaded).
 * Pinned items stay; others are limited to the newest [MAX_UNPINNED].
 */
class ClipHistory(private val context: Context) {

    val items = mutableStateListOf<ClipItem>()
    private val prefs = context.getSharedPreferences("jujukeys_clipboard", Context.MODE_PRIVATE)

    init { load() }

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val existing = items.firstOrNull { it.text == t }
        if (existing != null) {
            if (items.indexOf(existing) == 0) return
            items.remove(existing)
            items.add(0, existing)
        } else {
            items.add(0, ClipItem(System.currentTimeMillis(), t, false))
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

    private fun load() {
        val raw = prefs.getString("items", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(ClipItem(o.getLong("id"), o.getString("text"), o.optBoolean("pinned")))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("pinned", it.pinned)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    companion object {
        const val MAX_UNPINNED = 40
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
