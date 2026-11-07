package com.quran.watch8.ui.screens

import android.graphics.Paint
import android.os.SystemClock
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.BiasAbsoluteAlignment
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.Velocity
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
import com.quran.watch8.ui.components.LinePace
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
 *
 * Any of the three can scroll by itself (zaher, 2026-11-06, in place of the old
 * «سطر في الوسط» style), started and ended only from the long-press menu, at
 * the pace set in seconds per line. The bezel or a drag moves the text by hand and the scroll
 * goes on two seconds later. The long-press menu holds it while it is open and
 * sets the pace.
 *
 * Lines come from [ReaderLines]: whole words, stretched to their width.
 *
 * The app opens here, where reading stopped, with the surah list behind it:
 * a swipe to the right goes to the list, and back from the list leaves.
 *
 *  - A single tap: in plain reading, the surah and verse for two seconds; in
 *    the auto scroll, hold or go on (⏸ / ▶ for a second).
 *  - Double tap on a verse number: bookmark it at once (a red tick shows on
 *    the number); again, and the bookmark goes.
 *  - Double tap elsewhere: on by one whole screen, always (held stays held).
 *  - Long press on a verse: − «السور» + and settings, with the previous surah above.
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
    onOpenSurah: (Int) -> Unit = {},
    onOpenSurahAt: (Int, Int) -> Unit = { surah, _ -> onOpenSurah(surah) }
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
    val lineSeconds by viewModel.lineSeconds.collectAsState()

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

    var selectedAyahForAction by remember { mutableStateOf<Ayah?>(null) }
    // Bumped on every tap in the long-press menu, so it stays while in use.
    var menuTouches by remember { mutableIntStateOf(0) }
    var doubleTapInfoText by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    // Bookmarked verses of this surah, by index, for the tick on their numbers.
    val bookmarks by viewModel.bookmarks.collectAsState()
    val markedVerses = remember(bookmarks, surahNumber) {
        bookmarks.filter { it.surah == surahNumber }.associateBy { it.ayah }
    }
    val checked: (Int) -> Boolean = remember(markedVerses, ayahs) {
        { index -> ayahs.getOrNull(index)?.verse?.let { it in markedVerses } == true }
    }

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
        val fontPx = with(density) { fontSize.sp.toPx() }
        val textPaint = remember(typeface, fontPx, quranTextColor) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = fontPx; color = quranTextColor.toArgb() }
        }
        val markPaint = remember(numberTypeface, fontPx, ayahNumberColor) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = numberTypeface; textSize = fontPx; color = ayahNumberColor.toArgb() }
        }
        val marginPx = with(density) { 6.dp.toPx() }
        val lineHeight = fontPx * ReaderTypography.LINE_HEIGHT_FACTOR

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
                    else -> ReaderDoc(emptyList(), engine.layoutAll(words, diameterPx - 2 * marginPx), lineHeight)
                }
            }.also { DocCache.put(cacheKey, it) }
        }

        val nextSurah = SurahMetadata.getSurah(surahNumber + 1).takeIf { surahNumber < 114 }
        val openNext: () -> Unit = { AutoScroll.carry = AutoScroll.on; onOpenSurah(surahNumber + 1) }
        // Backing out of the first page opens the surah before, on its last
        // verse, so the Quran reads as one long page in both directions.
        val openPrev: (() -> Unit)? = if (surahNumber > 1) {
            { onOpenSurahAt(surahNumber - 1, SurahMetadata.getSurah(surahNumber - 1)?.versesCount ?: -1) }
        } else null
        // One step slower or faster, from the bezel or the buttons of the menu.
        val paceStep: (Boolean) -> Unit = { slower ->
            menuTouches++
            viewModel.setLineSeconds(LinePace.step(lineSeconds, slower))
        }

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

        // The auto scroll: on or off for the whole reading, so it runs on into
        // the next surah; held while the long-press menu is open.
        LaunchedEffect(surahNumber) {
            if (!AutoScroll.carry) AutoScroll.end()
            AutoScroll.carry = false
        }
        val autoOn = AutoScroll.on
        val running = autoOn && !AutoScroll.paused && selectedAyahForAction == null
        var sign by remember { mutableStateOf<Boolean?>(null) }   // true: ▶, false: ⏸
        LaunchedEffect(sign) { if (sign != null) { delay(1000); sign = null } }
        val gestures = ReaderGestures(
            running = running,
            // Always, in plain reading too (zaher, 2026-11-07).
            canAdvance = true,
            onTap = { index ->
                if (AutoScroll.on) {
                    // In the scroll: hold, or go on.
                    AutoScroll.paused = !AutoScroll.paused
                    sign = !AutoScroll.paused
                } else {
                    // Plain reading: the surah and verse, two seconds.
                    val ayah = ayahs.getOrNull(index ?: readingIndex)
                    if (ayah != null) doubleTapInfoText = "$surahName ${ReaderTypography.arabicDigits(ayah.verse)}"
                }
            },
            // With the menu open, the bezel sets the pace: clockwise is faster.
            menuPace = if (selectedAyahForAction != null) paceStep else null,
            seconds = lineSeconds,
            onDoubleTap = { index, onMark ->
                val ayah = ayahs.getOrNull(index)
                if (ayah != null && onMark) {
                    // On the number: the bookmark, at once, with no menu.
                    val existing = markedVerses[ayah.verse]
                    if (existing != null) viewModel.removeBookmarkAt(surahNumber, ayah.verse)
                    else viewModel.addBookmark(surahNumber, ayah.verse, ayah.text)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
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
                        "pages" -> PagedReader(d, anchorIndex, textPaint, markPaint, quranTextColor, nextSurah?.nameAr, surahNumber + 1 != 9, openNext, openPrev, onAt, gestures, checked)
                        else -> FlowReader(d, anchorIndex, diameterPx, fill = mode != "river", tickPerVerse = mode != "river", textPaint, markPaint, nextSurah?.nameAr, surahNumber + 1 != 9, openNext, openPrev, onAt, gestures, checked)
                    }
                }
            }

            PlaySign(sign, Modifier.align(Alignment.TopCenter).padding(top = 22.dp))

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

            // ── Long press: − «السور» + and settings, the previous surah above ──
            // No card, title or size read-out: the page itself shows the new
            // size. A tap anywhere else closes it; so does 2.5 s untouched.
            // Bookmarks are a double tap on the verse number now.
            if (selectedAyahForAction != null) {
                val ayah = selectedAyahForAction!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x59000000))
                        .pointerInput(ayah) { detectTapGestures { selectedAyahForAction = null } }
                ) {
                    // A ring around the play button (design «ب», zaher 2026-11-06):
                    // the previous surah and «السور» above, the font size at the
                    // sides, the pace under it and settings at the bottom.
                    fun at(x: Float, y: Float) = Modifier.align(BiasAbsoluteAlignment(x, y))
                    // The surah before this one in the mushaf; the only way back
                    // a surah, so the swipes stay for the list and the place.
                    if (surahNumber > 1) {
                        val previousName = SurahMetadata.getSurah(surahNumber - 1)?.nameAr
                        Box(at(0f, -0.76f)) {
                            ReaderPill(previousName?.let { "السابقة · $it" } ?: "السابقة") {
                                selectedAyahForAction = null
                                onOpenSurah(surahNumber - 1)
                            }
                        }
                    }
                    Box(at(0f, -0.36f)) {
                        ReaderIconButton(size = 40, onClick = {
                            selectedAyahForAction = null
                            onOpenIndex()
                        }) { WatchIcons.SurahList(modifier = Modifier.size(22.dp), color = Color.White) }
                    }
                    Box(at(-0.68f, 0f)) {
                        ReaderIconButton(onClick = {
                            menuTouches++
                            viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize + 2f))
                        }) { Text("أ+", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Box(at(0.68f, 0f)) {
                        ReaderIconButton(onClick = {
                            menuTouches++
                            viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize - 2f))
                        }) { Text("أ−", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    // Play starts the auto scroll; while it is on, ■ ends it.
                    Box(at(0f, 0f)) {
                        ReaderIconButton(size = 56, shade = AccentGold, onClick = {
                            selectedAyahForAction = null
                            if (AutoScroll.on) AutoScroll.end()
                            else { AutoScroll.on = true; AutoScroll.paused = false; sign = true }
                        }) { Text(if (autoOn) "■" else "▶", fontSize = 22.sp, color = Color(0xFF111111)) }
                    }
                    // The pace: the bezel sets it too while this is open (clockwise
                    // is faster); a step is about an eighth.
                    val laid = doc
                    val juz = remember(surahNumber, ayah.verse) { viewModel.juzLength(surahNumber, ayah.verse) }
                    val perLine = remember(laid, ayahs) {
                        val n = laid?.let { it.lines.size + it.pages.sumOf { p -> p.lines.size } } ?: 0
                        if (n == 0) 0f else ayahs.sumOf { it.text.length }.toFloat() / n
                    }
                    val minutes = LinePace.juzMinutes(juz.second, perLine, lineSeconds)
                    Box(at(-0.6f, 0.34f)) {
                        ReaderIconButton(size = 38, onClick = { paceStep(false) }) { Text("⏩", fontSize = 14.sp, color = Color.White) }
                    }
                    Box(at(0.6f, 0.34f)) {
                        ReaderIconButton(size = 38, onClick = { paceStep(true) }) { Text("⏪", fontSize = 14.sp, color = Color.White) }
                    }
                    Column(at(0f, 0.42f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(LinePace.secondsText(lineSeconds) + " للسطر", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "الجزء ${ReaderTypography.arabicDigits(juz.first)} ≈ ${ReaderTypography.arabicDigits(minutes)} د",
                            fontSize = 9.sp, color = Color(0xFFD1D5DB)
                        )
                    }
                    Box(at(0f, 0.8f)) {
                        ReaderIconButton(size = 38, onClick = {
                            selectedAyahForAction = null
                            onOpenSettings()
                        }) { Text("⚙", fontSize = 17.sp, color = Color.White) }
                    }
                }
            }

          }
        }
    }
}

