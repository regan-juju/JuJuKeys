package com.reganbarua.jujukeys.suggest

/**
 * Word suggestions, like Gboard.
 *
 * English: words starting with what is typed, most frequent first.
 * বাংলা:   the Avro result first, then dictionary words that "sound the same".
 *          Matching uses a loose skeleton (no kar/hasanta, শ=ষ=স, ন=ণ, …) so that
 *          amra → আমরা, tomake → তোমাকে, bhalobashi → ভালোবাসি are found.
 * Next word: learned on the phone from what the user types (never sent anywhere).
 *
 * Fast: words are kept sorted, so a prefix is found by binary search instead of
 * scanning the whole list on every key press.
 *
 * Pure Kotlin: dictionaries are passed in as lines "word<TAB>frequency".
 */
class Suggester(englishLines: Sequence<String>, banglaLines: Sequence<String>) {

    private class Entry(val word: String, val key: String, val freq: Long)

    /** Sorted by key, so all words with the same prefix sit next to each other. */
    private val english: Array<Entry> =
        englishLines.mapNotNull { parse(it) { w -> w } }.sortedBy { it.key }.toList().toTypedArray()
    private val bangla: Array<Entry> =
        banglaLines.mapNotNull { parse(it) { w -> skeleton(w) } }.sortedBy { it.key }.toList().toTypedArray()

    /** Most frequent words, for when nothing is typed yet. */
    val topEnglish: List<String> = english.sortedByDescending { it.freq }.take(30).map { it.word }
    val topBangla: List<String> = bangla.sortedByDescending { it.freq }.take(30).map { it.word }

    private val cache = HashMap<String, List<Entry>>()

    // ------------------------------------------------------------------ extra (rare) Bangla words
    // 141k correctly spelled but rare words (Avro dictionary). Stored lean — two plain arrays,
    // ALREADY sorted by sound-alike key in the asset file (no work at load time), and used only
    // to fill the list when the common words are not enough. Loaded a few seconds after start.

    @Volatile private var extraKeys: Array<String> = emptyArray()
    @Volatile private var extraWords: Array<String> = emptyArray()
    val extraSize: Int get() = extraWords.size

    /** Lines "word<TAB>freq<TAB>key", sorted by key (made by the build, see DictionaryFilesTest). */
    fun loadExtra(lines: Sequence<String>) {
        val keys = ArrayList<String>(150_000); val words = ArrayList<String>(150_000)
        for (line in lines) {
            if (line.isEmpty() || line[0] == '#') continue
            val a = line.indexOf('\t'); if (a <= 0) continue
            val b = line.indexOf('\t', a + 1); if (b <= 0) continue
            words.add(line.substring(0, a)); keys.add(line.substring(b + 1))
        }
        extraWords = words.toTypedArray(); extraKeys = keys.toTypedArray()
    }

