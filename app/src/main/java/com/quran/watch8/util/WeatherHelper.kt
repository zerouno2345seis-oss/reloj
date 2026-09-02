package com.quran.watch8.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherHelper {

    // Buenos Aires coordinates by default
    private const val DEFAULT_LAT = -34.6037
    private const val DEFAULT_LNG = -58.3816

    /**
     * Fetches weather from Open-Meteo API.
     * Returns a formatted string like "22° ☀️ | 🌧️ 10%"
     */
    suspend fun fetchWeatherSnapshot(lat: Double = DEFAULT_LAT, lng: Double = DEFAULT_LNG): WeatherSnapshot {
        return withContext(Dispatchers.IO) {
            try {
                // We use open-meteo for free, no-key weather data
                val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code&daily=precipitation_probability_max&timezone=America%2FArgentina%2FBuenos_Aires"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").toInt()
                    val code = current.getInt("weather_code")
                    
                    val daily = json.getJSONObject("daily")
                    val precipProbArray = daily.getJSONArray("precipitation_probability_max")
                    val precipProb = if (precipProbArray.length() > 0) precipProbArray.getInt(0) else 0

                    WeatherSnapshot(temp, code, precipProb, getWeatherIcon(code), true)
                } else {
                    WeatherSnapshot.unavailable()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                WeatherSnapshot.unavailable()
            }
        }
    }

    suspend fun fetchWeatherSummary(lat: Double = DEFAULT_LAT, lng: Double = DEFAULT_LNG): String =
        fetchWeatherSnapshot(lat, lng).summary

    private fun getWeatherIcon(code: Int): String {
        return when (code) {
            0 -> "☀️" // Clear
            1, 2, 3 -> "⛅" // Partly cloudy
            45, 48 -> "🌫️" // Fog
            51, 53, 55, 56, 57 -> "🌧️" // Drizzle
            61, 63, 65, 66, 67 -> "🌧️" // Rain
            71, 73, 75, 77 -> "❄️" // Snow
            80, 81, 82 -> "🌦️" // Rain showers
            85, 86 -> "❄️" // Snow showers
            95, 96, 99 -> "⛈️" // Thunderstorm
            else -> "🌡️"
        }
    }
}

data class WeatherSnapshot(
    val temperatureC: Int?,
    val weatherCode: Int?,
    val precipitationPercent: Int?,
    val icon: String,
    val isAvailable: Boolean
) {
    val temperatureLabel: String get() = temperatureC?.let { "$it°" } ?: "—°"
    val summary: String get() = if (isAvailable) "$temperatureLabel $icon | 🌧️ ${precipitationPercent ?: 0}%" else "غير متوفر"

    companion object {
        fun unavailable() = WeatherSnapshot(null, null, null, "◌", false)
    }
}
