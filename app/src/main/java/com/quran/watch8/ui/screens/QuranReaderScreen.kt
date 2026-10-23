package com.quran.watch8.ui.screens

import android.graphics.Paint
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.data.repository.QuranRepository
import com.quran.watch8.ui.components.ReaderLines
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.RepeatOnResumed
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A surah laid out for one display style. */
private class ReaderDoc(
    /** "pages": the screens; otherwise empty. */
    val pages: List<Page>,
    /** The continuous styles: every line of the surah. */
    val lines: List<ReaderLines.Line>,
    val lineHeight: Float,
) {
    class Page(val lines: List<ReaderLines.Line>, val top: Float)

    /** The line where the verse begins (see [ReaderLines.Line.placeAyah]). */
    fun lineOf(ayahIndex: Int): Int =
        lines.indexOfFirst { ayahIndex in it.begins }.takeIf { it >= 0 } ?: lines.indexOfFirst { it.holdsAyah(ayahIndex) }
    fun pageOf(ayahIndex: Int): Int =
        pages.indexOfFirst { p -> p.lines.any { ayahIndex in it.begins } }.takeIf { it >= 0 }
            ?: pages.indexOfFirst { p -> p.lines.any { it.holdsAyah(ayahIndex) } }
    fun placeOfPage(page: Page): Int = page.lines.firstNotNullOfOrNull { it.begins.firstOrNull() } ?: page.lines.firstOrNull()?.firstAyah ?: -1
}

/** Surahs already laid out, so reopening one at the same settings is instant. */
private object DocCache {
    private val entries = object : LinkedHashMap<String, ReaderDoc>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderDoc>?) = size > 3
    }
    @Synchronized fun get(key: String) = entries[key]
    @Synchronized fun put(key: String, doc: ReaderDoc) { entries[key] = doc }
}

private const val UTHMANI_BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/**
 * The Quran reader, in the display style chosen in settings:
 *
 *  - "pages": round pages, each line stretched to the glass; the bezel turns them.
 *  - "river": one continuous column at a fixed width; lines near the top and
 *    bottom shrink and fade, as lists do on the watch.
 *  - "fill" (the default): continuous, and every line meets the edge of the
 *    circle wherever it is; a light tick in the wrist at each new verse.
 *  - "line": a large bright line in the middle, the rest dim; it moves on by
 *    itself at the chosen speed, stops while a finger rests on the glass and
 *    goes on when it lifts.
 *
 * Lines come from [ReaderLines]: whole words, stretched to their width.
 *
 * The app opens here, where reading stopped; back leaves for the watch face.
 *
 *  - Tap: show or hide the surah buttons and the surah list.
 *  - Double tap on a verse: a small badge with the surah and verse, 2 s.
 *  - Long press on a verse: four bare icons, − bookmark + settings.
 */
