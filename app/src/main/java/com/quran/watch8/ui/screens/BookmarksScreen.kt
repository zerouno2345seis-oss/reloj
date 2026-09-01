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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
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

    // Sort mode: false = by time added (newest first), true = by Quran order (Surah & Ayah number)
    var sortByQuranOrder by remember { mutableStateOf(false) }

    val sortedBookmarks = remember(bookmarks, sortByQuranOrder) {
        if (sortByQuranOrder) {
            bookmarks.sortedWith(compareBy({ it.surah }, { it.ayah }))
        } else {
            bookmarks.sortedByDescending { it.timestamp }
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
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text = "الإشارات المرجعية",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            if (bookmarks.isNotEmpty()) {
                // Sorting Toggle Chip
                item {
                    CompactChip(
                        onClick = { sortByQuranOrder = !sortByQuranOrder },
                        label = {
                            Text(
                                text = if (sortByQuranOrder) "📖 الترتيب: حسب المصحف والسور" else "⏱️ الترتيب: حسب وقت الإضافة",
                                fontSize = 10.sp,
                                color = AyahYellow
                            )
                        },
                        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF1E242E)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }

            if (sortedBookmarks.isEmpty()) {
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
                items(sortedBookmarks, key = { it.id }) { bookmark ->
                    val surahName = SurahMetadata.getSurah(bookmark.surah)?.nameAr ?: "سورة ${bookmark.surah}"
                    var showOptions by remember { mutableStateOf(false) }

                    if (showOptions) {
                        Card(
                            onClick = { showOptions = false },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$surahName · آية ${bookmark.ayah}",
                                    style = MaterialTheme.typography.caption1,
                                    color = AccentGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    CompactChip(
                                        onClick = {
                                            showOptions = false
                                            onOpenAyah(bookmark.surah, bookmark.ayah)
                                        },
                                        label = { Text("📖 قراءة", fontSize = 10.sp) },
                                        colors = ChipDefaults.primaryChipColors()
                                    )
                                    CompactChip(
                                        onClick = {
                                            showOptions = false
                                            viewModel.removeBookmark(bookmark.id)
                                        },
                                        label = { Text("🗑️ حذف", fontSize = 10.sp, color = Color(0xFFFF6B6B)) },
                                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF3B1E22))
                                    )
                                }
                            }
                        }
                    } else {
                        Chip(
                            onClick = { onOpenAyah(bookmark.surah, bookmark.ayah) },
                            label = {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (!bookmark.note.isNullOrBlank()) bookmark.note!! else "$surahName [${bookmark.surah}:${bookmark.ayah}]",
                                            style = MaterialTheme.typography.body2,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dateFormat.format(Date(bookmark.timestamp)),
                                            style = MaterialTheme.typography.caption3,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = "﴿ ${bookmark.textSnippet} ﴾",
                                        style = MaterialTheme.typography.caption3,
                                        color = AyahYellow,
                                        maxLines = 2
                                    )
                                }
                            },
                            secondaryLabel = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "🗑️ حذف",
                                        fontSize = 9.sp,
                                        color = Color(0xFFFF8080),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            },
                            colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF151922)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
