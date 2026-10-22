package com.quran.watch8.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.RepeatOnResumed
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import com.quran.watch8.util.HijriDate
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The way into the app: the time, today's Hijri date, how long until the next
 * prayer, and one tap back to where reading stopped. Everything else is a chip
 * below. The bezel scrolls the list (ScalingLazyColumn's own rotary handling).
 */
@Composable
fun EntryScreen(
    onNavigate: (String) -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val lastRead by viewModel.lastReadingPosition.collectAsState()

    // Ticks on the minute so the clock and the countdown stay current.
    var now by remember { mutableStateOf(LocalTime.now()) }
    RepeatOnResumed {
        while (true) {
            now = LocalTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val status = remember(now, viewModel.prayerTimes) { PrayerTimesHelper.status(viewModel.prayerTimes) }
    val hijri = remember(now.hour) { HijriDate.arabic() }
    val chipWidth = Modifier.fillMaxWidth(WatchSafeInsets.contentWidthFraction)

    Scaffold(
        timeText = {},
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            item {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            item {
                Text(hijri, fontSize = 12.sp, color = AccentGold, textAlign = TextAlign.Center)
            }
            item {
                Chip(
                    onClick = { onNavigate("prayer") },
                    label = {
                        Text(
                            status?.let { "${it.next.nameAr} ${it.next.formatted}" } ?: "مواقيت الصلاة",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    },
                    secondaryLabel = {
                        Text(status?.let { "بعد ${it.remainingText}" } ?: "جارٍ الحساب…", fontSize = 11.sp)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = chipWidth.padding(top = 6.dp)
                )
            }
            lastRead?.let { pos ->
                item {
                    Chip(
                        onClick = { onNavigate("reader/${pos.surah}?startAyah=${pos.ayahNumber}") },
                        label = { Text("متابعة القراءة", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        secondaryLabel = {
                            Text("${pos.surahNameAr} • ${ReaderTypography.arabicDigits(pos.ayahNumber)}", fontSize = 11.sp, maxLines = 1)
                        },
                        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF0F766E)),
                        modifier = chipWidth
                    )
                }
            }
            item { EntryChip("المصحف والبحث", chipWidth) { onNavigate("quran") } }
            item { EntryChip("الإشارات المرجعية", chipWidth) { onNavigate("bookmarks") } }
            item { EntryChip("الموقع", chipWidth) { onNavigate("locations") } }
            item { EntryChip("الإعدادات والتحديث", chipWidth) { onNavigate("settings") } }
        }
    }
}

@Composable
private fun EntryChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = modifier
    )
}
