package com.quran.watch8.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text = "القرآن الكريم",
                    style = MaterialTheme.typography.title2,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                Text(
                    text = "Galaxy Watch 8 Classic",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Main tiles
            item {
                Chip(
                    onClick = { onNavigate("quran") },
                    label = {
                        Text("📖  القرآن الكريم", style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = { onNavigate("prayer") },
                    label = {
                        Text("🕌  أوقات الصلاة", style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = { onNavigate("locations") },
                    label = {
                        Text("📍  المواقع المحفوظة", style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = { onNavigate("notes") },
                    label = {
                        Text("🎤  ملاحظات صوتية", style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = { onNavigate("bookmarks") },
                    label = {
                        Text("🔖  الإشارات المرجعية", style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = { onNavigate("settings") },
                    label = {
                        Text("⚙️  الإعدادات", style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
