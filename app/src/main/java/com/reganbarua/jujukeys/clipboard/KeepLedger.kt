package com.reganbarua.jujukeys.clipboard

import android.content.Context
import com.reganbarua.jujukeys.settings.Prefs

/**
 * Bookkeeping for "which clipboard items are already in the Keep note".
 * Keep cannot tell other apps whether a note was saved, so items handed to Keep are only
 * "pending" until the user answers "হ্যাঁ, সেভ হয়েছে". Cancelled, not pasted, "না" → still unsent.
 */
object KeepLedger {
    /** Items that still need to go to Keep (sensitive ones never go automatically). */
    fun unsent(context: Context, items: List<ClipItem>): List<ClipItem> {
        val sent = Prefs.keepSentIds(context)
        return items.filter { !it.sensitive && it.id !in sent }
    }

    fun handOff(context: Context, ids: Set<Long>, creatingNote: Boolean) =
        Prefs.setKeepPending(context, ids, creatingNote)

    fun pendingCount(context: Context): Int = Prefs.keepPendingIds(context).size

    fun confirm(context: Context, saved: Boolean) {
        if (saved) {
            if (Prefs.keepPendingCreate(context)) Prefs.setKeepNoteCreated(context, true)
            Prefs.setKeepSentIds(context, Prefs.keepSentIds(context) + Prefs.keepPendingIds(context))
        }
        Prefs.clearKeepPending(context)
    }

    /** Settings → "আবার পাঠান": forget what was marked as sent. */
    fun resetSent(context: Context) {
        Prefs.setKeepSentIds(context, emptySet()); Prefs.clearKeepPending(context)
    }
}
