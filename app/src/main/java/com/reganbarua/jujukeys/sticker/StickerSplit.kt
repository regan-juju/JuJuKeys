package com.reganbarua.jujukeys.sticker

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One cut-out sticker: [w]×[h] ARGB pixels. */
class Piece(val w: Int, val h: Int, val px: IntArray)

/**
 * A sheet of stickers (e.g. 9 in a 3×3 grid) → separate stickers. Pure Kotlin on ARGB pixel
 * arrays (no Android), so it is unit-tested.
 *
 * 1. [darkBackgroundToAlpha]: a sheet saved on a black / very dark background (JPG, screenshot)
 *    — the dark area joined to the edges becomes see-through. Only accepted when what is left
 *    is outlined in white (sticker border), so an ordinary dark photo is not touched.
 * 2. [split]: the see-through sheet is cut into its stickers. Stickers that touch each other are
 *    separated by shrinking the shapes until they come apart, then growing each one back.
 *    Thin slivers cut from a neighbour are dropped.
 */
object StickerSplit {

    private fun lum(c: Int) = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
    private fun alpha(c: Int) = c ushr 24

    /** Returns true (and changes [px]) only for a dark-background sticker sheet. */
    fun darkBackgroundToAlpha(px: IntArray, w: Int, h: Int): Boolean {
        val bw = max(2, min(w, h) / 50)
        var dark = 0; var total = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (y < bw || y >= h - bw || x < bw || x >= w - bw) { total++; if (lum(px[y * w + x]) < 40) dark++ }
        }
        if (total == 0 || dark * 10 < total * 9) return false

