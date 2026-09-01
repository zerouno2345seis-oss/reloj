package com.quran.watch8.data.model

import org.json.JSONObject

enum class WatchFaceModelId(val title: String, val isLatinOnly: Boolean) {
    ULTRA_DIGITAL_CLASSIC("الرقمي العريض (كلاسيك)", false),
    CLASSIC_CHRONO_HERITAGE("الكلاسيكي المزدوج (تراثي)", false),
    CELESTIAL_SOLAR_ARC("المينيمال الفلكي (مع أرقام دائرية محيطية)", true),
    ULTRA_DIGITAL_LATIN_ALERT("الرقمي اللاتيني (مع تنبيه الصلاة)", true),
    CLASSIC_CHRONO_LATIN_ALERT("الكلاسيكي اللاتيني (مع قوس الأذان)", true),
    CELESTIAL_MINIMAL_LATIN_ALERT("المينيمال الفلكي النقي (رقم كبير في المنتصف بدون أرقام دائرية)", true),
    EDGE_TYPOGRAPHY_FULL("الرقمي الشامل المفتوح (بدون إطار)", true),
    QURANIC_AMBIENT_ORBIT("القرآني المداري الشامل", true),
    SOLAR_HORIZON_FULL("الشمسي النقي كامل الشاشة", true)
}

enum class ComplicationType(val title: String, val icon: String) {
    NEXT_PRAYER("الصلاة القادمة", "🕌"),
    BATTERY("البطارية", "🔋"),
    HIJRI_DATE("التاريخ الهجري", "🌙"),
    GREGORIAN_DATE("التاريخ الميلادي", "📅"),
    QURAN_RESUME("موضع القراءة", "📖"),
    QIBLA("القبلة", "🕋"),
    TASBIH("السبحة", "📿"),
    WEATHER("الطقس", "⛅"),
    SUNRISE_SUNSET("الشروق والغروب", "🌅"),
    DAILY_ATHKAR("أذكار اليوم", "🤲"),
    STEP_COUNTER("الخطوات والنشاط", "🚶‍♂️"),
    HEART_RATE("نبض القلب", "❤️"),
    FASTING_TRACKER("صيام النوافل", "✨"),
    PRAYER_ALERT("تنبيه الاستعداد", "🔔"),
    HIDDEN("إخفاء هذه المعلومة", "🚫");

    fun next(): ComplicationType {
        val values = values()
        val nextIdx = (ordinal + 1) % values.size
        return values[nextIdx]
    }
}

data class WatchFaceConfig(
    val modelId: WatchFaceModelId = WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT,
    val topSlot: ComplicationType = ComplicationType.HIJRI_DATE,
    val rightSlot: ComplicationType = ComplicationType.NEXT_PRAYER,
    val leftSlot: ComplicationType = ComplicationType.BATTERY,
    val bottomSlot: ComplicationType = ComplicationType.QURAN_RESUME,
    val alertMinutes: Int = 10,
    val isLatinDigits: Boolean = true
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("modelId", modelId.name)
            put("topSlot", topSlot.name)
            put("rightSlot", rightSlot.name)
            put("leftSlot", leftSlot.name)
            put("bottomSlot", bottomSlot.name)
            put("alertMinutes", alertMinutes)
            put("isLatinDigits", isLatinDigits)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?): WatchFaceConfig {
            if (jsonStr.isNullOrBlank()) return WatchFaceConfig()
            return try {
                val obj = JSONObject(jsonStr)
                WatchFaceConfig(
                    modelId = WatchFaceModelId.valueOf(obj.optString("modelId", WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT.name)),
                    topSlot = ComplicationType.valueOf(obj.optString("topSlot", ComplicationType.HIJRI_DATE.name)),
                    rightSlot = ComplicationType.valueOf(obj.optString("rightSlot", ComplicationType.NEXT_PRAYER.name)),
                    leftSlot = ComplicationType.valueOf(obj.optString("leftSlot", ComplicationType.BATTERY.name)),
                    bottomSlot = ComplicationType.valueOf(obj.optString("bottomSlot", ComplicationType.QURAN_RESUME.name)),
                    alertMinutes = obj.optInt("alertMinutes", 10),
                    isLatinDigits = obj.optBoolean("isLatinDigits", true)
                )
            } catch (_: Exception) {
                WatchFaceConfig()
            }
        }
    }
}
