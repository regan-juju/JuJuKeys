package com.reganbarua.jujukeys.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jujukeys_settings")

/** All settings that actually affect keyboard behavior, persisted with DataStore. */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val KEYPRESS_SOUND = booleanPreferencesKey("keypress_sound")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_POPUP = booleanPreferencesKey("key_popup")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
        val AUTOCORRECT_ENABLED = booleanPreferencesKey("autocorrect_enabled")
        val EMOJI_SUGGESTIONS = booleanPreferencesKey("emoji_suggestions")
        val KEYBOARD_HEIGHT_SCALE = floatPreferencesKey("keyboard_height_scale") // 0.85..1.2
        val KEY_SIZE_SCALE = floatPreferencesKey("key_size_scale") // 0.85..1.2
        val RECENT_EMOJI = stringPreferencesKey("recent_emoji") // comma-separated
        val LAST_LANGUAGE = stringPreferencesKey("last_language") // ENGLISH / BENGALI
    }

    val keypressSound: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEYPRESS_SOUND] ?: true }
    val hapticFeedback: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTIC_FEEDBACK] ?: true }
    val keyPopup: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEY_POPUP] ?: true }
    val suggestionsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SUGGESTIONS_ENABLED] ?: true }
    val autocorrectEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTOCORRECT_ENABLED] ?: true }
    val emojiSuggestionsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.EMOJI_SUGGESTIONS] ?: true }
    val keyboardHeightScale: Flow<Float> = context.dataStore.data.map { it[Keys.KEYBOARD_HEIGHT_SCALE] ?: 1.0f }
    val keySizeScale: Flow<Float> = context.dataStore.data.map { it[Keys.KEY_SIZE_SCALE] ?: 1.0f }
    val recentEmoji: Flow<List<String>> = context.dataStore.data.map {
        it[Keys.RECENT_EMOJI]?.split(",")?.filter { e -> e.isNotBlank() } ?: emptyList()
    }
    val lastLanguage: Flow<String> = context.dataStore.data.map { it[Keys.LAST_LANGUAGE] ?: "ENGLISH" }

    suspend fun setKeypressSound(value: Boolean) = context.dataStore.edit { it[Keys.KEYPRESS_SOUND] = value }
    suspend fun setHapticFeedback(value: Boolean) = context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = value }
    suspend fun setKeyPopup(value: Boolean) = context.dataStore.edit { it[Keys.KEY_POPUP] = value }
    suspend fun setSuggestionsEnabled(value: Boolean) = context.dataStore.edit { it[Keys.SUGGESTIONS_ENABLED] = value }
    suspend fun setAutocorrectEnabled(value: Boolean) = context.dataStore.edit { it[Keys.AUTOCORRECT_ENABLED] = value }
    suspend fun setEmojiSuggestionsEnabled(value: Boolean) = context.dataStore.edit { it[Keys.EMOJI_SUGGESTIONS] = value }
    suspend fun setKeyboardHeightScale(value: Float) = context.dataStore.edit { it[Keys.KEYBOARD_HEIGHT_SCALE] = value }
    suspend fun setKeySizeScale(value: Float) = context.dataStore.edit { it[Keys.KEY_SIZE_SCALE] = value }
    suspend fun setLastLanguage(value: String) = context.dataStore.edit { it[Keys.LAST_LANGUAGE] = value }

    suspend fun pushRecentEmoji(emoji: String) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.RECENT_EMOJI]?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        current.remove(emoji)
        current.add(0, emoji)
        prefs[Keys.RECENT_EMOJI] = current.take(24).joinToString(",")
    }
}
