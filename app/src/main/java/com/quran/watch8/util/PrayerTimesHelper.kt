package com.quran.watch8.util

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Prayer times calculation using official Adhan library (high precision, offline).
 * Optimized for Argentina / Buenos Aires Province:
 * - Default method: Muslim World League (Fajr 18°, Isha 17°), the one IslamicFinder
 *   uses for Buenos Aires; zaher wants the times to match IslamicFinder always.
 * - Timezone: America/Argentina/Buenos_Aires (UTC-3)
 * - Madhab: Shafi (common) or Hanafi
 */
object PrayerTimesHelper {

    /** IslamicFinder's method for Argentina. */
    const val DEFAULT_METHOD = "MWL"

    data class PrayerInfo(
        val nameAr: String,
        val nameEn: String,
        val nameEs: String,
        val time: Instant,
        val formatted: String
    )

    data class DayPrayers(
        val fajr: PrayerInfo,
        val sunrise: PrayerInfo,
        val dhuhr: PrayerInfo,
        val asr: PrayerInfo,
        val maghrib: PrayerInfo,
        val isha: PrayerInfo,
        val nextPrayer: PrayerInfo?,
        val timeUntilNext: String,
        val locationName: String = "",
        val methodName: String = DEFAULT_METHOD
    )

    /**
     * A countdown to any prayer: hours and minutes while it is more than an
     * hour away, plain minutes under that. "386m" for tomorrow's Fajr is not
     * something you can read at a glance.
     */
    fun formatCountdown(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        val hours = minutes / 60
        return if (hours > 0) "$hours س ${minutes % 60} د" else "$minutes د"
    }

