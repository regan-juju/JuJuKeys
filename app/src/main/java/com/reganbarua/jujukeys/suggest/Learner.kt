package com.reganbarua.jujukeys.suggest

/**
 * Learns, on the phone only, which words the user types and which word usually comes
 * next ("আমি" → "তোমাকে"). Used for next-word suggestions and to rank suggestions.
 * Pure Kotlin; the service saves/loads it as plain text in the app's private storage.
 */
class Learner {
    private val counts = HashMap<String, Int>()
    private val next = HashMap<String, HashMap<String, Int>>()

    @Synchronized
    fun learn(previous: String?, word: String) {
        if (word.isBlank() || word.length > 40) return
        counts[word] = (counts[word] ?: 0) + 1
        if (previous != null && previous.isNotBlank()) {
            val m = next.getOrPut(previous) { HashMap() }
            m[word] = (m[word] ?: 0) + 1
        }
        if (counts.size > MAX_WORDS) trim()
    }

    @Synchronized
    fun nextWords(previous: String, n: Int): List<String> =
        next[previous]?.entries?.sortedByDescending { it.value }?.take(n)?.map { it.key } ?: emptyList()

    @Synchronized
    fun count(word: String): Int = counts[word] ?: 0

    @Synchronized
    fun snapshotCounts(): Map<String, Int> = HashMap(counts)

    @Synchronized
    fun clear() { counts.clear(); next.clear() }

    private fun trim() {
        val keep = counts.entries.sortedByDescending { it.value }.take(MAX_WORDS * 3 / 4).map { it.key }.toHashSet()
        counts.keys.retainAll(keep)
        next.keys.retainAll(keep)
    }

    /** Lines: "w<TAB>word<TAB>count" and "n<TAB>prev<TAB>word<TAB>count". */
    @Synchronized
    fun serialize(): String = buildString {
        counts.forEach { (w, c) -> append("w\t").append(w).append('\t').append(c).append('\n') }
        next.forEach { (p, m) -> m.forEach { (w, c) -> append("n\t").append(p).append('\t').append(w).append('\t').append(c).append('\n') } }
    }

    @Synchronized
    fun load(text: String) {
        counts.clear(); next.clear()
        text.lineSequence().forEach { line ->
            val p = line.split('\t')
            when {
                p.size == 3 && p[0] == "w" -> p[2].toIntOrNull()?.let { counts[p[1]] = it }
                p.size == 4 && p[0] == "n" -> p[3].toIntOrNull()?.let { next.getOrPut(p[1]) { HashMap() }[p[2]] = it }
            }
        }
    }

    companion object { const val MAX_WORDS = 8000 }
}