@Composable
fun QuranReaderScreen(
    surahNumber: Int,
    startAyah: Int = -1,
    @Suppress("UNUSED_PARAMETER") listIndex: Int = -1,
    onBack: () -> Unit,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenIndex: () -> Unit = {},
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
    val mode by viewModel.readerMode.collectAsState()
    val lineAuto by viewModel.lineAuto.collectAsState()
    val lineSpeed by viewModel.lineSpeed.collectAsState()

    // Keyed on the text having loaded: opening the reader before the 1.6 MB
    // text finished parsing used to leave the page empty.
    LaunchedEffect(surahNumber) {
        viewModel.loadSurah(surahNumber)
    }
    val ayahs = remember(surahNumber, viewModel.isQuranLoaded) { viewModel.surahAyahs(surahNumber) }

    // The stretching fonts read the simple text, verse for verse.
    val simpleFont = ReaderTypography.usesSimpleText(fontFamilyName)
    var simpleVerses by remember(surahNumber) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(surahNumber, simpleFont) {
        if (simpleFont && simpleVerses == null) simpleVerses = viewModel.surahSimple(surahNumber)
    }
    val useSimple = simpleFont && simpleVerses?.size == ayahs.size && ayahs.isNotEmpty()
    val textReady = ayahs.isNotEmpty() && (!simpleFont || simpleVerses != null)

    val surahInfo = SurahMetadata.getSurah(surahNumber)
    val surahName = surahInfo?.nameAr ?: "سورة $surahNumber"
    // Shared with the settings screen, so its sample verse is the page.
    val ayahNumberColor = ReaderTypography.ayahNumberColor(ayahColorName, customAyahColorHex)
    val screenBgColor = ReaderTypography.backgroundColor(readerBgColorName, customReaderBgColorHex)
    val quranTextColor = ReaderTypography.textColor(readerTextColorName, customReaderTextColorHex)
    val withBasmala = surahNumber != 1 && surahNumber != 9

    var showControls by remember { mutableStateOf(false) }
    var selectedAyahForAction by remember { mutableStateOf<Ayah?>(null) }
    // Bumped on every tap in the long-press menu, so it stays while in use.
    var menuTouches by remember { mutableIntStateOf(0) }
    var doubleTapInfoText by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(selectedAyahForAction, menuTouches) {
        if (selectedAyahForAction != null) {
            delay(2500L)
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

    // Time read in each style and font, this week, for the card in settings.
    RepeatOnResumed(mode, fontFamilyName) {
        while (true) {
            delay(USAGE_TICK_MS)
            viewModel.addReadingTime(mode, fontFamilyName, USAGE_TICK_MS / 1000)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBgColor)
    ) {
        val diameterPx = minOf(constraints.maxWidth, constraints.maxHeight).toFloat()

        // ── Paints ───────────────────────────────────────────────────────────
        val typeface = remember(fontFamilyName) { ReaderTypography.typeface(context, fontFamilyName) }
        val numberTypeface = remember { ReaderTypography.quranTypeface(context) }
        // The middle line of the "line" style is a quarter larger.
        val scaleUp = if (mode == "line") 1.25f else 1f
        val fontPx = with(density) { fontSize.sp.toPx() } * scaleUp
        val textPaint = remember(typeface, fontPx, quranTextColor) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = fontPx; color = quranTextColor.toArgb() }
        }
        val markPaint = remember(numberTypeface, fontPx, ayahNumberColor) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = numberTypeface; textSize = fontPx; color = ayahNumberColor.toArgb() }
        }
        val marginPx = with(density) { 6.dp.toPx() }
        val lineHeight = fontPx * ReaderTypography.LINE_HEIGHT_FACTOR * (if (mode == "line") 1.05f else 1f)

        // ── Laying the surah out, off the main thread ────────────────────────
        val cacheKey = "$surahNumber|$mode|$fontPx|$diameterPx|$fontFamilyName|$useSimple|${quranTextColor.toArgb()}|${ayahNumberColor.toArgb()}"
        var doc by remember(cacheKey) { mutableStateOf(DocCache.get(cacheKey)) }
        LaunchedEffect(cacheKey, textReady) {
            if (doc != null || !textReady) return@LaunchedEffect
            doc = withContext(Dispatchers.Default) {
                val verses = if (useSimple) simpleVerses!! else ayahs.map { it.text }
                val basmala = if (!withBasmala) null else if (useSimple) QuranRepository.SIMPLE_BASMALA else UTHMANI_BASMALA
                val words = ReaderLines.words(verses, ayahs.map { it.verse }, basmala) { ReaderTypography.ayahEndMark(it) }
                val engine = ReaderLines(textPaint, markPaint, stretch = useSimple)
                when (mode) {
                    "pages" -> {
                        val cap = diameterPx * 0.04f
                        var k = max(1, ((diameterPx - 2 * cap) / lineHeight).toInt())
                        fun top(n: Int) = (diameterPx - n * lineHeight) / 2f
                        fun widthOf(n: Int, i: Int) =
                            ReaderLines.chordBetween(top(n) + i * lineHeight, top(n) + (i + 1) * lineHeight, diameterPx) - 2 * marginPx
                        while (k > 1 && widthOf(k, 0) < diameterPx * 0.26f) k--
                        val pages = ArrayList<ReaderDoc.Page>()
                        var at = 0
                        while (at < words.size) {
                            val (lines, next) = engine.layout(words, at, k) { widthOf(k, it) }
                            // A short last page sits in the middle, where lines are widest.
                            pages += ReaderDoc.Page(lines, top(k) + (k - lines.size) * lineHeight / 2f)
                            at = next
                        }
                        ReaderDoc(pages, emptyList(), lineHeight)
                    }
                    "river" -> ReaderDoc(emptyList(), engine.layoutAll(words, diameterPx * 0.8f), lineHeight)
                    "line" -> ReaderDoc(emptyList(), engine.layoutAll(words, diameterPx * 0.84f), lineHeight)
                    else -> ReaderDoc(emptyList(), engine.layoutAll(words, diameterPx - 2 * marginPx), lineHeight)
                }
            }.also { DocCache.put(cacheKey, it) }
        }

        val nextSurah = SurahMetadata.getSurah(surahNumber + 1).takeIf { surahNumber < 114 }
        val openNext: () -> Unit = { onOpenSurah(surahNumber + 1) }

        // The verse to open on: the one asked for, then wherever reading has
        // got to, so a change of font or style keeps the reader's place.
        var anchorIndex by remember(surahNumber, ayahs.size) {
            mutableIntStateOf(if (startAyah > 0) ayahs.indexOfFirst { it.verse == startAyah }.coerceAtLeast(0) else 0)
        }
        var readingIndex by remember(surahNumber) { mutableIntStateOf(-1) }

        // Remember where reading stopped, once it rests there.
        LaunchedEffect(surahNumber) {
            snapshotFlow { readingIndex }
                .distinctUntilChanged()
                .collectLatest { index ->
                    if (index < 0) return@collectLatest
                    delay(600L) // one database write per pause, not per line
                    val ayah = ayahs.getOrNull(index) ?: return@collectLatest
                    anchorIndex = index
                    viewModel.saveReadingPosition(
                        surah       = surahNumber,
                        ayahIndex   = ayah.verse - 1,
                        ayahNumber  = ayah.verse,
                        surahNameAr = surahName,
                        ayahSnippet = ayah.text.take(90)
                    )
                }
        }

        // The screen stays lit while reading: the watch's own timeout is a
        // few seconds, shorter than a long verse. Every movement re-arms it;
        // three minutes without one lets the watch sleep again.
        val view = LocalView.current
        var moved by remember { mutableIntStateOf(0) }
        LaunchedEffect(moved) {
            view.keepScreenOn = true
            delay(READING_AWAKE_MS)
            view.keepScreenOn = false
        }
        DisposableEffect(view) { onDispose { view.keepScreenOn = false } }

        val gestures = ReaderGestures(
            onTap = { if (selectedAyahForAction == null) showControls = !showControls },
            onDoubleTap = { index ->
                ayahs.getOrNull(index)?.let { doubleTapInfoText = "$surahName ${ReaderTypography.arabicDigits(it.verse)}" }
            },
            onLongPress = { index -> selectedAyahForAction = ayahs.getOrNull(index) },
        )
        val onAt: (Int) -> Unit = { index -> moved++; if (index >= 0) readingIndex = index }

        Scaffold(
            // No position arc: zaher asked for nothing around the text.
            positionIndicator = {}
        ) {
          Box(Modifier.fillMaxSize()) {
            val d = doc
            if (d == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                key(cacheKey) {
                    when (mode) {
                        "pages" -> PagedReader(d, anchorIndex, textPaint, markPaint, quranTextColor, nextSurah?.nameAr, surahNumber + 1 != 9, openNext, onAt, gestures)
                        "line" -> LineReader(d, anchorIndex, diameterPx, textPaint, markPaint, lineAuto, lineSpeed, nextSurah?.nameAr, openNext, onAt, gestures)
                        else -> FlowReader(d, anchorIndex, diameterPx, fill = mode != "river", tickPerVerse = mode != "river", textPaint, markPaint, nextSurah?.nameAr, surahNumber + 1 != 9, openNext, onAt, gestures)
                    }
                }
            }

            // ── Double tap: surah and verse, small, for two seconds ───────────
            AnimatedVisibility(
                visible = doubleTapInfoText != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                Text(
                    text = doubleTapInfoText.orEmpty(),
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC1E293B))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            // ── Long press: − bookmark + and settings, nothing else ────────────
            // No card, title or size read-out: the page itself shows the new
            // size. A tap anywhere else closes it; so does 2.5 s untouched.
            if (selectedAyahForAction != null) {
                val ayah = selectedAyahForAction!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(ayah) { detectTapGestures { selectedAyahForAction = null } },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderIconButton(onClick = {
                            menuTouches++
                            viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize - 2f))
                        }) { Text("−", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                        ReaderIconButton(size = 50, onClick = {
                            viewModel.addBookmark(surahNumber, ayah.verse, ayah.text)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedAyahForAction = null
                        }) {
                            WatchIcons.Bookmark(
                                modifier = Modifier.size(26.dp),
                                color = Color.White,
                                starColor = Color(0xFFFDE047)
                            )
                        }
                        ReaderIconButton(onClick = {
                            menuTouches++
                            viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize + 2f))
                        }) { Text("+", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    // Settings, centred under the bookmark.
                    ReaderIconButton(onClick = {
                        selectedAyahForAction = null
                        onOpenSettings()
                    }) { Text("⚙", fontSize = 19.sp, color = Color.White) }
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // The surah list, and through it the bookmarks.
                    ReaderNavButton("السور", enabled = true, onClick = { showControls = false; onOpenIndex() })
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
}

private const val READING_AWAKE_MS = 3 * 60_000L
private const val USAGE_TICK_MS = 30_000L

/** What a tap, a double tap and a long press on a verse do; the verse is its index. */
private class ReaderGestures(
    val onTap: () -> Unit,
    val onDoubleTap: (Int) -> Unit,
    val onLongPress: (Int) -> Unit,
)

/** Milliseconds a line stays in the middle before the next one moves up. */
private fun lineMillis(speed: String): Long = when (speed) {
    "slow" -> 6_000L
    "fast" -> 2_500L
    else -> 4_000L
}

/** Pixels in one bezel detent. */
@Composable
private fun rememberDetentPx(): Float {
    val context = LocalContext.current
    return remember { android.view.ViewConfiguration.get(context).scaledVerticalScrollFactor }
}

// ── 1. Round pages ───────────────────────────────────────────────────────────

@Composable
private fun PagedReader(
    doc: ReaderDoc,
    anchorIndex: Int,
    textPaint: TextPaint,
    markPaint: TextPaint,
    textColor: Color,
    nextName: String?,
    nextHasBasmala: Boolean,
    openNext: () -> Unit,
    onAt: (Int) -> Unit,
    gestures: ReaderGestures,
) {
    val screenCount = doc.pages.size
    // Past the last screen: a divider with the next surah's name and the
    // basmala; resting on it opens that surah, so reading runs on.
    val pageCount = screenCount + if (nextName != null) 1 else 0
    val start = doc.pageOf(anchorIndex).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start, pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            if (nextName != null && page == screenCount) {
                delay(700L)
                openNext()
            } else {
                doc.pages.getOrNull(page)?.let { onAt(doc.placeOfPage(it)) }
            }
        }
    }

    // ── Bezel: one screen per detent ──────────────────────────────────────
    // A detent arrives as one event of about scaledVerticalScrollFactor
    // pixels; half of that moves on, and what is left of the event is
    // dropped so a single detent never moves two.
    val focusRequester = remember { FocusRequester() }
    val detentPx = rememberDetentPx()
    var rotaryAcc by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        // A quick flick moves one screen, never a run of them.
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1)
        ),
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryAcc += event.verticalScrollPixels
                if (abs(rotaryAcc) >= detentPx / 2f && pageCount > 0) {
                    val step = if (rotaryAcc > 0) 1 else -1
                    rotaryAcc = 0f
                    val from = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
                    val to = (from + step).coerceIn(0, pageCount - 1)
                    if (to != from) scope.launch { pagerState.animateScrollToPage(to, animationSpec = tween(180)) }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) { page ->
        if (page == screenCount && nextName != null) {
            SurahDivider(nextName, basmala = nextHasBasmala, color = textColor)
            return@VerticalPager
        }
        val screen = doc.pages.getOrNull(page) ?: return@VerticalPager
        fun ayahAt(pos: Offset, width: Float): Int {
            val i = floor((pos.y - screen.top) / doc.lineHeight).toInt().coerceIn(0, screen.lines.size - 1)
            return screen.lines[i].ayahAt(pos.x - width / 2f)
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screen) {
                    detectTapGestures(
                        onTap = { gestures.onTap() },
                        onDoubleTap = { gestures.onDoubleTap(ayahAt(it, size.width.toFloat())) },
                        onLongPress = { gestures.onLongPress(ayahAt(it, size.width.toFloat())) }
                    )
                }
        ) {
            drawIntoCanvas { canvas ->
                screen.lines.forEachIndexed { i, line ->
                    line.draw(canvas.nativeCanvas, size.width / 2f, screen.top + (i + 0.5f) * doc.lineHeight, 1f, 1f, textPaint, markPaint)
                }
            }
        }
    }
}

