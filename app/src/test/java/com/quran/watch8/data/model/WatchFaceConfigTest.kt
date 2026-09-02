package com.quran.watch8.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceConfigTest {

    @Test
    fun `catalog contains the original nine plus six new faces`() {
        assertEquals(15, WatchFaceModelId.values().size)
        assertTrue(WatchFaceModelId.values().map { it.name }.containsAll(
            listOf("FAJR_MIHRAB", "DHIKR_PULSE", "QIBLA_SERENITY", "QURAN_GALLERY", "DAILY_ORBITS", "BELIEVER_MOSAIC")
        ))
    }

    @Test
    fun `legacy slots migrate into a profile for the selected model`() {
        val config = WatchFaceConfig.fromJson(
            """{"modelId":"ULTRA_DIGITAL_CLASSIC","topSlot":"WEATHER","rightSlot":"BATTERY","leftSlot":"QIBLA","bottomSlot":"TASBIH"}"""
        )

        assertEquals(ComplicationType.WEATHER, config.topSlot)
        assertEquals(ComplicationType.TASBIH, config.bottomSlot)
        assertEquals(config.activeSlots, config.slotProfiles[WatchFaceModelId.ULTRA_DIGITAL_CLASSIC.name])
    }

    @Test
    fun `switching models restores model specific defaults`() {
        val config = WatchFaceConfig().withModel(WatchFaceModelId.QURAN_GALLERY)

        assertEquals(WatchFaceModelId.QURAN_GALLERY, config.modelId)
        assertEquals(ComplicationType.HIJRI_DATE, config.topSlot)
        assertEquals(ComplicationType.NEXT_PRAYER, config.bottomSlot)
    }

    @Test
    fun `round trip keeps independent slot profiles`() {
        val first = WatchFaceConfig()
            .withModel(WatchFaceModelId.DHIKR_PULSE)
            .withSlot("right", ComplicationType.WEATHER)
            .withModel(WatchFaceModelId.QIBLA_SERENITY)
            .withSlot("left", ComplicationType.BATTERY)
        val restored = WatchFaceConfig.fromJson(first.toJson())

        assertEquals(first, restored)
        assertEquals(ComplicationType.WEATHER, restored.slotProfiles.getValue(WatchFaceModelId.DHIKR_PULSE.name).right)
        assertEquals(ComplicationType.BATTERY, restored.slotProfiles.getValue(WatchFaceModelId.QIBLA_SERENITY.name).left)
    }
}
