package com.reganbarua.jujukeys

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reganbarua.jujukeys.clipboard.ClipHistory
import com.reganbarua.jujukeys.clipboard.ClipItem
import com.reganbarua.jujukeys.clipboard.KeepLedger
import com.reganbarua.jujukeys.security.CryptoBox
import com.reganbarua.jujukeys.security.SensitiveAction
import com.reganbarua.jujukeys.security.SensitiveGate
import com.reganbarua.jujukeys.settings.Prefs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real Android (emulator) with the real Android Keystore. */
@RunWith(AndroidJUnit4::class)
class SecurityAndDataTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun prefs(name: String) = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun rawText(name: String) = prefs(name).all.values.joinToString("|") { it.toString() }

    @Before fun clean() {
        listOf("jujukeys_clipboard", "jujukeys_secret", Prefs.FILE).forEach { prefs(it).edit().clear().commit() }
        SensitiveGate.lock()
    }

    @Test fun cryptoRoundTripAndTamper() {
        val e = CryptoBox.encrypt("গোপন secret 123")
        assertNotNull(e)
        assertTrue(CryptoBox.isEncrypted(e))
        assertFalse(e!!.contains("secret"))
        assertEquals("গোপন secret 123", CryptoBox.decrypt(e))
        val tampered = e.dropLast(3) + (if (e.endsWith("AAA")) "BBB" else "AAA")
        assertNull(CryptoBox.decrypt(tampered))
    }

    @Test fun clipboardEncryptedAtRestAndExact() {
        val text = "  hello\tworld\n\n  "
        val h = ClipHistory(ctx)
        h.add(text)
        h.add("পাসওয়ার্ড-৯৮৭", sensitive = true)
        assertFalse("plain text found on disk", rawText("jujukeys_clipboard").contains("hello"))
        assertFalse("sensitive text found on disk", rawText("jujukeys_clipboard").contains("পাসওয়ার্ড-৯৮৭"))
        val again = ClipHistory(ctx)
        assertEquals(text, again.items.first { !it.sensitive }.text)          // spaces, tab, line breaks kept
        assertTrue(again.items.any { it.sensitive && it.text == "পাসওয়ার্ড-৯৮৭" })
        again.clearSensitive()
        assertTrue(ClipHistory(ctx).items.none { it.sensitive })
        assertTrue(ClipHistory(ctx).items.any { it.text == text })
    }

    @Test fun blankIsSkippedDuplicateKeepsNewest() {
        val h = ClipHistory(ctx)
        h.add("   \n\t")
        assertTrue(h.items.isEmpty())
        h.add("abc")
        h.add("abc  ")
        assertEquals(1, h.items.size)
        assertEquals("abc  ", h.items[0].text)
    }

    @Test fun oldPlainClipboardIsMigrated() {
        val arr = JSONArray()
            .put(JSONObject().put("id", 11L).put("text", " old one ").put("pinned", true))
            .put(JSONObject().put("id", 12L).put("text", "old two").put("pinned", false))
        prefs("jujukeys_clipboard").edit().putString(ClipHistory.KEY_PLAIN, arr.toString()).commit()
        val h = ClipHistory(ctx)
        assertEquals(listOf(" old one ", "old two"), h.items.map { it.text })
        assertNull("plain copy must be removed", prefs("jujukeys_clipboard").getString(ClipHistory.KEY_PLAIN, null))
        assertTrue(CryptoBox.isEncrypted(prefs("jujukeys_clipboard").getString(ClipHistory.KEY_ENC, null)))
        assertFalse(rawText("jujukeys_clipboard").contains("old two"))
        assertTrue(ClipHistory(ctx).items.first { it.id == 11L }.pinned)
    }

    @Test fun apiKeyIsMigratedAndEncrypted() {
        Prefs.sp(ctx).edit().putString("cloud_key", "TESTKEY-123").commit()
        assertEquals("TESTKEY-123", Prefs.cloudApiKey(ctx))
        assertNull(Prefs.sp(ctx).getString("cloud_key", null))
        assertFalse(rawText("jujukeys_secret").contains("TESTKEY"))
        assertTrue(Prefs.setCloudApiKey(ctx, "NEWKEY-9"))
        assertEquals("NEWKEY-9", Prefs.cloudApiKey(ctx))
        assertTrue(Prefs.setCloudApiKey(ctx, ""))
        assertEquals("", Prefs.cloudApiKey(ctx))
    }

    @Test fun keepNeedsConfirmation() {
        val items = listOf(ClipItem(1, "a", false), ClipItem(2, "b", false), ClipItem(3, "pw", false, sensitive = true))
        assertEquals(listOf(1L, 2L), KeepLedger.unsent(ctx, items).map { it.id })     // sensitive never automatic
        KeepLedger.handOff(ctx, setOf(1L, 2L), creatingNote = true)
        assertEquals(2, KeepLedger.pendingCount(ctx))
        KeepLedger.confirm(ctx, saved = false)                                         // cancelled / not saved
        assertEquals(listOf(1L, 2L), KeepLedger.unsent(ctx, items).map { it.id })
        assertFalse(Prefs.keepNoteCreated(ctx))
        KeepLedger.handOff(ctx, setOf(1L, 2L), creatingNote = true)
        KeepLedger.confirm(ctx, saved = true)
        assertTrue(KeepLedger.unsent(ctx, items).isEmpty())
        assertTrue(Prefs.keepNoteCreated(ctx))
        assertEquals(0, KeepLedger.pendingCount(ctx))
        KeepLedger.resetSent(ctx)                                                      // "আবার পাঠান"
        assertEquals(2, KeepLedger.unsent(ctx, items).size)
    }

    @Test fun sensitiveGate() {
        assertFalse(SensitiveGate.isUnlocked())
        SensitiveGate.setPending(5, SensitiveAction.PASTE, now = 1000)
        assertNull("locked: nothing runs", SensitiveGate.takePending(now = 1500))
        SensitiveGate.unlock(now = 2000)
        assertEquals(5L to SensitiveAction.PASTE, SensitiveGate.takePending(now = 2500))
        assertNull("runs only once", SensitiveGate.takePending(now = 2600))
        assertTrue(SensitiveGate.isUnlocked(now = 2000 + SensitiveGate.UNLOCK_MS - 1))
        assertFalse(SensitiveGate.isUnlocked(now = 2000 + SensitiveGate.UNLOCK_MS + 1))
    }

    private fun excludes(xml: XmlResourceParser, section: String?): Set<String> {
        val out = HashSet<String>()
        var inSection = section == null
        while (xml.next() != XmlResourceParser.END_DOCUMENT) {
            if (xml.eventType == XmlResourceParser.START_TAG) {
                if (section != null && xml.name == section) inSection = true
                if (inSection && xml.name == "exclude") out += xml.getAttributeValue(null, "domain") + ":" + xml.getAttributeValue(null, "path")
            } else if (xml.eventType == XmlResourceParser.END_TAG && section != null && xml.name == section) inSection = false
        }
        return out
    }

    @Test fun backupLeavesOutPrivateData() {
        val need = setOf("sharedpref:jujukeys_clipboard.xml", "sharedpref:jujukeys_secret.xml", "file:learned_words.txt")
        assertTrue(excludes(ctx.resources.getXml(R.xml.backup_rules), null).containsAll(need))
        assertTrue(excludes(ctx.resources.getXml(R.xml.data_extraction_rules), "cloud-backup").containsAll(need))
        assertTrue(excludes(ctx.resources.getXml(R.xml.data_extraction_rules), "device-transfer").containsAll(need))
        assertFalse("settings must still be backed up", need.contains("sharedpref:jujukeys_settings.xml"))
    }
}
