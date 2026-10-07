package com.reganbarua.jujukeys.sticker

/**
 * Sticker profiles ("প্রোফাইল"): named folders that can hold other profiles (any depth) and an
 * ordered list of stickers. A sticker can be in many profiles; the picture file exists only once.
 * Pure logic (no Android) so it is unit-tested. Every change returns a NEW tree.
 *
 * Stored as text, one profile per line, in tree order:
 *   id ⇥ parentId(or empty) ⇥ coverSticker(or empty) ⇥ name ⇥ sticker|sticker|…
 */
data class StickerProfile(
    val id: String,
    val name: String,
    val parent: String?,          // null = top level
    val cover: String?,           // chosen cover sticker; null = first sticker
    val stickers: List<String>,   // order inside this profile (first = top-left)
)

class ProfileTree(val profiles: List<StickerProfile> = emptyList()) {
    private val byId: Map<String, StickerProfile> = profiles.associateBy { it.id }

    operator fun get(id: String?): StickerProfile? = id?.let { byId[it] }
    fun exists(id: String?) = id != null && id in byId
    fun children(parent: String?): List<StickerProfile> = profiles.filter { it.parent == parent }

    /** Top level → … → this profile. Safe against broken data (cycles, missing parents). */
    fun path(id: String?): List<StickerProfile> {
        val out = ArrayList<StickerProfile>(); val seen = HashSet<String>()
        var cur = get(id)
        while (cur != null && seen.add(cur.id)) { out.add(0, cur); cur = get(cur.parent) }
        return out
    }

    fun depth(id: String): Int = path(id).size - 1

