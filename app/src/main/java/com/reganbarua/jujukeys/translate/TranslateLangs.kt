package com.reganbarua.jujukeys.translate

/**
 * Languages the translate box can translate INTO (you type in Bangla or English).
 * [offline] = Google ML Kit has an on-phone model (~30 MB each, downloaded on first use);
 * the others work only online with the user's API key.
 */
data class TLang(val code: String, val native: String, val bn: String, val offline: Boolean = true)

object TranslateLangs {
    /** Shown first (the user's choice), then English / Bangla, then the rest. */
    private val first = listOf("de", "ru", "ja", "pt")

    val all: List<TLang> = listOf(
        TLang("de", "Deutsch", "জার্মান"),
        TLang("ru", "Русский", "রাশিয়ান"),
        TLang("ja", "日本語", "জাপানি"),
        TLang("pt", "Português", "পর্তুগিজ"),
        TLang("en", "English", "ইংরেজি"),
        TLang("bn", "বাংলা", "বাংলা"),
        TLang("hi", "हिन्दी", "হিন্দি"),
        TLang("ur", "اردو", "উর্দু"),
        TLang("ar", "العربية", "আরবি"),
        TLang("zh", "中文", "চীনা"),
        TLang("ko", "한국어", "কোরিয়ান"),
        TLang("es", "Español", "স্প্যানিশ"),
        TLang("fr", "Français", "ফরাসি"),
        TLang("it", "Italiano", "ইতালীয়"),
        TLang("tr", "Türkçe", "তুর্কি"),
        TLang("fa", "فارسی", "ফারসি"),
        TLang("th", "ไทย", "থাই"),
        TLang("ms", "Bahasa Melayu", "মালয়"),
        TLang("id", "Bahasa Indonesia", "ইন্দোনেশীয়"),
        TLang("vi", "Tiếng Việt", "ভিয়েতনামি"),
        TLang("ta", "தமிழ்", "তামিল"),
        TLang("te", "తెలుగు", "তেলুগু"),
        TLang("kn", "ಕನ್ನಡ", "কন্নড়"),
        TLang("mr", "मराठी", "মারাঠি"),
        TLang("gu", "ગુજરાતી", "গুজরাটি"),
        TLang("nl", "Nederlands", "ডাচ"),
        TLang("pl", "Polski", "পোলিশ"),
        TLang("uk", "Українська", "ইউক্রেনীয়"),
        TLang("el", "Ελληνικά", "গ্রিক"),
        TLang("he", "עברית", "হিব্রু"),
        TLang("sv", "Svenska", "সুইডিশ"),
        TLang("no", "Norsk", "নরওয়েজীয়"),
        TLang("da", "Dansk", "ডেনিশ"),
        TLang("fi", "Suomi", "ফিনিশ"),
        TLang("cs", "Čeština", "চেক"),
        TLang("sk", "Slovenčina", "স্লোভাক"),
        TLang("hu", "Magyar", "হাঙ্গেরীয়"),
        TLang("ro", "Română", "রোমানীয়"),
        TLang("bg", "Български", "বুলগেরীয়"),
        TLang("hr", "Hrvatski", "ক্রোয়েশীয়"),
        TLang("sl", "Slovenščina", "স্লোভেনীয়"),
        TLang("lt", "Lietuvių", "লিথুয়ানীয়"),
        TLang("lv", "Latviešu", "লাটভীয়"),
        TLang("et", "Eesti", "এস্তোনীয়"),
        TLang("sw", "Kiswahili", "সোয়াহিলি"),
        TLang("tl", "Tagalog", "তাগালগ"),
        TLang("af", "Afrikaans", "আফ্রিকান্স"),
        TLang("sq", "Shqip", "আলবেনীয়"),
        TLang("be", "Беларуская", "বেলারুশীয়"),
        TLang("ca", "Català", "কাতালান"),
        TLang("cy", "Cymraeg", "ওয়েলশ"),
        TLang("eo", "Esperanto", "এস্পেরান্তো"),
        TLang("ga", "Gaeilge", "আইরিশ"),
        TLang("gl", "Galego", "গালিসীয়"),
        TLang("ht", "Kreyòl", "হাইতীয়"),
        TLang("is", "Íslenska", "আইসল্যান্ডীয়"),
        TLang("ka", "ქართული", "জর্জীয়"),
        TLang("mk", "Македонски", "ম্যাসেডোনীয়"),
        TLang("mt", "Malti", "মাল্টিজ"),
        TLang("ne", "नेपाली", "নেপালি", offline = false),
        TLang("my", "မြန်မာ", "বার্মিজ", offline = false),
        TLang("pa", "ਪੰਜਾਬੀ", "পাঞ্জাবি", offline = false),
        TLang("si", "සිංහල", "সিংহলি", offline = false),
    )

    fun byCode(code: String): TLang = all.firstOrNull { it.code == code } ?: all.first { it.code == "en" }

    /** Targets for text typed in [source] ("bn" or "en"): the user's four first, then the other of bn/en. */
    fun targetsFor(source: String): List<TLang> {
        val other = if (source == "bn") "en" else "bn"
        val head = first.map { byCode(it) } + byCode(other)
        return head + all.filter { it.code != source && it !in head }
    }

    /** Label on the pill: English name for Latin languages, the language's own name otherwise. */
    fun label(code: String): String = when (code) {
        "en" -> "ENGLISH"
        "bn" -> "বাংলা"
        else -> byCode(code).native
    }
}
