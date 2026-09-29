package com.reganbarua.jujukeys.bengali

import org.junit.Assert.assertEquals
import org.junit.Test

class AvroPhoneticTest {

    private val cases = linkedMapOf(
        "ami" to "আমি", "amar" to "আমার", "tumi" to "তুমি", "bhalo" to "ভালো", "valo" to "ভালো",
        "kemon" to "কেমন", "acho" to "আছো", "bangla" to "বাংলা", "bangladesh" to "বাংলাদেশ",
        "ekhon" to "এখন", "boi" to "বই", "ki" to "কি", "kori" to "করি",
        "prem" to "প্রেম", "krom" to "ক্রম", "dhormo" to "ধর্ম", "karyo" to "কার্য",
        "rokto" to "রক্ত", "golpo" to "গল্প", "shanti" to "শান্তি", "uttor" to "উত্তর",
        "buddhi" to "বুদ্ধি", "iccha" to "ইচ্ছা", "anondo" to "আনন্দ", "sombhob" to "সম্ভব",
        "jonmo" to "জন্ম", "atma" to "আত্মা", "bishwas" to "বিশ্বাস", "krriShi" to "কৃষি",
        "ghoRi" to "ঘড়ি", "baTi" to "বাটি", "kaNDo" to "কাণ্ড", "raShTro" to "রাষ্ট্র",
        "kOUshol" to "কৌশল", "biggan" to "বিজ্ঞান", "hoy" to "হয়", "byabsa" to "ব্যাবসা",
        "123" to "১২৩", "ami." to "আমি।", "hat``" to "হাৎ", "tOmake" to "তোমাকে",
        "ontor" to "অন্তর", "ca^d" to "চাঁদ", "du:kh" to "দুঃখ",
    )

    @Test
    fun convertsWords() {
        val failures = cases.mapNotNull { (roman, expected) ->
            val got = AvroPhonetic.convert(roman)
            if (got != expected) "$roman → $got (expected $expected)" else null
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun caseRule() {
        // Only o i u d g j n r s t y z are case-sensitive.
        assertEquals(AvroPhonetic.convert("ami"), AvroPhonetic.convert("AMI".replace("I", "i")))
        assertEquals("ট", AvroPhonetic.convert("T"))
        assertEquals("ত", AvroPhonetic.convert("t"))
    }
}
