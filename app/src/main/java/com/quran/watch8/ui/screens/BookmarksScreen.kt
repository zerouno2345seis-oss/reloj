package com.quran.watch8.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenAyah: (Int, Int) -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val bookmarks by viewModel.bookmarks.collectAsState()
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
            contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text = "الإشارات المرجعية",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    textAlign = TextAlign.Center
                )
            }

            if (bookmarks.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد إشارات بعد\nاضغط مطولاً على أي آية لإضافتها",
                        style = MaterialTheme.typography.body2,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                items(bookmarks.sortedByDescending { it.timestamp }) { bookmark ->
                    val surahName = SurahMetadata.getSurah(bookmark.surah)?.nameAr ?: ""
                    Chip(
                        onClick = { onOpenAyah(bookmark.surah, bookmark.ayah) },
                        label = {
                            Column {
                                Text(
                                    text = "$surahName ${bookmark.surah}:${bookmark.ayah}",
                                    style = MaterialTheme.typography.body2,
                                    color = Color.White
                                )
                                Text(
                                    text = bookmark.textSnippet,
                                    style = MaterialTheme.typography.caption3,
                                    color = Color.LightGray,
                                    maxLines = 2
                                )
                                Text(
                                    text = dateFormat.format(Date(bookmark.timestamp)),
                                    style = MaterialTheme.typography.caption3,
                                    color = Color.Gray
                                )
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF1A1A1A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}
