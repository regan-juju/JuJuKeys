package com.reganbarua.jujukeys.clipboard

/**
 * In-memory clipboard history for this keyboard session.
 *
 * Privacy: this is intentionally process-memory-only (cleared when the keyboard
 * process is killed) and is never written to disk or sent anywhere. It only ever
 * receives content the user copied themselves via ClipboardManager, and content
 * from password/sensitive fields is never pushed here (see JuJuKeysInputMethodService).
 */
object ClipboardHistoryStore {
    private const val MAX_ITEMS = 20
    private val items = ArrayDeque<String>()

    fun all(): List<String> = items.toList()

    fun push(text: String) {
        if (text.isBlank()) return
        items.remove(text) // move existing entry to front instead of duplicating
        items.addFirst(text)
        while (items.size > MAX_ITEMS) items.removeLast()
    }

    fun clear() {
        items.clear()
    }
}
