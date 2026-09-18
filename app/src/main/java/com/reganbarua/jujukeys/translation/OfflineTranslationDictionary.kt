package com.reganbarua.jujukeys.translation

/**
 * Small offline Bengali<->English word/phrase dictionary. This runs entirely on-device;
 * nothing here is ever sent to a server. It intentionally covers common everyday words
 * rather than being exhaustive — see [TranslationEngine] for how this combines with an
 * optional online provider.
 */
object OfflineTranslationDictionary {

    private val bnToEn: Map<String, String> = mapOf(
        "আমি" to "I", "আমার" to "my / mine", "তুমি" to "you", "তোমার" to "your",
        "আপনি" to "you (formal)", "সে" to "he / she", "আমরা" to "we", "ওরা" to "they",
        "ভালো" to "good", "মন্দ" to "bad", "হ্যাঁ" to "yes", "না" to "no",
        "ধন্যবাদ" to "thank you", "দয়া করে" to "please", "দুঃখিত" to "sorry",
        "কেমন আছো" to "how are you", "আজ" to "today", "কাল" to "tomorrow / yesterday",
        "সকাল" to "morning", "রাত" to "night", "বাড়ি" to "home", "কাজ" to "work",
        "টাকা" to "money", "ব্যাংক" to "bank", "ঋণ" to "loan", "গ্রাহক" to "customer",
        "শাখা" to "branch", "সময়" to "time", "পানি" to "water", "খাবার" to "food"
    )

    private val enToBn: Map<String, String> = bnToEn.entries
        .associate { (bn, en) -> en.substringBefore(" (").substringBefore(" /").lowercase() to bn }

    fun translateBengaliToEnglish(text: String): String? = bnToEn[text.trim()]

    fun translateEnglishToBengali(text: String): String? = enToBn[text.trim().lowercase()]
}
