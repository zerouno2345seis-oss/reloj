package com.quran.watch8.ui.screens

import android.text.TextPaint
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.RoundPassages
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

/** Screens already cut, so reopening a surah at the same size is instant. */
private object PassageCache {
    private val entries = object : LinkedHashMap<String, List<RoundPassages.Screen>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RoundPassages.Screen>>?) = size > 3
    }
    @Synchronized fun get(key: String) = entries[key]
    @Synchronized fun put(key: String, screens: List<RoundPassages.Screen>) { entries[key] = screens }
}

/**
 * The Quran reader: one passage per screen, cut at the Quran's own pauses and
 * shaped to the round glass (see [RoundPassages]).
 *
 * The font stays at the reader's size; a screen fills by holding more whole
 * pauses, not by growing the letters. Above the text, faintly, the last words
 * of the screen before. The bezel moves one screen per detent; a swipe up or
 * down does the same. The surah and verse are not printed on every screen --
 * that band went to the text -- but a double tap shows them.
 *
 *  - Tap: show or hide the surah buttons.
 *  - Double tap on a verse: a small badge with the surah and verse, 2 s.
 *  - Long press on a verse: three bare icons, − bookmark +.
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
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBgColor)
    ) {
        val diameterPx = minOf(constraints.maxWidth, constraints.maxHeight)

        // ── Geometry ─────────────────────────────────────────────────────────
        val typeface = remember(fontFamilyName) { ReaderTypography.typeface(context, fontFamilyName) }
        val numberTypeface = remember { ReaderTypography.quranTypeface(context) }
        val fontPx = with(density) { fontSize.sp.toPx() }
        val passages = remember(diameterPx, fontPx, typeface, quranTextColor, ayahNumberColor) {
            RoundPassages(
                diameterPx = diameterPx,
                lineHeightPx = (fontPx * ReaderTypography.LINE_HEIGHT_FACTOR).roundToInt(),
                // Lines run nearly to the glass, as in other watch mushafs the
                // owner compared: the old 10 dp + 4% margin left the page
                // looking empty. The position indicator may touch the text.
                marginPx = with(density) { 6.dp.roundToPx() },
                // Nothing is drawn above or below the text any more (a double
                // tap shows the surah and verse), so only a sliver is kept.
                capPx = (diameterPx * 0.04f).roundToInt(),
                minLineWidthPx = (diameterPx * 0.26f).roundToInt(),
                paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    textSize = fontPx
                    color = quranTextColor.toArgb()
                },
                numberColor = ayahNumberColor.toArgb(),
                numberTypeface = numberTypeface
            )
        }
        val cacheKey = "$surahNumber|$fontPx|$diameterPx|$fontFamilyName|${quranTextColor.toArgb()}|${ayahNumberColor.toArgb()}"

        // ── Cutting, off the main thread, screen by screen ───────────────────
        // The first screens show at once; a long surah finishes behind them.
        val screens = remember(passages, ayahs) {
            mutableStateListOf<RoundPassages.Screen>().apply { PassageCache.get(cacheKey)?.let { addAll(it) } }
        }
        var cut by remember(passages, ayahs) { mutableStateOf(PassageCache.get(cacheKey) != null) }
        LaunchedEffect(passages, ayahs) {
            if (cut || ayahs.isEmpty()) return@LaunchedEffect
            withContext(Dispatchers.Default) {
                val all = ArrayList<RoundPassages.Screen>()
                val batch = ArrayList<RoundPassages.Screen>()
                for (screen in passages.screens(RoundPassages.pauseUnits(ayahs, withBasmala))) {
                    all += screen
                    batch += screen
                    if (batch.size >= 4) {
                        val chunk = batch.toList()
                        batch.clear()
                        withContext(Dispatchers.Main) { screens.addAll(chunk) }
                    }
                }
                PassageCache.put(cacheKey, all)
                withContext(Dispatchers.Main) {
                    screens.addAll(batch)
                    cut = true
                }
            }
        }
        val screenCount = screens.size
        // Past the last screen: a divider with the next surah's name and the
        // basmala; resting on it opens that surah, so reading runs on.
        val nextSurah = SurahMetadata.getSurah(surahNumber + 1).takeIf { cut && surahNumber < 114 }
        val pageCount = screenCount + if (nextSurah != null) 1 else 0
        val pagerState = rememberPagerState(pageCount = { pageCount })
        LaunchedEffect(pagerState, nextSurah, screenCount) {
            if (nextSurah == null) return@LaunchedEffect
            snapshotFlow { pagerState.settledPage }.collectLatest { page ->
                if (page == screenCount) {
                    delay(700L)
                    onOpenSurah(surahNumber + 1)
                }
            }
        }

        // The verse to open on: the one asked for, then wherever reading has
        // got to, so a font change keeps the reader's place.
        var anchorAyah by remember(surahNumber) { mutableIntStateOf(if (startAyah > 0) startAyah else 0) }
        var positioned by remember(passages, ayahs) { mutableStateOf(false) }
        LaunchedEffect(passages, ayahs, screenCount, cut) {
            if (positioned || ayahs.isEmpty()) return@LaunchedEffect
            val target = ayahs.indexOfFirst { it.verse == anchorAyah }
            val page = if (anchorAyah <= 0 || target < 0) 0 else screens.indexOfFirst { it.holdsAyah(target) }
            if (page < 0) {
                if (!cut) return@LaunchedEffect // not cut that far yet
            } else {
                pagerState.scrollToPage(page)
            }
            positioned = true
        }

        // Remember where reading stopped, once the screen has settled.
        LaunchedEffect(pagerState, positioned, screens) {
            if (!positioned) return@LaunchedEffect
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    delay(600L) // one database write per pause, not per screen
                    val ayah = ayahs.getOrNull(screens.getOrNull(page)?.firstAyah ?: return@collectLatest)
                        ?: return@collectLatest
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

        // The screen stays lit while reading: the watch's own timeout is a
        // few seconds, shorter than a long verse. Each turn of the page
        // re-arms it; three minutes with no turn lets the watch sleep again.
        val view = LocalView.current
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collectLatest {
                view.keepScreenOn = true
                delay(READING_AWAKE_MS)
                view.keepScreenOn = false
            }
        }
        DisposableEffect(view) { onDispose { view.keepScreenOn = false } }

        // ── Bezel: one screen per detent ──────────────────────────────────────
        // A detent arrives as one event of about scaledVerticalScrollFactor
        // pixels; half of that moves on, and what is left of the event is
        // dropped so a single detent never moves two.
        val focusRequester = remember { FocusRequester() }
        val detentPx = remember {
            android.view.ViewConfiguration.get(context).scaledVerticalScrollFactor
        }
        var rotaryAcc by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Scaffold(
            // No position arc: zaher asked for nothing around the text.
            positionIndicator = {}
        ) {
          Box(Modifier.fillMaxSize()) {
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
                if (page == screenCount && nextSurah != null) {
                    SurahDivider(nextSurah.nameAr, basmala = surahNumber + 1 != 9, color = quranTextColor)
                    return@VerticalPager
                }
                val screen = screens.getOrNull(page) ?: return@VerticalPager
                fun ayahAt(pos: Offset): Ayah? {
                    val layout = screen.fit.layout
                    val line = layout.getLineForVertical((pos.y - screen.fit.top).toInt())
                    val offset = layout.getOffsetForHorizontal(line, pos.x)
                    return ayahs.getOrNull(screen.ayahAt(offset))
                }
                Box(Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(screen) {
                                detectTapGestures(
                                    onTap = {
                                        if (selectedAyahForAction == null) showControls = !showControls
                                    },
                                    onDoubleTap = { pos ->
                                        val ayah = ayahAt(pos)
                                        if (ayah != null) doubleTapInfoText = "$surahName ${ReaderTypography.arabicDigits(ayah.verse)}"
                                    },
                                    onLongPress = { pos ->
                                        selectedAyahForAction = ayahAt(pos)
                                    }
                                )
                            }
                    ) {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            native.save()
                            native.translate(0f, screen.fit.top.toFloat())
                            screen.fit.layout.draw(native)
                            native.restore()
                        }
                    }

                }
            }

            if (!positioned) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 3.dp
                )
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

            // ── Long press: − bookmark +, nothing else ─────────────────────────
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

private const val READING_AWAKE_MS = 3 * 60_000L

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
                "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
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
