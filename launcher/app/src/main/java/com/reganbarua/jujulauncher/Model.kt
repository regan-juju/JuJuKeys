package com.reganbarua.jujulauncher

import java.util.UUID

/** Pure layout logic (no Android) so it can be unit-tested. */
sealed interface Item { val id: String }
data class AppItem(val key: String) : Item { override val id get() = key }
data class FolderItem(override val id: String, val name: String, val keys: List<String>) : Item

/** pages = home pages, dock = app keys, hidden = apps removed from Home (still in the App Library). */
data class LayoutState(
    val pages: List<List<Item>>,
    val dock: List<String>,
    val hidden: Set<String> = emptySet(),
)

object Ops {
    const val COLS = 4
    const val ROWS = 6
    const val PER_PAGE = COLS * ROWS
    const val DOCK = 4

    fun normalize(l: LayoutState): LayoutState {
        val out = ArrayList<List<Item>>()
        var carry: List<Item> = emptyList()
        for (p in l.pages) {
            val cur = carry + p
            if (cur.size > PER_PAGE) { out.add(cur.subList(0, PER_PAGE)); carry = cur.subList(PER_PAGE, cur.size) }
            else { out.add(cur); carry = emptyList() }
        }
        while (carry.isNotEmpty()) {
            val n = minOf(PER_PAGE, carry.size)
            out.add(carry.subList(0, n)); carry = carry.subList(n, carry.size)
        }
        val nonEmpty = out.filter { it.isNotEmpty() }
        return l.copy(pages = if (nonEmpty.isEmpty()) listOf(emptyList()) else nonEmpty, dock = l.dock.distinct().take(DOCK))
    }

    fun remove(l: LayoutState, id: String) =
        l.copy(pages = l.pages.map { p -> p.filter { it.id != id } }, dock = l.dock.filter { it != id })

    private fun insert(l: LayoutState, item: Item, page: Int, idx: Int): LayoutState {
        val pages = l.pages.toMutableList()
        while (pages.size <= page) pages.add(emptyList())
        val p = pages[page].toMutableList()
        p.add(idx.coerceIn(0, p.size), item)
        pages[page] = p
        return l.copy(pages = pages)
    }

    /** where is this top-level id: (page, index) or null */
    fun find(l: LayoutState, id: String): Pair<Int, Int>? {
        l.pages.forEachIndexed { pi, p -> val i = p.indexOfFirst { it.id == id }; if (i >= 0) return pi to i }
        return null
    }

    /**
     * Drop [dragged] on home [page]. [idx] is the cell index shown on screen (the dragged item is still
     * in its old place there). If [mergeTarget] is set, dragged app goes into that app/folder.
     */
    fun drop(l: LayoutState, dragged: Item, page: Int, idx: Int, mergeTarget: String?, newId: () -> String = { "f" + UUID.randomUUID() }): LayoutState {
        val old = find(l, dragged.id)
        val base = remove(l, dragged.id)
        if (mergeTarget != null && dragged is AppItem && mergeTarget != dragged.id) {
            val (tp, ti) = find(base, mergeTarget) ?: return normalize(insert(base, dragged, page, idx))
            val t = base.pages[tp][ti]
            val merged: Item = when (t) {
                is AppItem -> FolderItem(newId(), "Folder", listOf(t.key, dragged.key))
                is FolderItem -> t.copy(keys = t.keys + dragged.key)
            }
            val pages = base.pages.toMutableList()
            pages[tp] = pages[tp].toMutableList().also { it[ti] = merged }
            return normalize(base.copy(pages = pages, hidden = base.hidden - dragged.key))
        }
        val fixed = if (old != null && old.first == page && old.second < idx) idx - 1 else idx
        return normalize(insert(base, dragged, page, fixed))
    }

    fun toDock(l: LayoutState, key: String, idx: Int): LayoutState {
        var b = remove(l, key)
        b = b.copy(hidden = b.hidden - key)
        val d = b.dock.toMutableList()
        d.add(idx.coerceIn(0, d.size), key)
        var res = b.copy(dock = d)
        if (d.size > DOCK) { // overflow goes back onto the last home page
            val over = d.removeAt(d.size - 1)
            res = res.copy(dock = d)
            res = insert(res, AppItem(over), res.pages.size - 1, Int.MAX_VALUE)
        }
        return normalize(res)
    }

    /** Remove from Home but keep in the App Library. */
    fun hide(l: LayoutState, key: String) = remove(l, key).let { normalize(it.copy(hidden = it.hidden + key)) }

    fun addToHome(l: LayoutState, key: String) =
        normalize(insert(remove(l, key).copy(hidden = l.hidden - key), AppItem(key), l.pages.size - 1, Int.MAX_VALUE))

    fun leaveFolder(l: LayoutState, folderId: String, key: String): LayoutState {
        val (pi, i) = find(l, folderId) ?: return l
        val f = l.pages[pi][i] as? FolderItem ?: return l
        val rest = f.keys - key
        val pages = l.pages.toMutableList()
        val p = pages[pi].toMutableList()
        when (rest.size) {
            0 -> p.removeAt(i)
            1 -> p[i] = AppItem(rest[0])
            else -> p[i] = f.copy(keys = rest)
        }
        p.add(minOf(i + 1, p.size), AppItem(key))
        pages[pi] = p
        return normalize(l.copy(pages = pages))
    }

    fun rename(l: LayoutState, folderId: String, name: String) =
        l.copy(pages = l.pages.map { p -> p.map { if (it is FolderItem && it.id == folderId) it.copy(name = name) else it } })

    /** Keep layout in step with the installed apps: drop uninstalled, add new ones. */
    fun reconcile(l: LayoutState, installed: List<String>, defaultDock: List<String>): LayoutState {
        val have = installed.toSet()
        val pages = l.pages.map { p ->
            p.mapNotNull { it ->
                when (it) {
                    is AppItem -> if (it.key in have) it else null
                    is FolderItem -> {
                        val ks = it.keys.filter { k -> k in have }
                        when (ks.size) { 0 -> null; 1 -> AppItem(ks[0]); else -> it.copy(keys = ks) }
                    }
                }
            }
        }
        var dock = l.dock.filter { it in have }
        val placed = HashSet<String>(dock)
        pages.forEach { p -> p.forEach { if (it is AppItem) placed.add(it.key) else if (it is FolderItem) placed.addAll(it.keys) } }
        var res = LayoutState(pages, dock, l.hidden.filter { it in have }.toSet())
        if (res.dock.isEmpty() && l.pages.all { it.isEmpty() }) { // first run
            dock = defaultDock.filter { it in have }.distinct().take(DOCK)
            placed.addAll(dock)
            res = res.copy(dock = dock)
        }
        val fresh = installed.filter { it !in placed && it !in res.hidden }
        var last = res.pages.toMutableList()
        if (last.isEmpty()) last.add(emptyList())
        last[last.size - 1] = last[last.size - 1] + fresh.map { AppItem(it) }
        return normalize(res.copy(pages = last))
    }
}
