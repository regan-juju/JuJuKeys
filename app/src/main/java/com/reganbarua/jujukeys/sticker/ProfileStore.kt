package com.reganbarua.jujukeys.sticker

import android.content.Context
import java.io.File

/**
 * Saves the sticker profiles (files/stickers/profiles.txt) and the recently opened ones.
 * Every change is load → change → save under one lock, written atomically (tmp + rename).
 */
object ProfileStore {
    private const val FILE = "profiles.txt"
    private const val PREFS = "jujukeys_stickers"
    private const val RECENT = "recent_profiles"
    private const val LAST = "last_profile"
    private val lock = Any()
    private var counter = 0

    private fun file(ctx: Context) = File(StickerStore.dir(ctx), FILE)

    fun load(ctx: Context): ProfileTree = synchronized(lock) {
        runCatching { ProfileTree.parse(file(ctx).readText()) }.getOrDefault(ProfileTree())
    }

    /** Applies [change] and saves; returns the new tree. */
    fun update(ctx: Context, change: (ProfileTree) -> ProfileTree): ProfileTree = synchronized(lock) {
        val now = change(load(ctx))
        val f = file(ctx); val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(now.format())
        if (!tmp.renameTo(f)) { f.writeText(now.format()); tmp.delete() }
        now
    }

    fun newId(): String = synchronized(lock) { "p${System.currentTimeMillis()}_${counter++}" }

    /** Creates a profile and returns its id. */
    fun create(ctx: Context, name: String, parent: String?): String {
        val id = newId()
        update(ctx) { it.add(id, name, parent) }
        return id
    }

    // ---- where the keyboard was last, and recently opened profiles (for quick chips)
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastOpened(ctx: Context): String? = sp(ctx).getString(LAST, null)

    fun recent(ctx: Context): List<String> =
        sp(ctx).getString(RECENT, "").orEmpty().split('|').filter { it.isNotBlank() }

    fun opened(ctx: Context, id: String?) {
        val r = if (id == null) recent(ctx) else (listOf(id) + recent(ctx).filter { it != id }).take(8)
        sp(ctx).edit().putString(LAST, id).putString(RECENT, r.joinToString("|")).apply()
    }
}
