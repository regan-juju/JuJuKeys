package com.reganbarua.jujukeys.suggestions

/**
 * A compact, offline, commonly-used English word list for suggestions/autocorrect.
 * Stored uppercase because English typed output is always uppercase in JuJuKeys.
 * Extend freely — this is a plain list, not a network dependency.
 */
object EnglishDictionary {
    val words: List<String> = listOf(
        "THE", "BE", "TO", "OF", "AND", "A", "IN", "THAT", "HAVE", "I",
        "IT", "FOR", "NOT", "ON", "WITH", "HE", "AS", "YOU", "DO", "AT",
        "THIS", "BUT", "HIS", "BY", "FROM", "THEY", "WE", "SAY", "HER", "SHE",
        "OR", "AN", "WILL", "MY", "ONE", "ALL", "WOULD", "THERE", "THEIR", "WHAT",
        "SO", "UP", "OUT", "IF", "ABOUT", "WHO", "GET", "WHICH", "GO", "ME",
        "WHEN", "MAKE", "CAN", "LIKE", "TIME", "NO", "JUST", "HIM", "KNOW", "TAKE",
        "PEOPLE", "INTO", "YEAR", "YOUR", "GOOD", "SOME", "COULD", "THEM", "SEE", "OTHER",
        "THAN", "THEN", "NOW", "LOOK", "ONLY", "COME", "ITS", "OVER", "THINK", "ALSO",
        "BACK", "AFTER", "USE", "TWO", "HOW", "OUR", "WORK", "FIRST", "WELL", "WAY",
        "EVEN", "NEW", "WANT", "BECAUSE", "ANY", "THESE", "GIVE", "DAY", "MOST", "US",
        "HELLO", "HI", "THANKS", "THANK", "PLEASE", "SORRY", "YES", "OKAY", "OK", "BYE",
        "LOVE", "HAPPY", "TODAY", "TOMORROW", "MORNING", "NIGHT", "PHONE", "MESSAGE", "CALL", "HOME",
        "WORKING", "MEETING", "SEND", "RECEIVED", "PLEASE", "REGARDS", "BEST", "BANK", "LOAN", "CUSTOMER"
    ).distinct()

    /** Prefix search, case-insensitive input, returns uppercase matches. */
    fun suggestionsFor(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank()) return emptyList()
        val upper = prefix.uppercase()
        return words.filter { it.startsWith(upper) && it != upper }.take(limit)
    }
}
