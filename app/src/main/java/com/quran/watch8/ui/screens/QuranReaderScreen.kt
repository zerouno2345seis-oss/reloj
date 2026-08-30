package com.quran.watch8.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.rememberRotaryFontSizeModifier
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahGreen
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.theme.QuranBlack
import com.quran.watch8.ui.theme.QuranWhite
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Quran Reader with full physical rotating bezel (الإطار الدوار) support.
 *
 * - Bezel rotation → scroll through ayahs smoothly
 * - Double-tap → hide/show controls
 * - Long-press ayah → bookmark
 * - When "font mode" is active (via button), bezel changes font size
 * - PositionIndicator shows scroll progress (works with bezel)
 */
@Composable
fun QuranReaderScreen(
    surahNumber: Int,
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val fontSize by viewModel.fontSize.collectAsState()
    val ayahColorName by viewModel.ayahColor.collectAsState()
    val ayahs by remember(surahNumber) {
        derivedStateOf {
            viewModel.loadSurah(surahNumber)
            viewModel.currentSurahAyahs
        }
    }
    val surahInfo = SurahMetadata.getSurah(surahNumber)
    val ayahNumberColor = if (ayahColorName == "green") AyahGreen else AyahYellow

    var showControls by remember { mutableStateOf(true) }
    // When true, rotating the bezel changes font size instead of scrolling
    var fontMode by remember { mutableStateOf(false) }

    // Primary rotary: scroll the list (always preferred unless fontMode)
    val rotaryScrollMod = rememberRotaryScrollModifier(
        listState = listState,
        enabled = !fontMode
    )
    // Secondary rotary: font size when fontMode is active
    val rotaryFontMod = rememberRotaryFontSizeModifier(
        currentSize = fontSize,
        onSizeChange = { viewModel.setFontSize(it) },
        enabled = fontMode
    )

    LaunchedEffect(surahNumber) {
        viewModel.loadSurah(surahNumber)
    }

    Scaffold(
        timeText = { if (showControls) TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = {
            // This indicator updates when scrolling via bezel or finger
            PositionIndicator(scalingLazyListState = listState)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(QuranBlack)
                .then(if (fontMode) rotaryFontMod else rotaryScrollMod)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            showControls = !showControls
                            if (!showControls) fontMode = false
                        }
                    )
                }
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = if (showControls) 48.dp else 16.dp,
                    bottom = if (showControls) 90.dp else 24.dp,
                    start = 12.dp,
                    end = 12.dp
                )
            ) {
                // Header
                item {
                    Text(
                        text = surahInfo?.nameAr ?: "سورة $surahNumber",
                        style = MaterialTheme.typography.title3,
                        color = AccentGold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                item {
                    Text(
                        text = "${surahInfo?.nameEn ?: ""} • ${surahInfo?.versesCount ?: 0} آية",
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Hint for bezel
                item {
                    Text(
                        text = if (fontMode) "🔄 الإطار يغيّر حجم الخط" else "🔄 حرّك الإطار للتمرير",
                        style = MaterialTheme.typography.caption3,
                        color = if (fontMode) AyahYellow else Color.DarkGray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Bismillah
                if (surahNumber != 1 && surahNumber != 9) {
                    item {
                        Text(
                            text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
                            color = QuranWhite,
                            fontSize = (fontSize + 2).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }

                // Ayahs
                itemsIndexed(ayahs) { index, ayah ->
                    val annotated = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = ayahNumberColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSize - 2).sp
                            )
                        ) {
                            append("﴿${ayah.verse}﴾ ")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = QuranWhite,
                                fontSize = fontSize.sp
                            )
                        ) {
                            append(ayah.text)
                        }
                    }
                    Text(
                        text = annotated,
                        textAlign = TextAlign.Right,
                        lineHeight = (fontSize * 1.6f).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        viewModel.addBookmark(
                                            ayah.chapter,
                                            ayah.verse,
                                            ayah.text
                                        )
                                    }
                                )
                            }
                    )
                }
            }

            // Bottom controls
            if (showControls) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xEE000000))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Font size + mode toggle
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CompactChip(
                            onClick = { viewModel.setFontSize(fontSize - 2f) },
                            label = { Text("أ-", color = Color.White) }
                        )
                        Text(
                            text = "${fontSize.toInt()}",
                            color = AyahYellow,
                            style = MaterialTheme.typography.caption1
                        )
                        CompactChip(
                            onClick = { viewModel.setFontSize(fontSize + 2f) },
                            label = { Text("أ+", color = Color.White) }
                        )
                        // Toggle: bezel = font size
                        CompactChip(
                            onClick = { fontMode = !fontMode },
                            label = {
                                Text(
                                    if (fontMode) "Aa✓" else "Aa",
                                    color = if (fontMode) Color.Black else Color.White
                                )
                            },
                            colors = if (fontMode)
                                ChipDefaults.primaryChipColors(backgroundColor = AyahYellow)
                            else
                                ChipDefaults.secondaryChipColors()
                        )
                        CompactChip(
                            onClick = {
                                val newColor = if (ayahColorName == "yellow") "green" else "yellow"
                                viewModel.setAyahColor(newColor)
                            },
                            label = {
                                Text(
                                    if (ayahColorName == "yellow") "🟡" else "🟢",
                                    color = Color.White
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Jump to start / end (also usable with bezel + buttons)
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CompactChip(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            label = { Text("⤒ بداية", color = Color.White) }
                        )
                        CompactChip(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(ayahs.size.coerceAtLeast(0))
                                }
                            },
                            label = { Text("⤓ نهاية", color = Color.White) }
                        )
                    }

                    Text(
                        text = "الإطار الدوار للتمرير • ضغط مطول = إشارة • دبل تاب = إخفاء",
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
