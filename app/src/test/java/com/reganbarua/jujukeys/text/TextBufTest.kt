package com.reganbarua.jujukeys.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBufTest {
    @Test fun backspaceRemovesWholeEmoji() {
        val sb = StringBuilder("hi😀")
        TextBuf.dropLast(sb)
        assertEquals("hi", sb.toString())
        val only = StringBuilder("😀")
        TextBuf.dropLast(only)
        assertTrue("field must look empty after deleting one emoji", only.isEmpty())
        TextBuf.dropLast(only)               // nothing left: no crash
        assertEquals("", only.toString())
    }

    @Test fun keepsLastEightCharactersNotChars() {
        val sb = StringBuilder("abc😀😀😀😀😀😀")
        TextBuf.keepLast(sb, 8)
        assertEquals("bc😀😀😀😀😀😀", sb.toString())
        val bn = StringBuilder("আমি ভালো আছি")
        TextBuf.keepLast(bn, 8)
        assertEquals(8, bn.codePointCount(0, bn.length))
    }
}
