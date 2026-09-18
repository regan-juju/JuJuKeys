package com.reganbarua.jujukeys.suggestions

/** A small offline common-Bengali-word list used for suggestion-strip hints while composing. */
object BengaliDictionary {
    val words: List<String> = listOf(
        "আমি", "আমার", "তুমি", "তোমার", "আপনি", "আপনার", "সে", "তার",
        "আমরা", "আমাদের", "তোমরা", "ওরা", "ভালো", "মন্দ", "হ্যাঁ", "না",
        "কেমন", "আছো", "আছেন", "ধন্যবাদ", "দয়া করে", "দুঃখিত", "বাংলা",
        "বাংলাদেশ", "আজ", "কাল", "সকাল", "রাত", "বাড়ি", "কাজ", "টাকা",
        "ব্যাংক", "ঋণ", "গ্রাহক", "শাখা", "রিপোর্ট", "ফোন", "বার্তা"
    )

    fun suggestionsFor(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank()) return emptyList()
        return words.filter { it.startsWith(prefix) && it != prefix }.take(limit)
    }
}