// ── 2 and 4. The continuous river ────────────────────────────────────────────

@Composable
private fun FlowReader(
    doc: ReaderDoc,
    anchorIndex: Int,
    diameter: Float,
    /** Every line meets the circle's edge (4); otherwise a fixed width that fades (2). */
    fill: Boolean,
    tickPerVerse: Boolean,
    textPaint: TextPaint,
    markPaint: TextPaint,
    nextName: String?,
    nextHasBasmala: Boolean,
    openNext: () -> Unit,
    onAt: (Int) -> Unit,
    gestures: ReaderGestures,
) {
    val lines = doc.lines
    val lh = doc.lineHeight
    val r = diameter / 2f
    // The first line opens a fifth of the way down; the last can rise as high.
    val topPad = diameter * 0.2f
    val bottomPad = diameter * 0.2f
    val textEnd = topPad + lines.size * lh
    // After the last line, the next surah's name; resting there opens it.
    val dividerY = textEnd + diameter * 0.3f
    val maxOffset = if (nextName != null) max(0f, dividerY - r) else max(0f, textEnd - (diameter - bottomPad))
    val lineWidth = lines.maxOfOrNull { it.width } ?: diameter

    val startLine = doc.lineOf(anchorIndex).coerceAtLeast(0)
    var offset by remember { mutableFloatStateOf((startLine * lh).coerceIn(0f, maxOffset)) }
    val scrollState = rememberScrollableState { delta ->
        val old = offset
        offset = (offset - delta).coerceIn(0f, maxOffset)
        old - offset
    }
    val scope = rememberCoroutineScope()

    fun yOf(i: Int) = topPad + (i + 0.5f) * lh - offset
    fun scaleAt(y: Float): Float = if (fill) {
        ((ReaderLines.chord(y, diameter) - diameter * 0.03f) / lineWidth).coerceIn(0.3f, 1f)
    } else {
        val t = (y - r) / r
        max(0.5f, 1f - 0.42f * t * t)
    }
    fun alphaAt(y: Float, scale: Float): Float = if (fill) 0.35f + 0.65f * scale else {
        val t = (y - r) / r
        max(0.25f, 1f - 0.75f * t * t)
    }
    fun centreLine(): Int = floor((r - topPad + offset) / lh).toInt().coerceIn(0, max(0, lines.size - 1))
    // The place saved is the line at the top of the text, where it opens again.
    fun topLine(): Int = floor(offset / lh + 0.5f).toInt().coerceIn(0, max(0, lines.size - 1))

    LaunchedEffect(doc) {
        snapshotFlow { lines.getOrNull(topLine())?.placeAyah ?: -1 }.distinctUntilChanged().collect { onAt(it) }
    }
    // A tick in the wrist as each new verse reaches the middle.
    val view = LocalView.current
    if (tickPerVerse) {
        LaunchedEffect(doc) {
            var last = -2
            snapshotFlow { lines.getOrNull(centreLine())?.firstAyah ?: -1 }.distinctUntilChanged().collect { ayah ->
                if (last != -2 && ayah >= 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                last = ayah
            }
        }
    }
    if (nextName != null) {
        LaunchedEffect(doc) {
            snapshotFlow { offset >= maxOffset - 2f }.collectLatest { atEnd ->
                if (atEnd) { delay(900L); openNext() }
            }
        }
    }

    // ── Bezel: smooth, about a line and a half per detent ─────────────────
    val focusRequester = remember { FocusRequester() }
    val detentPx = rememberDetentPx()
    var rotaryTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val dividerPaint = remember(textPaint) {
        TextPaint(textPaint).apply {
            color = AccentGold.toArgb(); textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
    }
    val basmalaPaint = remember(textPaint) { TextPaint(textPaint).apply { textAlign = Paint.Align.CENTER; textSize *= 0.8f } }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val step = event.verticalScrollPixels / detentPx * lh * 1.5f
                val target = ((rotaryTarget ?: offset) + step).coerceIn(0f, maxOffset)
                rotaryTarget = target
                scope.launch {
                    scrollState.animateScrollBy(-(target - offset), tween(180))
                    if (rotaryTarget == target) rotaryTarget = null
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .scrollable(scrollState, Orientation.Vertical)
            .pointerInput(doc) {
                fun ayahAt(pos: Offset): Int {
                    if (lines.isEmpty()) return 0
                    val i = floor((pos.y + offset - topPad) / lh).toInt().coerceIn(0, lines.size - 1)
                    return lines[i].ayahAt((pos.x - size.width / 2f) / scaleAt(yOf(i)))
                }
                detectTapGestures(
                    onTap = { gestures.onTap() },
                    onDoubleTap = { gestures.onDoubleTap(ayahAt(it)) },
                    onLongPress = { gestures.onLongPress(ayahAt(it)) }
                )
            }
    ) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val first = max(0, floor((offset - topPad) / lh).toInt() - 1)
            val last = min(lines.size - 1, floor((offset - topPad + diameter) / lh).toInt() + 1)
            for (i in first..last) {
                val y = yOf(i)
                val s = scaleAt(y)
                lines[i].draw(native, size.width / 2f, y, s, alphaAt(y, s), textPaint, markPaint)
            }
            if (nextName != null) {
                val y = dividerY - offset
                if (y < diameter + lh) {
                    native.drawText("سورة $nextName", size.width / 2f, y, dividerPaint)
                    if (nextHasBasmala) native.drawText(UTHMANI_BASMALA, size.width / 2f, y + lh * 1.1f, basmalaPaint)
                }
            }
        }
    }
}

