package com.quran.watch8.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranRepositoryTextTest {
    @Test
    fun joinsTheAlefThatCarriesAnOpenTanween() {
        assertEquals("كَانُوٓاْ إِذࣰا مُّنظَرِينَ", QuranRepository.joinOpenTanween("كَانُوٓاْ إِذࣰ ا مُّنظَرِينَ"))
        assertEquals("مَرَضࣰاۖ", QuranRepository.joinOpenTanween("مَرَضࣰ اۖ"))
        assertEquals("هُدࣰى", QuranRepository.joinOpenTanween("هُدࣰ ى"))
        assertEquals("مُصَلࣰّى", QuranRepository.joinOpenTanween("مُصَلࣰّ ى"))
        assertEquals("بَلَٰٓؤࣱا", QuranRepository.joinOpenTanween("بَلَٰٓؤࣱ ا"))
    }

    @Test
    fun leavesOrdinaryWordBreaksAlone() {
        val text = "مَا نُنَزِّلُ ٱلۡمَلَٰٓئِكَةَ إِلَّا بِٱلۡحَقِّ"
        assertEquals(text, QuranRepository.joinOpenTanween(text))
    }
}
