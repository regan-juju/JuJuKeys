package com.reganbarua.jujukeys

import com.reganbarua.jujukeys.sticker.ProfileTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTreeTest {
    private fun sample(): ProfileTree = ProfileTree()
        .add("fam", "পরিবার", null)
        .add("son", "রিয়ান", "fam")
        .add("bday", "জন্মদিন", "son")
        .add("fun", "মজা", null)
        .addStickers("son", listOf("a.webp", "b.webp"))
        .addStickers("fun", listOf("b.webp", "c.webp"))

    @Test fun nestedPathsAndChildren() {
        val t = sample()
        assertEquals(listOf("পরিবার", "রিয়ান", "জন্মদিন"), t.path("bday").map { it.name })
        assertEquals(listOf("fam", "fun"), t.children(null).map { it.id })
        assertEquals(setOf("son", "bday"), t.descendants("fam"))
        assertEquals(listOf("fam" to 0, "son" to 1, "bday" to 2, "fun" to 0), t.flat().map { it.first.id to it.second })
    }

    @Test fun oneStickerManyProfilesAndOrder() {
        val t = sample()
        assertEquals(setOf("son", "fun"), t.profilesContaining("b.webp"))
        assertEquals(listOf("b.webp", "c.webp"), t["fun"]!!.stickers)
        assertEquals(listOf("c.webp", "b.webp"), t.moveSticker("fun", "c.webp", "b.webp")["fun"]!!.stickers)
        assertEquals(listOf("c.webp", "b.webp"), t.moveSticker("fun", "b.webp", null)["fun"]!!.stickers)
        assertEquals(listOf("b.webp", "a.webp"), t.moveStickerTop("son", "b.webp")["son"]!!.stickers)
        assertEquals(listOf("a.webp"), t.removeSticker("son", "b.webp")["son"]!!.stickers)
        val m = t.setMembership("c.webp", setOf("son"))
        assertEquals(listOf("c.webp", "a.webp", "b.webp"), m["son"]!!.stickers)
        assertFalse("c.webp" in m["fun"]!!.stickers)
    }

    @Test fun deleteTakesSubtreeButKeepsStickersElsewhere() {
        val t = sample().delete("fam")
        assertEquals(listOf("fun"), t.profiles.map { it.id })
        assertEquals(listOf("b.webp", "c.webp"), t["fun"]!!.stickers)
    }

    @Test fun moveRefusesCyclesAndKeepsSubtree() {
        val t = sample()
        assertEquals(t, t.move("fam", "bday"))          // into its own grandchild → refused
        val m = t.move("son", "fun")
        assertEquals(listOf("মজা", "রিয়ান", "জন্মদিন"), m.path("bday").map { it.name })
        assertEquals(listOf("son"), m.children("fun").map { it.id })
        assertTrue(m.children("fam").isEmpty())
    }

    @Test fun shiftSiblings() {
        val t = sample()
        assertEquals(listOf("fun", "fam"), t.shift("fun", -1).children(null).map { it.id })
        assertEquals(t, t.shift("fam", -1))
        // subtree still attached after shifting
        assertEquals(listOf("son"), t.shift("fun", -1).children("fam").map { it.id })
    }

    @Test fun deletedStickerGoneEverywhereAndCover() {
        val t = sample().setCover("fun", "c.webp").removeEverywhere("c.webp")
        assertNull(t["fun"]!!.cover)
        assertEquals(listOf("b.webp"), t["fun"]!!.stickers)
        assertEquals("a.webp", sample().coverOf("fam"))       // from a sub-profile
        assertEquals("b.webp", sample().onlyExisting(setOf("b.webp")).coverOf("son"))
    }

    @Test fun saveLoadRoundTripWithBanglaTabsAndBrokenLines() {
        val t = sample().rename("fun", "মজা\tও হাসি").setCover("son", "b.webp")
        val back = ProfileTree.parse(t.format() + "\nbroken line\n\tno id\tx\ty")
        assertEquals("মজা ও হাসি", back["fun"]!!.name)
        assertEquals(t, back)
        // missing parent → top level, duplicate id ignored
        val odd = ProfileTree.parse("x\tghost\t\tএকা\ta.webp\nx\t\t\tdup\t")
        assertEquals(1, odd.profiles.size); assertNull(odd["x"]!!.parent)
    }

    @Test fun hundredsOfProfilesStayFast() {
        var t = ProfileTree()
        for (i in 0 until 600) t = t.add("p$i", "প্রোফাইল $i", if (i < 20) null else "p${i % 20}")
        val start = System.nanoTime()
        val flat = t.flat(); val hits = t.search("59"); val back = ProfileTree.parse(t.format())
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(600, flat.size); assertEquals(t, back); assertTrue("p59" in hits.map { it.id } && hits.all { "59" in it.name })
        assertTrue("took $ms ms", ms < 1500)
    }
}
