package com.reganbarua.jujukeys.suggest

/**
 * Word suggestions, like Gboard / iPhone.
 *
 * English: words starting with what is typed, most frequent first.
 * বাংলা:   the Avro result first, then dictionary words that "sound the same".
 *          Matching uses a loose skeleton (no kar/hasanta, শ=ষ=স, ন=ণ, …) so that
 *          amra → আমরা, tomake → তোমাকে, bhalobashi → ভালোবাসি are found.
 *
 * Pure Kotlin: dictionaries are passed in as lines "word<TAB>frequency".
 */
class Suggester(englishLines: Sequence<String>, banglaLines: Sequence<String>) {

    private class Entry(val word: String, val key: String, val freq: Long)

    private val english: List<Entry> = englishLines.mapNotNull { parse(it) { w -> w } }.toList()
    private val bangla: List<Entry> = banglaLines.mapNotNull { parse(it) { w -> skeleton(w) } }.toList()

    private inline fun parse(line: String, keyOf: (String) -> String): Entry? {
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        val w = line.substring(0, tab)
        val f = line.substring(tab + 1).trim().toLongOrNull() ?: return null
        return Entry(w, keyOf(w), f)
    }

    /** The user's own words (settings → অভিধান); always suggested first. */
    @Volatile private var userEnglish: List<String> = emptyList()
    @Volatile private var userBangla: List<Pair<String, String>> = emptyList()

    fun setUserWords(words: List<String>) {
        userEnglish = words.filter { w -> w.all { it.code < 128 } }
        userBangla = words.filter { w -> w.any { it in '\u0980'..'\u09FF' } }.map { it to skeleton(it) }
    }

    /** Up to [n] English words (lowercase) completing [prefix]. */
    fun english(prefix: String, n: Int = 3): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val out = ArrayList<String>(n)
        userEnglish.filter { it.lowercase().startsWith(p) }.take(n).forEach { out.add(it) }
        // exact word first, then completions by frequency (list is already sorted by frequency)
        english.firstOrNull { it.word == p }?.let { if (out.size < n && it.word !in out) out.add(it.word) }
        for (e in english) {
            if (out.size >= n) break
            if (e.word != p && e.word.startsWith(p) && e.word !in out) out.add(e.word)
        }
        return out
    }

    /** Up to [n] Bangla words for the Avro conversion [converted]. */
    fun bangla(converted: String, n: Int = 3): List<String> {
        if (converted.isEmpty()) return emptyList()
        val key = skeleton(converted)
        if (key.isEmpty()) return listOf(converted)
        val same = ArrayList<Entry>()
        val longer = ArrayList<Entry>()
        for (e in bangla) {
            if (e.key == key) same.add(e)
            else if (e.key.startsWith(key) && longer.size < 50) longer.add(e)
        }
        val out = LinkedHashSet<String>()
        out.add(converted)
        userBangla.filter { it.second.startsWith(key) }.forEach { if (out.size < n) out.add(it.first) }
        (same.sortedByDescending { it.freq } + longer).forEach { if (out.size < n) out.add(it.word) }
        return out.toList()
    }

    companion object {
        private const val DROP = "ািীুূৃৄেৈোৌ্ঁ‌‍ৗ"

        /** Loose sound-alike key for a Bangla word. */
        fun skeleton(word: String): String {
            // Decompose precomposed nukta letters so both spellings match.
            val w = word.replace("\u09DC", "\u09A1\u09BC")
                .replace("\u09DD", "\u09A2\u09BC")
                .replace("\u09DF", "\u09AF\u09BC")
            return buildString(w.length) {
                var i = 0
                while (i < w.length) {
                    val ch = w[i]
                    val nukta = i + 1 < w.length && w[i + 1] == '\u09BC'
                    if (nukta) {
                        append(if (ch == '\u09AF') 'Y' else 'R')   // য় → Y, ড়/ঢ় → R
                        i += 2
                        continue
                    }
                    if (ch == '্' && i + 1 < w.length && w[i + 1] == 'য') { i += 2; continue } // য-ফলা
                    i++
                    if (ch in DROP) continue
                    val mapped =
                        when (ch) {
                            'শ', 'ষ' -> 'স'
                            'ণ' -> 'ন'
                            'য' -> 'জ'
                            'ঈ' -> 'ই'
                            'ঊ' -> 'উ'
                            'আ', 'ও', 'ঔ' -> 'অ'
                            'ঐ' -> 'এ'
                            'ৎ' -> 'ত'
                            'ঙ' -> 'ং'
                            'খ' -> 'ক'; 'ঘ' -> 'গ'; 'ছ' -> 'চ'; 'ঝ' -> 'জ'
                            'ঠ' -> 'ট'; 'ঢ' -> 'ড'; 'থ' -> 'ত'; 'ধ' -> 'দ'
                            'ফ' -> 'প'; 'ভ' -> 'ব'
                            else -> ch
                        }
                    // doubled letters count once: ধন্নবাদ ≈ ধন্যবাদ
                    if (isNotEmpty() && this[length - 1] == mapped) continue
                    append(mapped)
                }
            }
        }
    }
}
