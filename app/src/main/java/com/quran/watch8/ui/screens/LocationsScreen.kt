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
import com.quran.watch8.data.model.LocationType
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val locations by viewModel.locations.collectAsState()
    val isLoading by remember { derivedStateOf { viewModel.isLoadingLocation } }
    val currentLoc by remember { derivedStateOf { viewModel.currentLocation } }
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("ar"))

    LaunchedEffect(Unit) {
        if (currentLoc == null) viewModel.fetchCurrentLocation()
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
            contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text = "المواقع المحفوظة",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Save buttons
            item {
                Text(
                    text = if (currentLoc != null)
                        "الموقع الحالي جاهز"
                    else if (isLoading) "جاري تحديد الموقع..."
                    else "اضغط لتحديد الموقع",
                    style = MaterialTheme.typography.caption2,
                    color = if (currentLoc != null) AyahYellow else Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Chip(
                    onClick = {
                        viewModel.fetchCurrentLocation()
                        viewModel.saveLocation("موقع السيارة", LocationType.CAR)
                    },
                    label = { Text("🚗 حفظ موقع السيارة") },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A2A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    enabled = !isLoading
                )
            }

            item {
                Chip(
                    onClick = {
                        viewModel.fetchCurrentLocation()
                        viewModel.saveLocation("موقع مهم", LocationType.IMPORTANT)
                    },
                    label = { Text("⭐ حفظ موقع مهم") },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A1A2A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    enabled = !isLoading
                )
            }

            item {
                Chip(
                    onClick = {
                        viewModel.fetchCurrentLocation()
                        viewModel.saveLocation("مسجد", LocationType.MOSQUE)
                    },
                    label = { Text("🕌 حفظ مسجد") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    enabled = !isLoading
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "المواقع المحفوظة (${locations.size})",
                    style = MaterialTheme.typography.caption1,
                    color = Color.Gray
                )
            }

            if (locations.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد مواقع محفوظة بعد",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(locations.sortedByDescending { it.timestamp }) { loc ->
                    val icon = when (loc.type) {
                        LocationType.CAR -> "🚗"
                        LocationType.MOSQUE -> "🕌"
                        LocationType.IMPORTANT -> "⭐"
                        else -> "📍"
                    }
                    Chip(
                        onClick = { /* open maps or compass later */ },
                        label = {
                            Column {
                                Text(
                                    text = "$icon ${loc.name}",
                                    style = MaterialTheme.typography.body2,
                                    color = Color.White
                                )
                                Text(
                                    text = "%.5f, %.5f".format(loc.latitude, loc.longitude),
                                    style = MaterialTheme.typography.caption3,
                                    color = Color.Gray
                                )
                                Text(
                                    text = dateFormat.format(Date(loc.timestamp)),
                                    style = MaterialTheme.typography.caption3,
                                    color = Color.DarkGray
                                )
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF111111)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
