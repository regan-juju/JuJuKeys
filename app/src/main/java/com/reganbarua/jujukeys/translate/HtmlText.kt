package com.reganbarua.jujukeys.translate

/** Safety net for online translation: turn &amp; &#39; &quot; &lt; &gt; &#NN; &#xNN; back into characters. */
object HtmlText {
    private val ENTITY = Regex("&(#\\d+|#[xX][0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);")

    fun unescape(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                else -> {
                    val code = if (e[1] == 'x' || e[1] == 'X') e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
                    if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else m.value
                }
            }
        }
    }
}
