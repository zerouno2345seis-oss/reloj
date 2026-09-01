package com.quran.watch8.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model for user-customizable pre-prayer reminders.
 * Each prayer can have multiple pre-alarm offsets in minutes (e.g. 15 mins before, 5 mins before, 0 mins).
 */
data class PrayerReminderConfig(
    val fajrMinutes: List<Int> = listOf(15, 5),
    val dhuhrMinutes: List<Int> = listOf(10),
    val asrMinutes: List<Int> = listOf(10),
    val maghribMinutes: List<Int> = listOf(10, 5),
    val ishaMinutes: List<Int> = listOf(10),
    val isVibrationEnabled: Boolean = true,
    val isFullScreenEnabled: Boolean = true
) {
    fun getMinutesForPrayer(prayerKey: String): List<Int> {
        return when (prayerKey.lowercase()) {
            "fajr", "الفجر" -> fajrMinutes
            "dhuhr", "الظهر" -> dhuhrMinutes
            "asr", "العصر" -> asrMinutes
            "maghrib", "المغرب" -> maghribMinutes
            "isha", "العشاء" -> ishaMinutes
            else -> listOf(10)
        }
    }

    fun withUpdatedPrayer(prayerKey: String, newMinutes: List<Int>): PrayerReminderConfig {
        val sorted = newMinutes.distinct().sortedDescending()
        return when (prayerKey.lowercase()) {
            "fajr", "الفجر" -> copy(fajrMinutes = sorted)
            "dhuhr", "الظهر" -> copy(dhuhrMinutes = sorted)
            "asr", "العصر" -> copy(asrMinutes = sorted)
            "maghrib", "المغرب" -> copy(maghribMinutes = sorted)
            "isha", "العشاء" -> copy(ishaMinutes = sorted)
            else -> this
        }
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("fajr", JSONArray(fajrMinutes))
        root.put("dhuhr", JSONArray(dhuhrMinutes))
        root.put("asr", JSONArray(asrMinutes))
        root.put("maghrib", JSONArray(maghribMinutes))
        root.put("isha", JSONArray(ishaMinutes))
        root.put("isVibrationEnabled", isVibrationEnabled)
        root.put("isFullScreenEnabled", isFullScreenEnabled)
        return root.toString()
    }

    companion object {
        val DEFAULT = PrayerReminderConfig()

        fun fromJson(jsonStr: String): PrayerReminderConfig {
            return try {
                if (jsonStr.isBlank()) return DEFAULT
                val obj = JSONObject(jsonStr)

                fun parseList(key: String, def: List<Int>): List<Int> {
                    val arr = obj.optJSONArray(key) ?: return def
                    val list = mutableListOf<Int>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getInt(i))
                    }
                    return if (list.isNotEmpty()) list.distinct().sortedDescending() else def
                }

                PrayerReminderConfig(
                    fajrMinutes = parseList("fajr", listOf(15, 5)),
                    dhuhrMinutes = parseList("dhuhr", listOf(10)),
                    asrMinutes = parseList("asr", listOf(10)),
                    maghribMinutes = parseList("maghrib", listOf(10, 5)),
                    ishaMinutes = parseList("isha", listOf(10)),
                    isVibrationEnabled = obj.optBoolean("isVibrationEnabled", true),
                    isFullScreenEnabled = obj.optBoolean("isFullScreenEnabled", true)
                )
            } catch (_: Exception) {
                DEFAULT
            }
        }
    }
}
