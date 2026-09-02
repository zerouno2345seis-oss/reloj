package com.quran.watch8.ui.screens.watchfaces

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.quran.watch8.data.model.ComplicationType
import com.quran.watch8.data.model.WatchFaceConfig
import com.quran.watch8.util.HijriDate
import com.quran.watch8.util.PrayerTimesHelper
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

const val REFERENCE_SIZE = 438f
const val SAFE_INSET = 53f

private val Aqua = Color(0xFF35E6D0)
private val Cyan = Color(0xFF34D9FF)
private val Gold = Color(0xFFF3C969)
private val Green = Color(0xFF52E59A)
private val Violet = Color(0xFF8A7CFF)
private val Surface = Color(0xFF07141D)
private val SurfaceBright = Color(0xFF0A2630)
private val Muted = Color(0xFF9BA9B5)

data class WatchFaceActions(
    val onAction: (ComplicationType) -> Unit,
    val onAdjustSlot: (String) -> Unit,
    val onIncrementTasbih: () -> Unit,
    val onOpenTasbih: () -> Unit
)

private class FaceScale(private val unit: Float) {
    fun d(value: Float): Dp = (value * unit).dp
    fun s(value: Float): TextUnit = (value * unit).sp
}

private val dhikrNames = listOf("سبحان الله", "الحمد لله", "لا إله إلا الله", "الله أكبر", "أستغفر الله", "اللهم صلِّ على محمد")

private fun complicationCompactValue(type: ComplicationType, data: WatchFaceLiveData): String = when (type) {
    ComplicationType.NEXT_PRAYER, ComplicationType.PRAYER_ALERT -> PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)
    ComplicationType.BATTERY -> "${data.batteryPercent}%"
    ComplicationType.HIJRI_DATE -> HijriDate.shortArabic()
    ComplicationType.GREGORIAN_DATE -> SimpleDateFormat("d MMM", Locale("ar")).format(Date(data.nowMillis))
    ComplicationType.QURAN_RESUME -> "${data.reading.surah}:${data.reading.ayah}"
    ComplicationType.TASBIH -> "${data.tasbih.count}/${data.tasbih.target}"
    ComplicationType.WEATHER -> data.weather.temperatureLabel
    ComplicationType.SUNRISE_SUNSET -> data.prayers?.sunrise?.formatted ?: "—:—"
    ComplicationType.STEP_COUNTER, ComplicationType.HEART_RATE -> "غير متاح"
    ComplicationType.HIDDEN -> ""
    else -> type.title
}

@Composable
private fun FaceFrame(content: @Composable BoxScope.(FaceScale) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val scale = FaceScale(side.value / REFERENCE_SIZE)
        Box(Modifier.size(side).clip(CircleShape).background(Color.Black)) { content(scale) }
    }
}

private fun Modifier.faceAction(
    slot: String,
    type: ComplicationType,
    actions: WatchFaceActions,
    tapOverride: (() -> Unit)? = null,
    longOverride: (() -> Unit)? = null
): Modifier = pointerInput(slot, type) {
    detectTapGestures(
        onTap = { tapOverride?.invoke() ?: actions.onAction(type) },
        onLongPress = { longOverride?.invoke() ?: actions.onAdjustSlot(slot) }
    )
}

private fun Modifier.fixedAction(
    type: ComplicationType,
    actions: WatchFaceActions,
    tapOverride: (() -> Unit)? = null,
    longOverride: (() -> Unit)? = null
): Modifier {
    val tap = tapOverride ?: { actions.onAction(type) }
    return faceAction("fixed", type, actions, tapOverride = tap, longOverride = longOverride ?: tap)
}

private val Ink = Color(0xFF05090C)
private val Panel = Color(0xFF0C1319)
private val Hair = Color(0x1FFFFFFF)
private val HairSoft = Color(0x12FFFFFF)
private val TextDim = Color(0xFF8A97A2)