private const val READING_AWAKE_MS = 3 * 60_000L
private const val USAGE_TICK_MS = 30_000L

/**
 * The auto scroll, started and ended only from the long-press menu (zaher,
 * 2026-11-06); while it is on, a single tap holds and lets go. It runs on into
 * the next surah ([carry]) and ends when the reader is left any other way.
 */
private object AutoScroll {
    var on by mutableStateOf(false)
    var paused by mutableStateOf(false)
    /** Set just before the next surah opens by itself, so the scroll goes on. */
    var carry = false
    fun end() { on = false; paused = false }
}

/** How long (ms) a hand on the bezel or the glass holds the auto scroll. */
private const val AUTO_RESUME_MS = 2000L

/**
 * What the touches on a verse do; the verse is its index. [onDoubleTap] is
 * told whether the tap was on the verse's number. [running]: the auto scroll
 * is moving. [menuPace]: set while the menu is open with the scroll on.
 */
private class ReaderGestures(
    val running: Boolean,
    /** A double tap off a verse number moves one screen on. */
    val canAdvance: Boolean,
    /** A single tap; the verse under it, when there is one. */
    val onTap: (index: Int?) -> Unit,
    val menuPace: ((slower: Boolean) -> Unit)?,
    /** Seconds per line of the auto scroll. */
    val seconds: Float,
    val onDoubleTap: (index: Int, onMark: Boolean) -> Unit,
    val onLongPress: (Int) -> Unit,
)

