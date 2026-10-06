package com.reganbarua.jujukeys.sticker

/**
 * Order of the stickers in the keyboard (first = top-left). Pure logic, no Android, so it is
 * unit-tested. Stored as one file name per line.
 */
object StickerOrder {
    /** Keep the saved order for files that still exist; files missing from it go to the front, newest first. */
    fun reconcile(saved: List<String>, files: Map<String, Long>): List<String> {
        val known = saved.filter { it in files }.distinct()
        val extra = files.keys.filter { it !in known }.sortedByDescending { files[it] }
        return extra + known
    }

    fun addFront(order: List<String>, names: List<String>): List<String> =
        names.distinct() + order.filter { it !in names }

    fun moveTop(order: List<String>, name: String): List<String> =
        if (name !in order) order else listOf(name) + order.filter { it != name }

    fun remove(order: List<String>, name: String): List<String> = order.filter { it != name }

    fun parse(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun format(order: List<String>): String = order.joinToString("\n")
}