@Composable
private fun GlassCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    border: Color = Hair,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.clip(shape).background(Panel).border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** A calm rounded strip for a complication, matching the reference faces. */
@Composable
private fun Pill(
    modifier: Modifier,
    scale: FaceScale,
    border: Color = Hair,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Panel)
            .border(1.dp, border, RoundedCornerShape(percent = 50))
            .padding(horizontal = scale.d(16f), vertical = scale.d(7f)),
        horizontalArrangement = Arrangement.spacedBy(scale.d(6f)),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SlotPill(
    modifier: Modifier,
    slot: String,
    type: ComplicationType,
    expected: ComplicationType,
    data: WatchFaceLiveData,
    scale: FaceScale,
    actions: WatchFaceActions,
    tapOverride: (() -> Unit)? = null,
    defaultContent: @Composable RowScope.() -> Unit
) {
    if (type == ComplicationType.HIDDEN) return
    Pill(modifier.faceAction(slot, type, actions, tapOverride = tapOverride), scale) {
        if (type == expected) defaultContent() else {
            Text(type.icon, fontSize = scale.s(12f), maxLines = 1)
            Text(
                complicationLine(type, data), color = Color.White, fontSize = scale.s(13f),
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun complicationLine(type: ComplicationType, data: WatchFaceLiveData): String = when (type) {
    ComplicationType.NEXT_PRAYER, ComplicationType.PRAYER_ALERT -> "${data.nextPrayerName} · ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}"
    ComplicationType.BATTERY -> "${data.batteryPercent}%"
    ComplicationType.HIJRI_DATE -> HijriDate.arabic()
    ComplicationType.GREGORIAN_DATE -> SimpleDateFormat("d MMM", Locale("ar")).format(Date(data.nowMillis))
    ComplicationType.QURAN_RESUME -> "${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}"
    ComplicationType.QIBLA -> "اتجاه القبلة"
    ComplicationType.TASBIH -> "${data.tasbih.count}/${data.tasbih.target}"
    ComplicationType.WEATHER -> data.weather.temperatureLabel
    ComplicationType.SUNRISE_SUNSET -> "${data.prayers?.sunrise?.formatted ?: "—:—"} · ${data.prayers?.maghrib?.formatted ?: "—:—"}"
    ComplicationType.HIDDEN -> ""
    else -> type.title
}

/** Tiny icon + value badge that sits on a ring (used by the radial faces). */
@Composable
private fun RingBadge(modifier: Modifier, icon: String, value: String, scale: FaceScale) {
    if (icon.isEmpty()) return
    Column(
        modifier
            .clip(CircleShape).background(Ink).border(1.dp, Hair, CircleShape)
            .padding(scale.d(9f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = scale.s(14f), maxLines = 1)
        Text(value, color = Color.White, fontSize = scale.s(11f), fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = scale.d(46f)))
    }
}

@Composable
private fun PrayerRow(data: WatchFaceLiveData, scale: FaceScale, modifier: Modifier = Modifier, big: Boolean = false) {
    val prayers = data.prayers
    val values = listOf(
        "فجر" to prayers?.fajr?.formatted, "ظهر" to prayers?.dhuhr?.formatted,
        "عصر" to prayers?.asr?.formatted, "مغرب" to prayers?.maghrib?.formatted, "عشاء" to prayers?.isha?.formatted
    )
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, (name, time) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, color = if (index == 0) Aqua else TextDim, fontSize = scale.s(if (big) 12f else 10.5f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(time ?: "—:—", color = Color.White, fontSize = scale.s(if (big) 12f else 11f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, icon: String, value: String, scale: FaceScale) {
    if (icon.isEmpty()) return
    RingBadge(modifier, icon, value, scale)
}

@Composable
private fun rememberCompassHeading(): Pair<Float, Boolean> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember(manager) { manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var heading by remember { mutableFloatStateOf(0f) }
    DisposableEffect(manager, sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9); val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                heading = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { manager.unregisterListener(listener) }
    }
    return heading to (sensor != null)
}

@Composable
private fun clockOf(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

private fun ayahLabel(data: WatchFaceLiveData) = "سورة ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah} "

// ─────────────────────────────────────────────────────────────────────────────
//  1 — محراب الفجر : a filled mihrab panel holds the time; everything else recedes
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FajrMihrabFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    Column(
        Modifier.fillMaxSize().padding(horizontal = scale.d(12f)).padding(top = scale.d(16f), bottom = scale.d(18f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scale.d(8f), Alignment.CenterVertically)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.d(6f)),
            modifier = Modifier.faceAction("top", config.topSlot, actions)) {
            Text("☾", color = Gold, fontSize = scale.s(13f))
            Text(
                if (config.topSlot == ComplicationType.HIJRI_DATE || config.topSlot == ComplicationType.HIDDEN) HijriDate.arabic()
                else complicationLine(config.topSlot, data),
                color = Gold, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height; val x = w * 0.03f
                val arch = Path().apply {
                    moveTo(x, h)
                    lineTo(x, h * 0.42f)
                    cubicTo(x, h * 0.10f, w * 0.28f, 0f, w * 0.5f, 0f)
                    cubicTo(w * 0.72f, 0f, w - x, h * 0.10f, w - x, h * 0.42f)
                    lineTo(w - x, h); close()
                }
                drawPath(arch, Brush.verticalGradient(listOf(Color(0xFF0E1F26), Color(0xFF060C10))))
                drawPath(arch, Aqua.copy(alpha = 0.55f), style = Stroke(width = w * 0.006f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(time, color = Color.White, fontSize = scale.s(68f), fontWeight = FontWeight.Light, maxLines = 1,
                    modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
                Spacer(Modifier.height(scale.d(6f)))
                Box(Modifier.faceAction("right", config.rightSlot, actions)) {
                    if (config.rightSlot == ComplicationType.NEXT_PRAYER || config.rightSlot == ComplicationType.HIDDEN)
                        Text("${data.nextPrayerName} بعد ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                            color = TextDim, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
                    else Text(complicationLine(config.rightSlot, data), color = TextDim, fontSize = scale.s(13f), maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().faceAction("left", config.leftSlot, actions)) {
            if (config.leftSlot == ComplicationType.SUNRISE_SUNSET || config.leftSlot == ComplicationType.HIDDEN)
                PrayerRow(data, scale, Modifier.fillMaxWidth())
            else Text(complicationLine(config.leftSlot, data), color = Color.White, fontSize = scale.s(12f),
                maxLines = 1, modifier = Modifier.align(Alignment.Center))
        }
        SlotPill(Modifier.faceAction("bottom", config.bottomSlot, actions), "bottom", config.bottomSlot,
            ComplicationType.QURAN_RESUME, data, scale, actions) {
            Text("📖", fontSize = scale.s(12f))
            Text("${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}",
                color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2 — نبض الذكر : a thin progress ring frames the time; badges sit on the ring
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DhikrPulseFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.45f
            val tl = Offset(size.width / 2 - r, size.height / 2 - r)
            val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
            val stroke = size.width * 0.022f
            drawArc(Color(0xFF10241E), -90f, 360f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(Green, Aqua, Green)), -90f, 360f * data.tasbih.progress, false, tl, sz,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        SlotPill(Modifier.align(Alignment.TopCenter).offset(y = scale.d(26f)), "top", config.topSlot,
            ComplicationType.NEXT_PRAYER, data, scale, actions) {
            Text("🕌", fontSize = scale.s(12f))
            Text("${data.nextPrayerName} ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        RingBadge(Modifier.align(Alignment.CenterStart).offset(x = scale.d(16f)).faceAction("left", config.leftSlot, actions),
            if (config.leftSlot == ComplicationType.HIDDEN) "" else config.leftSlot.icon,
            if (config.leftSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.leftSlot, data), scale)
        RingBadge(Modifier.align(Alignment.CenterEnd).offset(x = -scale.d(16f)).faceAction("right", config.rightSlot, actions),
            if (config.rightSlot == ComplicationType.HIDDEN) "" else if (config.rightSlot == ComplicationType.WEATHER) data.weather.icon else config.rightSlot.icon,
            if (config.rightSlot == ComplicationType.WEATHER) data.weather.temperatureLabel else complicationCompactValue(config.rightSlot, data), scale)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dhikrNames[data.tasbih.dhikrIndex % dhikrNames.size], color = TextDim, fontSize = scale.s(14f),
                maxLines = 1, modifier = Modifier.faceAction("tasbih", ComplicationType.TASBIH, actions, longOverride = actions.onOpenTasbih))
            Text(time, color = Color.White, fontSize = scale.s(58f), fontWeight = FontWeight.Light, maxLines = 1,
                modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
            Spacer(Modifier.height(scale.d(8f)))
            Box(Modifier.size(scale.d(64f)).clip(CircleShape).border(1.dp, Gold.copy(alpha = 0.7f), CircleShape)
                .faceAction("center", ComplicationType.TASBIH, actions, tapOverride = actions.onIncrementTasbih, longOverride = actions.onOpenTasbih),
                contentAlignment = Alignment.Center) {
                Text("\u200E${data.tasbih.count} / ${data.tasbih.target}", color = Green, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        SlotPill(Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(26f)), "bottom", config.bottomSlot,
            ComplicationType.QURAN_RESUME, data, scale, actions) {
            Text("📖", fontSize = scale.s(12f))
            Text("${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}",
                color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3 — بوصلة السكينة : a soft ellipse holds the arrow; badges float beside it
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun QiblaSerenityFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val (heading, hasSensor) = rememberCompassHeading()
    val bearing = remember(data.latitude, data.longitude) { qiblaBearing(data.latitude, data.longitude) }
    val rotation = normalizedRotation(bearing - heading)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.align(Alignment.Center).fillMaxWidth(0.74f).aspectRatio(1.34f)) {
            drawOval(Cyan.copy(alpha = 0.7f), style = Stroke(width = size.width * 0.012f))
        }
        SlotPill(Modifier.align(Alignment.TopCenter).offset(y = scale.d(22f)), "top", config.topSlot,
            ComplicationType.GREGORIAN_DATE, data, scale, actions) {
            Text("📅", fontSize = scale.s(11f))
            Text(SimpleDateFormat("d MMM", Locale("ar")).format(Date(data.nowMillis)),
                color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        RingBadge(Modifier.align(Alignment.CenterStart).offset(x = scale.d(12f)).faceAction("left", config.leftSlot, actions),
            if (config.leftSlot == ComplicationType.WEATHER) data.weather.icon else config.leftSlot.icon,
            if (config.leftSlot == ComplicationType.WEATHER) data.weather.temperatureLabel else complicationCompactValue(config.leftSlot, data), scale)
        RingBadge(Modifier.align(Alignment.CenterEnd).offset(x = -scale.d(12f)).faceAction("right", config.rightSlot, actions),
            config.rightSlot.icon,
            if (config.rightSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.rightSlot, data), scale)
        Column(Modifier.align(Alignment.Center).fixedAction(ComplicationType.QIBLA, actions), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(scale.d(78f))) {
                val c = Offset(size.width / 2, size.height / 2)
                val arrow = Path().apply {
                    moveTo(c.x, size.height * 0.06f); lineTo(size.width * 0.68f, size.height * 0.62f)
                    lineTo(c.x, size.height * 0.5f); lineTo(size.width * 0.32f, size.height * 0.62f); close()
                }
                rotate(rotation, c) { drawPath(arrow, Brush.verticalGradient(listOf(Gold, Color(0xFF9D6500)))) }
            }
            Text(if (hasSensor) "القبلة" else "القبلة · للمعايرة", color = Gold, fontSize = scale.s(12f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        SlotPill(Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(42f)), "bottom", config.bottomSlot,
            ComplicationType.NEXT_PRAYER, data, scale, actions) {
            Text("🕌", fontSize = scale.s(12f))
            Text("${data.nextPrayerName} · ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("☾ ${data.prayers?.maghrib?.formatted ?: "—:—"}  ·  ${data.prayers?.sunrise?.formatted ?: "—:—"} ☀",
            color = TextDim, fontSize = scale.s(12f), maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(18f)).fixedAction(ComplicationType.SUNRISE_SUNSET, actions))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4 — رِواق الآية : time + date, one calm verse card, quiet footer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun QuranGalleryFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    Column(
        Modifier.fillMaxSize().padding(horizontal = scale.d(14f)).padding(top = scale.d(18f), bottom = scale.d(18f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scale.d(5f), Alignment.CenterVertically)
    ) {
        Text(time, color = Color.White, fontSize = scale.s(46f), fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.d(5f)),
            modifier = Modifier.faceAction("top", config.topSlot, actions)) {
            Text("☾", color = Gold, fontSize = scale.s(12f))
            Text(
                if (config.topSlot == ComplicationType.HIJRI_DATE || config.topSlot == ComplicationType.HIDDEN) HijriDate.arabic()
                else complicationLine(config.topSlot, data),
                color = Gold, fontSize = scale.s(12f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Spacer(Modifier.height(scale.d(4f)))
        GlassCard(
            Modifier.fillMaxWidth().weight(1f).fixedAction(ComplicationType.QURAN_RESUME, actions),
            RoundedCornerShape(scale.d(22f))
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)); append(ayahLabel(data)); pop()
                    append(data.reading.text)
                },
                color = Color(0xFFF3ECDA), fontSize = scale.s(21f), lineHeight = scale.s(35f),
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(scale.d(18f))
            )
        }
        Spacer(Modifier.height(scale.d(4f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.d(5f)),
            modifier = Modifier.faceAction("bottom", config.bottomSlot, actions)) {
            if (config.bottomSlot == ComplicationType.NEXT_PRAYER || config.bottomSlot == ComplicationType.HIDDEN) {
                Text("🕌", fontSize = scale.s(12f))
                Text("${data.nextPrayerName} ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                    color = TextDim, fontSize = scale.s(12f), fontWeight = FontWeight.Bold, maxLines = 1)
            } else {
                Text(config.bottomSlot.icon, fontSize = scale.s(12f))
                Text(complicationLine(config.bottomSlot, data), color = TextDim, fontSize = scale.s(12f), maxLines = 1)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  5 — مدارات اليوم : four thin arcs, a floating clock capsule, quiet labels
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DailyOrbitsFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    val date = remember(data.nowMillis) { SimpleDateFormat("EEEE d", Locale("ar")).format(Date(data.nowMillis)) }
    val now = data.nowMillis / 1000
    val daylight = data.prayers?.let { progressBetween(now, it.sunrise.time.epochSecond, it.maghrib.time.epochSecond) } ?: 0f
    val prayerWindow = data.prayers?.let { p ->
        val list = listOf(p.fajr, p.dhuhr, p.asr, p.maghrib, p.isha)
        val prev = list.lastOrNull { it.time.epochSecond <= now } ?: list.first()
        val next = list.firstOrNull { it.time.epochSecond > now } ?: list.last()
        progressBetween(now, prev.time.epochSecond, next.time.epochSecond)
    } ?: 0f
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width * 0.415f
            val tl = Offset(size.width / 2 - r, size.height / 2 - r)
            val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
            val stroke = size.width * 0.016f
            fun arc(start: Float, p: Float, color: Color) {
                drawArc(color.copy(alpha = 0.16f), start, 62f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, start, 62f * p.coerceIn(0f, 1f), false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            arc(206f, data.batteryPercent / 100f, Cyan)
            arc(276f, prayerWindow, Green)
            arc(22f, daylight, Gold)
            arc(92f, data.tasbih.progress, Color(0xFFFF5D64))
        }
        GlassCard(
            Modifier.align(Alignment.Center).fillMaxWidth(0.62f).height(scale.d(104f)).fixedAction(ComplicationType.GREGORIAN_DATE, actions),
            RoundedCornerShape(percent = 42)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = Color.White, fontSize = scale.s(52f), fontWeight = FontWeight.Light, maxLines = 1)
                Text(date, color = TextDim, fontSize = scale.s(13f), maxLines = 1)
            }
        }
        OrbitLabel(Modifier.align(Alignment.TopStart).offset(x = scale.d(70f), y = scale.d(92f)).widthIn(max = scale.d(80f)).faceAction("top", config.topSlot, actions),
            if (config.topSlot == ComplicationType.BATTERY) "شحن" else config.topSlot.title,
            if (config.topSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.topSlot, data), scale)
        OrbitLabel(Modifier.align(Alignment.TopEnd).offset(x = -scale.d(70f), y = scale.d(92f)).widthIn(max = scale.d(80f)).faceAction("left", config.leftSlot, actions),
            if (config.leftSlot == ComplicationType.SUNRISE_SUNSET) "النهار" else config.leftSlot.title,
            if (config.leftSlot == ComplicationType.SUNRISE_SUNSET) "${(daylight * 100).toInt()}%" else complicationCompactValue(config.leftSlot, data), scale)
        OrbitLabel(Modifier.align(Alignment.BottomStart).offset(x = scale.d(70f), y = -scale.d(100f)).widthIn(max = scale.d(80f)).fixedAction(ComplicationType.NEXT_PRAYER, actions),
            data.nextPrayerName, PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer), scale)
        OrbitLabel(Modifier.align(Alignment.BottomEnd).offset(x = -scale.d(70f), y = -scale.d(100f)).widthIn(max = scale.d(80f))
            .faceAction("right", config.rightSlot, actions, tapOverride = if (config.rightSlot == ComplicationType.TASBIH) actions.onIncrementTasbih else null),
            if (config.rightSlot == ComplicationType.TASBIH) "ذكر" else config.rightSlot.title,
            if (config.rightSlot == ComplicationType.TASBIH) "${data.tasbih.count}/${data.tasbih.target}" else complicationCompactValue(config.rightSlot, data), scale)
    }
}

@Composable
private fun OrbitLabel(modifier: Modifier, title: String, value: String, scale: FaceScale) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TextDim, fontSize = scale.s(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White, fontSize = scale.s(15f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  6 — فسيفساء المؤمن : a tidy mosaic of hairline tiles
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BelieverMosaicFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    Column(
        Modifier.fillMaxSize().padding(horizontal = scale.d(10f)).padding(top = scale.d(12f), bottom = scale.d(12f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scale.d(9f), Alignment.CenterVertically)
    ) {
        SlotPill(Modifier.faceAction("top", config.topSlot, actions), "top", config.topSlot, ComplicationType.WEATHER, data, scale, actions) {
            Text(data.weather.icon, fontSize = scale.s(14f))
            Text(data.weather.temperatureLabel, color = Color.White, fontSize = scale.s(14f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().height(scale.d(112f)), horizontalArrangement = Arrangement.spacedBy(scale.d(9f))) {
            GlassCard(Modifier.weight(1f).fillMaxHeight().faceAction("left", config.leftSlot, actions), RoundedCornerShape(scale.d(18f))) {
                MosaicCell(if (config.leftSlot == ComplicationType.QIBLA || config.leftSlot == ComplicationType.HIDDEN) "🕋" else config.leftSlot.icon,
                    if (config.leftSlot == ComplicationType.QIBLA || config.leftSlot == ComplicationType.HIDDEN) "القبلة" else complicationCompactValue(config.leftSlot, data), scale)
            }
            GlassCard(Modifier.weight(1.6f).fillMaxHeight().fixedAction(ComplicationType.GREGORIAN_DATE, actions), RoundedCornerShape(scale.d(30f)), Gold.copy(alpha = 0.55f)) {
                Text(time, color = Color.White, fontSize = scale.s(48f), fontWeight = FontWeight.Light, maxLines = 1)
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight().faceAction("right", config.rightSlot, actions), RoundedCornerShape(scale.d(18f))) {
                MosaicCell(if (config.rightSlot == ComplicationType.BATTERY || config.rightSlot == ComplicationType.HIDDEN) "🔋" else config.rightSlot.icon,
                    if (config.rightSlot == ComplicationType.BATTERY || config.rightSlot == ComplicationType.HIDDEN) "${data.batteryPercent}%" else complicationCompactValue(config.rightSlot, data), scale)
            }
        }
        SlotPill(Modifier.faceAction("bottom", config.bottomSlot, actions), "bottom", config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale, actions) {
            Text("📖", fontSize = scale.s(12f))
            Text("${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}", color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().height(scale.d(64f)), horizontalArrangement = Arrangement.spacedBy(scale.d(11f)), verticalAlignment = Alignment.CenterVertically) {
            GlassCard(Modifier.weight(1f).fillMaxHeight().fixedAction(ComplicationType.NEXT_PRAYER, actions), RoundedCornerShape(scale.d(16f))) {
                PrayerRow(data, scale, Modifier.fillMaxWidth().padding(horizontal = scale.d(6f)))
            }
            Box(Modifier.size(scale.d(58f)).clip(CircleShape).border(1.dp, Gold.copy(alpha = 0.6f), CircleShape)
                .faceAction("tasbih", ComplicationType.TASBIH, actions, tapOverride = actions.onIncrementTasbih, longOverride = actions.onOpenTasbih),
                contentAlignment = Alignment.Center) {
                Text("${data.tasbih.count}", color = Gold, fontSize = scale.s(17f), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MosaicCell(icon: String, value: String, scale: FaceScale) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = scale.s(15f), maxLines = 1)
        Text(value, color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
