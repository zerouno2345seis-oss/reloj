package com.quran.watch8.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PrayerTimesHelperTest {

    @Test
    fun `countdown uses compact Arabic units without mixed direction Latin letters`() {
        val countdown = PrayerTimesHelper.formatCountdown(478)

        assertEquals("7 س 58 د", countdown)
        assertFalse(countdown.contains('h'))
        assertFalse(countdown.contains('m'))
    }

    @Test
    fun `countdown under one hour remains compact and Arabic`() {
        assertEquals("9 د", PrayerTimesHelper.formatCountdown(9))
        assertEquals("0 د", PrayerTimesHelper.formatCountdown(-5))
    }

    // Buenos Aires, the app's home coordinates.
    private fun today() = PrayerTimesHelper.calculate(-34.60, -58.38)

    @Test
    fun `every surface reads the same next prayer and the same countdown`() {
        val prayers = today()
        val status = PrayerTimesHelper.status(prayers)!!

        assertEquals(prayers.nextPrayer?.nameAr, status.next.nameAr)
        // The schedule screen prints timeUntilNext, the tiles print
        // remainingText. They must be the same string, units included.
        assertEquals(prayers.timeUntilNext, status.remainingText)
    }

    @Test
    fun `sunrise is in the schedule but is never the next prayer`() {
        val prayers = today()
        val status = PrayerTimesHelper.status(prayers)!!

        assertTrue(PrayerTimesHelper.schedule(prayers).any { it.nameAr == "الشروق" })
        assertEquals(5, PrayerTimesHelper.cycle(prayers).size)
        assertTrue(PrayerTimesHelper.cycle(prayers).none { it.nameAr == "الشروق" })
        assertTrue(status.next.nameAr != "الشروق")
        assertTrue(status.current.nameAr != "الشروق")
    }

    @Test
    fun `the prayer that just passed is never in the future`() {
        val prayers = today()
        val now = java.time.Instant.now()
        val status = PrayerTimesHelper.status(prayers, now)!!

        assertTrue(status.current.time <= now)
        assertTrue(status.next.time.isAfter(now))
        assertTrue(status.minutesElapsed >= 0)
        assertTrue(status.minutesRemaining >= 0)
    }
}
