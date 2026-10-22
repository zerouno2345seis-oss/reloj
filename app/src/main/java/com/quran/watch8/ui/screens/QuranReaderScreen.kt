package com.quran.watch8.ui.screens

import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.RoundPageLayout
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Quran reader, as pages shaped to the round screen.
 *
 * Each page's lines are as wide as the circle at their height (see
 * [RoundPageLayout]), so the text fills the glass without losing words at the
 * rim. The bezel turns one page per detent; a swipe up or down does the same.
 *
 *  - Tap: show or hide the surah buttons.
 *  - Double tap on a verse: a badge with the surah and verse.
 *  - Long press on a verse: font size and bookmark.
 */
@Composable
fun QuranReaderScreen(
    surahNumber: Int,
    startAyah: Int = -1,
    @Suppress("UNUSED_PARAMETER") listIndex: Int = -1,
    onBack: () -> Unit,
    viewModel: MainViewModel,
    onOpenSurah: (Int) -> Unit = {}
) {
    val fontSize by viewModel.fontSize.collectAsState()
    val ayahColorName by viewModel.ayahColor.collectAsState()
    val readerBgColorName by viewModel.readerBgColor.collectAsState()
    val readerTextColorName by viewModel.readerTextColor.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val customAyahColorHex by viewModel.customAyahColor.collectAsState()
    val customReaderBgColorHex by viewModel.customReaderBgColor.collectAsState()
    val customReaderTextColorHex by viewModel.customReaderTextColor.collectAsState()

    // Keyed on the text having loaded: opening the reader before the 1.6 MB
    // text finished parsing used to leave the page empty.
    LaunchedEffect(surahNumber) {
        viewModel.loadSurah(surahNumber)
    }
    val ayahs = remember(surahNumber, viewModel.isQuranLoaded) { viewModel.surahAyahs(surahNumber) }

    val surahInfo = SurahMetadata.getSurah(surahNumber)
    val surahName = surahInfo?.nameAr ?: "سورة $surahNumber"
    // Shared with the settings screen, so its sample verse is the page.
    val ayahNumberColor = ReaderTypography.ayahNumberColor(ayahColorName, customAyahColorHex)
    val screenBgColor = ReaderTypography.backgroundColor(readerBgColorName, customReaderBgColorHex)
    val quranTextColor = ReaderTypography.textColor(readerTextColorName, customReaderTextColorHex)
    val withBasmala = surahNumber != 1 && surahNumber != 9

    var showControls by remember { mutableStateOf(false) }
    var selectedAyahForAction by remember { mutableStateOf<Ayah?>(null) }
    var actionToastText by remember { mutableStateOf<String?>(null) }
    var doubleTapInfoText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actionToastText) {
        if (actionToastText != null) {
            delay(1600L)
            actionToastText = null
            selectedAyahForAction = null
        }
    }
    LaunchedEffect(doubleTapInfoText) {
        if (doubleTapInfoText != null) {
            delay(2000L)
            doubleTapInfoText = null
        }
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBgColor)
    ) {
        val diameterPx = minOf(constraints.maxWidth, constraints.maxHeight)

        // ── Page geometry and text ───────────────────────────────────────────
        val typeface = remember(fontFamilyName) { ReaderTypography.typeface(context, fontFamilyName) }
        val numberTypeface = remember { ReaderTypography.quranTypeface(context) }
        val fontPx = with(density) { fontSize.sp.toPx() }
        val lineHeightPx = (fontPx * ReaderTypography.LINE_HEIGHT_FACTOR).roundToInt()
        val pageLayout = remember(diameterPx, fontPx, typeface, quranTextColor) {
            RoundPageLayout(
                diameterPx = diameterPx,
                lineHeightPx = lineHeightPx,
                // The glass ends a little inside the panel's circle, and the
                // position indicator runs along the rim: at 3 dp the widest
                // middle lines lost their first and last letters on the watch.
                marginPx = with(density) { 10.dp.roundToPx() } + (diameterPx * 0.04f).roundToInt(),
                minLineWidthPx = (diameterPx * 0.42f).roundToInt(),
                textPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    textSize = fontPx
                    color = quranTextColor.toArgb()
                }
            )
        }
        val surahText = remember(ayahs, pageLayout, ayahNumberColor) {
            RoundPageLayout.surahText(
                surahName = surahName,
                withBasmala = withBasmala,
                ayahs = ayahs,
                lineHeightPx = lineHeightPx,
                nameColor = AccentGold.toArgb(),
                numberColor = ayahNumberColor.toArgb(),
                numberTypeface = numberTypeface
            )
        }
        val text = surahText.first
        val ayahStarts = surahText.second

        // Index of the verse holding a character of the text (-1 before verse 1).
        fun ayahIndexAt(offset: Int): Int {
            var lo = 0
            var hi = ayahStarts.size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (ayahStarts[mid] <= offset) { found = mid; lo = mid + 1 } else hi = mid - 1
            }
            return found
        }

        // ── Pagination, off the main thread, page by page ────────────────────
        // The first pages show at once; a long surah finishes behind them.
        val pageStarts = remember(text, pageLayout) { mutableStateListOf<Int>() }
        var paginated by remember(text, pageLayout) { mutableStateOf(false) }
        LaunchedEffect(text, pageLayout) {
            if (ayahs.isEmpty()) return@LaunchedEffect
            withContext(Dispatchers.Default) {
                val batch = ArrayList<Int>()
                for (start in pageLayout.pageStarts(text)) {
                    batch += start
                    if (batch.size >= 6) {
                        val chunk = batch.toList()
                        batch.clear()
                        withContext(Dispatchers.Main) { pageStarts.addAll(chunk) }
                    }
                }
                withContext(Dispatchers.Main) {
                    pageStarts.addAll(batch)
                    paginated = true
                }
            }
        }
        // A page is shown once its end is known.
        val pageCount = if (paginated) pageStarts.size else maxOf(0, pageStarts.size - 1)
        fun pageEnd(page: Int): Int = pageStarts.getOrNull(page + 1) ?: text.length

        val pagerState = rememberPagerState(pageCount = { pageCount })

        // The verse the page should open on: the one asked for, then wherever
        // reading has got to, so a font change keeps the reader's place.
        var anchorAyah by remember(surahNumber) { mutableIntStateOf(if (startAyah > 0) startAyah else 0) }
        var positioned by remember(text, pageLayout) { mutableStateOf(false) }
        LaunchedEffect(text, pageLayout, pageStarts.size, paginated) {
            if (positioned || ayahs.isEmpty()) return@LaunchedEffect
            val idx = ayahs.indexOfFirst { it.verse == anchorAyah }
            val target = if (anchorAyah <= 0 || idx < 0) 0 else ayahStarts[idx]
            val known = if (paginated) Int.MAX_VALUE else pageStarts.lastOrNull() ?: 0
            if (target >= known) return@LaunchedEffect // not cut that far yet
            var page = pageStarts.indexOfLast { it <= target }.coerceAtLeast(0)
            page = page.coerceAtMost(maxOf(0, pageCount - 1))
            pagerState.scrollToPage(page)
            positioned = true
        }

        // The verse a page stands for: the first one beginning on it, or the
        // one running onto it from the page before.
        fun ayahForPage(page: Int): Ayah? {
            val start = pageStarts.getOrNull(page) ?: return null
            val end = pageEnd(page)
            val first = ayahIndexAt(start)
            val next = first + 1
            val idx = if (next < ayahStarts.size && ayahStarts[next] < end && (first < 0 || ayahStarts[first] < start)) next
                      else first.coerceAtLeast(0)
            return ayahs.getOrNull(idx)
        }

        // Remember where reading stopped, once the page has settled.
        LaunchedEffect(pagerState, positioned, text) {
            if (!positioned) return@LaunchedEffect
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    delay(600L) // one database write per pause, not per page turned
                    val ayah = ayahForPage(page) ?: return@collectLatest
                    anchorAyah = ayah.verse
                    viewModel.saveReadingPosition(
                        surah       = surahNumber,
                        ayahIndex   = ayah.verse - 1,
                        ayahNumber  = ayah.verse,
                        surahNameAr = surahName,
                        ayahSnippet = ayah.text.take(90)
                    )
                }
        }

        // ── Bezel: one page per detent ────────────────────────────────────────
        // A detent arrives as one event of about scaledVerticalScrollFactor
        // pixels; half of that turns the page, and what is left of the event
        // is dropped so a single detent never turns two.
        val focusRequester = remember { FocusRequester() }
        val detentPx = remember {
            android.view.ViewConfiguration.get(context).scaledVerticalScrollFactor
        }
        var rotaryAcc by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Scaffold(
            positionIndicator = {
                if (pageCount > 1) {
                    PositionIndicator(
                        value = { pagerState.currentPage / (pageCount - 1f) }
                    )
                }
            }
        ) {
          Box(Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        rotaryAcc += event.verticalScrollPixels
                        if (abs(rotaryAcc) >= detentPx / 2f && pageCount > 0) {
                            val step = if (rotaryAcc > 0) 1 else -1
                            rotaryAcc = 0f
                            val from = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
                            val to = (from + step).coerceIn(0, pageCount - 1)
                            if (to != from) scope.launch { pagerState.animateScrollToPage(to) }
                        }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable()
            ) { page ->
                val start = pageStarts.getOrNull(page) ?: 0
                val end = pageEnd(page)
                val layout: StaticLayout = remember(text, pageLayout, start, end) {
                    pageLayout.build(text, start, end)
                }
                fun ayahAt(pos: Offset): Ayah? {
                    val y = (pos.y - pageLayout.firstLineTop).toInt()
                    val line = layout.getLineForVertical(y)
                    val offset = layout.getOffsetForHorizontal(line, pos.x)
                    return ayahs.getOrNull(ayahIndexAt(offset))
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(layout) {
                            detectTapGestures(
                                onTap = {
                                    if (selectedAyahForAction == null) showControls = !showControls
                                },
                                onDoubleTap = { pos ->
                                    val ayah = ayahAt(pos) ?: ayahForPage(page)
                                    if (ayah != null) doubleTapInfoText = "$surahName (${ayah.verse})"
                                },
                                onLongPress = { pos ->
                                    selectedAyahForAction = ayahAt(pos) ?: ayahForPage(page)
                                    actionToastText = null
                                }
                            )
                        }
                ) {
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.translate(0f, pageLayout.firstLineTop.toFloat())
                        layout.draw(native)
                        native.restore()
                    }
                }
            }

            if (!positioned) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 3.dp
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

            // ── Floating Navigation Controls ──────────────────────────────────
            // Round buttons, no bar: the bar's square ends still met the rim.
            // Right to left, as Arabic reads: previous surah on the right.
            AnimatedVisibility(
                visible = showControls && selectedAyahForAction == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderNavButton("التالية", enabled = surahNumber < 114) { onOpenSurah(surahNumber + 1) }
                    ReaderNavButton("خروج", enabled = true, onClick = onBack)
                    ReaderNavButton("السابقة", enabled = surahNumber > 1) { onOpenSurah(surahNumber - 1) }
                }
            }
          }
        }
    }
}

@Composable
private fun ReaderNavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xEE1E293B) else Color(0x661E293B))
            .border(1.dp, if (enabled) Color(0xFF38BDF8) else Color(0x5538BDF8), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.sp,
            maxLines = 1,
            color = if (enabled) Color.White else Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