// ── 3. One line in the middle ────────────────────────────────────────────────

@Composable
private fun LineReader(
    doc: ReaderDoc,
    anchorIndex: Int,
    diameter: Float,
    textPaint: TextPaint,
    markPaint: TextPaint,
    auto: Boolean,
    speed: String,
    nextName: String?,
    openNext: () -> Unit,
    onAt: (Int) -> Unit,
    gestures: ReaderGestures,
) {
    val lines = doc.lines
    val lh = doc.lineHeight
    val r = diameter / 2f
    val lastIndex = max(0, lines.size - 1)
    val pos = remember { Animatable(doc.lineOf(anchorIndex).coerceAtLeast(0).toFloat()) }
    val scope = rememberCoroutineScope()
    // A finger resting on the glass holds the line where it is.
    var touching by remember { mutableStateOf(false) }

    fun current() = pos.targetValue.roundToInt()
    fun goTo(index: Int) {
        val to = index.coerceIn(0, lastIndex)
        scope.launch { pos.animateTo(to.toFloat(), tween(420)) }
    }

    LaunchedEffect(doc) {
        snapshotFlow { pos.value.roundToInt() }.distinctUntilChanged().collect { onAt(lines.getOrNull(it)?.placeAyah ?: -1) }
    }

    // Moving on by itself, at the chosen pace; at the end, into the next surah.
    LaunchedEffect(doc, auto, speed, touching) {
        if (!auto || touching) return@LaunchedEffect
        while (true) {
            delay(lineMillis(speed))
            if (current() >= lastIndex) {
                if (nextName != null) openNext()
                break
            }
            pos.animateTo(current() + 1f, tween(520))
        }
    }

    val focusRequester = remember { FocusRequester() }
    val detentPx = rememberDetentPx()
    var rotaryAcc by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryAcc += event.verticalScrollPixels
                if (abs(rotaryAcc) >= detentPx / 2f) {
                    goTo(current() + if (rotaryAcc > 0) 1 else -1)
                    rotaryAcc = 0f
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                // Watches the finger without taking the touch from the gestures below.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    touching = false
                }
            }
            .pointerInput(doc) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        val steps = (-dragged / lh).roundToInt()
                        if (steps != 0) goTo(current() + steps)
                    },
                    onVerticalDrag = { change, amount -> change.consume(); dragged += amount }
                )
            }
            .pointerInput(doc) {
                fun ayahAt(p: Offset): Int {
                    val i = (pos.value + (p.y - r) / lh).roundToInt().coerceIn(0, lastIndex)
                    return lines.getOrNull(i)?.ayahAt(p.x - size.width / 2f) ?: 0
                }
                detectTapGestures(
                    onTap = { gestures.onTap() },
                    onDoubleTap = { gestures.onDoubleTap(ayahAt(it)) },
                    onLongPress = { gestures.onLongPress(ayahAt(it)) }
                )
            }
    ) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val p = pos.value
            val first = max(0, floor(p - r / lh).toInt() - 1)
            val last = min(lastIndex, floor(p + r / lh).toInt() + 1)
            for (i in first..last) {
                val d = i - p
                val y = r + d * lh
                val near = max(0f, 1f - abs(d))
                val fit = ((ReaderLines.chord(y, diameter) - diameter * 0.04f) / lines[i].width.coerceAtLeast(1f)).coerceAtMost(1f)
                val s = max(0.35f, min(fit, 0.78f + 0.22f * near))
                lines[i].draw(native, size.width / 2f, y, s, 0.28f + 0.72f * near, textPaint, markPaint)
            }
        }
    }
}

@Composable
private fun SurahDivider(name: String, basmala: Boolean, color: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("سورة $name", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentGold, textAlign = TextAlign.Center)
        if (basmala) {
            Spacer(Modifier.height(10.dp))
            Text(
                UTHMANI_BASMALA,
                fontFamily = ReaderTypography.QuranFont,
                fontSize = 18.sp,
                color = color,
                textAlign = TextAlign.Center
            )
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

/** A round icon with just enough shade to read over the verse behind it. */
@Composable
private fun ReaderIconButton(size: Int = 42, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xB3000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
