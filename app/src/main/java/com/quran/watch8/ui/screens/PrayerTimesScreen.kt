package com.quran.watch8.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.data.model.ArgentinaLocations
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import com.quran.watch8.util.PrayerTimesHelper

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
    val method by viewModel.calculationMethod.collectAsState()
    val locationError by remember { derivedStateOf { viewModel.locationError } }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showMethodPicker by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxSize().then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 6.dp, end = 6.dp)
        ) {
            item {
                Text(
                    text = "أوقات الصلاة",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Current location chip
            item {
                Chip(
                    onClick = { showLocationPicker = !showLocationPicker },
                    label = {
                        Column {
                            Text(
                                text = "📍 $locationName",
                                style = MaterialTheme.typography.caption1,
                                color = Color.White,
                                maxLines = 2
                            )
                            Text(
                                text = "اضغط لتغيير الموقع (الأرجنتين)",
                                style = MaterialTheme.typography.caption3,
                                color = Color.Gray
                            )
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A2A1A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            if (showLocationPicker) {
                item {
                    Text(
                        text = "محافظة بوينس آيرس + الأرجنتين",
                        style = MaterialTheme.typography.caption2,
                        color = AyahYellow,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                // GPS
                item {
                    CompactChip(
                        onClick = {
                            viewModel.selectGpsLocation()
                            showLocationPicker = false
                        },
                        label = { Text("📡 الموقع الحالي (GPS)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Buenos Aires focused presets
                items(ArgentinaLocations.buenosAiresProvince) { preset ->
                    CompactChip(
                        onClick = {
                            viewModel.selectPreset(preset)
                            showLocationPicker = false
                        },
                        label = {
                            Text(
                                "${preset.nameAr}\n${preset.nameEs}",
                                style = MaterialTheme.typography.caption3,
                                maxLines = 2
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Other cities
                item {
                    Text(
                        text = "مدن أخرى",
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray
                    )
                }
                items(ArgentinaLocations.allPresets.filter { !it.isProvinceBuenosAires && it.id != "ba_caba" }) { preset ->
                    CompactChip(
                        onClick = {
                            viewModel.selectPreset(preset)
                            showLocationPicker = false
                        },
                        label = {
                            Text(
                                "${preset.nameAr} • ${preset.nameEs}",
                                style = MaterialTheme.typography.caption3
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Method
            item {
                Chip(
                    onClick = { showMethodPicker = !showMethodPicker },
                    label = {
                        Text(
                            "طريقة الحساب: ${PrayerTimesHelper.methodDisplayName(method)}",
                            style = MaterialTheme.typography.caption2
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }

            if (showMethodPicker) {
                listOf("ISNA", "MWL", "EGYPTIAN", "UMM_AL_QURA", "KARACHI").forEach { m ->
                    item {
                        CompactChip(
                            onClick = {
                                viewModel.setCalculationMethod(m)
                                showMethodPicker = false
                            },
                            label = { Text(PrayerTimesHelper.methodDisplayName(m)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (locationError != null) {
                item {
                    Text(
                        text = locationError!!,
                        style = MaterialTheme.typography.caption3,
                        color = Color(0xFFFF9800),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(4.dp)
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
                item {
                    Text(
                        text = "الصلاة القادمة: ${prayers!!.nextPrayer?.nameAr ?: "—"}",
                        style = MaterialTheme.typography.body1,
                        color = AyahYellow,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "متبقي: ${prayers!!.timeUntilNext}",
                        style = MaterialTheme.typography.caption1,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                listOf(
                    prayers!!.fajr,
                    prayers!!.sunrise,
                    prayers!!.dhuhr,
                    prayers!!.asr,
                    prayers!!.maghrib,
                    prayers!!.isha
                ).forEach { prayer ->
                    item {
                        Chip(
                            onClick = {},
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = prayer.nameAr,
                                        style = MaterialTheme.typography.body1,
                                        color = Color.White
                                    )
                                    Text(
                                        text = prayer.formatted,
                                        style = MaterialTheme.typography.body1,
                                        color = AyahYellow
                                    )
                                }
                            },
                            colors = ChipDefaults.secondaryChipColors(
                                backgroundColor = Color(0xFF151515)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    ToggleChip(
                        checked = notifications,
                        onCheckedChange = { viewModel.setNotifications(it) },
                        label = {
                            Text("تنبيهات الصلاة", style = MaterialTheme.typography.button)
                        },
                        toggleControl = {
                            Switch(
                                checked = notifications,
                                onCheckedChange = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    CompactChip(
                        onClick = { viewModel.fetchCurrentLocation() },
                        label = { Text("تحديث GPS") },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = "جاري تحميل الأوقات...\nالافتراضي: بوينس آيرس",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item {
                    Chip(
                        onClick = {
                            viewModel.selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
                        },
                        label = { Text("استخدام بوينس آيرس") },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
        }
    }
}
