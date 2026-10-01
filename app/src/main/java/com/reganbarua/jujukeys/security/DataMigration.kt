package com.reganbarua.jujukeys.security

import android.content.Context
import com.reganbarua.jujukeys.clipboard.ClipHistory
import com.reganbarua.jujukeys.settings.Prefs

/**
 * Moves data saved by older versions (plain text) into the encrypted form. Safe to run many
 * times; it never deletes the old copy until the new encrypted copy reads back correctly.
 */
object DataMigration {
    fun run(context: Context) {
        runCatching { Prefs.migrateSecrets(context) }
        runCatching { ClipHistory(context) }      // loading the history migrates it
    }
}
