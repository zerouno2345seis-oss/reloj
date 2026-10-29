package com.quran.watch8.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MushafDivisionsTest {

    @Test
    fun knownPlacesInTheMadaniMushaf() {
        assertEquals(MushafDivisions.Place(juz = 1, hizb = 1, quarter = 1, page = 1), MushafDivisions.placeOf(1, 1))
        // Al-Baqara 142, "سيقول السفهاء": juz 2, hizb 3, page 22.
        assertEquals(MushafDivisions.Place(juz = 2, hizb = 3, quarter = 1, page = 22), MushafDivisions.placeOf(2, 142))
        // Al-Imran opens page 50.
        assertEquals(50, MushafDivisions.placeOf(3, 1).page)
        // An-Naba opens juz 30 and hizb 59; al-A'la opens hizb 60.
        assertEquals(MushafDivisions.Place(juz = 30, hizb = 59, quarter = 1, page = 582), MushafDivisions.placeOf(78, 1))
        assertEquals(60, MushafDivisions.placeOf(87, 1).hizb)
        assertEquals(MushafDivisions.Place(juz = 30, hizb = 60, quarter = 4, page = 604), MushafDivisions.placeOf(114, 6))
    }

    @Test
    fun theLastVerseBeforeAPartStaysInThePartBefore() {
        assertEquals(1, MushafDivisions.placeOf(2, 141).juz)
        assertEquals(21, MushafDivisions.placeOf(2, 141).page)
        assertEquals(29, MushafDivisions.placeOf(77, 50).juz)
    }

    /** Every verse agrees with assets/quran_info.json, which the tables were taken from. */
    @Test
    fun everyVerseMatchesTheAsset() {
        val info = JSONObject(File("src/main/assets/quran_info.json").readText())
        val quarters = info.getJSONObject("maqras").getJSONArray("references")
        val chapters = info.getJSONArray("chapters")
        var checked = 0
        for (c in 0 until chapters.length()) {
            val chapter = chapters.getJSONObject(c)
            val verses = chapter.getJSONArray("verses")
            for (v in 0 until verses.length()) {
                val verse = verses.getJSONObject(v)
                val place = MushafDivisions.placeOf(chapter.getInt("chapter"), verse.getInt("verse"))
                val where = "${chapter.getInt("chapter")}:${verse.getInt("verse")}"
                assertEquals(where, verse.getInt("juz"), place.juz)
                assertEquals(where, verse.getInt("page"), place.page)
                assertEquals(where, verse.getInt("maqra"), (place.hizb - 1) * 4 + place.quarter)
                checked++
            }
        }
        assertEquals(6236, checked)
        assertEquals(240, quarters.length())
    }
}
