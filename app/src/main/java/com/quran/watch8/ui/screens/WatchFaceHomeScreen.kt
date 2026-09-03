package com.quran.watch8.ui.screens

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.items
import androidx.wear.compose.material.rememberScalingLazyListState
import com.quran.watch8.data.model.ComplicationType
import com.quran.watch8.data.model.WatchFaceConfig
import com.quran.watch8.data.model.WatchFaceModelId
import com.quran.watch8.ui.components.RepeatOnResumed
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import com.quran.watch8.ui.screens.watchfaces.*
import com.quran.watch8.util.HijriDate
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * The one complication slot the user rewires by tapping the face. The other
 * three are locked to what the web studio set, so an accidental tap on a corner
 * can't quietly reshuffle the face. All four are still fully configurable from
 * the web studio and via a long press on the slot itself.
 */
private const val FLEX_SLOT = "bottom"

/**
 * 9-Model Watch Face Engine (Frameless Large Dial Carousel Edition)
 * - Single Tap Complication:
 *     • Flex slot ("bottom"), if visible -> Cycles to the next complication.
 *     • Locked slot, if visible -> Opens that slot's detail dialog.
 *     • Any slot, if hidden -> Opens the Complication Chooser to activate it.
 * - Long Press Complication:
 *     • Locked slot -> Opens the Complication Chooser to rewire it.
 *     • QURAN_RESUME -> Direct navigation to Quran Reader at the exact Ayah.
 *     • TASBIH -> Opens Circular Interactive Tasbih Popup Dialog.
 *     • NEXT_PRAYER / PRAYER_ALERT -> Opens Simplified Luxury Prayer Times Schedule Dialog.
 *     • QIBLA -> Direct navigation to Qibla Compass screen.
 *     • BATTERY -> Opens Simplified Battery Details Dialog.
 *     • HIJRI_DATE / GREGORIAN_DATE -> Opens Simplified Islamic Calendar & Events Dialog.
 *     • WEATHER -> Opens Simplified Weather Info Dialog.
 *     • SUNRISE_SUNSET -> Opens Sunrise & Sunset Dialog.
 *     • DAILY_ATHKAR -> Opens Daily Athkar Dialog.
 *     • STEP_COUNTER / HEART_RATE -> Opens Health & Activity Dialog.
 *     • FASTING_TRACKER -> Opens Voluntary Fasting & Imsak Dialog.
 *     • HIDDEN -> Opens Complication Chooser Dialog to activate.
 * - Single Tap Quran Emblem 📖 or swipe left: Enters Tiles (Layer 2).
 * - Long Press Quran Emblem 📖: Opens Full Luxury Prayer Times Popup with Elapsed/Remaining.
 * - Long Press Background: Large Frameless Visual Dial Carousel (Tap any face to apply).
 * - Swipe up: App Drawer. Swipe right / swipe down: left to Wear OS (back, quick settings).
 */
