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
fun FajrMihrabFace(
    config: WatchFaceConfig,
    data: WatchFaceLiveData,
    actions: WatchFaceActions
) = FaceFrame { scale ->
    val time = remember(data.nowMillis) { SimpleDateFormat("HH:mm", Locale.US).format(Date(data.nowMillis)) }
    Canvas(Modifier.fillMaxSize()) {
        val inset = SAFE_INSET / REFERENCE_SIZE * size.minDimension
        val left = inset; val right = size.width - inset
        val top = size.height * .19f; val bottom = size.height * .80f
        val path = Path().apply {
            moveTo(left, bottom)
            lineTo(left, size.height * .51f)
            cubicTo(left, size.height * .38f, size.width * .38f, size.height * .32f, size.width * .5f, top)
            cubicTo(size.width * .62f, size.height * .32f, right, size.height * .38f, right, size.height * .51f)
            lineTo(right, bottom)
        }
        drawPath(path, Brush.verticalGradient(listOf(Aqua, Aqua.copy(alpha = .28f))), style = Stroke(width = size.width * .006f))
        drawLine(Gold.copy(alpha = .5f), Offset(size.width * .28f, size.height * .55f), Offset(size.width * .72f, size.height * .55f), size.width * .002f)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = scale.d(SAFE_INSET), vertical = scale.d(48f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("☾", color = Gold, fontSize = scale.s(27f))
        Box(Modifier.faceAction("top", config.topSlot, actions)) {
            FaceSlotValue(config.topSlot, ComplicationType.HIJRI_DATE, data, scale) {
                Text(HijriDate.arabic(), color = Color.White, fontSize = scale.s(16f), maxLines = 1)
            }
        }
        Spacer(Modifier.height(scale.d(43f)))
        Text(time, color = Color.White, fontSize = scale.s(74f), fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
        Box(Modifier.faceAction("right", config.rightSlot, actions)) {
            FaceSlotValue(config.rightSlot, ComplicationType.NEXT_PRAYER, data, scale) {
                Text("${data.nextPrayerName} بعد ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}", color = Color.White,
                    fontSize = scale.s(21f), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(scale.d(22f)))
        Box(Modifier.fillMaxWidth().height(scale.d(55f)).faceAction("left", config.leftSlot, actions), contentAlignment = Alignment.Center) {
            FaceSlotValue(config.leftSlot, ComplicationType.SUNRISE_SUNSET, data, scale) { PrayerTable(data, scale, Modifier.fillMaxWidth()) }
        }
        Spacer(Modifier.weight(1f))
        GlassCard(
            Modifier.fillMaxWidth(.72f).height(scale.d(42f)).faceAction("bottom", config.bottomSlot, actions),
            RoundedCornerShape(scale.d(22f))
        ) {
            FaceSlotValue(config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale) {
                Text("📖  ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}", color = Color.White,
                    fontSize = scale.s(19f), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
fun DhikrPulseFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = remember(data.nowMillis) { SimpleDateFormat("HH:mm", Locale.US).format(Date(data.nowMillis)) }
    val progress = data.tasbih.progress
    Canvas(Modifier.fillMaxSize()) {
        val ring = size.minDimension * .29f
        val topLeft = Offset(size.width / 2f - ring, size.height / 2f - ring + size.height * .015f)
        val arcSize = androidx.compose.ui.geometry.Size(ring * 2f, ring * 2f)
        drawArc(Color(0xFF143B32), -90f, 360f, false, topLeft, arcSize, style = Stroke(size.width * .018f, cap = StrokeCap.Round))
        drawArc(Brush.sweepGradient(listOf(Green, Aqua, Green)), -90f, 360f * progress, false, topLeft, arcSize,
            style = Stroke(size.width * .018f, cap = StrokeCap.Round))
    }
    GlassCard(
        Modifier.align(Alignment.TopCenter).offset(y = scale.d(55f)).width(scale.d(116f)).height(scale.d(50f))
            .faceAction("top", config.topSlot, actions), RoundedCornerShape(scale.d(18f)), Green.copy(alpha = .5f)
    ) {
        FaceSlotValue(config.topSlot, ComplicationType.NEXT_PRAYER, data, scale) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.nextPrayerName, color = Color.White, fontSize = scale.s(15f), fontWeight = FontWeight.Bold)
                Text(PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer), color = Gold, fontSize = scale.s(15f))
            }
        }
    }
    MiniStat(
        Modifier.align(Alignment.CenterStart).offset(x = scale.d(55f), y = scale.d(13f)).size(scale.d(58f))
        .faceAction("left", config.leftSlot, actions), if (config.leftSlot == ComplicationType.HIDDEN) "" else config.leftSlot.icon,
        if (config.leftSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.leftSlot, data), scale, Green
    )
    MiniStat(
        Modifier.align(Alignment.CenterEnd).offset(x = -scale.d(55f), y = scale.d(13f)).size(scale.d(58f))
        .faceAction("right", config.rightSlot, actions), if (config.rightSlot == ComplicationType.HIDDEN) "" else if (config.rightSlot == ComplicationType.WEATHER) data.weather.icon else config.rightSlot.icon,
        if (config.rightSlot == ComplicationType.WEATHER) data.weather.temperatureLabel else complicationCompactValue(config.rightSlot, data), scale, Aqua
    )
    Column(Modifier.align(Alignment.Center).offset(y = -scale.d(8f)), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dhikrNames[data.tasbih.dhikrIndex % dhikrNames.size], color = Color.White, fontSize = scale.s(19f), fontWeight = FontWeight.Bold,
            modifier = Modifier.faceAction("tasbih", ComplicationType.TASBIH, actions, longOverride = actions.onOpenTasbih))
        Text(time, color = Color.White, fontSize = scale.s(61f), fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
        GlassCard(
            Modifier.size(scale.d(86f)).faceAction("center", ComplicationType.TASBIH, actions,
                tapOverride = actions.onIncrementTasbih, longOverride = actions.onOpenTasbih),
            RoundedCornerShape(scale.d(43f)), Gold.copy(alpha = .72f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${data.tasbih.count}", color = Green, fontSize = scale.s(33f), fontWeight = FontWeight.Bold)
                Text("/ ${data.tasbih.target}", color = Color.White, fontSize = scale.s(15f))
            }
        }
    }
    GlassCard(
        Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(57f)).width(scale.d(210f)).height(scale.d(43f))
            .faceAction("bottom", config.bottomSlot, actions), RoundedCornerShape(scale.d(22f)), Green.copy(alpha = .6f)
    ) {
        FaceSlotValue(config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale) {
            Text("📖  ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}", color = Color.White,
                fontSize = scale.s(18f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, icon: String, value: String, scale: FaceScale, color: Color) {
    Box(modifier.clip(CircleShape).background(Surface).border(1.dp, color.copy(alpha = .65f), CircleShape), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = scale.s(15f)); Text(value, color = Color.White, fontSize = scale.s(15f), fontWeight = FontWeight.Bold)
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
fun QiblaSerenityFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val (heading, hasSensor) = rememberCompassHeading()
    val bearing = remember(data.latitude, data.longitude) { qiblaBearing(data.latitude, data.longitude) }
    val rotation = normalizedRotation(bearing - heading)
    val time = remember(data.nowMillis) { SimpleDateFormat("HH:mm", Locale.US).format(Date(data.nowMillis)) }
    GlassCard(Modifier.align(Alignment.TopCenter).offset(y = scale.d(53f)).width(scale.d(128f)).height(scale.d(44f))
        .faceAction("top", config.topSlot, actions), RoundedCornerShape(scale.d(16f)), Color(0xFF344554)) {
        FaceSlotValue(config.topSlot, ComplicationType.GREGORIAN_DATE, data, scale) {
            Text(time, color = Color.White, fontSize = scale.s(28f), fontWeight = FontWeight.Light)
        }
    }
    MiniStat(Modifier.align(Alignment.TopStart).offset(x = scale.d(63f), y = scale.d(104f)).size(scale.d(64f))
        .faceAction("left", config.leftSlot, actions), if (config.leftSlot == ComplicationType.WEATHER) data.weather.icon else config.leftSlot.icon,
        if (config.leftSlot == ComplicationType.WEATHER) data.weather.temperatureLabel else complicationCompactValue(config.leftSlot, data), scale, Cyan)
    MiniStat(Modifier.align(Alignment.TopEnd).offset(x = -scale.d(63f), y = scale.d(104f)).size(scale.d(64f))
        .faceAction("right", config.rightSlot, actions), config.rightSlot.icon,
        if (config.rightSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.rightSlot, data), scale, Cyan)
    Box(Modifier.align(Alignment.Center).offset(y = -scale.d(7f)).size(scale.d(205f)).clip(CircleShape)
        .background(Color(0xFF020608)).border(1.dp, Cyan.copy(alpha = .9f), CircleShape)
        .fixedAction(ComplicationType.QIBLA, actions), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(scale.d(148f))) {
            val center = Offset(size.width / 2, size.height / 2)
            val arrow = Path().apply {
                moveTo(center.x, size.height * .06f); lineTo(size.width * .65f, size.height * .58f)
                lineTo(center.x, size.height * .49f); lineTo(size.width * .35f, size.height * .58f); close()
            }
            rotate(rotation, center) { drawPath(arrow, Brush.verticalGradient(listOf(Gold, Color(0xFF9D6500)))) }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = scale.d(18f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("القبلة", color = Gold, fontSize = scale.s(17f), fontWeight = FontWeight.Bold)
            if (!hasSensor) Text("افتح للمعايرة", color = Muted, fontSize = scale.s(10f))
        }
    }
    GlassCard(Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(72f)).width(scale.d(210f)).height(scale.d(50f))
        .faceAction("bottom", config.bottomSlot, actions), RoundedCornerShape(scale.d(25f)), Gold.copy(alpha = .7f)) {
        FaceSlotValue(config.bottomSlot, ComplicationType.NEXT_PRAYER, data, scale) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.nextPrayerName, color = Gold, fontSize = scale.s(15f), fontWeight = FontWeight.Bold)
                Text(PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer), color = Color.White, fontSize = scale.s(17f))
            }
        }
    }
    Text("☀ ${data.prayers?.sunrise?.formatted ?: "—:—"}  ·  ${data.prayers?.maghrib?.formatted ?: "—:—"} ☾",
        color = Color.White, fontSize = scale.s(14f), modifier = Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(50f))
            .fixedAction(ComplicationType.SUNRISE_SUNSET, actions))
}

@Composable
fun QuranGalleryFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = remember(data.nowMillis) { SimpleDateFormat("HH:mm", Locale.US).format(Date(data.nowMillis)) }
    Column(Modifier.fillMaxSize().padding(horizontal = scale.d(SAFE_INSET), vertical = scale.d(50f)), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = Color.White, fontSize = scale.s(43f), fontWeight = FontWeight.Light,
            modifier = Modifier.fixedAction(ComplicationType.GREGORIAN_DATE, actions))
        Box(Modifier.faceAction("top", config.topSlot, actions)) {
            FaceSlotValue(config.topSlot, ComplicationType.HIJRI_DATE, data, scale) { Text(HijriDate.arabic(), color = Gold, fontSize = scale.s(17f)) }
        }
        Spacer(Modifier.height(scale.d(26f)))
        GlassCard(
            Modifier.fillMaxWidth().height(scale.d(185f)).fixedAction(ComplicationType.QURAN_RESUME, actions),
            RoundedCornerShape(scale.d(27f)), Aqua.copy(alpha = .5f)
        ) {
            val label = "سورة ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah} "
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)); append(label); pop()
                    append(data.reading.text)
                },
                color = Color(0xFFFFF5DC), fontSize = scale.s(24f), lineHeight = scale.s(38f),
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = scale.d(18f))
            )
        }
        Spacer(Modifier.height(scale.d(18f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.faceAction("bottom", config.bottomSlot, actions)) {
                FaceSlotValue(config.bottomSlot, ComplicationType.NEXT_PRAYER, data, scale) {
                    Text("${data.nextPrayerName} بعد ${PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer)}", color = Color.White, fontSize = scale.s(18f))
                }
            }
            Text("♧", color = Gold, fontSize = scale.s(29f), modifier = Modifier.fixedAction(ComplicationType.QURAN_RESUME, actions))
        }
    }
    if (config.leftSlot != ComplicationType.HIDDEN) {
        MiniStat(
            Modifier.align(Alignment.TopStart).offset(x = scale.d(58f), y = scale.d(70f)).size(scale.d(52f))
                .faceAction("left", config.leftSlot, actions),
            config.leftSlot.icon, complicationCompactValue(config.leftSlot, data), scale, Aqua
        )
    }
    if (config.rightSlot != ComplicationType.HIDDEN) {
        MiniStat(
            Modifier.align(Alignment.TopEnd).offset(x = -scale.d(58f), y = scale.d(70f)).size(scale.d(52f))
                .faceAction("right", config.rightSlot, actions),
            config.rightSlot.icon, complicationCompactValue(config.rightSlot, data), scale, Gold
        )
    }
}