    /** All profiles below [id] (not including it). */
    fun descendants(id: String): Set<String> {
        val out = LinkedHashSet<String>(); val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            children(p).forEach { if (out.add(it.id)) queue.add(it.id) }
        }
        out.remove(id)
        return out
    }

    /** Whole tree in display order (parent, then its children), with depth — for pickers. */
    fun flat(): List<Pair<StickerProfile, Int>> {
        val out = ArrayList<Pair<StickerProfile, Int>>(); val seen = HashSet<String>()
        fun walk(parent: String?, d: Int) {
            children(parent).forEach { if (seen.add(it.id)) { out.add(it to d); walk(it.id, d + 1) } }
        }
        walk(null, 0)
        // profiles whose parent is missing (broken data) are shown at the top level
        profiles.filter { it.id !in seen }.forEach { if (seen.add(it.id)) { out.add(it to 0); walk(it.id, 1) } }
        return out
    }

    /** Picture for a profile tile: chosen cover, else its first sticker, else a sub-profile's. */
    fun coverOf(id: String, alive: Set<String>? = null): String? {
        fun ok(s: String?) = s != null && (alive == null || s in alive)
        val p = get(id) ?: return null
        if (ok(p.cover)) return p.cover
        p.stickers.firstOrNull { ok(it) }?.let { return it }
        for (d in descendants(id)) get(d)?.stickers?.firstOrNull { ok(it) }?.let { return it }
        return null
    }

    fun profilesContaining(sticker: String): Set<String> = profiles.filter { sticker in it.stickers }.map { it.id }.toSet()

    // ---------------------------------------------------------------- changes

    private fun replace(p: StickerProfile) = ProfileTree(profiles.map { if (it.id == p.id) p else it })
    private fun edit(id: String, f: (StickerProfile) -> StickerProfile): ProfileTree = get(id)?.let { replace(f(it)) } ?: this

    /** New profile at the END of [parent]'s children. */
    fun add(id: String, name: String, parent: String?): ProfileTree {
        require(id !in byId) { "duplicate id" }
        val p = StickerProfile(id, cleanName(name), parent?.takeIf { it in byId }, null, emptyList())
        return ProfileTree(insertAfterSubtree(profiles, p))
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = cleanName(name)) }
    fun setCover(id: String, sticker: String?) = edit(id) { it.copy(cover = sticker) }

    /** Deletes the profile AND everything inside it. Stickers themselves are not touched. */
    fun delete(id: String): ProfileTree {
        if (id !in byId) return this
        val gone = descendants(id) + id
        return ProfileTree(profiles.filter { it.id !in gone })
    }

    /** Moves a profile under [newParent] (null = top level). Refused if it would go inside itself. */
    fun move(id: String, newParent: String?): ProfileTree {
        val p = get(id) ?: return this
        if (newParent != null && (newParent == id || newParent in descendants(id) || newParent !in byId)) return this
        val subtree = (listOf(id) + descendants(id)).mapNotNull { get(it) }
        val rest = profiles.filter { it.id !in subtree.map { s -> s.id } }
        val moved = listOf(p.copy(parent = newParent)) + subtree.drop(1)
        var list = rest
        // put the moved block after newParent's existing subtree
        val anchor = lastIndexOfSubtree(list, newParent)
        list = list.take(anchor + 1) + moved + list.drop(anchor + 1)
        return ProfileTree(list)
    }

    /** Moves a profile one place up (-1) or down (+1) among its siblings. */
    fun shift(id: String, step: Int): ProfileTree {
        val p = get(id) ?: return this
        val sibs = children(p.parent).map { it.id }
        val i = sibs.indexOf(id); val j = i + step
        if (i < 0 || j !in sibs.indices) return this
        val newOrder = sibs.toMutableList().apply { removeAt(i); add(j, id) }
        // rebuild: keep non-sibling profiles in place, re-emit sibling subtrees in new order
        val blocks = newOrder.associateWith { s -> (listOf(s) + descendants(s)).mapNotNull { get(it) } }
        val inBlocks = blocks.values.flatten().map { it.id }.toSet()
        val firstPos = profiles.indexOfFirst { it.id in inBlocks }
        val others = profiles.filter { it.id !in inBlocks }
        return ProfileTree(others.take(firstPos) + newOrder.flatMap { blocks.getValue(it) } + others.drop(firstPos))
    }

    /** Adds stickers to the FRONT of a profile (already-present ones move to the front too). */
    fun addStickers(id: String, names: List<String>) =
        edit(id) { p -> p.copy(stickers = names.distinct() + p.stickers.filter { it !in names }) }

    fun removeSticker(id: String, name: String) =
        edit(id) { p -> p.copy(stickers = p.stickers - name, cover = p.cover.takeIf { it != name }) }

    /** A sticker was deleted from the phone: drop it from every profile. */
    fun removeEverywhere(name: String) = ProfileTree(profiles.map {
        if (name in it.stickers || it.cover == name) it.copy(stickers = it.stickers - name, cover = it.cover.takeIf { c -> c != name }) else it
    })

    /** Puts [name] directly before [before] (null = at the end) inside the profile. */
    fun moveSticker(id: String, name: String, before: String?) = edit(id) { p ->
        if (name !in p.stickers || name == before) p else {
            val rest = p.stickers - name
            val at = before?.let { rest.indexOf(it) }?.takeIf { it >= 0 } ?: rest.size
            p.copy(stickers = rest.take(at) + name + rest.drop(at))
        }
    }

    fun moveStickerTop(id: String, name: String) = edit(id) { p ->
        if (name !in p.stickers) p else p.copy(stickers = listOf(name) + (p.stickers - name))
    }

    /** Sets exactly which profiles hold [sticker] (added ones get it at the front). */
    fun setMembership(sticker: String, ids: Set<String>): ProfileTree = ProfileTree(profiles.map { p ->
        val has = sticker in p.stickers
        when {
            p.id in ids && !has -> p.copy(stickers = listOf(sticker) + p.stickers)
            p.id !in ids && has -> p.copy(stickers = p.stickers - sticker, cover = p.cover.takeIf { it != sticker })
            else -> p
        }
    })

    /** Same tree, with stickers that no longer exist on the phone left out. */
    fun onlyExisting(alive: Set<String>) = ProfileTree(profiles.map { p ->
        if (p.stickers.all { it in alive } && (p.cover == null || p.cover in alive)) p
        else p.copy(stickers = p.stickers.filter { it in alive }, cover = p.cover?.takeIf { it in alive })
    })

    /** Profiles whose name contains [query] (case-insensitive), in tree order. */
    fun search(query: String): List<StickerProfile> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return flat().map { it.first }.filter { it.name.lowercase().contains(q) }
    }

    fun format(): String = profiles.joinToString("\n") { p ->
        listOf(p.id, p.parent.orEmpty(), p.cover.orEmpty(), escape(p.name), p.stickers.joinToString("|")).joinToString("\t")
    }

    override fun equals(other: Any?) = other is ProfileTree && other.profiles == profiles
    override fun hashCode() = profiles.hashCode()

    private fun lastIndexOfSubtree(list: List<StickerProfile>, parent: String?): Int {
        if (parent == null) return list.lastIndex
        val ids = setOf(parent) + ProfileTree(list).descendants(parent)
        return list.indexOfLast { it.id in ids }.let { if (it < 0) list.lastIndex else it }
    }

    private fun insertAfterSubtree(list: List<StickerProfile>, p: StickerProfile): List<StickerProfile> {
        val at = lastIndexOfSubtree(list, p.parent)
        return list.take(at + 1) + p + list.drop(at + 1)
    }

    companion object {
        fun cleanName(s: String) = s.replace(Regex("[\\t\\r\\n]+"), " ").trim().ifEmpty { "নতুন প্রোফাইল" }.take(60)
        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        private fun unescape(s: String): String {
            val sb = StringBuilder(); var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    sb.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }

        /** Reads the saved text; broken lines are skipped, duplicate ids keep the first. */
        fun parse(text: String): ProfileTree {
            val seen = HashSet<String>()
            val list = text.lines().mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 4 || f[0].isBlank() || !seen.add(f[0])) return@mapNotNull null
                StickerProfile(
                    id = f[0], name = unescape(f[3]).ifEmpty { "নতুন প্রোফাইল" },
                    parent = f[1].ifEmpty { null }, cover = f[2].ifEmpty { null },
                    stickers = f.getOrNull(4).orEmpty().split('|').filter { it.isNotBlank() }.distinct(),
                )
            }
            // a parent that does not exist → top level
            val ids = list.map { it.id }.toSet()
            return ProfileTree(list.map { if (it.parent != null && it.parent !in ids) it.copy(parent = null) else it })
        }
    }
}
