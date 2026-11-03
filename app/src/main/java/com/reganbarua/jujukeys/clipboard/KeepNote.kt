package com.reganbarua.jujukeys.clipboard

/**
 * The text of the one Keep note ("JuJuKeys ক্লিপবোর্ড"): items are joined with a clear divider line,
 * and the same divider is used to cut a copied note back into separate items.
 */
object KeepNote {
    const val TITLE = "JuJuKeys ক্লিপবোর্ড"
    const val SEP = "———"

    /** What goes into Keep: every item, with a divider line between two items. */
    fun join(texts: List<String>): String = texts.joinToString("\n\n$SEP\n\n")

    /** Text to paste at the END of an existing note: a divider first, so it starts as a new item. */
    fun append(texts: List<String>): String = SEP + "\n\n" + join(texts)

    /** [items] cut out of a copied note; [bySeparator] = false when no divider was found and blank lines were used. */
    class Split(val items: List<String>, val bySeparator: Boolean)

    /**
     * Cuts a note that was copied from Keep into separate items.
     * - With divider lines (our own notes): split only at those lines, so an item may keep its own blank lines.
     * - Without (older notes): split at blank lines — an item that itself contains blank lines would be cut.
     * Empty pieces and a first line equal to the note title are dropped; repeats are kept once.
     */
    fun split(note: String): Split {
        val text = note.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val hasSep = lines.any { it.trim() == SEP }
        val pieces = ArrayList<String>()
        if (hasSep) {
            val cur = StringBuilder()
            for (line in lines) {
                if (line.trim() == SEP) { pieces.add(cur.toString()); cur.setLength(0) }
                else { if (cur.isNotEmpty()) cur.append('\n'); cur.append(line) }
            }
            pieces.add(cur.toString())
        } else {
            val cur = StringBuilder()
            for (line in lines) {
                if (line.isBlank()) { pieces.add(cur.toString()); cur.setLength(0) }
                else { if (cur.isNotEmpty()) cur.append('\n'); cur.append(line) }
            }
            pieces.add(cur.toString())
        }
        val out = ArrayList<String>()
        for (p in pieces) {
            val t = p.trim()
            if (t.isEmpty()) continue
            if (out.isEmpty() && t == TITLE) continue
            if (t !in out) out.add(t)
        }
        return Split(out, hasSep)
    }
}
