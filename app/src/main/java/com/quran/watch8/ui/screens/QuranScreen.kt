package com.quran.watch8.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel

@Composable
fun QuranScreen(
    onBack: () -> Unit,
    onNavigateToReader: (Int) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    var showSearchResults by remember { mutableStateOf(false) }
    val searchResults by remember { derivedStateOf { viewModel.searchResults } }
    val surahResults by remember { derivedStateOf { viewModel.surahSearchResults } }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            viewModel.handleSpeechResult(matches)
            showSearchResults = true
        }
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
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            item {
                Text(
                    text = "القرآن الكريم",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Voice search button
            item {
                Chip(
                    onClick = {
                        speechLauncher.launch(viewModel.createSpeechIntent())
                    },
                    icon = { WatchIcons.MicRecording(modifier = Modifier.size(20.dp), color = AyahYellow) },
                    label = { Text("بحث صوتي", style = MaterialTheme.typography.button) },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF2A1A00),
                        contentColor = AyahYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                        .padding(vertical = 6.dp)
                )
            }

            item {
                Chip(
                    onClick = onNavigateToBookmarks,
                    icon = { WatchIcons.Bookmark(modifier = Modifier.size(20.dp)) },
                    label = { Text("الإشارات", style = MaterialTheme.typography.button) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                        .padding(bottom = 8.dp)
                )
            }

            if (showSearchResults && (searchResults.isNotEmpty() || surahResults.isNotEmpty())) {
                item {
                    Text(
                        text = "نتائج البحث",
                        style = MaterialTheme.typography.caption1,
                        color = AyahYellow,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(surahResults) { surah ->
                    CompactChip(
                        onClick = {
                            onNavigateToReader(surah.number)
                            viewModel.clearSearch()
                            showSearchResults = false
                        },
                        label = {
                            Text(
                                "${surah.number}. ${surah.nameAr}",
                                style = MaterialTheme.typography.caption2
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(searchResults.take(15)) { ayah ->
                    CompactChip(
                        onClick = {
                            onNavigateToReader(ayah.chapter)
                            viewModel.clearSearch()
                            showSearchResults = false
                        },
                        label = {
                            Text(
                                "${ayah.chapter}:${ayah.verse} ${ayah.text.take(40)}...",
                                style = MaterialTheme.typography.caption3,
                                maxLines = 2
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    CompactChip(
                        onClick = {
                            viewModel.clearSearch()
                            showSearchResults = false
                        },
                        label = { Text("إغلاق النتائج") },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                // Full surah list
                items(SurahMetadata.surahs) { surah ->
                    Chip(
                        onClick = { onNavigateToReader(surah.number) },
                        label = {
                            Column {
                                Text(
                                    text = "${surah.number}. ${surah.nameAr}",
                                    style = MaterialTheme.typography.body1,
                                    color = Color.White
                                )
                                Text(
                                    text = "${surah.nameEn} • ${surah.versesCount} آية",
                                    style = MaterialTheme.typography.caption3,
                                    color = Color.Gray
                                )
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = Color(0xFF111111)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
