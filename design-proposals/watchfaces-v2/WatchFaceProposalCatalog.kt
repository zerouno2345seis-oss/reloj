package com.quran.watch8.designproposal

import com.quran.watch8.data.model.ComplicationType

/**
 * Implementation-ready catalogue for the six proposed faces.
 *
 * Hardware rule: the physical rotating bezel already contains numbers and
 * graduation marks. Renderers must keep a clean 12% circular safe margin and
 * must never draw hour numbers, compass degrees, cardinal letters, or ticks on
 * the display perimeter.
 */
data class WatchFaceProposal(
    val modelId: String,
    val arabicName: String,
    val rendererName: String,
    val description: String,
    val topSlot: ComplicationType,
    val leftSlot: ComplicationType,
    val rightSlot: ComplicationType,
    val bottomSlot: ComplicationType,
    val fixedCapabilities: Set<ComplicationType>,
    val accentColors: List<Long>,
    val imagePath: String
)

val proposedWatchFaces: List<WatchFaceProposal> = listOf(
    WatchFaceProposal(
        modelId = "FAJR_MIHRAB",
        arabicName = "محراب الفجر",
        rendererName = "FajrMihrabFaceView",
        description = "ساعة مركزية داخل محراب هادئ، مع الصلاة القادمة والمواقيت والعودة للقرآن.",
        topSlot = ComplicationType.HIJRI_DATE,
        leftSlot = ComplicationType.SUNRISE_SUNSET,
        rightSlot = ComplicationType.NEXT_PRAYER,
        bottomSlot = ComplicationType.QURAN_RESUME,
        fixedCapabilities = setOf(ComplicationType.NEXT_PRAYER, ComplicationType.QURAN_RESUME),
        accentColors = listOf(0xFF45E6D0, 0xFFF2BE55, 0xFF0A1728),
        imagePath = "design-proposals/watchfaces-v2/images/01-fajr-mihrab.png"
    ),
    WatchFaceProposal(
        modelId = "DHIKR_PULSE",
        arabicName = "نبض الذكر",
        rendererName = "DhikrPulseFaceView",
        description = "حلقة تسبيح مركزية هادئة مع الوقت والصلاة والقراءة الأخيرة.",
        topSlot = ComplicationType.NEXT_PRAYER,
        leftSlot = ComplicationType.BATTERY,
        rightSlot = ComplicationType.WEATHER,
        bottomSlot = ComplicationType.QURAN_RESUME,
        fixedCapabilities = setOf(ComplicationType.TASBIH),
        accentColors = listOf(0xFF72E8B4, 0xFFF0C56C, 0xFF07120E),
        imagePath = "design-proposals/watchfaces-v2/images/02-dhikr-pulse.png"
    ),
    WatchFaceProposal(
        modelId = "QIBLA_SERENITY",
        arabicName = "بوصلة السكينة",
        rendererName = "QiblaSerenityFaceView",
        description = "سهم قبلة واضح بلا تدريج محيطي، مع الوقت والصلاة والطقس والبطارية.",
        topSlot = ComplicationType.GREGORIAN_DATE,
        leftSlot = ComplicationType.WEATHER,
        rightSlot = ComplicationType.BATTERY,
        bottomSlot = ComplicationType.NEXT_PRAYER,
        fixedCapabilities = setOf(ComplicationType.QIBLA, ComplicationType.SUNRISE_SUNSET),
        accentColors = listOf(0xFF22D3EE, 0xFFE7B34B, 0xFF05090D),
        imagePath = "design-proposals/watchfaces-v2/images/03-qibla-serenity.png"
    ),
    WatchFaceProposal(
        modelId = "QURAN_GALLERY",
        arabicName = "رِواق الآية",
        rendererName = "QuranGalleryFaceView",
        description = "متن الآية هو العنصر الأهم، يسبقه اسم السورة ورقم الآية ويتبعه موعد الصلاة.",
        topSlot = ComplicationType.HIJRI_DATE,
        leftSlot = ComplicationType.HIDDEN,
        rightSlot = ComplicationType.HIDDEN,
        bottomSlot = ComplicationType.NEXT_PRAYER,
        fixedCapabilities = setOf(ComplicationType.QURAN_RESUME),
        accentColors = listOf(0xFF0D5B57, 0xFFDDB46C, 0xFF0A0A09),
        imagePath = "design-proposals/watchfaces-v2/images/04-quran-gallery.png"
    ),
    WatchFaceProposal(
        modelId = "DAILY_ORBITS",
        arabicName = "مدارات اليوم",
        rendererName = "DailyOrbitsFaceView",
        description = "أقواس تقدم داخلية قصيرة للبطارية والصلاة وضوء النهار والتسبيح حول ساعة بيضوية.",
        topSlot = ComplicationType.BATTERY,
        leftSlot = ComplicationType.SUNRISE_SUNSET,
        rightSlot = ComplicationType.TASBIH,
        bottomSlot = ComplicationType.NEXT_PRAYER,
        fixedCapabilities = setOf(ComplicationType.BATTERY, ComplicationType.SUNRISE_SUNSET, ComplicationType.TASBIH),
        accentColors = listOf(0xFF26C6F3, 0xFF59DE8A, 0xFFFFB52E, 0xFFFF5D62),
        imagePath = "design-proposals/watchfaces-v2/images/05-daily-orbits.png"
    ),
    WatchFaceProposal(
        modelId = "BELIEVER_MOSAIC",
        arabicName = "فسيفساء المؤمن",
        rendererName = "BelieverMosaicFaceView",
        description = "مزيج من البلاطات البيضوية والمربعة يعرض أهم البيانات بلا ازدحام.",
        topSlot = ComplicationType.WEATHER,
        leftSlot = ComplicationType.QIBLA,
        rightSlot = ComplicationType.BATTERY,
        bottomSlot = ComplicationType.QURAN_RESUME,
        fixedCapabilities = setOf(ComplicationType.NEXT_PRAYER, ComplicationType.TASBIH),
        accentColors = listOf(0xFF12CBE8, 0xFF7B67F4, 0xFFD7A94D, 0xFF0D2633),
        imagePath = "design-proposals/watchfaces-v2/images/06-believer-mosaic.png"
    )
)

/** Entries to append to WatchFaceModelId. All six support the user's digit preference. */
val proposedEnumEntries: List<String> = listOf(
    "FAJR_MIHRAB(\"محراب الفجر\", false)",
    "DHIKR_PULSE(\"نبض الذكر\", false)",
    "QIBLA_SERENITY(\"بوصلة السكينة\", false)",
    "QURAN_GALLERY(\"رِواق الآية\", false)",
    "DAILY_ORBITS(\"مدارات اليوم\", false)",
    "BELIEVER_MOSAIC(\"فسيفساء المؤمن\", false)"
)
