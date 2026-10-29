package com.quran.watch8.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LinePaceTest {
    @Test
    fun secondsStayInRangeToATenth() {
        assertEquals(1f, LinePace.coerceLineSeconds(0.2f))
        assertEquals(20f, LinePace.coerceLineSeconds(99f))
        assertEquals(2.5f, LinePace.coerceLineSeconds(2.49f))
    }

    @Test
    fun secondsReadInArabicDigits() {
        assertEquals("٤ ث", LinePace.secondsText(4f))
        assertEquals("٢٫٥ ث", LinePace.secondsText(2.5f))
    }

    @Test
    fun juzMinutesAndBackAgree() {
        // 60 000 letters, 40 to a line: 1 500 lines; at 4 s a line, 100 minutes.
        assertEquals(100, LinePace.juzMinutes(60_000, 40f, 4f))
        assertEquals(4f, LinePace.secondsForJuz(60_000, 40f, 100))
        // One minute more slows every line a little.
        assertEquals(4f * 101 / 100, LinePace.secondsForJuz(60_000, 40f, 101), 0.05f)
    }
}
