package com.quran.watch8.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.ArgentinaLocations
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import java.time.Instant

/**
 * Lumia Metro Style Prayer Times Screen for Galaxy Watch 8
 *
 * Optimized circular layout with unified tile sizes and guaranteed single-line text (no line breaks).
 */
@Composable
fun PrayerTimesScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val prayers by remember { derivedStateOf { viewModel.prayerTimes } }
    val isLoading by remember { derivedStateOf { viewModel.isLoadingLocation } }
    val notifications by viewModel.notificationsEnabled.collectAsState()
    val locationName by viewModel.selectedLocationName.collectAsState()
    var showLocationPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPrayerTimes()
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            // Header
            item {
                Text(
                    text = "مواقيت الصلاة",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Location Chip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { showLocationPicker = !showLocationPicker }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WatchIcons.LocationPin(modifier = Modifier.size(15.dp), color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = locationName,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            if (showLocationPicker) {
                item {
                    CompactChip(
                        onClick = { viewModel.selectGpsLocation(); showLocationPicker = false },
                        label = { Text("📡 الموقع الحالي (GPS)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(ArgentinaLocations.buenosAiresProvince) { preset ->
                    CompactChip(
                        onClick = { viewModel.selectPreset(preset); showLocationPicker = false },
                        label = { Text(preset.nameAr) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        indicatorColor = AyahYellow
                    )
                }
            } else if (prayers != null) {
                val p = prayers!!
                val now = Instant.now()
                val allPrayers = listOf(p.fajr, p.sunrise, p.dhuhr, p.asr, p.maghrib, p.isha)

                val pastPrayers = allPrayers.filter { it.time.isBefore(now) }
                val lastPrayer = pastPrayers.lastOrNull() ?: p.isha
                val nextPrayer = allPrayers.firstOrNull { it.time.isAfter(now) } ?: p.fajr

                val elapsedSec = (now.epochSecond - lastPrayer.time.epochSecond).coerceAtLeast(0)
                val elapsedH = elapsedSec / 3600
                val elapsedM = (elapsedSec % 3600) / 60
                val elapsedText = if (elapsedH > 0) "$elapsedH س $elapsedM د" else "$elapsedM دقيقة"

                // ── Top Hero Lumia Block (Previous & Next) ─────────────────────
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(WatchSafeInsets.contentWidthFraction),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Previous Prayer Tile (Red/Brown)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF7C2D12))
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "مضى من ${lastPrayer.nameAr}",
                                    fontSize = 9.sp,
                                    color = Color(0xFFFED7AA),
                                    maxLines = 1
                                )
                                Text(
                                    text = elapsedText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // Next Prayer Tile (Green)
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF065F46))
                                .border(1.2.dp, Color(0xFF34D399), RoundedCornerShape(8.dp))
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "باقٍ على ${nextPrayer.nameAr}",
                                    fontSize = 9.sp,
                                    color = Color(0xFFA7F3D0),
                                    maxLines = 1
                                )
                                Text(
                                    text = p.timeUntilNext,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AyahYellow,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "جدول الصلوات اليوم",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }

                // ── Daily Prayer Lumia Tiles (Clean, unified full-width rows) ──
                val prayerItems = listOf(
                    Triple(p.fajr, "🌅", Color(0xFF1E3A8A)),
                    Triple(p.sunrise, "🌞", Color(0xFF854D0E)),
                    Triple(p.dhuhr, "☀️", Color(0xFF047857)),
                    Triple(p.asr, "🌤️", Color(0xFFB45309)),
                    Triple(p.maghrib, "🌇", Color(0xFF9F1239)),
                    Triple(p.isha, "🌙", Color(0xFF4C1D95))
                )

                prayerItems.forEach { (prayer, icon, color) ->
                    val isNext = prayer == nextPrayer
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .border(
                                    width = if (isNext) 2.dp else 0.dp,
                                    color = if (isNext) AyahYellow else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(text = icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = prayer.nameAr,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = prayer.formatted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNext) AyahYellow else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Notifications Toggle
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    ToggleChip(
                        checked = notifications,
                        onCheckedChange = { viewModel.setNotifications(it) },
                        label = { Text("تنبيهات الصلاة") },
                        toggleControl = { Switch(checked = notifications, onCheckedChange = null) },
                        modifier = Modifier.fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                    )
                }
            }
        }
    }
}
