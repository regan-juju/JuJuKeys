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

    // ------------------------------------------------------------------ extra (rare) words
    // 333k Bangla and 90k English rare words. Each list is kept as the raw UTF-8 bytes of its
    // asset file plus a few int arrays (where each line starts) — NO String per word. That is about
    // 4x less memory and creates no garbage while loading, so the keyboard does not stutter.
    // The files are already sorted, so lookups are a binary search on the bytes.
    // Loaded a few seconds after start and used only to fill the list when common words are not enough.

    private class ByteTable(private val bytes: ByteArray, private val cols: Int) {
        private val start: IntArray
        private val tab1: IntArray
        private val tab2: IntArray
        private val end: IntArray
        val size: Int

        init {
            var n = 0
            scan { _, _, _, _ -> n++ }
            size = n
            start = IntArray(n); tab1 = IntArray(n); tab2 = IntArray(if (cols >= 3) n else 0); end = IntArray(n)
            var i = 0
            scan { st, t1, t2, e -> start[i] = st; tab1[i] = t1; if (cols >= 3) tab2[i] = t2; end[i] = e; i++ }
        }

        /** Calls [f] for every data line: start, first tab, second tab (or -1), end. Skips comments and bad lines. */
        private inline fun scan(f: (Int, Int, Int, Int) -> Unit) {
            val len = bytes.size
            var p = 0
            while (p < len) {
                var e = p
                while (e < len && bytes[e] != 10.toByte()) e++
                if (e > p && bytes[p] != 35.toByte()) {            // not empty, not '#'
                    var t1 = p
                    while (t1 < e && bytes[t1] != 9.toByte()) t1++
                    var t2 = -1
                    if (cols >= 3 && t1 < e) { t2 = t1 + 1; while (t2 < e && bytes[t2] != 9.toByte()) t2++ }
                    val ok = t1 > p && t1 < e && (cols < 3 || (t2 in (t1 + 1) until e))
                    if (ok) f(p, t1, t2, e)
                }
                p = e + 1
            }
        }

        private fun keyStart(i: Int) = if (cols >= 3) tab2[i] + 1 else start[i]
        private fun keyEnd(i: Int) = if (cols >= 3) end[i] else tab1[i]
        fun keyLength(i: Int) = keyEnd(i) - keyStart(i)

        fun word(i: Int): String = String(bytes, start[i], tab1[i] - start[i], Charsets.UTF_8)

        fun count(i: Int): Int {
            if (cols < 3) return 1
            var v = 0
            for (j in tab1[i] + 1 until tab2[i]) { val d = bytes[j] - 48; if (d !in 0..9 || v > 100_000_000) return 1; v = v * 10 + d }
            return if (v < 1) 1 else v
        }

        /** <0 if the key of line [i] sorts before [p], 0 if equal, >0 if after. */
        fun compare(i: Int, p: ByteArray): Int {
            var a = keyStart(i); val e = keyEnd(i); var b = 0
            while (a < e && b < p.size) {
                val x = bytes[a].toInt() and 0xff; val y = p[b].toInt() and 0xff
                if (x != y) return x - y
                a++; b++
            }
            return (e - a) - (p.size - b)
        }

        fun startsWith(i: Int, p: ByteArray): Boolean {
            var a = keyStart(i)
            if (keyEnd(i) - a < p.size) return false
            for (b in p.indices) { if (bytes[a] != p[b]) return false; a++ }
            return true
        }

        /** First line whose key is >= [p]. */
        fun lowerBound(p: ByteArray): Int {
            var lo = 0; var hi = size
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (compare(mid, p) < 0) lo = mid + 1 else hi = mid }
            return lo
        }
    }

    @Volatile private var xBn: ByteTable? = null
    @Volatile private var xEn: ByteTable? = null
    val extraSize: Int get() = xBn?.size ?: 0
    val extraEnglishSize: Int get() = xEn?.size ?: 0

    /** Bangla list: lines "word<TAB>count<TAB>key", sorted by key then count (made by the build, see DictionaryFilesTest). */
    fun loadExtra(lines: Sequence<String>) = loadExtraBytes(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
    fun loadExtraBytes(bytes: ByteArray) { xBn = ByteTable(bytes, 3) }

    /** English list: lines "word<TAB>1", sorted by word. */
    fun loadExtraEnglish(lines: Sequence<String>) = loadExtraEnglishBytes(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
    fun loadExtraEnglishBytes(bytes: ByteArray) { xEn = ByteTable(bytes, 2) }

    private fun extraEnglishMatches(prefix: String, max: Int): List<String> {
        val t = xEn ?: return emptyList()
        if (t.size == 0 || max <= 0) return emptyList()
        val p = prefix.toByteArray(Charsets.UTF_8)
        val found = ArrayList<Int>(64)
        var i = t.lowerBound(p)
        while (i < t.size && found.size < 300 && t.startsWith(i, p)) { found.add(i); i++ }
        return found.sortedBy { t.keyLength(it) }.take(max).map { t.word(it) }   // the shortest completions are the likeliest
    }

    private fun extraEnglishKnown(w: String): Boolean {
        val t = xEn ?: return false
        val p = w.toByteArray(Charsets.UTF_8)
        val i = t.lowerBound(p)
        return i < t.size && t.compare(i, p) == 0
    }

    private fun extraMatches(key: String, max: Int): List<String> {
        val t = xBn ?: return emptyList()
        if (t.size == 0 || max <= 0) return emptyList()
        val p = key.toByteArray(Charsets.UTF_8)
        // the most common words among those that start with [key] (up to 400 are looked at)
        val idx = ArrayList<Int>(64)
        var i = t.lowerBound(p)
        while (i < t.size && idx.size < 400 && t.startsWith(i, p)) { idx.add(i); i++ }
        return idx.sortedByDescending { t.count(it) }.take(max).map { t.word(it) }
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
        if (out.size < n) extraEnglishMatches(p, n - out.size + 2).forEach { if (out.size < n) out.add(it) }
        return out.toList()
    }

    /** Frequency of an English word: 1 if only in the extra list, 0 if it is not in the dictionary. */
    fun englishFreq(word: String): Long {
        val w = word.lowercase()
        val r = range(english, w)
        for (i in r) if (english[i].key == w) return english[i].freq
        return if (extraEnglishKnown(w)) 1 else 0
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
        val t = xBn ?: return 0
        val p = key.toByteArray(Charsets.UTF_8)
        var i = t.lowerBound(p)
        while (i < t.size && t.compare(i, p) == 0) { if (t.word(i) == w) return 1; i++ }
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
            // a typed conjunct (kkh → ক্ষ) is never "fixed" into an unrelated word: the change must be small for the length
            if (d > 3 || d * 2 > c.length) continue
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
