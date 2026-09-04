package com.quran.watch8.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.components.rememberUltraRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahGreen
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Enhanced Quran Reader Screen for Galaxy Watch Circular Screen
 *
 * Features:
 *  1. Double Tap on any Ayah -> Instant floating badge with "SurahName (AyahNum)" auto-hiding after 2 seconds.
 *  2. Long Press -> Context menu (+ font size, - font size, bookmark).
 *  3. Dynamic Circular text padding maximizing screen width without clipping edge text.
 *  4. Single Tap -> Toggle header/footer navigation controls.
 */
@Composable
fun QuranReaderScreen(
    surahNumber: Int,
    startAyah: Int = -1,
    listIndex: Int = -1,
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val fontSize by viewModel.fontSize.collectAsState()
    val ayahColorName by viewModel.ayahColor.collectAsState()
    val readerBgColorName by viewModel.readerBgColor.collectAsState()
    val readerTextColorName by viewModel.readerTextColor.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val customAyahColorHex by viewModel.customAyahColor.collectAsState()
    val customReaderBgColorHex by viewModel.customReaderBgColor.collectAsState()
    val customReaderTextColorHex by viewModel.customReaderTextColor.collectAsState()

    // ── Load ayahs ────────────────────────────────────────────────────────────
    LaunchedEffect(surahNumber) {
        viewModel.loadSurah(surahNumber)
    }
    val ayahs = viewModel.currentSurahAyahs

    val surahInfo = SurahMetadata.getSurah(surahNumber)
    // Shared with the settings screen, so its sample verse is the page.
    val ayahNumberColor = ReaderTypography.ayahNumberColor(ayahColorName, customAyahColorHex)
    val screenBgColor = ReaderTypography.backgroundColor(readerBgColorName, customReaderBgColorHex)
    val quranTextColor = ReaderTypography.textColor(readerTextColorName, customReaderTextColorHex)
    val selectedFont = ReaderTypography.fontFamily(fontFamilyName)

    val headerCount = 2
    val bismillahOffset = if (surahNumber != 1 && surahNumber != 9) 1 else 0
    val ayahStartIndex = headerCount + bismillahOffset

    var showControls by remember { mutableStateOf(false) }
    var showScrollIndicator by remember { mutableStateOf(false) }
    var selectedAyahForAction by remember { mutableStateOf<Ayah?>(null) }
    var actionToastText by remember { mutableStateOf<String?>(null) }
    var doubleTapInfoText by remember { mutableStateOf<String?>(null) }

    // Instant Rotary focus on screen load
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
    }

    // Auto-hide scroll indicator
    LaunchedEffect(showScrollIndicator) {
        if (showScrollIndicator) {
            delay(3000L)
            showScrollIndicator = false
        }
    }

    // Auto-hide action toast text
    LaunchedEffect(actionToastText) {
        if (actionToastText != null) {
            delay(1600L)
            actionToastText = null
            selectedAyahForAction = null
        }
    }

    // Auto-hide Double-Tap Surah Info badge after 2 seconds
    LaunchedEffect(doubleTapInfoText) {
        if (doubleTapInfoText != null) {
            delay(2000L)
            doubleTapInfoText = null
        }
    }

    // Scroll to target ayah or listIndex
    LaunchedEffect(startAyah, listIndex, ayahs.size) {
        if (listIndex >= 0 && ayahs.isNotEmpty()) {
            listState.animateScrollToItem(listIndex.coerceIn(0, ayahStartIndex + ayahs.size - 1))
        } else if (startAyah > 0 && ayahs.isNotEmpty()) {
            val exactAyahIndex = ayahs.indexOfFirst { it.verse == startAyah }
            val targetIndex = if (exactAyahIndex >= 0) {
                ayahStartIndex + exactAyahIndex
            } else {
                (ayahStartIndex + startAyah - 1).coerceIn(0, ayahStartIndex + ayahs.size - 1)
            }
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Save reading position instantaneously
    LaunchedEffect(listState, ayahs) {
        snapshotFlow { listState.centerItemIndex }
            .distinctUntilChanged()
            .collect { centerIndex ->
                if (ayahs.isEmpty()) return@collect
                val ayahIdx = (centerIndex - ayahStartIndex).coerceIn(0, ayahs.size - 1)
                val ayah = ayahs.getOrNull(ayahIdx) ?: ayahs.first()
                val snippet = ayah.text.take(90)
                viewModel.saveReadingPosition(
                    surah       = surahNumber,
                    ayahIndex   = centerIndex,
                    ayahNumber  = ayah.verse,
                    surahNameAr = surahInfo?.nameAr ?: "سورة $surahNumber",
                    ayahSnippet = snippet
                )
            }
    }

    val ultraRotaryMod = rememberUltraRotaryScrollModifier(
        listState = listState,
        focusRequester = focusRequester,
        enabled = selectedAyahForAction == null
    )

    Scaffold(
        timeText = { if (showControls && selectedAyahForAction == null) TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { if (showScrollIndicator) PositionIndicator(scalingLazyListState = listState) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBgColor)
                .then(ultraRotaryMod)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (selectedAyahForAction == null) {
                                showControls = !showControls
                                showScrollIndicator = true
                            }
                        }
                    )
                }
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = if (showControls) 48.dp else 28.dp,
                    bottom = if (showControls) 80.dp else 36.dp,
                    start = 10.dp,
                    end = 10.dp
                )
            ) {
                // Surah Name Header
                item {
                    Text(
                        text = surahInfo?.nameAr ?: "سورة $surahNumber",
                        style = MaterialTheme.typography.title3,
                        fontFamily = selectedFont,
                        color = AccentGold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                // Info
                item {
                    Text(
                        text = "${surahInfo?.nameEn ?: ""} • ${surahInfo?.versesCount ?: 0} آية",
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray
                    )
                }

                // Bismillah
                if (surahNumber != 1 && surahNumber != 9) {
                    item {
                        Text(
                            text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
                            color = quranTextColor,
                            fontFamily = selectedFont,
                            fontSize = (fontSize + 1).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }

                // Ayahs with Double-Tap detection and safe circular text container
                itemsIndexed(ayahs) { _, ayah ->
                    val annotated = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = ayahNumberColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSize - 2).sp
                            )
                        ) { append("﴿${ayah.verse}﴾ ") }
                        withStyle(
                            SpanStyle(
                                color = quranTextColor,
                                fontFamily = selectedFont,
                                fontSize = fontSize.sp
                            )
                        ) { append(ayah.text) }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(vertical = 3.dp)
                            .pointerInput(ayah.verse) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        doubleTapInfoText = "${surahInfo?.nameAr ?: "سورة $surahNumber"} (${ayah.verse})"
                                    },
                                    onLongPress = {
                                        selectedAyahForAction = ayah
                                        actionToastText = null
                                    },
                                    onTap = {
                                        if (selectedAyahForAction == null) {
                                            showControls = !showControls
                                            showScrollIndicator = true
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = annotated,
                            textAlign = TextAlign.Center,
                            lineHeight = (fontSize * 1.45f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Floating Double-Tap Quick Info Overlay (Auto-Hides) ───────────
            AnimatedVisibility(
                visible = doubleTapInfoText != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xF00F766E))
                        .border(1.2.dp, Color(0xFFFDE047), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📖 ${doubleTapInfoText.orEmpty()}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Interactive Long-Press Context Menu (3 Icons: +, -, 🔖) ────────
            AnimatedVisibility(
                visible = selectedAyahForAction != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (selectedAyahForAction != null) {
                    val ayah = selectedAyahForAction!!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.88f))
                            .clickable { selectedAyahForAction = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
                                .clickable(enabled = false) {}
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "الآية (${ayah.verse}) من ${surahInfo?.nameAr ?: ""}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1. Decrease Font Size
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155))
                                        .clickable {
                                            viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize - 2f))
                                            actionToastText = "حجم الخط: ${(fontSize - 2f).toInt()} sp"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("−", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                // 2. Add Bookmark
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F766E))
                                        .clickable {
                                            viewModel.addBookmark(surahNumber, ayah.verse, ayah.text)
                                            actionToastText = "تمت إضافة إشارة مرجعية ✓"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    WatchIcons.Bookmark(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        starColor = Color(0xFFFDE047)
                                    )
                                }

                                // 3. Increase Font Size
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155))
                                        .clickable {
                                            viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize + 2f))
                                            actionToastText = "حجم الخط: ${(fontSize + 2f).toInt()} sp"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (actionToastText != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = actionToastText!!,
                                    fontSize = 10.sp,
                                    color = Color(0xFFFDE047),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // ── Floating Navigation Controls Bar ───────────────────────────────
            AnimatedVisibility(
                visible = showControls && selectedAyahForAction == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xEE1E293B))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactChip(
                        onClick = onBack,
                        label = { Text("‹ خروج", fontSize = 10.sp) },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF334155))
                    )
                    if (surahNumber > 1) {
                        CompactChip(
                            onClick = { viewModel.loadSurah(surahNumber - 1); scope.launch { listState.scrollToItem(0) } },
                            label = { Text("السابقة", fontSize = 10.sp) },
                            colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF334155))
                        )
                    }
                    if (surahNumber < 114) {
                        CompactChip(
                            onClick = { viewModel.loadSurah(surahNumber + 1); scope.launch { listState.scrollToItem(0) } },
                            label = { Text("التالية", fontSize = 10.sp) },
                            colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF334155))
                        )
                    }
                }
            }
        }
    }
}
