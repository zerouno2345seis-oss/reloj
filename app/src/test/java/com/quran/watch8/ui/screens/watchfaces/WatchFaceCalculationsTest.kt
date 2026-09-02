package com.quran.watch8.ui.screens.watchfaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceCalculationsTest {

    @Test
    fun `qibla bearing for Buenos Aires is stable`() {
        assertEquals(76.27f, qiblaBearing(-34.6037, -58.3816), 0.1f)
    }

    @Test
    fun `rotation is normalized to shortest signed angle`() {
        assertEquals(-10f, normalizedRotation(350f), 0.001f)
        assertEquals(10f, normalizedRotation(10f), 0.001f)
    }

    @Test
    fun `progress between instants clamps at both ends`() {
        assertEquals(0f, progressBetween(nowEpochSeconds = 50, startEpochSeconds = 100, endEpochSeconds = 200), 0.001f)
        assertEquals(0.5f, progressBetween(nowEpochSeconds = 150, startEpochSeconds = 100, endEpochSeconds = 200), 0.001f)
        assertEquals(1f, progressBetween(nowEpochSeconds = 250, startEpochSeconds = 100, endEpochSeconds = 200), 0.001f)
    }

    @Test
    fun `invalid progress window is safe`() {
        assertEquals(0f, progressBetween(nowEpochSeconds = 100, startEpochSeconds = 100, endEpochSeconds = 100), 0.001f)
    }

    @Test
    fun `tasbih increment wraps at configured target`() {
        val state = TasbihState(count = 32, target = 33, dhikrIndex = 0)
        assertEquals(33, state.incremented().count)
        assertEquals(0, state.incremented().incremented().count)
        assertEquals(0f, TasbihState(count = 0, target = 0, dhikrIndex = 0).progress, 0.001f)
    }

    @Test
    fun `Quran reading line keeps surah number and text in one sequence`() {
        val line = formatQuranReadingLine("الكهف", 18, "وَتَحْسَبُهُمْ أَيْقَاظًا")
        assertEquals("سورة الكهف · 18 وَتَحْسَبُهُمْ أَيْقَاظًا", line)
        assertTrue(line.indexOf("18") < line.indexOf("وَتَحْسَبُهُمْ"))
    }
}
