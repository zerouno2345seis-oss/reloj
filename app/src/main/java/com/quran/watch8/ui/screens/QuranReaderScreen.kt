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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahGreen
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

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
    viewModel: MainViewModel,
    onOpenSurah: (Int) -> Unit = {}
) {
    val listState = rememberScalingLazyListState()

    val fontSize by viewModel.fontSize.collectAsState()
    val ayahColorName by viewModel.ayahColor.collectAsState()
    val readerBgColorName by viewModel.readerBgColor.collectAsState()
    val readerTextColorName by viewModel.readerTextColor.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val customAyahColorHex by viewModel.customAyahColor.collectAsState()
    val customReaderBgColorHex by viewModel.customReaderBgColor.collectAsState()
    val customReaderTextColorHex by viewModel.customReaderTextColor.collectAsState()

    // ── Load ayahs ────────────────────────────────────────────────────────────
    // Read this surah's verses directly, keyed on the surah and on the text
    // having loaded. The old shared `currentSurahAyahs` was filled once: open
    // the reader before the 1.6 MB text finished parsing and the page stayed
    // empty, and "next surah" swapped the verses under the old surah's header.
    LaunchedEffect(surahNumber) {
        viewModel.loadSurah(surahNumber)
    }
    val ayahs = remember(surahNumber, viewModel.isQuranLoaded) { viewModel.surahAyahs(surahNumber) }

    val surahInfo = SurahMetadata.getSurah(surahNumber)
    // Shared with the settings screen, so its sample verse is the page.
    val ayahNumberColor = ReaderTypography.ayahNumberColor(ayahColorName, customAyahColorHex)
    val screenBgColor = ReaderTypography.backgroundColor(readerBgColorName, customReaderBgColorHex)
    val quranTextColor = ReaderTypography.textColor(readerTextColorName, customReaderTextColorHex)
    val selectedFont = ReaderTypography.fontFamily(fontFamilyName)

    // On a round face a line is only as wide as the circle at its height, so
    // with a 12 dp margin the first and last words of lines near the top and
    // bottom fell outside the glass. Keep the text inside the inscribed square
    // (about 14.6% of the width on each side, less a little so the middle of
    // the page is not starved), give the page room above and below, and fade
    // the last few dp of the rim where any line is cut anyway.
    val configuration = LocalConfiguration.current
    val isRound = configuration.isScreenRound
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val sideMargin = if (isRound) screenWidth * 0.13f else 8.dp
    val topMargin = if (isRound) screenHeight * 0.16f else 16.dp
    val bottomMargin = if (isRound) screenHeight * 0.24f else 32.dp
    val rimFade = if (isRound) screenHeight * 0.09f else 0.dp

    // Amiri hangs its marks well above and below the letters. Compose trims
    // the extra line height above the first line and below the last one by
    // default, which pressed the last line's lower marks into the next verse.
    val ayahTextStyle = remember(fontSize) {
        TextStyle(
            lineHeight = (fontSize * ReaderTypography.LINE_HEIGHT_FACTOR).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            ),
            platformStyle = PlatformTextStyle(includeFontPadding = true),
            textAlign = TextAlign.Center
        )
    }

    val headerCount = 2
    val bismillahOffset = if (surahNumber != 1 && surahNumber != 9) 1 else 0
    val ayahStartIndex = headerCount + bismillahOffset

    var showControls by remember { mutableStateOf(false) }
    var showScrollIndicator by remember { mutableStateOf(false) }
    var selectedAyahForAction by remember { mutableStateOf<Ayah?>(null) }
    var actionToastText by remember { mutableStateOf<String?>(null) }
    var doubleTapInfoText by remember { mutableStateOf<String?>(null) }

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

    // Jump straight to the target ayah. This used to animate through every
    // verse in between -- a visible crawl of a few seconds to reach the middle
    // of al-Baqarah. The ayah number wins over the stored list index, which
    // goes stale whenever the header rows change.
    var positioned by remember(surahNumber) { mutableStateOf(startAyah <= 0 && listIndex < 0) }
    LaunchedEffect(surahNumber, startAyah, listIndex, ayahs.size) {
        if (ayahs.isEmpty() || positioned) return@LaunchedEffect
        val lastIndex = ayahStartIndex + ayahs.size - 1
        val target = if (startAyah > 0) {
            val exactAyahIndex = ayahs.indexOfFirst { it.verse == startAyah }
            if (exactAyahIndex >= 0) ayahStartIndex + exactAyahIndex
            else (ayahStartIndex + startAyah - 1).coerceIn(0, lastIndex)
        } else {
            listIndex.coerceIn(0, lastIndex)
        }
        listState.scrollToItem(target)
        positioned = true
    }

    // Remember where reading stopped, once the page has settled. Before the
    // jump above lands the centre is ayah 1, and saving that overwrote the
    // very position the reader was opened to resume.
    LaunchedEffect(listState, ayahs, positioned) {
        if (!positioned) return@LaunchedEffect
        snapshotFlow { listState.centerItemIndex }
            .distinctUntilChanged()
            .collectLatest { centerIndex ->
                if (ayahs.isEmpty()) return@collectLatest
                delay(600L) // one database write per pause, not per verse scrolled past
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

    // No vignette: it dimmed the top and bottom lines of every page.
    Scaffold(
        // No clock here: it was drawn over the surah name at the top of the page.
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBgColor)
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
                // Fixed padding: changing it on every tap made the page jump.
                contentPadding = PaddingValues(top = topMargin, bottom = bottomMargin, start = sideMargin, end = sideMargin),
                // Text at full size and full opacity from edge to edge. The
                // default shrinks rows to 70% and fades them to 50% near the top
                // and bottom, which squeezed verses while they were being read.
                scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
                // The list handles the bezel itself: it holds focus, smooths
                // each detent into a short animated scroll, and ticks once per
                // detent. The hand-rolled handler it replaces took focus away
                // from the list, jumped 4.5x the raw distance per event with
                // no animation, and buzzed on every event.
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
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
                            style = ayahTextStyle,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }

                // Ayahs with Double-Tap detection and safe circular text container
                itemsIndexed(ayahs, key = { _, ayah -> ayah.verse }) { _, ayah ->
                    // Built once per verse and style, not on every frame of a scroll.
                    // The verse number closes the verse, as in the printed mushaf:
                    // U+06DD followed by Arabic-Indic digits, which Amiri draws as
                    // one ornament with the digits inside. It always uses the
                    // Quran face, since the system fonts cannot draw it. The old
                    // ﴿N﴾ prefix came out in the system font as mirrored
                    // brackets around a Latin digit.
                    val annotated = remember(ayah, fontSize, ayahNumberColor, quranTextColor, selectedFont) { buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = quranTextColor,
                                fontFamily = selectedFont,
                                fontSize = fontSize.sp
                            )
                        ) { append(ayah.text) }
                        append(' ')
                        withStyle(
                            SpanStyle(
                                color = ayahNumberColor,
                                fontFamily = ReaderTypography.QuranFont,
                                fontSize = fontSize.sp
                            )
                        ) { append(ReaderTypography.ayahEndMark(ayah.verse)) }
                    } }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
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
                            style = ayahTextStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Rim fade: only the thin band where the circle cuts every line.
            if (rimFade > 0.dp) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(rimFade)
                        .background(Brush.verticalGradient(listOf(screenBgColor, Color.Transparent)))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(rimFade)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, screenBgColor)))
                )
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
                // Raised off the rim: at 8 dp the circle cut off the outer chips.
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xEE1E293B))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactChip(
                        onClick = onBack,
                        label = { Text("‹ خروج", fontSize = 10.sp) },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF334155))
                    )
                    if (surahNumber > 1) {
                        CompactChip(
                            onClick = { onOpenSurah(surahNumber - 1) },
                            label = { Text("السابقة", fontSize = 10.sp) },
                            colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF334155))
                        )
                    }
                    if (surahNumber < 114) {
                        CompactChip(
                            onClick = { onOpenSurah(surahNumber + 1) },
                            label = { Text("التالية", fontSize = 10.sp) },
                            colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF334155))
                        )
                    }
                }
            }
        }
    }
}