@Composable
fun DailyOrbitsFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = remember(data.nowMillis) { SimpleDateFormat("HH:mm", Locale.US).format(Date(data.nowMillis)) }
    val date = remember(data.nowMillis) { SimpleDateFormat("EEEE d", Locale("ar")).format(Date(data.nowMillis)) }
    val now = data.nowMillis / 1000
    val daylight = data.prayers?.let { progressBetween(now, it.sunrise.time.epochSecond, it.maghrib.time.epochSecond) } ?: 0f
    val prayerWindow = data.prayers?.let { prayers ->
        val list = listOf(prayers.fajr, prayers.dhuhr, prayers.asr, prayers.maghrib, prayers.isha)
        val previous = list.lastOrNull { it.time.epochSecond <= now } ?: list.first()
        val next = list.firstOrNull { it.time.epochSecond > now } ?: list.last()
        progressBetween(now, previous.time.epochSecond, next.time.epochSecond)
    } ?: 0f
    Canvas(Modifier.fillMaxSize()) {
        fun arc(start: Float, progress: Float, color: Color, radius: Float) {
            val tl = Offset(size.width / 2 - radius, size.height / 2 - radius)
            val sz = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            drawArc(color.copy(alpha = .18f), start, 62f, false, tl, sz, style = Stroke(size.width * .015f, cap = StrokeCap.Round))
            drawArc(color, start, 62f * progress.coerceIn(0f, 1f), false, tl, sz, style = Stroke(size.width * .015f, cap = StrokeCap.Round))
        }
        val r = size.width * .34f
        arc(204f, data.batteryPercent / 100f, Cyan, r)
        arc(274f, prayerWindow, Green, r)
        arc(24f, daylight, Gold, r)
        arc(94f, data.tasbih.progress, Color(0xFFFF5D64), r)
    }
    OrbitLabel(Modifier.align(Alignment.TopStart).offset(x = scale.d(92f), y = scale.d(80f)).faceAction("top", config.topSlot, actions),
        if (config.topSlot == ComplicationType.BATTERY) "بطارية" else config.topSlot.title,
        if (config.topSlot == ComplicationType.BATTERY) "${data.batteryPercent}%" else complicationCompactValue(config.topSlot, data), scale)
    OrbitLabel(Modifier.align(Alignment.TopEnd).offset(x = -scale.d(92f), y = scale.d(80f)).faceAction("left", config.leftSlot, actions),
        if (config.leftSlot == ComplicationType.SUNRISE_SUNSET) "ضوء النهار" else config.leftSlot.title,
        if (config.leftSlot == ComplicationType.SUNRISE_SUNSET) "${(daylight * 100).toInt()}%" else complicationCompactValue(config.leftSlot, data), scale)
    GlassCard(Modifier.align(Alignment.Center).width(scale.d(248f)).height(scale.d(112f))
        .fixedAction(ComplicationType.GREGORIAN_DATE, actions), RoundedCornerShape(scale.d(45f)), Color.White.copy(alpha = .32f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = Color.White, fontSize = scale.s(62f), fontWeight = FontWeight.Light)
            Text(date, color = Color.White, fontSize = scale.s(18f))
        }
    }
    OrbitLabel(Modifier.align(Alignment.BottomStart).offset(x = scale.d(96f), y = -scale.d(90f)).fixedAction(ComplicationType.NEXT_PRAYER, actions),
        "بين الصلاتين", "${(prayerWindow * 100).toInt()}%", scale)
    OrbitLabel(Modifier.align(Alignment.BottomEnd).offset(x = -scale.d(96f), y = -scale.d(90f))
        .faceAction("right", config.rightSlot, actions, tapOverride = if (config.rightSlot == ComplicationType.TASBIH) actions.onIncrementTasbih else null),
        if (config.rightSlot == ComplicationType.TASBIH) "تسبيح" else config.rightSlot.title,
        if (config.rightSlot == ComplicationType.TASBIH) "${data.tasbih.count}/${data.tasbih.target}" else complicationCompactValue(config.rightSlot, data), scale)
    Column(Modifier.align(Alignment.BottomCenter).offset(y = -scale.d(46f)).faceAction("bottom", config.bottomSlot, actions), horizontalAlignment = Alignment.CenterHorizontally) {
        FaceSlotValue(config.bottomSlot, ComplicationType.NEXT_PRAYER, data, scale) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.nextPrayerName, color = Gold, fontSize = scale.s(14f)); Text(PrayerTimesHelper.formatCountdown(data.minutesToNextPrayer), color = Color.White, fontSize = scale.s(17f))
            }
        }
    }
}

