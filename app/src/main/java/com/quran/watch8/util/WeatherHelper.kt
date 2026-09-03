package com.quran.watch8.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherHelper {

    private const val DEFAULT_LAT = -34.6037
    private const val DEFAULT_LNG = -58.3816

    /**
     * Open-Meteo current weather. Retries a few times with backoff before giving
     * up, and every field is read defensively so one odd response shape can't
     * turn the whole thing "unavailable". `timezone=auto` lets the API resolve
     * the zone from the coordinates instead of a hardcoded Buenos Aires.
     */
    suspend fun fetchWeatherSnapshot(lat: Double = DEFAULT_LAT, lng: Double = DEFAULT_LNG): WeatherSnapshot {
        repeat(3) { attempt ->
            val snap = attemptFetch(lat, lng)
            if (snap.isAvailable) return snap
            if (attempt < 2) delay(1500L * (attempt + 1))
        }
        return WeatherSnapshot.unavailable()
    }

    private suspend fun attemptFetch(lat: Double, lng: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lng" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=precipitation_probability_max&timezone=auto"
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext WeatherSnapshot.unavailable()

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val current = json.optJSONObject("current") ?: return@withContext WeatherSnapshot.unavailable()
            if (!current.has("temperature_2m")) return@withContext WeatherSnapshot.unavailable()

            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return@withContext WeatherSnapshot.unavailable()
            val code = current.optInt("weather_code", -1)

            val precip = json.optJSONObject("daily")
                ?.optJSONArray("precipitation_probability_max")
                ?.let { if (it.length() > 0) it.optInt(0, 0) else 0 } ?: 0

            WeatherSnapshot(
                temperatureC = Math.round(temp).toInt(),
                weatherCode = code.takeIf { it >= 0 },
                precipitationPercent = precip,
                icon = getWeatherIcon(code),
                isAvailable = true,
                fetchedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            WeatherSnapshot.unavailable()
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetchWeatherSummary(lat: Double = DEFAULT_LAT, lng: Double = DEFAULT_LNG): String =
        fetchWeatherSnapshot(lat, lng).summary

    private fun getWeatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2, 3 -> "⛅"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌧️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77 -> "❄️"
        80, 81, 82 -> "🌦️"
        85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}

data class WeatherSnapshot(
    val temperatureC: Int?,
    val weatherCode: Int?,
    val precipitationPercent: Int?,
    val icon: String,
    val isAvailable: Boolean,
    val fetchedAt: Long = 0L
) {
    val temperatureLabel: String get() = temperatureC?.let { "$it°" } ?: "—°"
    val summary: String get() = if (isAvailable) "$temperatureLabel $icon | 🌧️ ${precipitationPercent ?: 0}%" else "غير متوفر"

    /** True once the cached value is older than six hours — still shown, just old. */
    val isStale: Boolean get() = isAvailable && fetchedAt > 0 && System.currentTimeMillis() - fetchedAt > 6 * 3_600_000L

    fun toJson(): String = JSONObject().apply {
        put("t", temperatureC ?: JSONObject.NULL)
        put("c", weatherCode ?: JSONObject.NULL)
        put("p", precipitationPercent ?: JSONObject.NULL)
        put("i", icon)
        put("a", isAvailable)
        put("at", fetchedAt)
    }.toString()

    companion object {
        fun unavailable() = WeatherSnapshot(null, null, null, "◌", false)

        fun fromJson(str: String?): WeatherSnapshot? {
            if (str.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(str)
                WeatherSnapshot(
                    temperatureC = if (o.isNull("t")) null else o.optInt("t"),
                    weatherCode = if (o.isNull("c")) null else o.optInt("c"),
                    precipitationPercent = if (o.isNull("p")) null else o.optInt("p"),
                    icon = o.optString("i", "◌"),
                    isAvailable = o.optBoolean("a", false),
                    fetchedAt = o.optLong("at", 0L)
                )
            }.getOrNull()
        }
    }
}
