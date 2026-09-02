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
    ComplicationType.QURAN_RESUME -> "${data.reading.surah}:${data.reading.ayah}"
    ComplicationType.TASBIH -> "${data.tasbih.count}/${data.tasbih.target}"
    ComplicationType.WEATHER -> data.weather.temperatureLabel
    ComplicationType.SUNRISE_SUNSET -> data.prayers?.sunrise?.formatted ?: "—:—"
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

@Composable
private fun GlassCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    border: Color = Aqua.copy(alpha = 0.42f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.clip(shape).background(
            Brush.verticalGradient(listOf(SurfaceBright.copy(alpha = 0.92f), Surface.copy(alpha = 0.94f)))
        ).border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun PrayerTable(data: WatchFaceLiveData, scale: FaceScale, modifier: Modifier = Modifier) {
    val prayers = data.prayers
    val values = listOf(
        "فجر" to prayers?.fajr?.formatted,
        "ظهر" to prayers?.dhuhr?.formatted,
        "عصر" to prayers?.asr?.formatted,
        "مغرب" to prayers?.maghrib?.formatted,
        "عشاء" to prayers?.isha?.formatted
    )
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, (name, time) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, color = if (index == 0) Aqua else Gold, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(time ?: "—:—", color = Color.White, fontSize = scale.s(12f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun FaceSlotValue(
    type: ComplicationType,
    expected: ComplicationType,
    data: WatchFaceLiveData,
    scale: FaceScale,
    defaultContent: @Composable () -> Unit
) {
    if (type == ComplicationType.HIDDEN) return
    if (type == expected) {
        defaultContent()
        return
    }
    val value = when (type) {
        ComplicationType.NEXT_PRAYER, ComplicationType.PRAYER_ALERT -> "${data.nextPrayerName} · ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}"
        ComplicationType.BATTERY -> "${data.batteryPercent}%"
        ComplicationType.HIJRI_DATE -> HijriDate.arabic()
        ComplicationType.GREGORIAN_DATE -> SimpleDateFormat("d MMM", Locale("ar")).format(Date(data.nowMillis))
        ComplicationType.QURAN_RESUME -> "${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}"
        ComplicationType.QIBLA -> "اتجاه القبلة"
        ComplicationType.TASBIH -> "${data.tasbih.count}/${data.tasbih.target}"
        ComplicationType.WEATHER -> data.weather.temperatureLabel
        ComplicationType.SUNRISE_SUNSET -> data.prayers?.sunrise?.formatted ?: "—:—"
        ComplicationType.HIDDEN -> ""
        else -> type.title
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(type.icon, fontSize = scale.s(14f)); Spacer(Modifier.width(scale.d(4f)))
        Text(value, color = Color.White, fontSize = scale.s(14f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun clockOf(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

/**
 * Every face is one vertically-centred column that can never spill outside the
 * 438px circle: the padding keeps content inside the inscribed square and
 * SpaceEvenly distributes the bands so nothing overlaps. Decorative canvases go
 * behind it. Tuned for the Galaxy Watch 8 / 8 Classic (1.34", 438×438).
 */
@Composable
private fun FaceStack(
    scale: FaceScale,
    behind: (@Composable BoxScope.() -> Unit)? = null,
    topPad: Float = 40f,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        behind?.invoke(this)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = scale.d(34f))
                .padding(top = scale.d(topPad), bottom = scale.d(40f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            content = content
        )
    }
}

@Composable
private fun SlotChip(
    slot: String,
    type: ComplicationType,
    expected: ComplicationType,
    data: WatchFaceLiveData,
    scale: FaceScale,
    actions: WatchFaceActions,
    accent: Color = Aqua,
    tapOverride: (() -> Unit)? = null,
    defaultContent: @Composable () -> Unit
) {
    if (type == ComplicationType.HIDDEN) {
        Spacer(Modifier.height(scale.d(2f)))
        return
    }
    GlassCard(
        Modifier
            .widthIn(min = scale.d(72f), max = scale.d(250f))
            .heightIn(min = scale.d(34f))
            .faceAction(slot, type, actions, tapOverride = tapOverride),
        RoundedCornerShape(scale.d(20f)),
        accent.copy(alpha = 0.5f)
    ) {
        Box(Modifier.padding(horizontal = scale.d(14f), vertical = scale.d(6f)), contentAlignment = Alignment.Center) {
            FaceSlotValue(type, expected, data, scale, defaultContent)
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, icon: String, value: String, scale: FaceScale, color: Color) {
    Box(
        modifier.clip(CircleShape).background(Surface).border(1.dp, color.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = scale.s(14f), maxLines = 1)
            Text(value, color = Color.White, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
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
fun FajrMihrabFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    FaceStack(scale, topPad = 34f, behind = {
        Canvas(Modifier.fillMaxSize()) {
            val ins = size.width * 0.15f
            val path = Path().apply {
                moveTo(ins, size.height * 0.82f)
                lineTo(ins, size.height * 0.46f)
                cubicTo(ins, size.height * 0.24f, size.width * 0.30f, size.height * 0.14f, size.width * 0.5f, size.height * 0.12f)
                cubicTo(size.width * 0.70f, size.height * 0.14f, size.width - ins, size.height * 0.24f, size.width - ins, size.height * 0.46f)
                lineTo(size.width - ins, size.height * 0.82f)
            }
            drawPath(path, Brush.verticalGradient(listOf(Aqua, Aqua.copy(alpha = 0.15f))), style = Stroke(width = size.width * 0.006f))
        }
    }) {
        Text("☾", color = Gold, fontSize = scale.s(24f), modifier = Modifier.fixedAction(ComplicationType.HIJRI_DATE, actions))
        SlotChip("top", config.topSlot, ComplicationType.HIJRI_DATE, data, scale, actions, Gold) {
            Text(HijriDate.arabic(), color = Color.White, fontSize = scale.s(15f), maxLines = 1)
        }
        Text(
            time, color = Color.White, fontSize = scale.s(62f), fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions)
        )
        SlotChip("right", config.rightSlot, ComplicationType.NEXT_PRAYER, data, scale, actions, Aqua) {
            Text(
                "${data.nextPrayerName} · ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                color = Color.White, fontSize = scale.s(16f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Box(Modifier.fillMaxWidth().faceAction("left", config.leftSlot, actions), contentAlignment = Alignment.Center) {
            FaceSlotValue(config.leftSlot, ComplicationType.SUNRISE_SUNSET, data, scale) {
                PrayerTable(data, scale, Modifier.fillMaxWidth())
            }
        }
        SlotChip("bottom", config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale, actions, Aqua) {
            Text(
                "📖 ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}",
                color = Color.White, fontSize = scale.s(16f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
    }
}

@Composable
fun DhikrPulseFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    val progress = data.tasbih.progress
    FaceStack(scale, topPad = 30f, behind = {
        Canvas(Modifier.fillMaxSize()) {
            val ring = size.minDimension * 0.40f
            val tl = Offset(size.width / 2f - ring, size.height / 2f - ring)
            val sz = androidx.compose.ui.geometry.Size(ring * 2f, ring * 2f)
            drawArc(Color(0xFF143B32), -90f, 360f, false, tl, sz, style = Stroke(size.width * 0.02f, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(Green, Aqua, Green)), -90f, 360f * progress, false, tl, sz, style = Stroke(size.width * 0.02f, cap = StrokeCap.Round))
        }
    }) {
        SlotChip("top", config.topSlot, ComplicationType.NEXT_PRAYER, data, scale, actions, Green) {
            Text(
                "${data.nextPrayerName} ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                color = Color.White, fontSize = scale.s(14f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            MiniStat(
                Modifier.size(scale.d(52f)).faceAction("left", config.leftSlot, actions),
                if (config.leftSlot == ComplicationType.HIDDEN) "" else config.leftSlot.icon,
                if (config.leftSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.leftSlot, data),
                scale, Green
            )
            Text(
                dhikrNames[data.tasbih.dhikrIndex % dhikrNames.size], color = Color.White, fontSize = scale.s(17f), fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = scale.d(6f)).faceAction("tasbih", ComplicationType.TASBIH, actions, longOverride = actions.onOpenTasbih)
            )
            MiniStat(
                Modifier.size(scale.d(52f)).faceAction("right", config.rightSlot, actions),
                if (config.rightSlot == ComplicationType.HIDDEN) "" else if (config.rightSlot == ComplicationType.WEATHER) data.weather.icon else config.rightSlot.icon,
                if (config.rightSlot == ComplicationType.WEATHER) data.weather.temperatureLabel else complicationCompactValue(config.rightSlot, data),
                scale, Aqua
            )
        }
        Text(
            time, color = Color.White, fontSize = scale.s(52f), fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions)
        )
        Box(
            Modifier.size(scale.d(78f)).clip(CircleShape).background(Gold.copy(alpha = 0.20f)).border(1.dp, Gold.copy(alpha = 0.7f), CircleShape)
                .faceAction("center", ComplicationType.TASBIH, actions, tapOverride = actions.onIncrementTasbih, longOverride = actions.onOpenTasbih),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${data.tasbih.count}", color = Green, fontSize = scale.s(28f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text("/ ${data.tasbih.target}", color = Color.White, fontSize = scale.s(13f), maxLines = 1)
            }
        }
        SlotChip("bottom", config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale, actions, Green) {
            Text(
                "📖 ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}",
                color = Color.White, fontSize = scale.s(15f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
    }
}

@Composable
fun QiblaSerenityFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val (heading, hasSensor) = rememberCompassHeading()
    val bearing = remember(data.latitude, data.longitude) { qiblaBearing(data.latitude, data.longitude) }
    val rotation = normalizedRotation(bearing - heading)
    val time = clockOf(data.nowMillis)
    FaceStack(scale, topPad = 32f) {
        SlotChip("top", config.topSlot, ComplicationType.GREGORIAN_DATE, data, scale, actions, Color(0xFF6FA8C4)) {
            Text(time, color = Color.White, fontSize = scale.s(24f), fontWeight = FontWeight.Light, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MiniStat(
                Modifier.size(scale.d(56f)).faceAction("left", config.leftSlot, actions),
                if (config.leftSlot == ComplicationType.WEATHER) data.weather.icon else config.leftSlot.icon,
                if (config.leftSlot == ComplicationType.WEATHER) data.weather.temperatureLabel else complicationCompactValue(config.leftSlot, data),
                scale, Cyan
            )
            Box(
                Modifier.size(scale.d(132f)).clip(CircleShape).background(Color(0xFF020608)).border(1.dp, Cyan.copy(alpha = 0.9f), CircleShape)
                    .fixedAction(ComplicationType.QIBLA, actions),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(scale.d(96f))) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val arrow = Path().apply {
                        moveTo(c.x, size.height * 0.08f); lineTo(size.width * 0.66f, size.height * 0.60f)
                        lineTo(c.x, size.height * 0.50f); lineTo(size.width * 0.34f, size.height * 0.60f); close()
                    }
                    rotate(rotation, c) { drawPath(arrow, Brush.verticalGradient(listOf(Gold, Color(0xFF9D6500)))) }
                }
            }
            MiniStat(
                Modifier.size(scale.d(56f)).faceAction("right", config.rightSlot, actions),
                config.rightSlot.icon,
                if (config.rightSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.rightSlot, data),
                scale, Cyan
            )
        }
        Text(
            if (hasSensor) "القبلة" else "القبلة · افتح للمعايرة",
            color = Gold, fontSize = scale.s(15f), fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.QIBLA, actions)
        )
        SlotChip("bottom", config.bottomSlot, ComplicationType.NEXT_PRAYER, data, scale, actions, Gold) {
            Text(
                "${data.nextPrayerName} · ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                color = Color.White, fontSize = scale.s(16f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Text(
            "☀ ${data.prayers?.sunrise?.formatted ?: "—:—"}  ·  ${data.prayers?.maghrib?.formatted ?: "—:—"} ☾",
            color = Muted, fontSize = scale.s(13f), maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.SUNRISE_SUNSET, actions)
        )
    }
}

@Composable
fun QuranGalleryFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    FaceStack(scale, topPad = 34f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.d(8f))) {
            Text(
                time, color = Color.White, fontSize = scale.s(40f), fontWeight = FontWeight.Light, maxLines = 1,
                modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions)
            )
        }
        SlotChip("top", config.topSlot, ComplicationType.HIJRI_DATE, data, scale, actions, Gold) {
            Text(HijriDate.arabic(), color = Gold, fontSize = scale.s(15f), maxLines = 1)
        }
        GlassCard(
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = scale.d(120f), max = scale.d(190f))
                .fixedAction(ComplicationType.QURAN_RESUME, actions),
            RoundedCornerShape(scale.d(24f)), Aqua.copy(alpha = 0.5f)
        ) {
            val label = "سورة ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah} "
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)); append(label); pop()
                    append(data.reading.text)
                },
                color = Color(0xFFFFF5DC), fontSize = scale.s(21f), lineHeight = scale.s(33f),
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(scale.d(14f))
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (config.leftSlot != ComplicationType.HIDDEN) {
                MiniStat(
                    Modifier.size(scale.d(48f)).faceAction("left", config.leftSlot, actions),
                    config.leftSlot.icon, complicationCompactValue(config.leftSlot, data), scale, Aqua
                )
            } else Spacer(Modifier.width(scale.d(4f)))
            SlotChip("bottom", config.bottomSlot, ComplicationType.NEXT_PRAYER, data, scale, actions, Aqua) {
                Text(
                    "${data.nextPrayerName} ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}",
                    color = Color.White, fontSize = scale.s(15f), maxLines = 1
                )
            }
            if (config.rightSlot != ComplicationType.HIDDEN) {
                MiniStat(
                    Modifier.size(scale.d(48f)).faceAction("right", config.rightSlot, actions),
                    config.rightSlot.icon, complicationCompactValue(config.rightSlot, data), scale, Gold
                )
            } else Spacer(Modifier.width(scale.d(4f)))
        }
    }
}

@Composable
fun DailyOrbitsFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    val date = remember(data.nowMillis) { SimpleDateFormat("EEEE d", Locale("ar")).format(Date(data.nowMillis)) }
    val now = data.nowMillis / 1000
    val daylight = data.prayers?.let { progressBetween(now, it.sunrise.time.epochSecond, it.maghrib.time.epochSecond) } ?: 0f
    val prayerWindow = data.prayers?.let { prayers ->
        val list = listOf(prayers.fajr, prayers.dhuhr, prayers.asr, prayers.maghrib, prayers.isha)
        val previous = list.lastOrNull { it.time.epochSecond <= now } ?: list.first()
        val next = list.firstOrNull { it.time.epochSecond > now } ?: list.last()
        progressBetween(now, previous.time.epochSecond, next.time.epochSecond)
    } ?: 0f
    FaceStack(scale, topPad = 30f, behind = {
        Canvas(Modifier.fillMaxSize()) {
            fun arc(start: Float, progress: Float, color: Color) {
                val radius = size.width * 0.40f
                val tl = Offset(size.width / 2 - radius, size.height / 2 - radius)
                val sz = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                drawArc(color.copy(alpha = 0.18f), start, 66f, false, tl, sz, style = Stroke(size.width * 0.016f, cap = StrokeCap.Round))
                drawArc(color, start, 66f * progress.coerceIn(0f, 1f), false, tl, sz, style = Stroke(size.width * 0.016f, cap = StrokeCap.Round))
            }
            arc(202f, data.batteryPercent / 100f, Cyan)
            arc(272f, prayerWindow, Green)
            arc(22f, daylight, Gold)
            arc(92f, data.tasbih.progress, Color(0xFFFF5D64))
        }
    }) {
        Row(Modifier.fillMaxWidth(0.66f), horizontalArrangement = Arrangement.SpaceBetween) {
            OrbitLabel(
                Modifier.weight(1f).faceAction("top", config.topSlot, actions),
                if (config.topSlot == ComplicationType.BATTERY) "بطارية" else config.topSlot.title,
                if (config.topSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.topSlot, data),
                scale
            )
            OrbitLabel(
                Modifier.weight(1f).faceAction("left", config.leftSlot, actions),
                if (config.leftSlot == ComplicationType.SUNRISE_SUNSET) "النهار" else config.leftSlot.title,
                if (config.leftSlot == ComplicationType.SUNRISE_SUNSET) "${(daylight * 100).toInt()}%" else complicationCompactValue(config.leftSlot, data),
                scale
            )
        }
        Column(
            Modifier.clip(RoundedCornerShape(scale.d(40f))).background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = scale.d(22f), vertical = scale.d(8f)).fixedAction(ComplicationType.GREGORIAN_DATE, actions),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(time, color = Color.White, fontSize = scale.s(54f), fontWeight = FontWeight.Light, maxLines = 1)
            Text(date, color = Color.White, fontSize = scale.s(16f), maxLines = 1, modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
        }
        Row(Modifier.fillMaxWidth(0.66f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            OrbitLabel(
                Modifier.weight(1f).faceAction("bottom", config.bottomSlot, actions),
                if (config.bottomSlot == ComplicationType.NEXT_PRAYER) data.nextPrayerName else config.bottomSlot.title,
                if (config.bottomSlot == ComplicationType.NEXT_PRAYER) PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer) else complicationCompactValue(config.bottomSlot, data),
                scale
            )
            OrbitLabel(
                Modifier.weight(1f).faceAction("right", config.rightSlot, actions, tapOverride = if (config.rightSlot == ComplicationType.TASBIH) actions.onIncrementTasbih else null),
                if (config.rightSlot == ComplicationType.TASBIH) "تسبيح" else config.rightSlot.title,
                if (config.rightSlot == ComplicationType.TASBIH) "${data.tasbih.count}/${data.tasbih.target}" else complicationCompactValue(config.rightSlot, data),
                scale
            )
        }
    }
}

@Composable
private fun OrbitLabel(modifier: Modifier, title: String, value: String, scale: FaceScale) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Muted, fontSize = scale.s(10.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White, fontSize = scale.s(17f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BelieverMosaicFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = clockOf(data.nowMillis)
    FaceStack(scale, topPad = 30f) {
        SlotChip("top", config.topSlot, ComplicationType.WEATHER, data, scale, actions, Color(0xFF6B8DA4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.d(8f))) {
                Text(data.weather.icon, fontSize = scale.s(20f), modifier = Modifier.fixedAction(ComplicationType.WEATHER, actions))
                Text(data.weather.temperatureLabel, color = Color.White, fontSize = scale.s(18f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (data.weather.isAvailable) "الطقس" else "غير متوفر", color = Muted, fontSize = scale.s(11f), maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().height(scale.d(96f)), horizontalArrangement = Arrangement.spacedBy(scale.d(7f))) {
            GlassCard(Modifier.weight(0.85f).fillMaxHeight().faceAction("left", config.leftSlot, actions), RoundedCornerShape(scale.d(20f)), Aqua.copy(alpha = 0.6f)) {
                FaceSlotValue(config.leftSlot, ComplicationType.QIBLA, data, scale) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("➤", color = Aqua, fontSize = scale.s(24f)); Text("القبلة", color = Aqua, fontSize = scale.s(13f), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            GlassCard(Modifier.weight(1.5f).fillMaxHeight().fixedAction(ComplicationType.GREGORIAN_DATE, actions), RoundedCornerShape(scale.d(32f)), Gold.copy(alpha = 0.7f)) {
                Text(time, color = Color.White, fontSize = scale.s(42f), fontWeight = FontWeight.Light, maxLines = 1)
            }
            GlassCard(Modifier.weight(0.85f).fillMaxHeight().faceAction("right", config.rightSlot, actions), RoundedCornerShape(scale.d(20f)), Violet.copy(alpha = 0.7f)) {
                FaceSlotValue(config.rightSlot, ComplicationType.BATTERY, data, scale) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔋", fontSize = scale.s(18f)); Text("${data.batteryPercent}%", color = Violet, fontSize = scale.s(17f), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        SlotChip("bottom", config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale, actions, Aqua) {
            Text(
                "📖 ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}",
                color = Color.White, fontSize = scale.s(15f), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Row(Modifier.fillMaxWidth().height(scale.d(60f)), horizontalArrangement = Arrangement.spacedBy(scale.d(7f)), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(scale.d(56f)).clip(CircleShape).background(Color(0xFF18170E)).border(1.dp, Gold.copy(alpha = 0.7f), CircleShape)
                    .faceAction("tasbih", ComplicationType.TASBIH, actions, tapOverride = actions.onIncrementTasbih, longOverride = actions.onOpenTasbih),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📿", fontSize = scale.s(15f)); Text("${data.tasbih.count}", color = Gold, fontSize = scale.s(16f), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight().fixedAction(ComplicationType.NEXT_PRAYER, actions), RoundedCornerShape(scale.d(18f)), Color(0xFF43515C)) {
                PrayerTable(data, scale, Modifier.fillMaxWidth().padding(horizontal = scale.d(4f)))
            }
        }
    }
}
