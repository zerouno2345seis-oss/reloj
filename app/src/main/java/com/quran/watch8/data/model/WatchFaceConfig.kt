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
    SOLAR_HORIZON_FULL("الشمسي النقي كامل الشاشة", true),
    FAJR_MIHRAB("محراب الفجر", false),
    DHIKR_PULSE("نبض الذكر", false),
    QIBLA_SERENITY("بوصلة السكينة", false),
    QURAN_GALLERY("رِواق الآية", false),
    DAILY_ORBITS("مدارات اليوم", false),
    BELIEVER_MOSAIC("فسيفساء المؤمن", false)
}

enum class ComplicationType(val title: String, val icon: String) {
    NEXT_PRAYER("الصلاة القادمة", "🕌"), BATTERY("البطارية", "🔋"),
    HIJRI_DATE("التاريخ الهجري", "🌙"), GREGORIAN_DATE("التاريخ الميلادي", "📅"),
    QURAN_RESUME("موضع القراءة", "📖"), QIBLA("القبلة", "🕋"), TASBIH("السبحة", "📿"),
    WEATHER("الطقس", "⛅"), SUNRISE_SUNSET("الشروق والغروب", "🌅"),
    DAILY_ATHKAR("أذكار اليوم", "🤲"), STEP_COUNTER("الخطوات والنشاط", "🚶‍♂️"),
    HEART_RATE("نبض القلب", "❤️"), FASTING_TRACKER("صيام النوافل", "✨"),
    PRAYER_ALERT("تنبيه الاستعداد", "🔔"), HIDDEN("إخفاء هذه المعلومة", "🚫");

    fun next(): ComplicationType = values()[(ordinal + 1) % values().size]
}

data class ComplicationSlots(
    val top: ComplicationType,
    val right: ComplicationType,
    val left: ComplicationType,
    val bottom: ComplicationType
) {
    fun withSlot(slot: String, type: ComplicationType): ComplicationSlots = when (slot) {
        "top" -> copy(top = type); "right" -> copy(right = type)
        "left" -> copy(left = type); "bottom" -> copy(bottom = type); else -> this
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("top", top.name); put("right", right.name); put("left", left.name); put("bottom", bottom.name)
    }

    companion object {
        fun fromJson(obj: JSONObject, fallback: ComplicationSlots) = ComplicationSlots(
            obj.enumOr("top", fallback.top), obj.enumOr("right", fallback.right),
            obj.enumOr("left", fallback.left), obj.enumOr("bottom", fallback.bottom)
        )
    }
}

