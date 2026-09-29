package com.reganbarua.jujukeys.settings

import android.content.Context

/** Small settings stored on the phone. */
object Prefs {
    private const val FILE = "jujukeys_settings"

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Optional Google Cloud Translation API key for online translation. Empty = offline only. */
    fun cloudApiKey(context: Context): String = sp(context).getString("cloud_key", "") ?: ""
    fun setCloudApiKey(context: Context, key: String) {
        sp(context).edit().putString("cloud_key", key.trim()).apply()
    }

    fun lastLanguageBangla(context: Context): Boolean = sp(context).getBoolean("bangla", true)
    fun setLastLanguageBangla(context: Context, bangla: Boolean) {
        sp(context).edit().putBoolean("bangla", bangla).apply()
    }
}
