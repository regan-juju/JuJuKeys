package com.reganbarua.jujukeys.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {
    @Test fun namedEntities() {
        assertEquals("Tom's \"A & B\" <ok>", HtmlText.unescape("Tom&#39;s &quot;A &amp; B&quot; &lt;ok&gt;"))
        assertEquals("it's", HtmlText.unescape("it&apos;s"))
    }
    @Test fun numericEntities() {
        assertEquals("আমি", HtmlText.unescape("&#2438;&#2478;&#2495;"))
        assertEquals("A", HtmlText.unescape("&#x41;"))
    }
    @Test fun plainTextUntouched() {
        assertEquals("৳ 100 & more", HtmlText.unescape("৳ 100 & more"))
        assertEquals("&unknown;", HtmlText.unescape("&unknown;"))
    }
}
