package com.reganbarua.jujukeys.suggest

/**
 * Which QWERTY letters sit next to each other. A finger that slips usually hits a NEIGHBOUR of the key it
 * aimed at (g → h), so a correction that undoes such a slip is far more likely right than one that swaps
 * in a letter from the other side of the keyboard (g → j is NOT a slip; g → h is). Pure Kotlin, unit-tested.
 */
object KeyNeighbors {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    /** True when [a] and [b] are different letters on touching keys (same row ±1, or the stagger row above/below). */
    fun adjacent(a: Char, b: Char): Boolean {
        val x = pos(a.lowercaseChar()) ?: return false
        val y = pos(b.lowercaseChar()) ?: return false
        if (x == y) return false
        val (r1, c1) = x; val (r2, c2) = y
        return when {
            r1 == r2 -> Math.abs(c1 - c2) == 1
            // a key on the lower row touches the two keys above it: column c and c + 1
            r2 == r1 + 1 -> c2 == c1 || c2 == c1 - 1
            r1 == r2 + 1 -> c1 == c2 || c1 == c2 - 1
            else -> false
        }
    }

    /** How much to trust a correction that REPLACES [typed] by [wanted]: 1 for a slip to a neighbour, less for a far key. */
    fun replaceWeight(typed: Char, wanted: Char): Double = if (adjacent(typed, wanted)) 1.0 else FAR_KEY

    /** A replacement by a letter far away on the keyboard counts this much of its frequency. */
    const val FAR_KEY = 0.3

    private fun pos(c: Char): Pair<Int, Int>? {
        for ((r, row) in rows.withIndex()) { val i = row.indexOf(c); if (i >= 0) return r to i }
        return null
    }
}
