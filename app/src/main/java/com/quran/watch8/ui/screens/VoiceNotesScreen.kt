package com.quran.watch8.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VoiceNotesScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val notes by viewModel.voiceNotes.collectAsState()
    val isRecording by remember { derivedStateOf { viewModel.isRecording } }
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("ar"))

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
                    text = "ملاحظات صوتية",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Record button
            item {
                if (isRecording) {
                    Chip(
                        onClick = { viewModel.stopRecording() },
                        label = {
                            Text(
                                "⏹ إيقاف التسجيل",
                                color = Color.White,
                                style = MaterialTheme.typography.button
                            )
                        },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = Color(0xFF8B0000)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "🔴 جاري التسجيل...",
                        color = Color.Red,
                        style = MaterialTheme.typography.caption1
                    )
                } else {
                    Chip(
                        onClick = { viewModel.startRecording(context) },
                        label = {
                            Text(
                                "🎤 تسجيل ملاحظة جديدة",
                                style = MaterialTheme.typography.button
                            )
                        },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = Color(0xFF1A3A1A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "الملاحظات المحفوظة (${notes.size})",
                    style = MaterialTheme.typography.caption1,
                    color = Color.Gray
                )
            }

            if (notes.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد ملاحظات صوتية بعد",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                items(notes.sortedByDescending { it.timestamp }) { note ->
                    Chip(
                        onClick = { /* play audio - implement MediaPlayer later */ },
                        label = {
                            Column {
                                Text(
                                    text = "🎙 ${note.title}",
                                    style = MaterialTheme.typography.body2,
                                    color = Color.White
                                )
                                Text(
                                    text = dateFormat.format(Date(note.timestamp)),
                                    style = MaterialTheme.typography.caption3,
                                    color = Color.Gray
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
