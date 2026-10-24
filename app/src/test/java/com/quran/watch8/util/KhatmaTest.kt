package com.quran.watch8.util

import com.quran.watch8.complication.KhatmaProgressComplicationService.Companion.percentRead
import com.quran.watch8.data.model.SurahMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class KhatmaTest {
    @Test
    fun theMetadataCountsAllTheVerses() {
        assertEquals(6236, SurahMetadata.surahs.sumOf { it.versesCount })
    }

    @Test
    fun countsTheVersesBeforeThePlace() {
        assertEquals(0f, percentRead(1, 1), 0.001f)
        // Al-Baqara 1: the seven verses of al-Fatiha are behind.
        assertEquals(7 * 100f / 6236, percentRead(2, 1), 0.001f)
        assertEquals(6235 * 100f / 6236, percentRead(114, 6), 0.001f)
    }
}
