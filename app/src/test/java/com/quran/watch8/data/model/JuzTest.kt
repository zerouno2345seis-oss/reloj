package com.quran.watch8.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class JuzTest {
    @Test
    fun findsTheJuzOfAVerse() {
        assertEquals(1, Juz.of(1, 1))
        assertEquals(1, Juz.of(2, 141))
        assertEquals(2, Juz.of(2, 142))
        assertEquals(29, Juz.of(77, 50))
        assertEquals(30, Juz.of(114, 6))
    }

    @Test
    fun theThirtyCoverEveryVerseOnce() {
        val all = (1..30).flatMap { Juz.verses(it) }
        assertEquals(6236, all.size)
        assertEquals(6236, all.toSet().size)
        assertEquals(2 to 141, Juz.verses(1).last())
        assertEquals(114 to 6, Juz.verses(30).last())
    }
}
