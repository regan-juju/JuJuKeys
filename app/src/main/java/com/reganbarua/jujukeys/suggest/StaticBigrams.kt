package com.reganbarua.jujukeys.suggest

/**
 * Ready-made Bangla word pairs ("আমি" → ভালো, তোমাকে …) counted from Bangla Wikipedia (assets/bigram_bn.txt):
 * for each word, the few words that most often come right after it. Pure Kotlin; read-only.
 * Lines: "word<TAB>next1 next2 next3 …" (most common first).
 */
class StaticBigrams {
    private val map = HashMap<String, List<String>>()
    val size: Int get() = map.size

    fun load(text: String) {
        map.clear()
        text.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val next = line.substring(tab + 1).trim().split(' ').filter { it.isNotEmpty() }
            if (next.isNotEmpty()) map[line.substring(0, tab)] = next
        }
    }

    /** The words that most often follow [previous] (at most [n]). */
    fun next(previous: String?, n: Int): List<String> =
        if (previous.isNullOrBlank()) emptyList() else (map[previous] ?: emptyList()).take(n)

    /** [next] as a rank score for ranking suggestions: the likeliest word 5, then 4 … 1. */
    fun ranks(previous: String?): Map<String, Int> {
        val list = next(previous, 5)
        val out = HashMap<String, Int>()
        list.forEachIndexed { i, w -> out[w] = 5 - i }
        return out
    }
}
