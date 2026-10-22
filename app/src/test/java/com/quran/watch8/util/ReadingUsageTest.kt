package com.quran.watch8.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReadingUsageTest {
    private val monday = LocalDate.of(2026, 10, 19)

    @Test
    fun addsUpThisWeekAndPicksTheMostUsed() {
        var json = ""
        json = ReadingUsage.add(json, "fill", "default", 600, monday)
        json = ReadingUsage.add(json, "line", "naskh", 300, monday.plusDays(1))
        json = ReadingUsage.add(json, "fill", "default", 120, monday.plusDays(2))
        assertEquals(ReadingUsage.Top("fill", "default", 720), ReadingUsage.top(json, monday.plusDays(3)))
    }

    @Test
    fun aNewWeekStartsFromZero() {
        val json = ReadingUsage.add("", "fill", "default", 6000, monday)
        assertNull(ReadingUsage.top(json, monday.plusDays(7)))
        val next = ReadingUsage.add(json, "pages", "amiri", 90, monday.plusDays(7))
        assertEquals(ReadingUsage.Top("pages", "amiri", 90), ReadingUsage.top(next, monday.plusDays(8)))
    }

    @Test
    fun underAMinuteIsNotShown() {
        assertNull(ReadingUsage.top(ReadingUsage.add("", "fill", "default", 30, monday), monday))
    }
}