@Composable
fun WatchFaceHomeScreen(
    onNavigate: (String) -> Unit,
    onOpenAppDrawer: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val config by viewModel.watchFaceConfig.collectAsState()

    // Only the two chronograph faces sweep a second hand; the other seven render
    // HH:mm, so waking the whole tree every second was 60x more work than needed.
    val needsSecondTick = config.modelId == WatchFaceModelId.CLASSIC_CHRONO_HERITAGE ||
        config.modelId == WatchFaceModelId.CLASSIC_CHRONO_LATIN_ALERT

    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    // Scoped to RESUMED: nobody can read the clock behind a dark screen, and the
    // composition outlives onStop, so a bare LaunchedEffect would tick forever.
    RepeatOnResumed(needsSecondTick) {
        val periodMs = if (needsSecondTick) 1_000L else 60_000L
        while (true) {
            val now = Calendar.getInstance()
            currentTime = now
            // Sleep to the next boundary rather than a flat delay, so the clock
            // never drifts and never wakes earlier than the next visible change.
            delay(periodMs - (now.timeInMillis % periodMs))
        }
    }

    val tickMinute = currentTime.get(Calendar.MINUTE)

    val prayerTimes = viewModel.prayerTimes
    val lastReading by viewModel.lastReadingPosition.collectAsState()
    val lastPosition: Pair<Int, Int> = if (lastReading != null) (lastReading!!.surah to lastReading!!.ayahNumber) else (1 to 1)
    val tasbihState by viewModel.tasbihState.collectAsState()
    val latitude by viewModel.selectedLat.collectAsState()
    val longitude by viewModel.selectedLng.collectAsState()

    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    // Charge moves over minutes, so re-reading it every tick was pure waste.
    val batteryPct = remember(tickMinute) {
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 85
    }

    val nextPrayerInfo = remember(prayerTimes, tickMinute) {
        calculateNextPrayer(prayerTimes, currentTime)
    }
    val nextPrayerName = nextPrayerInfo.first
    val minutesToNextPrayer = nextPrayerInfo.second
    val isAlertActive = minutesToNextPrayer in 0..10
    val newFaceData = WatchFaceLiveData(
        nowMillis = currentTime.timeInMillis,
        batteryPercent = batteryPct,
        weather = viewModel.weatherSnapshot,
        prayers = prayerTimes,
        nextPrayerName = nextPrayerName,
        minutesToNextPrayer = minutesToNextPrayer,
        reading = WatchFaceReading(
            surah = lastReading?.surah ?: 1,
            ayah = lastReading?.ayahNumber ?: 1,
            surahName = lastReading?.surahNameAr ?: "الفاتحة",
            text = viewModel.lastReadingAyahText
        ),
        latitude = latitude,
        longitude = longitude,
        tasbih = tasbihState
    )

    // Popups state
    var showModelCarousel by remember { mutableStateOf(false) }
    var showPrayerSchedulePopup by remember { mutableStateOf(false) }
    var showTasbihPopup by remember { mutableStateOf(false) }
    var showBatteryPopup by remember { mutableStateOf(false) }
    var showCalendarPopup by remember { mutableStateOf(false) }
    var showWeatherPopup by remember { mutableStateOf(false) }
    var showSunrisePopup by remember { mutableStateOf(false) }
    var showAthkarPopup by remember { mutableStateOf(false) }
    var showHealthPopup by remember { mutableStateOf(false) }
    var showFastingPopup by remember { mutableStateOf(false) }

    // Slot Adjust Chooser
    var adjustingSlotName by remember { mutableStateOf<String?>(null) }

    fun vibrate(pattern: Long = 40) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(pattern, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern)
        }
    }

    fun onLongPressComplication(slotName: String, type: ComplicationType) {
        vibrate(70)
        when (type) {
            ComplicationType.QURAN_RESUME -> {
                onNavigate("reader/${lastPosition.first}?startAyah=${lastPosition.second}")
            }
            ComplicationType.TASBIH -> {
                showTasbihPopup = true
            }
            ComplicationType.NEXT_PRAYER, ComplicationType.PRAYER_ALERT -> {
                showPrayerSchedulePopup = true
            }
            ComplicationType.QIBLA -> {
                onNavigate("qibla")
            }
            ComplicationType.BATTERY -> {
                showBatteryPopup = true
            }
            ComplicationType.HIJRI_DATE, ComplicationType.GREGORIAN_DATE -> {
                showCalendarPopup = true
            }
            ComplicationType.WEATHER -> {
                showWeatherPopup = true
            }
            ComplicationType.SUNRISE_SUNSET -> {
                showSunrisePopup = true
            }
            ComplicationType.DAILY_ATHKAR -> {
                showAthkarPopup = true
            }
            ComplicationType.STEP_COUNTER, ComplicationType.HEART_RATE -> {
                showHealthPopup = true
            }
            ComplicationType.FASTING_TRACKER -> {
                showFastingPopup = true
            }
            ComplicationType.HIDDEN -> {
                adjustingSlotName = slotName
            }
        }
    }

    val newFaceActions = WatchFaceActions(
        onAction = { type -> onLongPressComplication("fixed", type) },
        onAdjustSlot = { slot -> adjustingSlotName = slot },
        onIncrementTasbih = {
            vibrate(if (tasbihState.count + 1 >= tasbihState.target) 110 else 35)
            viewModel.incrementTasbih()
        },
        onOpenTasbih = { showTasbihPopup = true }
    )

    var totalDragY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    // Where the drag began, so the drawer only answers a pull from the very
    // bottom edge instead of every upward flick anywhere on the face.
    var dragStartedAtBottom by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        totalDragX = 0f
                        dragStartedAtBottom = offset.y > size.height * DRAWER_EDGE_FRACTION
                    },
                    onDragEnd = {
                        val absY = kotlin.math.abs(totalDragY)
                        val absX = kotlin.math.abs(totalDragX)
                        // Only two app gestures now. Swipe right (system back /
                        // dismiss) and swipe down (system quick settings) are
                        // left to Wear OS, and the prayer schedule moved to a
                        // long press on the emblem or the prayer complication.
                        if (absY > absX && totalDragY < -DRAWER_PULL_PX && dragStartedAtBottom) {
                            // Swipe up -> App Drawer
                            vibrate(40)
                            onOpenAppDrawer()
                        } else if (absX > absY && totalDragX < -30f) {
                            // Swipe left -> forward into the tiles (Layer 2)
                            vibrate(40)
                            onNavigate("tiles")
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                        totalDragX += dragAmount.x
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        vibrate(80)
                        showModelCarousel = true
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Render Active Watch Face Model (1 to 15)
        CompositionLocalProvider(LocalWatchFaceLiveData provides newFaceData) {
        when (config.modelId) {
            WatchFaceModelId.ULTRA_DIGITAL_CLASSIC -> {
                UltraDigitalFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = false,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.CLASSIC_CHRONO_HERITAGE -> {
                ClassicChronoFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = false,
                    isLatin = true,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.CELESTIAL_SOLAR_ARC -> {
                // Variant 1: Celestial Minimal with Circular Perimeter Latin Numbers (1..12) + Arc
                CelestialPerimeterFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT -> {
                UltraDigitalFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.CLASSIC_CHRONO_LATIN_ALERT -> {
                ClassicChronoFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    isLatin = true,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.CELESTIAL_MINIMAL_LATIN_ALERT -> {
                // Variant 2: Celestial Minimal Clean (Big Center Number ONLY, NO perimeter numbers on circle)
                CelestialCenterOnlyFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.EDGE_TYPOGRAPHY_FULL -> {
                EdgeTypographyFullFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.QURANIC_AMBIENT_ORBIT -> {
                QuranicAmbientOrbitFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.SOLAR_HORIZON_FULL -> {
                SolarHorizonFullFaceView(
                    config = config,
                    currentTime = currentTime,
                    batteryPct = batteryPct,
                    minutesToNextPrayer = minutesToNextPrayer,
                    nextPrayerName = nextPrayerName,
                    lastPosition = lastPosition,
                    isAlertActive = isAlertActive,
                    onOpenTiles = { onNavigate("tiles") },
                    onOpenPrayerSchedule = { showPrayerSchedulePopup = true },
                    onCycleSlot = { slot -> viewModel.cycleComplicationSlot(slot) },
                    onLongPressSlot = ::onLongPressComplication,
                    onAdjustSlot = { slot -> adjustingSlotName = slot },
                    vibrate = ::vibrate
                )
            }
            WatchFaceModelId.FAJR_MIHRAB -> FajrMihrabFace(config, newFaceData, newFaceActions)
            WatchFaceModelId.DHIKR_PULSE -> DhikrPulseFace(config, newFaceData, newFaceActions)
            WatchFaceModelId.QIBLA_SERENITY -> QiblaSerenityFace(config, newFaceData, newFaceActions)
            WatchFaceModelId.QURAN_GALLERY -> QuranGalleryFace(config, newFaceData, newFaceActions)
            WatchFaceModelId.DAILY_ORBITS -> DailyOrbitsFace(config, newFaceData, newFaceActions)
            WatchFaceModelId.BELIEVER_MOSAIC -> BelieverMosaicFace(config, newFaceData, newFaceActions)
        }
        }

        // One shared affordance, on every one of the fifteen faces: two page dots
        // saying "there is a second screen". Swiping left already opened the
        // tiles, but nothing on screen ever said so. Tapping them does the same.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { vibrate(30); onNavigate("tiles") })
                }
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
            Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.20f)))
        }

        // ─────────────────────────────────────────────────────────────────────
        // 1. Popup: All Prayer Times Schedule + Elapsed/Remaining Times
        // ─────────────────────────────────────────────────────────────────────
        if (showPrayerSchedulePopup) {
            val details = remember(prayerTimes, tickMinute) {
                computePrayerScheduleDetails(prayerTimes)
            }
            PrayerScheduleDialog(
                details = details,
                onDismiss = { showPrayerSchedulePopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 2. Popup: Interactive Circular Tasbih Counter
        // ─────────────────────────────────────────────────────────────────────
        if (showTasbihPopup) {
            TasbihDialog(
                state = tasbihState,
                onIncrement = viewModel::incrementTasbih,
                onReset = viewModel::resetTasbih,
                onCycleTarget = viewModel::cycleTasbihTarget,
                onCycleDhikr = viewModel::cycleTasbihDhikr,
                onDismiss = { showTasbihPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 3. Popup: Battery Details Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showBatteryPopup) {
            BatteryDetailsDialog(
                batteryPct = batteryPct,
                onDismiss = { showBatteryPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 4. Popup: Islamic Calendar & Events Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showCalendarPopup) {
            IslamicCalendarDialog(
                onDismiss = { showCalendarPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 5. Popup: Weather Info Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showWeatherPopup) {
            WeatherDetailsDialog(
                weather = viewModel.weatherSnapshot,
                onDismiss = { showWeatherPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 6. Popup: Sunrise & Sunset Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showSunrisePopup) {
            SunriseSunsetDialog(
                prayers = prayerTimes,
                onDismiss = { showSunrisePopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 7. Popup: Daily Athkar Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showAthkarPopup) {
            DailyAthkarDialog(
                onDismiss = { showAthkarPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 8. Popup: Health & Activity Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showHealthPopup) {
            HealthActivityDialog(
                onDismiss = { showHealthPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 9. Popup: Voluntary Fasting & Imsak Dialog
        // ─────────────────────────────────────────────────────────────────────
        if (showFastingPopup) {
            FastingDetailsDialog(
                onDismiss = { showFastingPopup = false },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 10. Complication Slot Chooser Dialog (including Hide / Select any)
        // ─────────────────────────────────────────────────────────────────────
        if (adjustingSlotName != null) {
            val targetSlot = adjustingSlotName!!
            ComplicationSlotAdjustDialog(
                slotName = targetSlot,
                currentType = when (targetSlot) {
                    "top" -> config.topSlot
                    "right" -> config.rightSlot
                    "left" -> config.leftSlot
                    "bottom" -> config.bottomSlot
                    else -> ComplicationType.HIDDEN
                },
                onSelectType = { selectedType ->
                    vibrate(40)
                    viewModel.setComplicationSlot(targetSlot, selectedType)
                    adjustingSlotName = null
                },
                onDismiss = { adjustingSlotName = null },
                vibrate = ::vibrate
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // 11. Long Press Background: Large Frameless Visual Dial Carousel
        // ─────────────────────────────────────────────────────────────────────
        if (showModelCarousel) {
            VisualWatchFaceCarousel(
                currentModel = config.modelId,
                onSelectModel = { selectedModel ->
                    vibrate(50)
                    viewModel.setWatchFaceModel(selectedModel)
                    showModelCarousel = false
                },
                onDismiss = { showModelCarousel = false },
                vibrate = ::vibrate
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Ultra Digital Watch Face (Models 1 & 4 - Frameless)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UltraDigitalFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(currentTime.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isAlertActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Complication
            ComplicationSlotWrapper(
                slotName = "top",
                slotType = config.topSlot,
                onCycle = { onCycleSlot("top") },
                onLongPress = { onLongPressSlot("top", config.topSlot) },
                onOpenChooser = { onAdjustSlot("top") },
                vibrate = vibrate,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                if (isAlertActive && config.topSlot != ComplicationType.HIDDEN) {
                    // Calm panel + hairline, same as every other complication —
                    // only the amber text still carries the urgency.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(TilePanel)
                            .border(1.dp, ComplicationHairline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🔔 $nextPrayerName · ${PrayerTimesHelper.formatCountdown(minutesToNextPrayer)}",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else {
                    RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            // Big Crisp Clock
            Text(
                text = timeStr,
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )

            // Middle Triad Row: [Left Complication] [Quran Emblem 📖] [Right Complication]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                ComplicationSlotWrapper(
                    slotName = "left",
                    slotType = config.leftSlot,
                    onCycle = { onCycleSlot("left") },
                    onLongPress = { onLongPressSlot("left", config.leftSlot) },
                    onOpenChooser = { onAdjustSlot("left") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }

                QuranEmblemBadge(
                    onClick = onOpenTiles,
                    onLongClick = onOpenPrayerSchedule,
                    vibrate = vibrate
                )

                ComplicationSlotWrapper(
                    slotName = "right",
                    slotType = config.rightSlot,
                    onCycle = { onCycleSlot("right") },
                    onLongPress = { onLongPressSlot("right", config.rightSlot) },
                    onOpenChooser = { onAdjustSlot("right") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bottom Complication
            ComplicationSlotWrapper(
                slotName = "bottom",
                slotType = config.bottomSlot,
                onCycle = { onCycleSlot("bottom") },
                onLongPress = { onLongPressSlot("bottom", config.bottomSlot) },
                onOpenChooser = { onAdjustSlot("bottom") },
                vibrate = vibrate
            ) {
                RenderComplicationContent(config.bottomSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Classic Chronograph Watch Face (Models 2 & 5 - Frameless)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ClassicChronoFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    isLatin: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val hours = currentTime.get(Calendar.HOUR)
    val minutes = currentTime.get(Calendar.MINUTE)
    val seconds = currentTime.get(Calendar.SECOND)

    val hourAngle = (hours + minutes / 60f) * 30f
    val minuteAngle = (minutes + seconds / 60f) * 6f
    val secondAngle = seconds * 6f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            if (isAlertActive) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 120f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // The physical rotating bezel already carries hour markings.
        }

        // Top Subdial (12 o clock)
        ComplicationSlotWrapper(
            slotName = "top",
            slotType = config.topSlot,
            onCycle = { onCycleSlot("top") },
            onLongPress = { onLongPressSlot("top", config.topSlot) },
            onOpenChooser = { onAdjustSlot("top") },
            vibrate = vibrate,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 42.dp)
        ) {
            RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
        }

        // Left Subdial (9 o clock)
        ComplicationSlotWrapper(
            slotName = "left",
            slotType = config.leftSlot,
            onCycle = { onCycleSlot("left") },
            onLongPress = { onLongPressSlot("left", config.leftSlot) },
            onOpenChooser = { onAdjustSlot("left") },
            vibrate = vibrate,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 36.dp, top = 22.dp)
        ) {
            RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
        }

        // Right Subdial (3 o clock)
        ComplicationSlotWrapper(
            slotName = "right",
            slotType = config.rightSlot,
            onCycle = { onCycleSlot("right") },
            onLongPress = { onLongPressSlot("right", config.rightSlot) },
            onOpenChooser = { onAdjustSlot("right") },
            vibrate = vibrate,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 36.dp, top = 22.dp)
        ) {
            RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
        }

        // Bottom Quran Emblem (6 o clock)
        ClassicQuranMedallion(
            onClick = onOpenTiles,
            onLongClick = onOpenPrayerSchedule,
            vibrate = vibrate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
        )

        // Analog Hands
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            val hRad = Math.toRadians((hourAngle - 90f).toDouble())
            val hLen = 38.dp.toPx()
            drawLine(
                color = Color(0xFFFDE68A),
                start = center,
                end = Offset(center.x + cos(hRad).toFloat() * hLen, center.y + sin(hRad).toFloat() * hLen),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            val mRad = Math.toRadians((minuteAngle - 90f).toDouble())
            val mLen = 58.dp.toPx()
            drawLine(
                color = Color(0xFFFDE68A),
                start = center,
                end = Offset(center.x + cos(mRad).toFloat() * mLen, center.y + sin(mRad).toFloat() * mLen),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            val sRad = Math.toRadians((secondAngle - 90f).toDouble())
            val sLen = 68.dp.toPx()
            drawLine(
                color = Color(0xFFEF4444),
                start = center,
                end = Offset(center.x + cos(sRad).toFloat() * sLen, center.y + sin(sRad).toFloat() * sLen),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(color = Color(0xFFD97706), radius = 4.dp.toPx(), center = center)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Celestial Variant A (Model 3): With Circular Perimeter Latin Numbers (1..12) + Arc
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CelestialPerimeterFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(currentTime.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // The real bezel provides the complete perimeter scale.

            if (isAlertActive) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Complication
            ComplicationSlotWrapper(
                slotName = "top",
                slotType = config.topSlot,
                onCycle = { onCycleSlot("top") },
                onLongPress = { onLongPressSlot("top", config.topSlot) },
                onOpenChooser = { onAdjustSlot("top") },
                vibrate = vibrate,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }

            // Center Digital Clock
            Text(
                text = timeStr,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )

            // Middle Triad Row: [Left Complication] [Quran Emblem 📖] [Right Complication]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                ComplicationSlotWrapper(
                    slotName = "left",
                    slotType = config.leftSlot,
                    onCycle = { onCycleSlot("left") },
                    onLongPress = { onLongPressSlot("left", config.leftSlot) },
                    onOpenChooser = { onAdjustSlot("left") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }

                QuranEmblemBadge(
                    onClick = onOpenTiles,
                    onLongClick = onOpenPrayerSchedule,
                    vibrate = vibrate
                )

                ComplicationSlotWrapper(
                    slotName = "right",
                    slotType = config.rightSlot,
                    onCycle = { onCycleSlot("right") },
                    onLongPress = { onLongPressSlot("right", config.rightSlot) },
                    onOpenChooser = { onAdjustSlot("right") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bottom Complication
            ComplicationSlotWrapper(
                slotName = "bottom",
                slotType = config.bottomSlot,
                onCycle = { onCycleSlot("bottom") },
                onLongPress = { onLongPressSlot("bottom", config.bottomSlot) },
                onOpenChooser = { onAdjustSlot("bottom") },
                vibrate = vibrate
            ) {
                RenderComplicationContent(config.bottomSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Celestial Variant B (Model 6): Minimal Clean (Big Center Time ONLY, NO Perimeter Numbers)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CelestialCenterOnlyFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(currentTime.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            for (i in 0 until 12) {
                val angleRad = Math.toRadians((i * 30.0 - 90.0))
                val dotRadius = radius - 8.dp.toPx()
                val dotCenter = Offset(
                    center.x + cos(angleRad).toFloat() * dotRadius,
                    center.y + sin(angleRad).toFloat() * dotRadius
                )
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                    radius = 2.dp.toPx(),
                    center = dotCenter
                )
            }

            if (isAlertActive) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Complication
            ComplicationSlotWrapper(
                slotName = "top",
                slotType = config.topSlot,
                onCycle = { onCycleSlot("top") },
                onLongPress = { onLongPressSlot("top", config.topSlot) },
                onOpenChooser = { onAdjustSlot("top") },
                vibrate = vibrate,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }

            // Giant Center Clock
            Text(
                text = timeStr,
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )

            // Middle Triad Row: [Left Complication] [Quran Emblem 📖] [Right Complication]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                ComplicationSlotWrapper(
                    slotName = "left",
                    slotType = config.leftSlot,
                    onCycle = { onCycleSlot("left") },
                    onLongPress = { onLongPressSlot("left", config.leftSlot) },
                    onOpenChooser = { onAdjustSlot("left") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }

                QuranEmblemBadge(
                    onClick = onOpenTiles,
                    onLongClick = onOpenPrayerSchedule,
                    vibrate = vibrate
                )

                ComplicationSlotWrapper(
                    slotName = "right",
                    slotType = config.rightSlot,
                    onCycle = { onCycleSlot("right") },
                    onLongPress = { onLongPressSlot("right", config.rightSlot) },
                    onOpenChooser = { onAdjustSlot("right") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bottom Complication
            ComplicationSlotWrapper(
                slotName = "bottom",
                slotType = config.bottomSlot,
                onCycle = { onCycleSlot("bottom") },
                onLongPress = { onLongPressSlot("bottom", config.bottomSlot) },
                onOpenChooser = { onAdjustSlot("bottom") },
                vibrate = vibrate
            ) {
                RenderComplicationContent(config.bottomSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Edge-to-Edge Typography Full Face (Model 7 - Frameless)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EdgeTypographyFullFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(currentTime.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isAlertActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            ComplicationSlotWrapper(
                slotName = "top",
                slotType = config.topSlot,
                onCycle = { onCycleSlot("top") },
                onLongPress = { onLongPressSlot("top", config.topSlot) },
                onOpenChooser = { onAdjustSlot("top") },
                vibrate = vibrate,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }

            Text(
                text = timeStr,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                ComplicationSlotWrapper(
                    slotName = "left",
                    slotType = config.leftSlot,
                    onCycle = { onCycleSlot("left") },
                    onLongPress = { onLongPressSlot("left", config.leftSlot) },
                    onOpenChooser = { onAdjustSlot("left") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }

                QuranEmblemBadge(
                    onClick = onOpenTiles,
                    onLongClick = onOpenPrayerSchedule,
                    vibrate = vibrate
                )

                ComplicationSlotWrapper(
                    slotName = "right",
                    slotType = config.rightSlot,
                    onCycle = { onCycleSlot("right") },
                    onLongPress = { onLongPressSlot("right", config.rightSlot) },
                    onOpenChooser = { onAdjustSlot("right") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            ComplicationSlotWrapper(
                slotName = "bottom",
                slotType = config.bottomSlot,
                onCycle = { onCycleSlot("bottom") },
                onLongPress = { onLongPressSlot("bottom", config.bottomSlot) },
                onOpenChooser = { onAdjustSlot("bottom") },
                vibrate = vibrate
            ) {
                RenderComplicationContent(config.bottomSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Quranic Ambient Orbit Face (Model 8 - Frameless)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QuranicAmbientOrbitFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(currentTime.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Keep the app perimeter unlabelled for the numbered hardware bezel.

            if (isAlertActive) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            ComplicationSlotWrapper(
                slotName = "top",
                slotType = config.topSlot,
                onCycle = { onCycleSlot("top") },
                onLongPress = { onLongPressSlot("top", config.topSlot) },
                onOpenChooser = { onAdjustSlot("top") },
                vibrate = vibrate,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }

            Text(
                text = timeStr,
                fontSize = 50.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                ComplicationSlotWrapper(
                    slotName = "left",
                    slotType = config.leftSlot,
                    onCycle = { onCycleSlot("left") },
                    onLongPress = { onLongPressSlot("left", config.leftSlot) },
                    onOpenChooser = { onAdjustSlot("left") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }

                QuranEmblemBadge(
                    onClick = onOpenTiles,
                    onLongClick = onOpenPrayerSchedule,
                    vibrate = vibrate
                )

                ComplicationSlotWrapper(
                    slotName = "right",
                    slotType = config.rightSlot,
                    onCycle = { onCycleSlot("right") },
                    onLongPress = { onLongPressSlot("right", config.rightSlot) },
                    onOpenChooser = { onAdjustSlot("right") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            ComplicationSlotWrapper(
                slotName = "bottom",
                slotType = config.bottomSlot,
                onCycle = { onCycleSlot("bottom") },
                onLongPress = { onLongPressSlot("bottom", config.bottomSlot) },
                onOpenChooser = { onAdjustSlot("bottom") },
                vibrate = vibrate
            ) {
                RenderComplicationContent(config.bottomSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. Solar Horizon Full Face (Model 9 - Frameless)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SolarHorizonFullFaceView(
    config: WatchFaceConfig,
    currentTime: Calendar,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>,
    isAlertActive: Boolean,
    onOpenTiles: () -> Unit,
    onOpenPrayerSchedule: () -> Unit,
    onCycleSlot: (String) -> Unit,
    onLongPressSlot: (String, ComplicationType) -> Unit,
    onAdjustSlot: (String) -> Unit,
    vibrate: (Long) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(currentTime.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 2.dp.toPx()

            // The alert state keeps its loud amber/red gradient — that's a real
            // signal a prayer is close. At rest the ring used to run a permanent
            // cyan/gold gradient purely for decoration; it now sits as a single
            // quiet hairline stroke, matching the calm system everywhere else.
            if (isAlertActive) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))
                    ),
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = AccentGold.copy(alpha = 0.35f),
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            ComplicationSlotWrapper(
                slotName = "top",
                slotType = config.topSlot,
                onCycle = { onCycleSlot("top") },
                onLongPress = { onLongPressSlot("top", config.topSlot) },
                onOpenChooser = { onAdjustSlot("top") },
                vibrate = vibrate,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                RenderComplicationContent(config.topSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }

            Text(
                text = timeStr,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                ComplicationSlotWrapper(
                    slotName = "left",
                    slotType = config.leftSlot,
                    onCycle = { onCycleSlot("left") },
                    onLongPress = { onLongPressSlot("left", config.leftSlot) },
                    onOpenChooser = { onAdjustSlot("left") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.leftSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }

                QuranEmblemBadge(
                    onClick = onOpenTiles,
                    onLongClick = onOpenPrayerSchedule,
                    vibrate = vibrate
                )

                ComplicationSlotWrapper(
                    slotName = "right",
                    slotType = config.rightSlot,
                    onCycle = { onCycleSlot("right") },
                    onLongPress = { onLongPressSlot("right", config.rightSlot) },
                    onOpenChooser = { onAdjustSlot("right") },
                    vibrate = vibrate
                ) {
                    RenderComplicationContent(config.rightSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            ComplicationSlotWrapper(
                slotName = "bottom",
                slotType = config.bottomSlot,
                onCycle = { onCycleSlot("bottom") },
                onLongPress = { onLongPressSlot("bottom", config.bottomSlot) },
                onOpenChooser = { onAdjustSlot("bottom") },
                vibrate = vibrate
            ) {
                RenderComplicationContent(config.bottomSlot, batteryPct, minutesToNextPrayer, nextPrayerName, lastPosition)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Complication Slot Touch Wrapper (Single Tap = Cycle/Activate, Long Press = Direct Action)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ComplicationSlotWrapper(
    slotName: String,
    slotType: ComplicationType,
    onCycle: () -> Unit,
    onLongPress: () -> Unit,
    onOpenChooser: () -> Unit,
    vibrate: (Long) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isHidden = slotType == ComplicationType.HIDDEN
    val isFlex = slotName == FLEX_SLOT
    Box(
        modifier = modifier
            .pointerInput(slotName, slotType) {
                detectTapGestures(
                    onTap = {
                        vibrate(30)
                        when {
                            isHidden -> onOpenChooser()
                            isFlex -> onCycle()          // flex slot: tap rotates the type
                            else -> onLongPress()        // locked slot: tap opens its detail
                        }
                    },
                    onLongPress = {
                        vibrate(70)
                        when {
                            isHidden -> onOpenChooser()
                            isFlex -> onLongPress()       // flex slot keeps detail on long press
                            else -> onOpenChooser()       // locked slot: long press to rewire
                        }
                    }
                )
            }
            .then(
                if (isHidden) Modifier.size(width = 46.dp, height = 24.dp) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!isHidden) {
            content()
        }
        // A short amber underline marks the one slot that responds to a tap.
        if (isFlex && !isHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .width(14.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AccentGold.copy(alpha = 0.7f))
            )
        }
    }
}

// A neutral hairline (no accent to tint with here, unlike the tiles) that
// matches the weight of TILE_BORDER_ALPHA so the complication chrome reads
// as the same "hairline on a dark panel" language.
private val ComplicationHairline = Color(0x1FFFFFFF)

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Complication Renderer (Proportional Text & Icons, Zero Truncation)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RenderComplicationContent(
    type: ComplicationType,
    batteryPct: Int,
    minutesToNextPrayer: Int,
    nextPrayerName: String,
    lastPosition: Pair<Int, Int>
) {
    if (type == ComplicationType.HIDDEN) return
    val liveData = LocalWatchFaceLiveData.current

    // Same calm system as the tiles: a dark panel and a hairline instead of a
    // saturated slate block, so every complication on the nine original faces
    // reads like the six new ones and the tile grid, not a separate language.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TilePanel)
            .border(1.dp, ComplicationHairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (type) {
                ComplicationType.NEXT_PRAYER -> {
                    Text("🕌", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = PrayerTimesHelper.formatCountdown(minutesToNextPrayer),
                        color = Color(0xFF38BDF8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                ComplicationType.BATTERY -> {
                    Text("🔋", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${batteryPct}%",
                        color = Color(0xFF10B981),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                ComplicationType.HIJRI_DATE -> {
                    Text("🌙", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = HijriDate.latin(),
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                ComplicationType.GREGORIAN_DATE -> {
                    val df = SimpleDateFormat("d MMM", Locale.US)
                    Text("📅", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = df.format(Date()),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                ComplicationType.QURAN_RESUME -> {
                    Text("📖", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    val reading = liveData?.reading
                    Text(
                        text = formatQuranReadingLine(
                            reading?.surahName ?: "الفاتحة",
                            reading?.ayah ?: lastPosition.second,
                            reading?.text.orEmpty(),
                        ),
                        color = Color(0xFFFDE68A),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.widthIn(max = 230.dp),
                    )
                }
                ComplicationType.QIBLA -> {
                    Text("🕋", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    val bearing = liveData?.let { qiblaBearing(it.latitude, it.longitude).toInt() }
                    Text(bearing?.let { "$it°" } ?: "—", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.TASBIH -> {
                    Text("📿", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(liveData?.tasbih?.count?.toString() ?: "—", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.WEATHER -> {
                    Text("⛅", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(liveData?.weather?.temperatureLabel ?: "—°", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.SUNRISE_SUNSET -> {
                    Text("🌅", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(liveData?.prayers?.sunrise?.formatted ?: "—:—", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.DAILY_ATHKAR -> {
                    Text("🤲", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("أذكار", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.STEP_COUNTER -> {
                    Text("🚶‍♂️", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("غير متاح", color = Color.LightGray, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.HEART_RATE -> {
                    Text("❤️", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("غير متاح", color = Color.LightGray, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.FASTING_TRACKER -> {
                    Text("✨", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("إمساك", color = AccentGold, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.PRAYER_ALERT -> {
                    Text("🔔", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(PrayerTimesHelper.formatCountdown(minutesToNextPrayer), color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ComplicationType.HIDDEN -> {
                    // Handled above
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Luxury Prayer Times Schedule Dialog (All 6 Prayers + Elapsed/Remaining)
// ─────────────────────────────────────────────────────────────────────────────
data class PrayerScheduleDetails(
    val list: List<Pair<String, String>>,
    val pastPrayerName: String,
    val pastElapsedMinutes: Int,
    val nextPrayerName: String,
    val nextRemainingMinutes: Int,
    val activeIndex: Int
)

fun computePrayerScheduleDetails(prayers: PrayerTimesHelper.DayPrayers?): PrayerScheduleDetails {
    if (prayers == null) {
        return PrayerScheduleDetails(
            list = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء").map { it to "—:—" },
            pastPrayerName = "—",
            pastElapsedMinutes = 0,
            nextPrayerName = "—",
            nextRemainingMinutes = 0,
            activeIndex = -1
        )
    }

    val prayerList = listOf(
        prayers.fajr,
        prayers.sunrise,
        prayers.dhuhr,
        prayers.asr,
        prayers.maghrib,
        prayers.isha
    )

    val nowInstant = java.time.Instant.now()

    val pastList = prayerList.filter { it.time.isBefore(nowInstant) }
    val pastPrayer = pastList.lastOrNull() ?: prayerList.last()
    val pastElapsedSec = (nowInstant.epochSecond - pastPrayer.time.epochSecond).coerceAtLeast(0)
    val pastElapsedMin = (pastElapsedSec / 60).toInt()

    val nextPrayer = prayerList.firstOrNull { it.time.isAfter(nowInstant) } ?: prayerList.first()
    val nextRemainingSec = (nextPrayer.time.epochSecond - nowInstant.epochSecond).coerceAtLeast(0)
    val nextRemainingMin = (nextRemainingSec / 60).toInt()

    val nextIndex = prayerList.indexOf(nextPrayer).coerceAtLeast(0)

    val list = prayerList.map { it.nameAr to it.formatted }

    return PrayerScheduleDetails(
        list = list,
        pastPrayerName = pastPrayer.nameAr,
        pastElapsedMinutes = pastElapsedMin,
        nextPrayerName = nextPrayer.nameAr,
        nextRemainingMinutes = nextRemainingMin,
        activeIndex = nextIndex
    )
}

@Composable
fun PrayerScheduleDialog(
    details: PrayerScheduleDetails,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text("🌙", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "مواقيت الصلاة",
                        color = Color(0xFFFDE68A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Summary Card: Elapsed & Remaining
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val pastHour = details.pastElapsedMinutes / 60
                        val pastMin = details.pastElapsedMinutes % 60
                        val pastText = if (pastHour > 0) "${pastHour}س ${pastMin}د" else "${pastMin}د"

                        val nextHour = details.nextRemainingMinutes / 60
                        val nextMin = details.nextRemainingMinutes % 60
                        val nextText = if (nextHour > 0) "${nextHour}س ${nextMin}د" else "${nextMin}د"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏳ مضى على ${details.pastPrayerName}", color = Color.LightGray, fontSize = 9.5.sp, maxLines = 1)
                            Text(pastText, color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⌛ متبقي على ${details.nextPrayerName}", color = Color(0xFF38BDF8), fontSize = 9.5.sp, maxLines = 1)
                            Text(nextText, color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            // 6 Prayers List
            items(details.list) { item ->
                val idx = details.list.indexOf(item)
                val isNext = idx == details.activeIndex
                val bg = if (isNext) Color(0xFF0D9488).copy(alpha = 0.4f) else Color(0xFF1E293B).copy(alpha = 0.5f)
                val txtColor = if (isNext) Color(0xFFFDE68A) else Color.White

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 1.5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.first,
                        color = txtColor,
                        fontSize = 11.5.sp,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = item.second,
                        color = if (isNext) Color(0xFF38BDF8) else Color.LightGray,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Close button
            item {
                Button(
                    onClick = { vibrate(30); onDismiss() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Interactive Circular Tasbih Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TasbihDialog(
    state: TasbihState,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    onCycleTarget: () -> Unit,
    onCycleDhikr: () -> Unit,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    val dhikrList = remember {
        listOf("سبحان الله", "الحمد لله", "لا إله إلا الله", "الله أكبر", "أستغفر الله", "اللهم صلِّ على محمد")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header / Dhikr Switcher
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .clickable {
                        vibrate(30)
                        onCycleDhikr()
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = dhikrList[state.dhikrIndex % dhikrList.size],
                    color = Color(0xFFFDE68A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Big Interactive Count Button
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF0D9488), Color(0xFF0F172A))
                        )
                    )
                    .clickable {
                        vibrate(40)
                        onIncrement()
                        if (state.count + 1 >= state.target) {
                            vibrate(120)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.count}",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "الهدف: ${state.target}",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Controls: Reset & Target Switcher & Close
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset
                Button(
                    onClick = { vibrate(40); onReset() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("🔄", fontSize = 11.sp, textAlign = TextAlign.Center)
                }

                // Target
                Button(
                    onClick = {
                        vibrate(30)
                        onCycleTarget()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("${state.target}", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Close
                Button(
                    onClick = { vibrate(30); onDismiss() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Battery Details Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BatteryDetailsDialog(
    batteryPct: Int,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text("🔋", fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${batteryPct}%",
                color = if (batteryPct > 20) Color(0xFF10B981) else Color(0xFFEF4444),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (batteryPct > 20) "الحالة: ممتازة" else "يرجى شحن الساعة",
                color = Color.LightGray,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Islamic Calendar & Events Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IslamicCalendarDialog(
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    val df = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ar")) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("🌙", fontSize = 22.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = HijriDate.arabic(),
                color = AccentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = df.format(Date()),
                color = Color.LightGray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "✨ الأيام البيض: 13-15 صفر\n✨ المولد النبوي: 12 ربيع الأول",
                    color = Color(0xFF38BDF8),
                    fontSize = 9.5.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Weather Details Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WeatherDetailsDialog(
    weather: com.quran.watch8.util.WeatherSnapshot,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(weather.icon, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = weather.temperatureLabel,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (weather.isAvailable) "احتمال المطر ${weather.precipitationPercent ?: 0}%" else "بيانات الطقس غير متوفرة",
                color = Color.LightGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(weather.summary, color = Color(0xFF38BDF8), fontSize = 9.5.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Sunrise & Sunset Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SunriseSunsetDialog(
    prayers: PrayerTimesHelper.DayPrayers?,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("🌅", fontSize = 26.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "الشروق والغروب",
                color = Color(0xFFFDE68A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الشروق", color = Color.LightGray, fontSize = 10.sp)
                    Text(prayers?.sunrise?.formatted ?: "—:—", color = Color(0xFFF59E0B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الغروب", color = Color.LightGray, fontSize = 10.sp)
                    Text(prayers?.maghrib?.formatted ?: "—:—", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(if (prayers == null) "طول النهار: غير متاح" else "المواقيت حسب موقعك المحفوظ", color = Color(0xFF38BDF8), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. Daily Athkar Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DailyAthkarDialog(
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("🤲", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ورد الأذكار اليومي",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(8.dp)
            ) {
                Text(
                    text = "«رضيتُ بالله رباً، وبالإسلام ديناً، وبمحمدٍ ﷺ نبياً ورسولاً»",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Health & Activity Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HealthActivityDialog(
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("🚶‍♂️", fontSize = 26.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "النشاط والصحة",
                color = Color(0xFF34D399),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الخطوات", color = Color.LightGray, fontSize = 10.sp)
                    Text("غير متاح", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("النبض", color = Color.LightGray, fontSize = 10.sp)
                    Text("غير متاح", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. Fasting Details Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FastingDetailsDialog(
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("✨", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "صيام النوافل والإمساك",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الإمساك", color = Color.LightGray, fontSize = 10.sp)
                    Text("05:45", color = Color(0xFF38BDF8), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الإفطار", color = Color.LightGray, fontSize = 10.sp)
                    Text("18:35", color = Color(0xFFF59E0B), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("تقبل الله طاعتكم", color = Color(0xFF34D399), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { vibrate(30); onDismiss() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. Complication Slot Adjust Dialog (Choice list + Hide option)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComplicationSlotAdjustDialog(
    slotName: String,
    currentType: ComplicationType,
    onSelectType: (ComplicationType) -> Unit,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    val slotTitleAr = when (slotName) {
        "top" -> "العلوية"
        "bottom" -> "السفلية"
        "left" -> "اليسرى"
        "right" -> "اليمنى"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "تخصيص المعلومة $slotTitleAr",
                    color = Color(0xFFFDE68A),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            items(ComplicationType.values()) { type ->
                val isSelected = currentType == type
                val isHideOption = type == ComplicationType.HIDDEN
                val btnColor = when {
                    isSelected -> Color(0xFF0D9488)
                    isHideOption -> Color(0xFF7F1D1D)
                    else -> Color(0xFF1E293B)
                }

                Button(
                    onClick = {
                        vibrate(40)
                        onSelectType(type)
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = btnColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(type.icon, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = type.title,
                            color = if (isSelected) Color.White else if (isHideOption) Color(0xFFFCA5A5) else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { vibrate(30); onDismiss() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Text("إلغاء", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 11. Large Frameless Visual Watch Face Carousel (Floating Dials)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VisualWatchFaceCarousel(
    currentModel: WatchFaceModelId,
    onSelectModel: (WatchFaceModelId) -> Unit,
    onDismiss: () -> Unit,
    vibrate: (Long) -> Unit
) {
    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(WatchFaceModelId.values()) { model ->
                val isSelected = currentModel == model
                val shortTitle = when (model) {
                    WatchFaceModelId.ULTRA_DIGITAL_CLASSIC -> "الرقمي الكلاسيكي"
                    WatchFaceModelId.CLASSIC_CHRONO_HERITAGE -> "الكرونوغراف التراثي"
                    WatchFaceModelId.CELESTIAL_SOLAR_ARC -> "فلكي هادئ"
                    WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT -> "الرقمي (تنبيه)"
                    WatchFaceModelId.CLASSIC_CHRONO_LATIN_ALERT -> "الكرونوغراف (تنبيه)"
                    WatchFaceModelId.CELESTIAL_MINIMAL_LATIN_ALERT -> "فلكي نقي (ساعة فقط)"
                    WatchFaceModelId.EDGE_TYPOGRAPHY_FULL -> "الخط الممتد"
                    WatchFaceModelId.QURANIC_AMBIENT_ORBIT -> "المداري القرآني"
                    WatchFaceModelId.SOLAR_HORIZON_FULL -> "الأفق الشمسي"
                    WatchFaceModelId.FAJR_MIHRAB -> "محراب الفجر"
                    WatchFaceModelId.DHIKR_PULSE -> "نبض الذكر"
                    WatchFaceModelId.QIBLA_SERENITY -> "بوصلة السكينة"
                    WatchFaceModelId.QURAN_GALLERY -> "رِواق الآية"
                    WatchFaceModelId.DAILY_ORBITS -> "مدارات اليوم"
                    WatchFaceModelId.BELIEVER_MOSAIC -> "فسيفساء المؤمن"
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clickable {
                            vibrate(50)
                            onSelectModel(model)
                        }
                ) {
                    // Large Frameless Visual Dial Representation (size 140dp)
                    Box(
                        modifier = Modifier
                            .size(122.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF030712))
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color(0xFF14B8A6), CircleShape)
                                else Modifier.border(0.5.dp, Color(0xFF334155), CircleShape)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (model) {
                            WatchFaceModelId.ULTRA_DIGITAL_CLASSIC, WatchFaceModelId.ULTRA_DIGITAL_LATIN_ALERT -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌙 " + HijriDate.latin(), fontSize = 9.sp, color = AccentGold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("12:45", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("📖 1:1  🕌 18m", fontSize = 9.sp, color = Color(0xFF38BDF8))
                                }
                            }
                            WatchFaceModelId.CLASSIC_CHRONO_HERITAGE, WatchFaceModelId.CLASSIC_CHRONO_LATIN_ALERT -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        // Hands
                                        drawLine(Color(0xFFFDE68A), center, Offset(center.x, center.y - 38.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                                        drawLine(Color(0xFFFDE68A), center, Offset(center.x + 44.dp.toPx(), center.y + 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                        drawLine(Color(0xFFEF4444), center, Offset(center.x - 22.dp.toPx(), center.y + 36.dp.toPx()), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                                        drawCircle(Color(0xFFD97706), radius = 4.dp.toPx(), center = center)
                                    }
                                }
                            }
                            WatchFaceModelId.CELESTIAL_SOLAR_ARC -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawArc(
                                            Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFFF59E0B))),
                                            startAngle = 200f,
                                            sweepAngle = 140f,
                                            useCenter = false,
                                            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("12:45", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            }
                            WatchFaceModelId.CELESTIAL_MINIMAL_LATIN_ALERT -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val radius = size.minDimension / 2f
                                        for (i in 0 until 12) {
                                            val angleRad = Math.toRadians((i * 30.0 - 90.0))
                                            val dotRadius = radius - 6.dp.toPx()
                                            val dotCenter = Offset(center.x + cos(angleRad).toFloat() * dotRadius, center.y + sin(angleRad).toFloat() * dotRadius)
                                            drawCircle(Color(0xFF38BDF8).copy(alpha = 0.5f), radius = 2.dp.toPx(), center = dotCenter)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("12:45", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            }
                            WatchFaceModelId.EDGE_TYPOGRAPHY_FULL -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("12:45", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-1).sp)
                                    Text("FULL AMOLED", fontSize = 8.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                }
                            }
                            WatchFaceModelId.QURANIC_AMBIENT_ORBIT -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📖", fontSize = 16.sp)
                                    Text("12:45", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text("المداري", fontSize = 9.sp, color = AccentGold)
                                }
                            }
                            WatchFaceModelId.SOLAR_HORIZON_FULL -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawArc(
                                            Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFF0284C7))),
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Text("12:45", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                            WatchFaceModelId.FAJR_MIHRAB -> NewFaceThumbnail("⌂", "06:12", Color(0xFF35E6D0))
                            WatchFaceModelId.DHIKR_PULSE -> NewFaceThumbnail("◉", "27/33", Color(0xFF52E59A))
                            WatchFaceModelId.QIBLA_SERENITY -> NewFaceThumbnail("➤", "القبلة", Color(0xFFF3C969))
                            WatchFaceModelId.QURAN_GALLERY -> NewFaceThumbnail("📖", "الآية", Color(0xFFF3C969))
                            WatchFaceModelId.DAILY_ORBITS -> NewFaceThumbnail("◜ ◝", "10:08", Color(0xFF34D9FF))
                            WatchFaceModelId.BELIEVER_MOSAIC -> NewFaceThumbnail("▦", "21:06", Color(0xFF8A7CFF))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Small sleek name underneath
                    Text(
                        text = shortTitle,
                        maxLines = 1,
                        color = if (isSelected) Color(0xFF14B8A6) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Button(
                    onClick = { vibrate(30); onDismiss() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .padding(top = 8.dp, bottom = 16.dp)
                ) {
                    Text("إغلاق", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun NewFaceThumbnail(icon: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun calculateNextPrayer(
    prayerTimes: PrayerTimesHelper.DayPrayers?,
    nowCal: Calendar
): Pair<String, Int> {
    if (prayerTimes == null) return "الفجر" to 21
    // PrayerTimesHelper already rolls "next" over to tomorrow's Fajr after
    // Isha, so trust it instead of re-scanning today's list (which clamped
    // the countdown to 0 late at night).
    val next = prayerTimes.nextPrayer ?: prayerTimes.fajr
    val nowInstant = java.time.Instant.now()
    val diffSec = (next.time.epochSecond - nowInstant.epochSecond).coerceAtLeast(0)
    val diffMin = (diffSec / 60).toInt()
    return next.nameAr to diffMin
}

@Composable
fun QuranEmblemBadge(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    vibrate: (Long) -> Unit = {},
    glowColor: Color = Color(0xFF38BDF8),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        vibrate(30)
                        onClick()
                    },
                    onLongPress = {
                        vibrate(80)
                        onLongClick()
                    }
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📖",
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ClassicQuranMedallion(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    vibrate: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        vibrate(30)
                        onClick()
                    },
                    onLongPress = {
                        vibrate(80)
                        onLongClick()
                    }
                )
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📖",
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}
