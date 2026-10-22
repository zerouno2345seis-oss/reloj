package com.quran.watch8.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.RepeatOnResumed
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import com.quran.watch8.util.HijriDate
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The way into the app, drawn as a watch face: the time in the middle, the
 * Hijri date above it, the next prayer below it with a ring around the rim that
 * fills from the last prayer to the next one, and a single "continue reading"
 * button. Three small buttons along the bottom open the Quran, bookmarks and
 * settings; the prayer line opens today's table, the place name the locations,
 * and a swipe up also opens the table.
 */
@Composable
fun EntryScreen(
    onNavigate: (String) -> Unit,
    viewModel: MainViewModel
) {
    val lastRead by viewModel.lastReadingPosition.collectAsState()

    // Ticks on the minute so the clock, the countdown and the ring stay current.
    var now by remember { mutableStateOf(LocalTime.now()) }
    RepeatOnResumed {
        while (true) {
            now = LocalTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val prayers = viewModel.prayerTimes
    val status = remember(now, prayers) { PrayerTimesHelper.status(prayers) }
    val hijri = remember(now.hour) { HijriDate.arabic() }
    val weekday = remember(now.hour) {
        val today = LocalDate.now()
        "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))} ${ReaderTypography.arabicDigits(today.dayOfMonth)}"
    }
    val progress = status?.let {
        val total = it.minutesElapsed + it.minutesRemaining
        if (total > 0) it.minutesElapsed / total.toFloat() else 0f
    } ?: 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -60f) onNavigate("prayer") }
                ) { _, dy -> dragged += dy }
            }
    ) {
        // The rim: a faint track, and the stretch of time between two prayers.
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color(0xFF1F2937), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
            )
            if (progress > 0f) {
                drawArc(
                    color = AccentGold, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(hijri, fontSize = 12.sp, color = AccentGold, maxLines = 1)
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 50.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                maxLines = 1
            )
            Text(weekday, fontSize = 11.sp, color = Color(0xFF9CA3AF), maxLines = 1)

            Spacer(Modifier.height(6.dp))
            // The next prayer; a tap opens the whole day.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onNavigate("prayer") }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = status?.let { "${it.next.nameAr} ${it.next.formatted}" } ?: "مواقيت الصلاة",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1
                )
                Text(
                    text = status?.let { "بعد ${it.remainingText}" } ?: "",
                    fontSize = 11.sp, color = AccentGold, maxLines = 1
                )
            }
            val place = prayers?.locationName.orEmpty()
            if (place.isNotBlank()) {
                Text(
                    text = place,
                    fontSize = 10.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onNavigate("locations") }.padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            // Back to where reading stopped.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0F766E))
                    .clickable {
                        val pos = lastRead
                        onNavigate(if (pos != null) "reader/${pos.surah}?startAyah=${pos.ayahNumber}" else "quran")
                    }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = lastRead?.let { "متابعة: ${it.surahNameAr} ${ReaderTypography.arabicDigits(it.ayahNumber)}" } ?: "ابدأ القراءة",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FaceButton(onClick = { onNavigate("settings") }) {
                    Text("⚙", fontSize = 15.sp, color = Color.White)
                }
                FaceButton(size = 38.dp, onClick = { onNavigate("quran") }) {
                    Text("المصحف", fontSize = 9.sp, color = Color.White, maxLines = 1)
                }
                FaceButton(onClick = { onNavigate("bookmarks") }) {
                    WatchIcons.Bookmark(modifier = Modifier.size(16.dp), color = Color.White, starColor = AccentGold)
                }
            }
        }
    }
}

@Composable
private fun FaceButton(size: Dp = 32.dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1F2937))
            .border(1.dp, Color(0xFF374151), CircleShape)
            .clickable(onClick = onClick)
    ) { content() }
}
