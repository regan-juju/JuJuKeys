package com.reganbarua.jujukeys.keyboard

import android.content.Context
import android.graphics.Paint

/** One emoji; [alternates] = skin tones / genders (long-press), empty if none. */
class EmojiItem(val emoji: String, val words: String, val alternates: List<String>)

class EmojiGroup(val name: String, val items: List<EmojiItem>)

/**
 * All emoji (Unicode 16 — the same set as the iPhone keyboard), in the standard order,
 * from assets/emoji.txt (Google emoji-metadata, Apache 2.0). Emoji the phone cannot draw
 * are left out, so no empty boxes appear. Loaded once, in the background.
 */
object EmojiRepo {
    @Volatile var groups: List<EmojiGroup> = emptyList(); private set

    /** Bangla names for the groups, in file order. */
    private val bnNames = mapOf(
        "Smileys and emotions" to "স্মাইলি ও আবেগ",
        "People" to "মানুষ",
        "Animals and nature" to "প্রাণী ও প্রকৃতি",
        "Food and drink" to "খাবার ও পানীয়",
        "Travel and places" to "ভ্রমণ ও স্থান",
        "Activities and events" to "খেলা ও উৎসব",
        "Objects" to "বস্তু",
        "Symbols" to "প্রতীক",
        "Flags" to "পতাকা",
    )

    fun load(context: Context) {
        if (groups.isNotEmpty()) return
        val paint = Paint()
        val out = ArrayList<EmojiGroup>()
        var name = ""
        var items = ArrayList<EmojiItem>()
        fun flush() { if (name.isNotEmpty() && items.isNotEmpty()) out.add(EmojiGroup(bnNames[name] ?: name, items)) }
        runCatching {
            context.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty()) continue
                    if (line[0] == '@') { flush(); name = line.substring(1); items = ArrayList(); continue }
                    val parts = line.split('\t')
                    val e = parts[0]
                    if (!paint.hasGlyph(e)) continue
                    val alts = parts.getOrNull(2)?.split(' ')?.filter { it.isNotEmpty() && paint.hasGlyph(it) } ?: emptyList()
                    items.add(EmojiItem(e, parts.getOrNull(1) ?: "", if (alts.size > 1) alts else emptyList()))
                }
            }
            flush()
        }
        groups = out
    }

    /** English search: "smile", "heart", "cat" … */
    fun search(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<String>(); val contains = ArrayList<String>()
        for (g in groups) for (it in g.items) {
            val w = it.words
            when {
                w.startsWith(q) || w.contains(" $q") -> starts.add(it.emoji)
                w.contains(q) -> contains.add(it.emoji)
            }
        }
        return (starts + contains).take(60)
    }
}
