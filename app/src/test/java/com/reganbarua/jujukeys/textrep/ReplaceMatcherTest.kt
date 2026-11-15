package com.reganbarua.jujukeys.textrep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceMatcherTest {
    private val omw = Rep(1, "omw", "On my way!")
    private val addr = Rep(2, "addr", "my address")
    private val pw = Rep(3, "pw1", "", password = true, enc = "v1:xx")
    private val all = listOf(omw, addr, pw)

    @Test fun shortcutNeedsTwoCharsAndNoSpaces() {
        assertTrue(ReplaceMatcher.validShortcut("omw"))
        assertTrue(ReplaceMatcher.validShortcut("@@"))
        assertFalse(ReplaceMatcher.validShortcut("o"))
        assertFalse(ReplaceMatcher.validShortcut("o m"))
        assertFalse(ReplaceMatcher.validShortcut(""))
    }

    @Test fun matchesAnyCaseWholeWordOnly() {
        assertEquals(omw, ReplaceMatcher.find("omw", all, false))
        assertEquals(omw, ReplaceMatcher.find("OMW", all, false))
        assertEquals(omw, ReplaceMatcher.find("Omw", all, false))
        assertNull(ReplaceMatcher.find("omws", all, false))
        assertNull(ReplaceMatcher.find("xomw", all, false))
        assertNull(ReplaceMatcher.find("o", all, false))
    }

    @Test fun passwordEntriesOnlyInPasswordFields() {
        assertNull(ReplaceMatcher.find("pw1", all, false))
        assertEquals(pw, ReplaceMatcher.find("pw1", all, true))
        assertNull(ReplaceMatcher.find("omw", all, true))
    }

    @Test fun capitalFollowsShortcutForSmallPhrase() {
        assertEquals("my address", ReplaceMatcher.applyCase("addr", "my address", false))
        assertEquals("My address", ReplaceMatcher.applyCase("Addr", "my address", false))
        assertEquals("On my way!", ReplaceMatcher.applyCase("omw", "On my way!", false))
        assertEquals("On my way!", ReplaceMatcher.applyCase("Omw", "On my way!", false))
    }

    @Test fun passwordCaseNeverChanges() {
        assertEquals("abc#12", ReplaceMatcher.applyCase("Pw1", "abc#12", true))
    }

    @Test fun banglaPhraseUnchanged() {
        assertEquals("আমার ঠিকানা", ReplaceMatcher.applyCase("Addr", "আমার ঠিকানা", false))
    }

    @Test fun duplicateIgnoresCaseAndSelf() {
        assertTrue(ReplaceMatcher.duplicate(all, "OMW"))
        assertFalse(ReplaceMatcher.duplicate(all, "omw", exceptId = 1))
        assertFalse(ReplaceMatcher.duplicate(all, "new"))
    }

    @Test fun lastTokenAfterSpaceOrNewline() {
        assertEquals("omw", ReplaceMatcher.lastToken("I will be late omw"))
        assertEquals("omw", ReplaceMatcher.lastToken("line one\nomw"))
        assertEquals("", ReplaceMatcher.lastToken("hello "))
        assertEquals("pw1", ReplaceMatcher.lastToken("pw1"))
    }

    @Test fun sectionLetters() {
        assertEquals('O', ReplaceMatcher.sectionOf("On my way!"))
        assertEquals('A', ReplaceMatcher.sectionOf("  abc"))
        assertEquals('#', ReplaceMatcher.sectionOf("আমার"))
        assertEquals('#', ReplaceMatcher.sectionOf(""))
    }
}