@Composable
private fun OrbitLabel(modifier: Modifier, title: String, value: String, scale: FaceScale) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Muted, fontSize = scale.s(14f), maxLines = 1)
        Text(value, color = Color.White, fontSize = scale.s(22f), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun BelieverMosaicFace(config: WatchFaceConfig, data: WatchFaceLiveData, actions: WatchFaceActions) = FaceFrame { scale ->
    val time = remember(data.nowMillis) { SimpleDateFormat("HH:mm", Locale.US).format(Date(data.nowMillis)) }
    Column(Modifier.fillMaxSize().padding(horizontal = scale.d(SAFE_INSET), vertical = scale.d(47f)), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCard(Modifier.width(scale.d(210f)).height(scale.d(58f)).faceAction("top", config.topSlot, actions), RoundedCornerShape(scale.d(29f)), Color(0xFF6B8DA4)) {
            FaceSlotValue(config.topSlot, ComplicationType.WEATHER, data, scale) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.d(10f))) {
                    Text(data.weather.icon, fontSize = scale.s(25f)); Column { Text(data.weather.temperatureLabel, color = Color.White, fontSize = scale.s(24f)); Text(if (data.weather.isAvailable) "الطقس" else "غير متوفر", color = Muted, fontSize = scale.s(12f)) }
                }
            }
        }
        Spacer(Modifier.height(scale.d(12f)))
        Row(Modifier.fillMaxWidth().height(scale.d(125f)), horizontalArrangement = Arrangement.spacedBy(scale.d(8f))) {
            GlassCard(Modifier.weight(.8f).fillMaxHeight().faceAction("left", config.leftSlot, actions), RoundedCornerShape(scale.d(23f)), Aqua.copy(alpha = .65f)) {
                FaceSlotValue(config.leftSlot, ComplicationType.QIBLA, data, scale) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("➤", color = Aqua, fontSize = scale.s(31f)); Text("القبلة", color = Aqua, fontSize = scale.s(16f), fontWeight = FontWeight.Bold) } }
            }
            GlassCard(Modifier.weight(1.55f).fillMaxHeight().fixedAction(ComplicationType.GREGORIAN_DATE, actions), RoundedCornerShape(scale.d(42f)), Gold.copy(alpha = .7f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("☾", color = Gold, fontSize = scale.s(16f)); Text(time, color = Color.White, fontSize = scale.s(48f), fontWeight = FontWeight.Light) }
            }
            GlassCard(Modifier.weight(.8f).fillMaxHeight().faceAction("right", config.rightSlot, actions), RoundedCornerShape(scale.d(23f)), Violet.copy(alpha = .7f)) {
                FaceSlotValue(config.rightSlot, ComplicationType.BATTERY, data, scale) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🔋", fontSize = scale.s(25f)); Text("${data.batteryPercent}%", color = Violet, fontSize = scale.s(22f), fontWeight = FontWeight.Bold); Text("البطارية", color = Muted, fontSize = scale.s(11f)) } }
            }
        }
        Spacer(Modifier.height(scale.d(8f)))
        GlassCard(Modifier.fillMaxWidth().height(scale.d(61f)).faceAction("bottom", config.bottomSlot, actions), RoundedCornerShape(scale.d(30f)), Aqua.copy(alpha = .7f)) {
            FaceSlotValue(config.bottomSlot, ComplicationType.QURAN_RESUME, data, scale) { Text("📖  ${data.reading.surahName.removePrefix("سورة ")} · ${data.reading.ayah}", color = Color.White, fontSize = scale.s(21f), fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        Spacer(Modifier.height(scale.d(7f)))
        Row(Modifier.fillMaxWidth().height(scale.d(75f)), horizontalArrangement = Arrangement.spacedBy(scale.d(8f))) {
            Box(Modifier.size(scale.d(67f)).clip(CircleShape).background(Color(0xFF18170E)).border(1.dp, Gold.copy(alpha = .7f), CircleShape)
                .faceAction("tasbih", ComplicationType.TASBIH, actions, tapOverride = actions.onIncrementTasbih, longOverride = actions.onOpenTasbih), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📿", fontSize = scale.s(18f)); Text("${data.tasbih.count}", color = Gold, fontSize = scale.s(19f), fontWeight = FontWeight.Bold) }
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight().fixedAction(ComplicationType.NEXT_PRAYER, actions), RoundedCornerShape(scale.d(22f)), Color(0xFF43515C)) {
                PrayerTable(data, scale, Modifier.fillMaxWidth().padding(horizontal = scale.d(4f)))
            }
        }
    }
}