/**
 * A double tap on [line] at [x] (from the line's centre, unscaled): on a verse
 * number, the bookmark; elsewhere, while stopped, [advance] one screen;
 * while scrolling, the badge.
 */
private fun ReaderGestures.doubleTap(line: ReaderLines.Line?, x: Float, advance: () -> Unit) {
    val mark = line?.markAt(x) ?: -1
    when {
        mark >= 0 -> onDoubleTap(mark, true)
        canAdvance -> advance()
    }
}

/** ⏸ / ▶ in a small disc, for a second after a tap. */
@Composable
private fun PlaySign(playing: Boolean?, modifier: Modifier) {
    if (playing == null) return
    Canvas(modifier.size(30.dp)) {
        val c = center
        val u = size.minDimension * 0.16f
        drawCircle(Color(0xB3000000), radius = size.minDimension / 2f, center = c)
        if (!playing) {
            drawRect(Color.White, topLeft = Offset(c.x - u * 0.9f, c.y - u), size = androidx.compose.ui.geometry.Size(u * 0.6f, u * 2f))
            drawRect(Color.White, topLeft = Offset(c.x + u * 0.3f, c.y - u), size = androidx.compose.ui.geometry.Size(u * 0.6f, u * 2f))
        } else {
            val tri = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x - u * 0.7f, c.y - u); lineTo(c.x + u, c.y); lineTo(c.x - u * 0.7f, c.y + u); close()
            }
            drawPath(tri, Color.White)
        }
    }
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
    openPrev: (() -> Unit)?,
    onAt: (Int) -> Unit,
    gestures: ReaderGestures,
    checked: (Int) -> Boolean,
) {
    val g by rememberUpdatedState(gestures)
    val screenCount = doc.pages.size
    // Past the last screen: a divider with the next surah's name and the
    // basmala; resting on it opens that surah, so reading runs on.
    val pageCount = screenCount + if (nextName != null) 1 else 0
    val start = doc.pageOf(anchorIndex).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    fun leaveBack() { if (!leaving && openPrev != null) { leaving = true; openPrev() } }
    // A pull down past the first screen carries on into the surah before.
    var pulled by remember { mutableFloatStateOf(0f) }
    val pullBack = remember(pagerState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (pagerState.currentPage == 0 && available.y > 0f) {
                    pulled += available.y
                    if (pulled > 90f) leaveBack()
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

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

    // ── Auto scroll: a screen after its lines' time; a page turned by hand
    // starts the count again.
    fun nextScreen() {
        val to = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
        if (to != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(to, animationSpec = tween(420)) }
    }
    LaunchedEffect(pagerState.settledPage, gestures.running, gestures.seconds) {
        if (!gestures.running) return@LaunchedEffect
        val n = doc.pages.getOrNull(pagerState.settledPage)?.lines?.size ?: return@LaunchedEffect
        delay((gestures.seconds * 1000 * n).toLong())
        nextScreen()
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
            .nestedScroll(pullBack)
            .onRotaryScrollEvent { event ->
                rotaryAcc += event.verticalScrollPixels
                val pace = g.menuPace
                if (pace != null) {
                    if (abs(rotaryAcc) >= detentPx / 2f) { pace(rotaryAcc < 0); rotaryAcc = 0f }
                } else if (abs(rotaryAcc) >= detentPx / 2f && pageCount > 0) {
                    val step = if (rotaryAcc > 0) 1 else -1
                    rotaryAcc = 0f
                    val from = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
                    if (step < 0 && from == 0) leaveBack()
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
        fun lineAt(pos: Offset): ReaderLines.Line? {
            if (screen.lines.isEmpty()) return null
            val i = floor((pos.y - screen.top) / doc.lineHeight).toInt().coerceIn(0, screen.lines.size - 1)
            return screen.lines[i]
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screen) {
                    // With onDoubleTap set, onTap waits out the double-tap time, so
                    // the first tap of a double tap never starts the scroll.
                    detectTapGestures(
                        onTap = { pos -> g.onTap(lineAt(pos)?.ayahAt(pos.x - size.width / 2f)) },
                        onDoubleTap = { g.doubleTap(lineAt(it), it.x - size.width / 2f, ::nextScreen) },
                        onLongPress = { pos -> lineAt(pos)?.let { g.onLongPress(it.ayahAt(pos.x - size.width / 2f)) } }
                    )
                }
        ) {
            drawIntoCanvas { canvas ->
                screen.lines.forEachIndexed { i, line ->
                    line.draw(canvas.nativeCanvas, size.width / 2f, screen.top + (i + 0.5f) * doc.lineHeight, 1f, 1f, textPaint, markPaint, checked)
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
    openPrev: (() -> Unit)?,
    onAt: (Int) -> Unit,
    gestures: ReaderGestures,
    checked: (Int) -> Boolean,
) {
    val g by rememberUpdatedState(gestures)
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
    var leaving by remember { mutableStateOf(false) }
    var pulled by remember { mutableFloatStateOf(0f) }
    fun leaveBack() { if (!leaving && openPrev != null) { leaving = true; openPrev() } }
    // When a hand last moved the text; the auto scroll waits after it.
    var handAt by remember { mutableLongStateOf(0L) }
    val scrollState = rememberScrollableState { delta ->
        handAt = SystemClock.uptimeMillis()
        val old = offset
        offset = (offset - delta).coerceIn(0f, maxOffset)
        // Pulled down past the first line: on into the surah before.
        if (old <= 0f && delta > 0f) {
            pulled += delta
            if (pulled > diameter * 0.2f) leaveBack()
        } else if (delta < 0f || offset > 0f) pulled = 0f
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
    var paceAcc by remember { mutableFloatStateOf(0f) }

    // ── Auto scroll: a steady glide, a line every [seconds] ───────────────
    LaunchedEffect(doc, gestures.running, gestures.seconds) {
        if (!gestures.running) return@LaunchedEffect
        val perMs = lh / (gestures.seconds * 1000f)
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                val idle = SystemClock.uptimeMillis() - handAt > AUTO_RESUME_MS && rotaryTarget == null
                if (last != 0L && idle) offset = (offset + (now - last) * perMs).coerceAtMost(maxOffset)
                last = now
            }
        }
    }
    // A double tap while stopped: on by the height of the text, one screen.
    val nextScreen: () -> Unit = {
        scope.launch { scrollState.animateScrollBy(-(diameter - topPad - bottomPad), tween(420)) }
    }
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
                g.menuPace?.let { pace ->
                    paceAcc += event.verticalScrollPixels
                    if (abs(paceAcc) >= detentPx / 2f) { pace(paceAcc < 0); paceAcc = 0f }
                    return@onRotaryScrollEvent true
                }
                val step = event.verticalScrollPixels / detentPx * lh * 1.5f
                if (step < 0f && (rotaryTarget ?: offset) <= 1f) leaveBack()
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
                // The line under a touch, and the touch's x on it before scaling.
                fun hit(pos: Offset): Pair<ReaderLines.Line, Float>? {
                    if (lines.isEmpty()) return null
                    val i = floor((pos.y + offset - topPad) / lh).toInt().coerceIn(0, lines.size - 1)
                    return lines[i] to (pos.x - size.width / 2f) / scaleAt(yOf(i))
                }
                detectTapGestures(
                    onTap = { pos -> g.onTap(hit(pos)?.let { (line, x) -> line.ayahAt(x) }) },
                    onDoubleTap = { pos -> val h = hit(pos); g.doubleTap(h?.first, h?.second ?: 0f, nextScreen) },
                    onLongPress = { pos -> hit(pos)?.let { (line, x) -> g.onLongPress(line.ayahAt(x)) } }
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
                lines[i].draw(native, size.width / 2f, y, s, alphaAt(y, s), textPaint, markPaint, checked)
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

/** A small rounded label button, shaded like the round icons. */
@Composable
private fun ReaderPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xB3000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** A round icon with just enough shade to read over the verse behind it. */
@Composable
private fun ReaderIconButton(size: Int = 42, shade: Color = Color(0xB3000000), onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(shade)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
