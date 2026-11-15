package com.reganbarua.jujukeys.keyboard

/**
 * Which key a finger touched. Pure arithmetic on arrays (no UI classes), so it is unit-tested.
 *
 * Fingers land a little BELOW the centre of the key they aim at, most of all when typing fast.
 * For letter keys the touch point is therefore moved up by [offset] pixels before looking up the key
 * (never for Shift / Space / Return / 123 …, which keep the exact point).
 */
internal object TouchPick {
    /** The key whose rectangle holds (x, y); a touch in a gap goes to the nearest touchable key of that row. -1 = none. */
    fun hit(l: FloatArray, t: FloatArray, r: FloatArray, b: FloatArray, gap: BooleanArray, x: Float, y: Float): Int {
        for (i in l.indices) {
            if (!gap[i] && x >= l[i] && x < r[i] && y >= t[i] && y < b[i]) return i
        }
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in l.indices) {
            if (gap[i]) continue
            if (y < t[i] || y > b[i]) continue
            val d = Math.abs((l[i] + r[i]) / 2f - x)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /**
     * [hit], with the touch point moved up by [offset] when both the raw and the moved point are letter keys.
     * A touch that lands on the DRAWN key (inside its rectangle shrunk by [inset] top and bottom) is always that key:
     * the offset only ever decides touches in the space between two rows (v1.0.64).
     */
    fun pick(
        l: FloatArray, t: FloatArray, r: FloatArray, b: FloatArray, gap: BooleanArray, isChar: BooleanArray,
        x: Float, y: Float, offset: Float, inset: Float = 0f
    ): Int {
        val raw = hit(l, t, r, b, gap, x, y)
        if (offset <= 0f || raw < 0 || !isChar[raw]) return raw
        if (y >= t[raw] + inset && y <= b[raw] - inset) return raw
        val moved = hit(l, t, r, b, gap, x, if (y - offset < 0f) 0f else y - offset)
        return if (moved >= 0 && isChar[moved]) moved else raw
    }

    /**
     * Smart touch: the letter key next to [i] (same row) when the touch [x] is within [zone] (0..0.5) of that shared edge,
     * else -1. Only between two letter keys; the middle of a key never has a neighbour.
     */
    fun neighbor(l: FloatArray, t: FloatArray, r: FloatArray, isChar: BooleanArray, i: Int, x: Float, zone: Float): Int {
        if (i < 0 || i >= l.size || !isChar[i]) return -1
        val w = r[i] - l[i]
        val j = when {
            x < l[i] + w * zone -> i - 1
            x > r[i] - w * zone -> i + 1
            else -> return -1
        }
        if (j < 0 || j >= l.size || !isChar[j] || t[j] != t[i]) return -1
        return j
    }
}

/** Smart touch: which of two neighbouring letters is meant, from the letters already typed in this word. */
internal object SmartTouch {
    const val ZONE = 0.25f      // outer quarter of a key, towards its neighbour
    const val RATIO = 4L        // the other letter must be this many times as common to win

    /**
     * [typed] = the current word so far (lowercase). [score] = how common the best word starting with a prefix is (0 = none).
     * The touched key ([primary]) is kept unless only [alt] makes a possible word, or [alt] is clearly more likely.
     * No letters typed yet, or anything that is not a plain word: no guess, [primary].
     */
    fun choose(typed: String, primary: Char, alt: Char, score: (String) -> Long): Char {
        if (typed.isEmpty() || typed.length > 40 || !typed.all { it.isLetter() || it == '\'' }) return primary
        val sp = score(typed + primary)
        val sa = score(typed + alt)
        return when {
            sa <= 0L -> primary
            sp <= 0L -> alt
            sa >= sp * RATIO -> alt
            else -> primary
        }
    }
}
