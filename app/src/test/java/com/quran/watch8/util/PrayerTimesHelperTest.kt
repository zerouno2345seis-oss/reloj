package com.quran.watch8.util

import org.junit.Assert.assertEquals
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
}
