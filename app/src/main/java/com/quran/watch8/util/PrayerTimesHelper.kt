package com.quran.watch8.util

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prayer times calculation using Adhan library (high precision, offline).
 * Optimized for Argentina / Buenos Aires Province:
 * - Default method: ISNA (most commonly used in Argentina)
 * - Timezone: America/Argentina/Buenos_Aires (UTC-3)
 * - Madhab: Shafi (common) or Hanafi
 */
object PrayerTimesHelper {

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
        val methodName: String = "ISNA"
    )

    fun calculate(
        latitude: Double,
        longitude: Double,
        methodName: String = "ISNA",
        madhab: Madhab = Madhab.SHAFI,
        locationName: String = "بوينس آيرس",
        zoneId: String = "America/Argentina/Buenos_Aires"
    ): DayPrayers {
        val coords = Coordinates(latitude, longitude)
        val method = getMethodFromName(methodName)
        val now = Clock.System.now()
        // Prefer Argentina timezone for correct local date when device is set elsewhere
        val tz = try {
            TimeZone.of(zoneId)
        } catch (e: Exception) {
            TimeZone.currentSystemDefault()
        }
        val localDate = now.toLocalDateTime(tz).date
        val dateComponents = DateComponents(localDate.year, localDate.monthNumber, localDate.dayOfMonth)

        val params = method.parameters.copy(madhab = madhab)
        val prayerTimes = PrayerTimes(coords, dateComponents, params)

        val zone = try {
            ZoneId.of(zoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale("ar"))

        fun toInfo(nameAr: String, nameEn: String, nameEs: String, instant: kotlinx.datetime.Instant): PrayerInfo {
            val javaInstant = Instant.ofEpochMilli(instant.toEpochMilliseconds())
            val local = javaInstant.atZone(zone)
            return PrayerInfo(
                nameAr = nameAr,
                nameEn = nameEn,
                nameEs = nameEs,
                time = javaInstant,
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
        val all = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
        val next = all.firstOrNull { it.time.isAfter(nowJava) }

        val timeUntil = if (next != null) {
            val diff = next.time.epochSecond - nowJava.epochSecond
            val h = diff / 3600
            val m = (diff % 3600) / 60
            if (h > 0) "$h س $m د" else "$m دقيقة"
        } else {
            "غداً / mañana"
        }

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

    fun getMethodFromName(name: String): CalculationMethod {
        return when (name.uppercase()) {
            "ISNA", "NORTH_AMERICA" -> CalculationMethod.NORTH_AMERICA
            "EGYPTIAN", "EGYPT" -> CalculationMethod.EGYPTIAN
            "UMM_AL_QURA", "MAKKAH" -> CalculationMethod.UMM_AL_QURA
            "KARACHI" -> CalculationMethod.KARACHI
            "MWL", "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            else -> CalculationMethod.NORTH_AMERICA // Best default for Argentina
        }
    }

    fun methodDisplayName(name: String): String {
        return when (name.uppercase()) {
            "ISNA", "NORTH_AMERICA" -> "ISNA (الأرجنتين)"
            "EGYPTIAN" -> "المصرية"
            "UMM_AL_QURA" -> "أم القرى"
            "KARACHI" -> "كراتشي"
            "MWL" -> "رابطة العالم الإسلامي"
            else -> name
        }
    }
}
