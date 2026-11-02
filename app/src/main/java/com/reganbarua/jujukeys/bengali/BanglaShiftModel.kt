package com.reganbarua.jujukeys.bengali

/**
 * Shift for Bangla typing (v1.0.62). Pure logic, so it can be tested without a phone.
 *
 *  - Tap Shift: the NEXT letter is a capital, then Shift switches off. If no letter is typed it
 *    switches off by itself after [timeoutMs] (2 s), so a forgotten Shift never capitalises later.
 *  - Hold Shift: every letter typed while it is held is a capital; letting go switches it off.
 *  - No caps lock: tapping twice does not lock.
 */
class BanglaShiftModel(private val timeoutMs: Long = 2000L) {
    var on = false; private set
    var held = false; private set
    private var chord = false
    private var deadline = NEVER

    /** Time (uptime ms) when an unused Shift switches off by itself, or [NEVER]. */
    val expiresAt: Long get() = deadline

    fun press() { held = true; chord = false; on = true; deadline = NEVER }

    fun release(now: Long) {
        held = false
        if (chord) { on = false; deadline = NEVER } else if (on) deadline = now + timeoutMs
    }

    /** A letter is typed: true when it must be a capital. */
    fun letter(now: Long): Boolean {
        expire(now)
        val capital = on
        if (on) {
            if (held) chord = true else { on = false; deadline = NEVER }
        }
        return capital
    }

    /** Switches an unused Shift off once its time is up; true when it did. */
    fun expire(now: Long): Boolean {
        if (on && !held && now >= deadline) { on = false; deadline = NEVER; return true }
        return false
    }

    fun reset() { on = false; held = false; chord = false; deadline = NEVER }

    companion object { const val NEVER = Long.MAX_VALUE }
}
