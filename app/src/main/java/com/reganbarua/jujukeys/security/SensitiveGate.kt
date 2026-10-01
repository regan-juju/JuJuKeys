package com.reganbarua.jujukeys.security

import android.os.SystemClock

/** What to do with a sensitive clipboard item once the user has unlocked. */
enum class SensitiveAction { PASTE, COPY, KEEP, VIEW }

/**
 * Sensitive clipboard items (passwords, OTPs …) stay locked until the user proves it is them
 * with the phone's PIN / pattern / fingerprint ([AuthActivity]). After that they stay open for
 * a short time only.
 */
object SensitiveGate {
    const val UNLOCK_MS = 60_000L

    @Volatile private var unlockedUntil = 0L
    @Volatile private var pending: Pair<Long, SensitiveAction>? = null
    @Volatile private var pendingAt = 0L

    fun isUnlocked(now: Long = SystemClock.elapsedRealtime()): Boolean = now < unlockedUntil

    fun unlock(now: Long = SystemClock.elapsedRealtime()) { unlockedUntil = now + UNLOCK_MS }

    fun lock() { unlockedUntil = 0L; pending = null }

    /** Remember what the user wanted while the lock screen is shown. */
    fun setPending(id: Long, action: SensitiveAction, now: Long = SystemClock.elapsedRealtime()) {
        pending = id to action; pendingAt = now
    }

    fun cancelPending() { pending = null }

    /** The waiting action — only if unlocked, and only once, and not older than the unlock window. */
    fun takePending(now: Long = SystemClock.elapsedRealtime()): Pair<Long, SensitiveAction>? {
        val p = pending ?: return null
        if (!isUnlocked(now) || now - pendingAt > UNLOCK_MS) return null
        pending = null
        return p
    }
}
