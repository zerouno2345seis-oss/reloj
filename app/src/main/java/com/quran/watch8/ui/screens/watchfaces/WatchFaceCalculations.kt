package com.quran.watch8.ui.screens.watchfaces

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val KAABA_LATITUDE = 21.422487
private const val KAABA_LONGITUDE = 39.826206

fun qiblaBearing(latitude: Double, longitude: Double): Float {
    val userLat = Math.toRadians(latitude)
    val kaabaLat = Math.toRadians(KAABA_LATITUDE)
    val longitudeDelta = Math.toRadians(KAABA_LONGITUDE - longitude)
    val y = sin(longitudeDelta) * cos(kaabaLat)
    val x = cos(userLat) * sin(kaabaLat) - sin(userLat) * cos(kaabaLat) * cos(longitudeDelta)
    return ((atan2(y, x) * 180.0 / PI + 360.0) % 360.0).toFloat()
}

fun normalizedRotation(degrees: Float): Float = ((degrees + 540f) % 360f) - 180f

fun progressBetween(nowEpochSeconds: Long, startEpochSeconds: Long, endEpochSeconds: Long): Float {
    if (endEpochSeconds <= startEpochSeconds) return 0f
    return ((nowEpochSeconds - startEpochSeconds).toDouble() / (endEpochSeconds - startEpochSeconds))
        .coerceIn(0.0, 1.0).toFloat()
}

data class TasbihState(val count: Int = 0, val target: Int = 33, val dhikrIndex: Int = 0) {
    val safeTarget: Int get() = target.coerceAtLeast(1)
    val progress: Float get() = if (target <= 0) 0f else (count.toFloat() / safeTarget).coerceIn(0f, 1f)
    fun incremented(): TasbihState = copy(count = if (count >= safeTarget) 0 else count + 1)
    fun reset(): TasbihState = copy(count = 0)
}

fun formatQuranReadingLine(surahName: String, ayahNumber: Int, ayahText: String): String {
    val normalizedName = surahName.removePrefix("سورة ").trim().ifEmpty { "الفاتحة" }
    return "سورة $normalizedName · ${ayahNumber.coerceAtLeast(1)} ${ayahText.trim()}".trim()
}