        val bg = BooleanArray(w * h)
        val q = IntArray(w * h); var qh = 0; var qt = 0
        fun push(i: Int) { if (!bg[i] && lum(px[i]) < 45) { bg[i] = true; q[qt++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (qh < qt) {
            val i = q[qh++]; val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        if (qt < w * h / 10 || qt > w * h * 97 / 100) return false

        // the band just inside the background (up to 4 px) must be mostly white: the sticker outline
        val ring = IntArray(w * h)                     // 0 = not reached, else distance from the background
        qh = 0; qt = 0
        for (i in 0 until w * h) if (bg[i]) q[qt++] = i
        while (qh < qt) {
            val i = q[qh++]; val d = if (bg[i]) 0 else ring[i]
            if (d >= 4) continue
            val x = i % w; val y = i / w
            for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                if (n >= 0 && !bg[n] && ring[n] == 0) { ring[n] = d + 1; q[qt++] = n }
            }
        }
        var edge = 0; var white = 0
        for (i in 0 until w * h) if (ring[i] in 2..4) { edge++; if (lum(px[i]) > 170) white++ }
        if (edge == 0 || white * 5 < edge * 2) return false     // at least 40 % white

        for (i in 0 until w * h) {
            if (bg[i]) { px[i] = 0; continue }
            // outer edge of the white outline, blended with the black: white, see-through by its darkness
            if (ring[i] == 1) {
                val a = (lum(px[i]) * 255 / 200).coerceIn(0, 255)
                px[i] = (a shl 24) or 0xFFFFFF
            }
        }
        return true
    }

    /** Cuts a see-through sheet into separate stickers; one piece if it is a single sticker. */
    fun split(px: IntArray, w: Int, h: Int): List<Piece> {
        val f = max(1, ceil(max(w, h) / 300.0).toInt())
        val sw = w / f; val sh = h / f
        if (sw < 8 || sh < 8) return listOf(trimmed(px, w, h) ?: Piece(w, h, px))
        val small = BooleanArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            var s = 0
            for (dy in 0 until f) for (dx in 0 until f) s += alpha(px[(y * f + dy) * w + x * f + dx])
            small[y * sw + x] = s > 20 * f * f
        }

        // shrink step by step; keep the step that gives the most big parts
        var m = small.copyOf()
        var bestN = 1; var bestLab: IntArray? = null; var bestCores: List<Int> = emptyList()
        for (k in 0..12) {
            val (lab, sizes) = label(m, sw, sh)
            if (sizes.isNotEmpty()) {
                val big = sizes.max()
                val cores = sizes.indices.filter { sizes[it] >= big * 0.12 }.map { it + 1 }
                if (cores.size > bestN) { bestN = cores.size; bestLab = lab; bestCores = cores }
            }
            m = erode(m, sw, sh)
        }
        if (bestN < 2 || bestLab == null) return listOfNotNull(trimmed(px, w, h))

        // grow the parts back: first inside the shape, then a little outside (edges, loose sparkles)
        val coreOf = HashMap<Int, Int>().apply { bestCores.forEachIndexed { j, c -> put(c, j + 1) } }
        val L = IntArray(sw * sh)
        val q = IntArray(sw * sh); var qh = 0; var qt = 0
        for (i in L.indices) { val j = coreOf[bestLab[i]] ?: 0; if (j > 0) { L[i] = j; q[qt++] = i } }
        while (qh < qt) {
            val i = q[qh++]; val x = i % sw; val y = i / sw
            for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < sw - 1) i + 1 else -1, if (y > 0) i - sw else -1, if (y < sh - 1) i + sw else -1)) {
                if (n >= 0 && small[n] && L[n] == 0) { L[n] = L[i]; q[qt++] = n }
            }
        }
        val dist = IntArray(sw * sh)
        val cap = max(3, max(sw, sh) / 25)
        qh = 0; qt = 0
        for (i in L.indices) if (L[i] != 0) q[qt++] = i
        while (qh < qt) {
            val i = q[qh++]; if (dist[i] >= cap) continue
            val x = i % sw; val y = i / sw
            for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < sw - 1) i + 1 else -1, if (y > 0) i - sw else -1, if (y < sh - 1) i + sw else -1)) {
                if (n >= 0 && L[n] == 0) { L[n] = L[i]; dist[n] = dist[i] + 1; q[qt++] = n }
            }
        }

        fun labelAt(x: Int, y: Int) = L[min(y / f, sh - 1) * sw + min(x / f, sw - 1)]
        val out = ArrayList<Piece>()
        for (j in 1..bestCores.size) {
            // box of this part (small map → full size, with a margin)
            var l = sw; var t = sh; var r = -1; var b = -1
            for (i in L.indices) if (L[i] == j) { val x = i % sw; val y = i / sw; if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
            if (r < 0) continue
            val x0 = max(0, (l - 1) * f); val y0 = max(0, (t - 1) * f)
            val x1 = min(w, (r + 2) * f); val y1 = min(h, (b + 2) * f)
            val bwd = x1 - x0; val bht = y1 - y0
            val mine = BooleanArray(bwd * bht)
            for (y in 0 until bht) for (x in 0 until bwd) {
                val c = px[(y + y0) * w + x + x0]
                mine[y * bwd + x] = alpha(c) > 8 && labelAt(x + x0, y + y0) == j
            }
            dropSlivers(mine, bwd, bht) { x, y ->
                val gx = x + x0; val gy = y + y0
                gx in 0 until w && gy in 0 until h && alpha(px[gy * w + gx]) > 8 && labelAt(gx, gy) != j
            }
            val crop = IntArray(bwd * bht)
            for (y in 0 until bht) for (x in 0 until bwd) if (mine[y * bwd + x]) crop[y * bwd + x] = px[(y + y0) * w + x + x0]
            trimmed(crop, bwd, bht)?.let { out += it }
        }
        return out.ifEmpty { listOfNotNull(trimmed(px, w, h)) }
    }

    /** Small bits of [m] that touch a neighbour's pixels (cut off from it) are removed. */
    private inline fun dropSlivers(m: BooleanArray, w: Int, h: Int, other: (Int, Int) -> Boolean) {
        val (lab, sizes) = label(m, w, h)
        if (sizes.size < 2) return
        val big = sizes.max()
        val drop = BooleanArray(sizes.size + 1)
        for (i in m.indices) {
            val c = lab[i]
            if (c == 0 || drop[c] || sizes[c - 1] >= big * 0.05) continue
            val x = i % w; val y = i / w
            var near = false
            loop@ for (d in 1..2) for ((dx, dy) in arrayOf(d to 0, -d to 0, 0 to d, 0 to -d)) {
                if (other(x + dx, y + dy)) { near = true; break@loop }
            }
            if (near) drop[c] = true
        }
        for (i in m.indices) if (drop[lab[i]]) m[i] = false
    }

    private fun trimmed(px: IntArray, w: Int, h: Int): Piece? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (alpha(px[y * w + x]) > 8) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        if (r < 0) return null
        val nw = r - l + 1; val nh = b - t + 1
        val o = IntArray(nw * nh)
        for (y in 0 until nh) System.arraycopy(px, (y + t) * w + l, o, y * nw, nw)
        return Piece(nw, nh, o)
    }

    private fun erode(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val o = BooleanArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            o[i] = m[i] && (x == 0 || m[i - 1]) && (x == w - 1 || m[i + 1]) && (y == 0 || m[i - w]) && (y == h - 1 || m[i + w])
        }
        return o
    }

    /** 4-connected parts: label per pixel (0 = empty) and the size of each part. */
    private fun label(m: BooleanArray, w: Int, h: Int): Pair<IntArray, IntArray> {
        val lab = IntArray(m.size)
        val sizes = ArrayList<Int>()
        val q = IntArray(m.size)
        for (s in m.indices) {
            if (!m[s] || lab[s] != 0) continue
            val id = sizes.size + 1
            var qh = 0; var qt = 0
            lab[s] = id; q[qt++] = s
            while (qh < qt) {
                val i = q[qh++]; val x = i % w; val y = i / w
                if (x > 0 && m[i - 1] && lab[i - 1] == 0) { lab[i - 1] = id; q[qt++] = i - 1 }
                if (x < w - 1 && m[i + 1] && lab[i + 1] == 0) { lab[i + 1] = id; q[qt++] = i + 1 }
                if (y > 0 && m[i - w] && lab[i - w] == 0) { lab[i - w] = id; q[qt++] = i - w }
                if (y < h - 1 && m[i + w] && lab[i + w] == 0) { lab[i + w] = id; q[qt++] = i + w }
            }
            sizes += qt
        }
        return lab to sizes.toIntArray()
    }
}
