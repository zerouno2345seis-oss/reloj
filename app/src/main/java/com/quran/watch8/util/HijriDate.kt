package com.quran.watch8.util

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Today's Hijri date, computed instead of the "١٨ صفر ١٤٤٨ هـ" literal that used
 * to sit hardcoded in the renderer and the watch face screen. Uses the Umm
 * al-Qura calendar that ships with the platform (java.time, available on
 * minSdk 30 with core library desugaring).
 */
object HijriDate {

    private val MONTHS_AR = arrayOf(
        "محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجّة"
    )

    private val MONTHS_EN = arrayOf(
        "Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    private fun today(): HijrahDate = HijrahDate.from(LocalDate.now())

    /** e.g. "١٩ صفر ١٤٤٧ هـ" */
    fun arabic(date: HijrahDate = today()): String {
        val day = date.get(ChronoField.DAY_OF_MONTH)
        val month = MONTHS_AR[date.get(ChronoField.MONTH_OF_YEAR) - 1]
        val year = date.get(ChronoField.YEAR_OF_ERA)
        return "${toArabicDigits(day)} $month ${toArabicDigits(year)} هـ"
    }

    /** e.g. "19 Safar 1447". The LRM keeps it one left-to-right run inside an RTL layout. */
    fun latin(date: HijrahDate = today()): String {
        val day = date.get(ChronoField.DAY_OF_MONTH)
        val month = MONTHS_EN[date.get(ChronoField.MONTH_OF_YEAR) - 1]
        val year = date.get(ChronoField.YEAR_OF_ERA)
        return "‎$day $month $year"
    }

    /** Short form for a tight complication slot, e.g. "١٩ صفر". */
    fun shortArabic(date: HijrahDate = today()): String {
        val day = date.get(ChronoField.DAY_OF_MONTH)
        val month = MONTHS_AR[date.get(ChronoField.MONTH_OF_YEAR) - 1]
        return "${toArabicDigits(day)} $month"
    }

    private fun toArabicDigits(n: Int): String {
        val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return n.toString().map { if (it in '0'..'9') map[it - '0'] else it }.joinToString("")
    }
}
