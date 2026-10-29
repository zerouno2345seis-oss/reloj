package com.quran.watch8.complication

import com.quran.watch8.util.MushafDivisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioComplicationsTest {

    @Test
    fun juzMeterCountsTheEightQuartersOfTheJuz() {
        assertEquals(1, JuzHizbComplicationService.quarterInJuz(MushafDivisions.placeOf(78, 1)))
        assertEquals(5, JuzHizbComplicationService.quarterInJuz(MushafDivisions.placeOf(87, 1)))
        assertEquals(8, JuzHizbComplicationService.quarterInJuz(MushafDivisions.placeOf(114, 6)))
    }

    @Test
    fun juzLabelMarksTheQuarterOfTheHizb() {
        assertEquals("الجزء 30 · الحزب 59", JuzHizbComplicationService.longLabel(MushafDivisions.placeOf(78, 1)))
        assertEquals(
            "الجزء 15 · ½ الحزب 29",
            JuzHizbComplicationService.longLabel(MushafDivisions.Place(juz = 15, hizb = 29, quarter = 3, page = 285))
        )
    }

    @Test
    fun sunTimesPackBothMinutesExactly() {
        val value = SunTimesComplicationService.encode(6 * 60 + 8, 19 * 60 + 37)
        assertEquals(6 * 60 + 8, value / 1440)
        assertEquals(19 * 60 + 37, value % 1440)
        // Under 2^24, so a float carries it without rounding.
        assertEquals(value, value.toFloat().toInt())
        assertEquals("06:08", SunTimesComplicationService.hhmm(6 * 60 + 8))
    }

    @Test
    fun sunTitleNamesTheOneComingNext() {
        val sunrise = 6 * 60
        val sunset = 19 * 60
        assertTrue(SunTimesComplicationService.nextIsSunrise(sunrise, sunset, 5 * 60))
        assertFalse(SunTimesComplicationService.nextIsSunrise(sunrise, sunset, 12 * 60))
        assertTrue(SunTimesComplicationService.nextIsSunrise(sunrise, sunset, 21 * 60))
    }
}