    fun calculate(
        latitude: Double,
        longitude: Double,
        methodName: String = DEFAULT_METHOD,
        madhab: Madhab = Madhab.SHAFI,
        locationName: String = "بوينس آيرس",
        zoneId: String = "America/Argentina/Buenos_Aires"
    ): DayPrayers {
        val coords = Coordinates(latitude, longitude)
        val params: CalculationParameters = getMethodFromName(methodName).parameters.apply {
            this.madhab = madhab
        }

        val zone = try {
            ZoneId.of(zoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

        val nowZoned = ZonedDateTime.now(zone)
        val dateComponents = DateComponents(nowZoned.year, nowZoned.monthValue, nowZoned.dayOfMonth)

        val prayerTimes = PrayerTimes(coords, dateComponents, params)
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        fun toInfo(nameAr: String, nameEn: String, nameEs: String, date: Date?): PrayerInfo {
            val instant = date?.toInstant() ?: Instant.now()
            val local = instant.atZone(zone)
            return PrayerInfo(
                nameAr = nameAr,
                nameEn = nameEn,
                nameEs = nameEs,
                time = instant,
                formatted = local.format(formatter)
            )
        }

        val fajr = toInfo("الفجر", "Fajr", "Fajr", prayerTimes.fajr)
        val sunrise = toInfo("الشروق", "Sunrise", "Amanecer", prayerTimes.sunrise)
        val dhuhr = toInfo("الظهر", "Dhuhr", "Dhuhr", prayerTimes.dhuhr)
        val asr = toInfo("العصر", "Asr", "Asr", prayerTimes.asr)
        val maghrib = toInfo("المغرب", "Maghrib", "Maghrib", prayerTimes.maghrib)
        val isha = toInfo("العشاء", "Isha", "Isha", prayerTimes.isha)

        val nowJava = Instant.now()
        // Sunrise is shown in the schedule but it is not a prayer, so it never
        // becomes "the next prayer" -- every screen agrees on this list.
        val all = listOf(fajr, dhuhr, asr, maghrib, isha)
        // After Isha every prayer today is in the past, so the next one is
        // tomorrow's Fajr. Without this the countdown clamped to "0m to Fajr"
        // all night.
        val next = all.firstOrNull { it.time.isAfter(nowJava) } ?: run {
            val tomorrow = nowZoned.plusDays(1)
            val tomorrowFajr = PrayerTimes(
                coords,
                DateComponents(tomorrow.year, tomorrow.monthValue, tomorrow.dayOfMonth),
                params,
            ).fajr
            toInfo("الفجر", "Fajr", "Fajr", tomorrowFajr)
        }

        // One formatter for every surface: the tiles, the watch faces, the
        // schedule screen and the system face all print the same string.
        val timeUntil = formatCountdown(
            ((next.time.epochSecond - nowJava.epochSecond).coerceAtLeast(0) / 60).toInt()
        )

        return DayPrayers(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            nextPrayer = next,
            timeUntilNext = timeUntil,
            locationName = locationName,
            methodName = methodName
        )
    }

    /**
     * Where the day stands right now: which prayer has just passed, which one
     * is coming, and how far each is from this instant.
     *
     * Every screen used to re-derive this on its own, and they disagreed --
     * some counted sunrise as a prayer, some fell back to a time already
     * hours in the past, and one printed "دقيقة" where the rest printed "د".
     * They all call this now.
     */
    data class PrayerStatus(
        val current: PrayerInfo,
        val next: PrayerInfo,
        val minutesElapsed: Int,
        val minutesRemaining: Int
    ) {
        val elapsedText: String get() = formatCountdown(minutesElapsed)
        val remainingText: String get() = formatCountdown(minutesRemaining)
    }

    /** The five prayers, in order. Sunrise is deliberately absent. */
    fun cycle(prayers: DayPrayers): List<PrayerInfo> =
        listOf(prayers.fajr, prayers.dhuhr, prayers.asr, prayers.maghrib, prayers.isha)

    /** The schedule as it is displayed -- the five prayers plus sunrise. */
    fun schedule(prayers: DayPrayers): List<PrayerInfo> =
        listOf(prayers.fajr, prayers.sunrise, prayers.dhuhr, prayers.asr, prayers.maghrib, prayers.isha)

    /** Which of the six times of the schedule is running, when it began, and when the next one begins. */
    data class SixTimes(val index: Int, val start: Instant, val next: Instant)

    /**
     * The day as six sections -- the five prayers and sunrise -- for the
     * face's ring of six. Before today's Fajr the running one is last night's
     * Isha; after Isha the next one is tomorrow's Fajr.
     */
    fun sixTimes(prayers: DayPrayers, now: Instant = Instant.now()): SixTimes {
        val times = schedule(prayers).map { it.time }
        val i = times.indexOfLast { !it.isAfter(now) }
        return when {
            i < 0 -> SixTimes(5, times[5].minusSeconds(86_400), times[0])
            i == 5 -> SixTimes(5, times[5], times[0].plusSeconds(86_400))
            else -> SixTimes(i, times[i], times[i + 1])
        }
    }

    fun status(prayers: DayPrayers?, now: Instant = Instant.now()): PrayerStatus? {
        if (prayers == null) return null
        val cycle = cycle(prayers)
        // Before today's Fajr the prayer that just passed is last night's Isha,
        // a day earlier than the Isha in this table.
        val current = cycle.lastOrNull { !it.time.isAfter(now) }
            ?: prayers.isha.let { it.copy(time = it.time.minusSeconds(86_400)) }
        val next = prayers.nextPrayer ?: cycle.firstOrNull { it.time.isAfter(now) } ?: prayers.fajr
        val elapsed = ((now.epochSecond - current.time.epochSecond).coerceAtLeast(0) / 60).toInt()
        val remaining = ((next.time.epochSecond - now.epochSecond).coerceAtLeast(0) / 60).toInt()
        return PrayerStatus(current, next, elapsed, remaining)
    }

    fun getMethodFromName(name: String): CalculationMethod {
        return when (name.uppercase()) {
            "ISNA", "NORTH_AMERICA" -> CalculationMethod.NORTH_AMERICA
            "EGYPTIAN", "EGYPT" -> CalculationMethod.EGYPTIAN
            "UMM_AL_QURA", "MAKKAH" -> CalculationMethod.UMM_AL_QURA
            "KARACHI" -> CalculationMethod.KARACHI
            "MWL", "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

    fun methodDisplayName(name: String): String {
        return when (name.uppercase()) {
            "ISNA", "NORTH_AMERICA" -> "ISNA"
            "EGYPTIAN" -> "المصرية"
            "UMM_AL_QURA" -> "أم القرى"
            "KARACHI" -> "كراتشي"
            "MWL", "MUSLIM_WORLD_LEAGUE" -> "رابطة العالم الإسلامي"
            else -> name
        }
    }
}
