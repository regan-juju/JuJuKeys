package com.reganbarua.jujukeys.translation

enum class TranslationDirection { BN_TO_EN, EN_TO_BN }

sealed class TranslationResult {
    data class Success(val text: String, val fromOffline: Boolean) : TranslationResult()
    object NoMatch : TranslationResult()
    object OnlineUnavailable : TranslationResult()
}

/**
 * Translation is only ever triggered by an explicit user tap on "Translate" — never
 * automatically on every keystroke.
 *
 * Online API: JuJuKeys does not ship a hardcoded API key (that would leak a secret in the
 * APK). To enable online translation, provide your own key via [OnlineTranslationProvider]
 * (e.g. read from a secure remote config or a value the user enters in Settings) and wire
 * it in here. Until then, translation runs fully offline using [OfflineTranslationDictionary]
 * and clearly reports [TranslationResult.OnlineUnavailable] rather than pretending to work.
 */
interface OnlineTranslationProvider {
    suspend fun translate(text: String, direction: TranslationDirection): String?
}

object TranslationEngine {

    /** Set from Settings if/when the user configures a real provider. Null by default. */
    var onlineProvider: OnlineTranslationProvider? = null

    suspend fun translate(text: String, direction: TranslationDirection): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return TranslationResult.NoMatch

        val offline = when (direction) {
            TranslationDirection.BN_TO_EN -> OfflineTranslationDictionary.translateBengaliToEnglish(trimmed)
            TranslationDirection.EN_TO_BN -> OfflineTranslationDictionary.translateEnglishToBengali(trimmed)
        }
        if (offline != null) return TranslationResult.Success(offline, fromOffline = true)

        val provider = onlineProvider ?: return TranslationResult.OnlineUnavailable
        val onlineResult = runCatching { provider.translate(trimmed, direction) }.getOrNull()
        return onlineResult
            ?.let { TranslationResult.Success(it, fromOffline = false) }
            ?: TranslationResult.NoMatch
    }
}