    private fun extraMatches(key: String, max: Int): List<String> {
        val ks = extraKeys; val ws = extraWords
        if (ks.isEmpty() || max <= 0) return emptyList()
        var lo = 0; var hi = ks.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (ks[mid] < key) lo = mid + 1 else hi = mid }
        val out = ArrayList<String>(max)
        var i = lo
        while (i < ks.size && out.size < max && ks[i].startsWith(key)) { out.add(ws[i]); i++ }
        return out
    }

    private inline fun parse(line: String, keyOf: (String) -> String): Entry? {
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        val w = nfc(line.substring(0, tab))
        val f = line.substring(tab + 1).trim().toLongOrNull() ?: return null
        return Entry(w, keyOf(w), f)
    }

    // ------------------------------------------------------------------ user & learned words

    /** The user's own words (settings → অভিধান); always suggested first. */
    @Volatile private var userEnglish: List<String> = emptyList()
    @Volatile private var userBangla: List<Pair<String, String>> = emptyList()

    fun setUserWords(words: List<String>) {
        userEnglish = words.filter { w -> w.all { it.code < 128 } }
        userBangla = words.filter { w -> w.any { it in 'ঀ'..'৿' } }.map { it to skeleton(it) }
    }

    /** Words the user often types get a boost. Filled by [Learner]. */
    @Volatile var learnedCounts: Map<String, Int> = emptyMap()
        set(value) {
            field = value
            // typed at least twice → offered while typing, even if it is not in the dictionary
            val often = value.entries.filter { it.value >= 2 }.sortedByDescending { it.value }
            learnedBn = often.filter { e -> e.key.any { it in 'ঀ'..'৿' } }.map { Triple(it.key, skeleton(it.key), it.value) }
            learnedEn = often.filter { e -> e.key.all { it.code < 128 } && e.key.any { it.isLetter() } }.map { it.key to it.value }
        }
    @Volatile private var learnedBn: List<Triple<String, String, Int>> = emptyList()
    @Volatile private var learnedEn: List<Pair<String, Int>> = emptyList()

    /** The user's most-typed words (for the suggestion bar before anything is typed). */
    fun favourites(bangla: Boolean, n: Int): List<String> =
        if (bangla) learnedBn.filter { it.third >= 3 }.take(n).map { it.first }
        else learnedEn.filter { it.second >= 3 && it.first.length > 1 }.take(n).map { it.first }

    /** True if the word is in the built-in dictionary. */
    fun known(word: String): Boolean =
        if (word.any { it in 'ঀ'..'৿' }) banglaFreq(word) > 0 else englishFreq(word) > 0

    // ------------------------------------------------------------------ lookup

    /** Index range [from, to) of entries whose key starts with [prefix]. */
    private fun range(arr: Array<Entry>, prefix: String): IntRange {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].key < prefix) lo = mid + 1 else hi = mid
        }
        val start = lo
        hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].key.startsWith(prefix) || arr[mid].key < prefix) lo = mid + 1 else hi = mid
        }
        return start until lo
    }

    /** Top [limit] entries by frequency whose key starts with [prefix] (cached for short prefixes). */
    private fun top(arr: Array<Entry>, tag: String, prefix: String, limit: Int): List<Entry> {
        val cacheKey = "$tag|$prefix"
        if (prefix.length <= 2) synchronized(cache) { cache[cacheKey]?.let { return it } }
        val r = range(arr, prefix)
        val list = if (r.isEmpty()) emptyList() else {
            val slice = ArrayList<Entry>(minOf(r.last - r.first + 1, 20000))
            for (i in r) slice.add(arr[i])
            slice.sortedByDescending { it.freq }.take(limit)
        }
        if (prefix.length <= 2) synchronized(cache) { cache[cacheKey] = list }
        return list
    }

    /** Up to [n] English words (lowercase) completing [prefix]. */
    fun english(prefix: String, n: Int = 3): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        userEnglish.filter { it.lowercase().startsWith(p) }.forEach { if (out.size < n) out.add(it) }
        val found = top(english, "en", p, 40)
        // exact word first, then words the user types often, then by frequency
        found.firstOrNull { it.word == p }?.let { if (out.size < n) out.add(it.word) }
        // words the user types often (dictionary or not), most-typed first
        learnedEn.asSequence().filter { it.first.startsWith(p) && it.first != p }.take(n)
            .forEach { if (out.size < n) out.add(it.first) }
        val learned = learnedCounts
        found.sortedByDescending { (learned[it.word] ?: 0) * 1_000_000_000L + it.freq }
            .forEach { if (out.size < n) out.add(it.word) }
        return out.toList()
    }

    /** Frequency of an English word, or 0 if it is not in the dictionary. */
    fun englishFreq(word: String): Long {
        val w = word.lowercase()
        val r = range(english, w)
        for (i in r) if (english[i].key == w) return english[i].freq
        return 0
    }

    /**
     * Auto-correction (English): the most common real word one small typing mistake away
     * (a letter missing, extra, swapped or wrong). Null when [word] is fine or nothing fits.
     */
    fun correct(word: String): String? {
        val w = word.lowercase()
        if (w.length < 3 || userEnglish.any { it.equals(w, true) }) return null
        val own = englishFreq(w)   // common misspellings are in the list too, but far rarer
        val letters = "abcdefghijklmnopqrstuvwxyz"
        val edits = HashSet<String>()
        for (i in 0..w.length) {
            if (i < w.length) edits.add(w.removeRange(i, i + 1))                       // delete
            if (i < w.length - 1) edits.add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)) // swap
            for (c in letters) {
                if (i < w.length) edits.add(w.substring(0, i) + c + w.substring(i + 1))  // replace
                edits.add(w.substring(0, i) + c + w.substring(i))                         // insert
            }
        }
        var best: String? = null
        var bestF = 0L
        for (e in edits) {
            val f = englishFreq(e)
            if (f > bestF) { bestF = f; best = e }
        }
        return if (bestF >= 50 && bestF > own * 50) best else null
    }

    /** How common a Bangla word is: its frequency, 1 if only in the extra list, 0 if unknown. */
    fun banglaFreq(word: String): Long {
        val w = nfc(word); val key = skeleton(w)
        for (i in range(bangla, key)) if (bangla[i].key == key && bangla[i].word == w) return bangla[i].freq
        val ks = extraKeys; val ws = extraWords
        var lo = 0; var hi = ks.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (ks[mid] < key) lo = mid + 1 else hi = mid }
        while (lo < ks.size && ks[lo] == key) { if (ws[lo] == w) return 1; lo++ }
        return 0
    }

    /**
     * iPhone-style fix: when Avro's spelling is not a real word, or a very rare one
     * ("jemon" → জেমন), and a common word sounds the same (যেমন), return that word.
     * The closest spelling wins (amra → আমরা, not আমার); null = keep Avro's spelling.
     */
    fun autoFix(converted: String, minFreq: Long = 20): String? {
        if (converted.isEmpty()) return null
        val c = nfc(converted)
        val key = skeleton(c)
        if (key.length < 2) return null
        val own = banglaFreq(c)
        val need = maxOf(minFreq, own * 50)            // a real, common Avro word is never replaced
        var best: Entry? = null; var bestD = Int.MAX_VALUE
        for (i in range(bangla, key)) {
            val e = bangla[i]
            if (e.key != key || e.freq < need || e.word == c) continue
            val d = distance(c, e.word)
            if (d > 3) continue
            val b = best
            if (b == null || d < bestD || (d == bestD && e.freq > b.freq)) { best = e; bestD = d }
        }
        return best?.word
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /** Up to [n] Bangla words for the Avro conversion [converted]. */
    fun bangla(converted: String, n: Int = 3): List<String> {
        if (converted.isEmpty()) return emptyList()
        val key = skeleton(converted)
        if (key.isEmpty()) return listOf(converted)
        val learned = learnedCounts
        val found = top(bangla, "bn", key, 60)
        val same = found.filter { it.key == key }
        val longer = found.filter { it.key != key }
        val rank = { e: Entry -> (learned[e.word] ?: 0) * 1_000_000_000L + e.freq }
        val out = LinkedHashSet<String>()
        out.add(nfc(converted))
        userBangla.filter { it.second.startsWith(key) }.forEach { if (out.size < n) out.add(it.first) }
        // words the user types often (even ones not in the dictionary), most-typed first
        learnedBn.asSequence().filter { it.second.startsWith(key) }.take(n).forEach { if (out.size < n) out.add(it.first) }
        (same.sortedByDescending(rank) + longer.sortedByDescending(rank)).forEach { if (out.size < n) out.add(it.word) }
        if (out.size < n) extraMatches(key, n - out.size + 4).forEach { if (out.size < n) out.add(it) }
        return out.toList()
    }

    companion object {
        /** One Unicode spelling, so the same word never shows twice. */
        fun nfc(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

        private const val DROP = "ািীুূৃৄেৈোৌ্ঁ‌‍ৗ"

        /** Loose sound-alike key for a Bangla word. */
        fun skeleton(word: String): String {
            // Decompose precomposed nukta letters so both spellings match.
            val w = word.replace("ড়", "ড়")
                .replace("ঢ়", "ঢ়")
                .replace("য়", "য়")
            return buildString(w.length) {
                var i = 0
                while (i < w.length) {
                    val ch = w[i]
                    val nukta = i + 1 < w.length && w[i + 1] == '়'
                    if (nukta) {
                        append(if (ch == 'য') 'Y' else 'R')   // য় → Y, ড়/ঢ় → R
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
