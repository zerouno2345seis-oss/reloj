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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahGreen
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val fontSize by viewModel.fontSize.collectAsState()
    val ayahColor by viewModel.ayahColor.collectAsState()
    val notifications by viewModel.notificationsEnabled.collectAsState()

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
                    text = "الإعدادات",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("حجم خط القرآن", style = MaterialTheme.typography.caption1, color = Color.Gray)
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactChip(onClick = { viewModel.setFontSize(fontSize - 2f) }, label = { Text("−") })
                    Text("${fontSize.toInt()} sp", color = AyahYellow)
                    CompactChip(onClick = { viewModel.setFontSize(fontSize + 2f) }, label = { Text("+") })
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("لون أرقام الآيات", style = MaterialTheme.typography.caption1, color = Color.Gray)
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Chip(
                        onClick = { viewModel.setAyahColor("yellow") },
                        label = { Text("أصفر 🟡", color = if (ayahColor == "yellow") Color.Black else Color.White) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = if (ayahColor == "yellow") AyahYellow else Color(0xFF222222)
                        )
                    )
                    Chip(
                        onClick = { viewModel.setAyahColor("green") },
                        label = { Text("أخضر 🟢", color = if (ayahColor == "green") Color.Black else Color.White) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = if (ayahColor == "green") AyahGreen else Color(0xFF222222)
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                ToggleChip(
                    checked = notifications,
                    onCheckedChange = { viewModel.setNotifications(it) },
                    label = { Text("تنبيهات أوقات الصلاة") },
                    toggleControl = {
                        Switch(checked = notifications, onCheckedChange = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "تطبيق القرآن لساعة Galaxy Watch 8 Classic\nWear OS 6 + One UI 8 Watch\nبدون تلاوة — قراءة فقط\nالإصدار 1.0.0",
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