data class WatchFaceConfig(
    val modelId: WatchFaceModelId = WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT,
    val topSlot: ComplicationType = ComplicationType.HIJRI_DATE,
    val rightSlot: ComplicationType = ComplicationType.NEXT_PRAYER,
    val leftSlot: ComplicationType = ComplicationType.BATTERY,
    val bottomSlot: ComplicationType = ComplicationType.QURAN_RESUME,
    val alertMinutes: Int = 10,
    val isLatinDigits: Boolean = true,
    val slotProfiles: Map<String, ComplicationSlots> = emptyMap()
) {
    val activeSlots get() = ComplicationSlots(topSlot, rightSlot, leftSlot, bottomSlot)

    fun withSlot(slot: String, type: ComplicationType): WatchFaceConfig {
        val updated = activeSlots.withSlot(slot, type)
        return copy(topSlot = updated.top, rightSlot = updated.right, leftSlot = updated.left,
            bottomSlot = updated.bottom, slotProfiles = slotProfiles + (modelId.name to updated))
    }

    fun withModel(model: WatchFaceModelId): WatchFaceConfig {
        val saved = slotProfiles + (modelId.name to activeSlots)
        val target = saved[model.name] ?: defaultSlotsFor(model)
        return copy(modelId = model, topSlot = target.top, rightSlot = target.right, leftSlot = target.left,
            bottomSlot = target.bottom, slotProfiles = saved + (model.name to target))
    }

    fun toJson(): String = JSONObject().apply {
        put("modelId", modelId.name); put("topSlot", topSlot.name); put("rightSlot", rightSlot.name)
        put("leftSlot", leftSlot.name); put("bottomSlot", bottomSlot.name)
        put("alertMinutes", alertMinutes); put("isLatinDigits", isLatinDigits)
        put("slotProfiles", JSONObject().apply {
            (slotProfiles + (modelId.name to activeSlots)).forEach { (id, slots) -> put(id, slots.toJson()) }
        })
    }.toString()

    companion object {
        fun defaultSlotsFor(model: WatchFaceModelId): ComplicationSlots = when (model) {
            WatchFaceModelId.FAJR_MIHRAB -> ComplicationSlots(ComplicationType.HIJRI_DATE, ComplicationType.NEXT_PRAYER, ComplicationType.NEXT_PRAYER, ComplicationType.QURAN_RESUME)
            WatchFaceModelId.DHIKR_PULSE -> ComplicationSlots(ComplicationType.NEXT_PRAYER, ComplicationType.WEATHER, ComplicationType.BATTERY, ComplicationType.QURAN_RESUME)
            WatchFaceModelId.QIBLA_SERENITY -> ComplicationSlots(ComplicationType.GREGORIAN_DATE, ComplicationType.BATTERY, ComplicationType.WEATHER, ComplicationType.NEXT_PRAYER)
            WatchFaceModelId.QURAN_GALLERY -> ComplicationSlots(ComplicationType.HIJRI_DATE, ComplicationType.HIDDEN, ComplicationType.HIDDEN, ComplicationType.NEXT_PRAYER)
            WatchFaceModelId.DAILY_ORBITS -> ComplicationSlots(ComplicationType.BATTERY, ComplicationType.TASBIH, ComplicationType.SUNRISE_SUNSET, ComplicationType.NEXT_PRAYER)
            WatchFaceModelId.BELIEVER_MOSAIC -> ComplicationSlots(ComplicationType.WEATHER, ComplicationType.BATTERY, ComplicationType.QIBLA, ComplicationType.QURAN_RESUME)
            else -> ComplicationSlots(ComplicationType.HIJRI_DATE, ComplicationType.NEXT_PRAYER, ComplicationType.BATTERY, ComplicationType.QURAN_RESUME)
        }

        fun fromJson(jsonStr: String?): WatchFaceConfig {
            if (jsonStr.isNullOrBlank()) return WatchFaceConfig()
            return runCatching {
                val obj = JSONObject(jsonStr)
                val model = obj.enumOr("modelId", WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT)
                val legacy = ComplicationSlots(obj.enumOr("topSlot", ComplicationType.HIJRI_DATE),
                    obj.enumOr("rightSlot", ComplicationType.NEXT_PRAYER), obj.enumOr("leftSlot", ComplicationType.BATTERY),
                    obj.enumOr("bottomSlot", ComplicationType.QURAN_RESUME))
                val profilesObject = obj.optJSONObject("slotProfiles")
                val profiles = buildMap {
                    if (profilesObject != null) profilesObject.keys().forEach { id ->
                        profilesObject.optJSONObject(id)?.let { profile ->
                            val profileModel = runCatching { WatchFaceModelId.valueOf(id) }.getOrDefault(model)
                            put(id, ComplicationSlots.fromJson(profile, defaultSlotsFor(profileModel)))
                        }
                    }
                    put(model.name, legacy)
                }
                WatchFaceConfig(model, legacy.top, legacy.right, legacy.left, legacy.bottom,
                    obj.optInt("alertMinutes", 10).coerceIn(1, 60), obj.optBoolean("isLatinDigits", true), profiles)
            }.getOrDefault(WatchFaceConfig())
        }
    }
}

private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, fallback: T): T =
    runCatching { enumValueOf<T>(optString(key, fallback.name)) }.getOrDefault(fallback)
