package com.quran.watch8.ui.screens.watchfaces

import androidx.compose.runtime.staticCompositionLocalOf
import com.quran.watch8.util.PrayerTimesHelper
import com.quran.watch8.util.WeatherSnapshot

data class WatchFaceReading(
    val surah: Int = 1,
    val ayah: Int = 1,
    val surahName: String = "الفاتحة",
    val text: String = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
)

data class WatchFaceLiveData(
    val nowMillis: Long,
    val batteryPercent: Int,
    val weather: WeatherSnapshot,
    val prayers: PrayerTimesHelper.DayPrayers?,
    val nextPrayerName: String,
    val minutesToNextPrayer: Int,
    val reading: WatchFaceReading,
    val latitude: Double,
    val longitude: Double,
    val tasbih: TasbihState
)

val LocalWatchFaceLiveData = staticCompositionLocalOf<WatchFaceLiveData?> { null }
