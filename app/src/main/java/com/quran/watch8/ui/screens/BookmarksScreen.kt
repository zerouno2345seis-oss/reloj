package com.quran.watch8.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Bookmark
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One card per bookmark: the surah, the verse number in a gold badge, and the
 * verse's opening words in the Quran face. A tap opens the reader there; a
 * sideways swipe reveals "حذف". The read and delete buttons that used to sit
 * in every card took most of its room on the watch.
 */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenAyah: (Int, Int) -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val bookmarks by viewModel.bookmarks.collectAsState()

    // false = newest first, true = in mushaf order
    var sortByQuranOrder by remember { mutableStateOf(false) }
    val sortedBookmarks = remember(bookmarks, sortByQuranOrder) {
        if (sortByQuranOrder) bookmarks.sortedWith(compareBy({ it.surah }, { it.ayah }))
        else bookmarks.sortedByDescending { it.timestamp }
    }

    Scaffold(
        timeText = {},
        vignette = {},
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الإشارات", color = AccentGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (bookmarks.isNotEmpty()) {
                        Text(
                            " (${ReaderTypography.arabicDigits(bookmarks.size)})",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (bookmarks.size > 1) {
                item {
                    Text(
                        text = if (sortByQuranOrder) "حسب المصحف" else "الأحدث أولًا",
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { sortByQuranOrder = !sortByQuranOrder }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (sortedBookmarks.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد إشارات بعد\nاضغط مطوّلًا على أي آية لإضافتها",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                items(sortedBookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        modifier = Modifier.fillMaxWidth(WatchSafeInsets.contentWidthFraction + 0.06f),
                        onOpen = { onOpenAyah(bookmark.surah, bookmark.ayah) },
                        onDelete = { viewModel.removeBookmark(bookmark.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    modifier: Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val surahName = SurahMetadata.getSurah(bookmark.surah)?.nameAr ?: "سورة ${bookmark.surah}"
    val revealPx = with(LocalDensity.current) { 64.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(20.dp)

    Box(modifier = modifier.padding(vertical = 2.dp).clip(shape)) {
        // Behind the card, uncovered by the swipe.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF7F1D1D))
                .clickable(enabled = offset.value > revealPx / 2) { onDelete() },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "حذف",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(with(LocalDensity.current) { revealPx.toDp() }),
                textAlign = TextAlign.Center
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .clip(shape)
                .background(Color(0xFF151922))
                .pointerInput(bookmark.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { offset.animateTo(if (offset.value > revealPx / 2) revealPx else 0f) }
                        },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, revealPx)) }
                        }
                    )
                }
                .clickable {
                    if (offset.value > 0f) scope.launch { offset.animateTo(0f) } else onOpen()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!bookmark.note.isNullOrBlank()) bookmark.note!! else surahName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = ReaderTypography.arabicDigits(bookmark.ayah),
                    color = AccentGold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3A2F0B))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
            Text(
                text = bookmark.textSnippet,
                color = Color(0xFFE5E7EB),
                fontFamily = ReaderTypography.QuranFont,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}
