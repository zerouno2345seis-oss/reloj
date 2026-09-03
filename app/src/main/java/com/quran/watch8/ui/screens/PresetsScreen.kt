package com.quran.watch8.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.PresetItem
import com.quran.watch8.data.model.PresetManager
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel

@Composable
fun PresetsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val presets = remember { PresetManager.getAllPresets(context) }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            item {
                Text(
                    text = "القوالب الجاهزة",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = "اختر قالباً لتطبيقه فوراً على الواجهة",
                    style = MaterialTheme.typography.caption3,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            items(presets, key = { it.id }) { preset ->
                Chip(
                    onClick = {
                        val newConfig = PresetManager.applyPreset(context, preset.id)
                        if (newConfig != null) {
                            viewModel.setTileConfig(newConfig)
                            Toast.makeText(context, "تم تطبيق: ${preset.title}", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    label = {
                        Text(
                            text = "${preset.icon} ${preset.title}",
                            style = MaterialTheme.typography.body2,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.caption3,
                            color = AyahYellow,
                            maxLines = 1
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF161E2E)),
                    modifier = Modifier
                        .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                        .padding(vertical = 3.dp)
                )
            }
        }
    }
}
