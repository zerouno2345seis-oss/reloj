package com.quran.watch8.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SurahCacheTest {
    @Test
    fun readsBackOneSurahAtATime() {
        val file = File.createTempFile("surahs", ".bin")
        val data = (1..114).associateWith { n -> List(n % 7 + 1) { v -> "سورة $n آية ${v + 1} ࣰ" } }
        QuranRepository.writeSurahCache(file, data)
        for (n in listOf(1, 2, 57, 114)) assertEquals(data[n], QuranRepository.readSurahCache(file, n))
        assertNull(QuranRepository.readSurahCache(file, 115))
        file.writeText("not a cache")
        assertNull(QuranRepository.readSurahCache(file, 1))
        file.delete()
    }

    @Test
    fun parsesTheJsonAsAStream() {
        val json = """{"quran":[{"chapter":1,"verse":1,"text":"إِذࣰ ا"},{"verse":2,"chapter":1,"text":"ب","x":[1]}],"other":{}}"""
        val ayahs = QuranRepository.parseUthmani(json.byteInputStream())
        assertEquals(2, ayahs.size)
        assertEquals("إِذࣰا", ayahs[0].text)
        assertEquals(2, ayahs[1].verse)
    }
}
