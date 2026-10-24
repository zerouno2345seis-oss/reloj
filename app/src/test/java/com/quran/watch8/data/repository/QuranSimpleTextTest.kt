package com.quran.watch8.data.repository

import com.google.gson.Gson
import com.quran.watch8.data.model.QuranResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** The simple text must line up with the Uthmani one verse for verse. */
class QuranSimpleTextTest {
    private val assets = File("src/main/assets")
    private val simple = File(assets, "quran_simple.txt").useLines { QuranRepository.parseSimple(it) }

    @Test
    fun everySurahHasTheSameVersesAsTheUthmaniText() {
        val uthmani = File(assets, "quran_uthmani.min.json").reader(Charsets.UTF_8).use {
            Gson().fromJson(it, QuranResponse::class.java).quran.groupBy { a -> a.chapter }
        }
        assertEquals(114, simple.size)
        for (surah in 1..114) assertEquals("surah $surah", uthmani.getValue(surah).size, simple.getValue(surah).size)
        assertEquals(6236, simple.values.sumOf { it.size })
    }

    @Test
    fun theBasmalaIsLeftToTheReader() {
        assertEquals(QuranRepository.SIMPLE_BASMALA, simple.getValue(1)[0])
        assertEquals("الْحَمْدُ لِلَّهِ الَّذِي أَنزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ وَلَمْ يَجْعَل لَّهُ عِوَجًا ۜ", simple.getValue(18)[0])
        assertEquals(true, simple.getValue(95)[0].startsWith("وَالتِّينِ"))
        assertEquals(true, simple.getValue(97)[0].startsWith("إِنَّا"))
        for (surah in 2..114) assertFalse("surah $surah", simple.getValue(surah)[0].startsWith(QuranRepository.SIMPLE_BASMALA))
    }
}
