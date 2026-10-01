package com.reganbarua.jujukeys.suggest

/**
 * Fixed suggestions for common Roman spellings that Avro's rules spell differently
 * (chottogram → চট্টগ্রাম, bilaichori → বিলাইছড়ি). The Avro result itself is NOT changed —
 * these words are only added as an extra suggestion.
 *
 * Lines: "roman<TAB>word"; lines starting with # are notes.
 * Match: exactly (3+ letters), or as the start of a longer spelling once at least 5 letters
 * and at least half of that spelling are typed (so "chot" does not push চট্টগ্রাম over ছোট).
 */
class RomanAliases(lines: Sequence<String>) {
    private val entries: List<Pair<String, String>> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") && '\t' in it }
        .map { val t = it.indexOf('\t'); it.substring(0, t).trim().lowercase() to Suggester.nfc(it.substring(t + 1).trim()) }
        .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        .sortedBy { it.first }
        .toList()

    val size: Int get() = entries.size

    fun lookup(roman: String, max: Int = 2): List<String> {
        val r = roman.lowercase()
        if (r.length < 3) return emptyList()
        val out = LinkedHashSet<String>()
        entries.forEach { (k, w) -> if (k == r) out.add(w) }
        if (r.length >= 5) {
            entries.forEach { (k, w) -> if (k.length > r.length && k.startsWith(r) && r.length * 2 >= k.length) out.add(w) }
        }
        return out.take(max)
    }

    companion object {
        /** Avro result first (unchanged behaviour), then the alias words, then the rest. */
        fun merge(base: List<String>, aliases: List<String>): List<String> {
            if (aliases.isEmpty() || base.isEmpty()) return if (base.isEmpty()) aliases else base
            val out = LinkedHashSet<String>()
            out.add(base[0])
            aliases.forEach { out.add(it) }
            base.drop(1).forEach { out.add(it) }
            return out.toList()
        }
    }
}
