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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.RoundPassages
import com.quran.watch8.ui.components.WatchIcons
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
 * of the screen before; below it, the surah and verse. The bezel moves one
 * screen per detent; a swipe up or down does the same.
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

        // ── Geometry ─────────────────────────────────────────────────────────
        val typeface = remember(fontFamilyName) { ReaderTypography.typeface(context, fontFamilyName) }
        val numberTypeface = remember { ReaderTypography.quranTypeface(context) }
        val fontPx = with(density) { fontSize.sp.toPx() }
        val passages = remember(diameterPx, fontPx, typeface, quranTextColor, ayahNumberColor) {
            RoundPassages(
                diameterPx = diameterPx,
                lineHeightPx = (fontPx * ReaderTypography.LINE_HEIGHT_FACTOR).roundToInt(),
                // The glass ends a little inside the panel's circle, and the
                // position indicator runs along the rim.
                marginPx = with(density) { 10.dp.roundToPx() } + (diameterPx * 0.04f).roundToInt(),
                capPx = (diameterPx * 0.15f).roundToInt(),
                minLineWidthPx = (diameterPx * 0.4f).roundToInt(),
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
        val pagerState = rememberPagerState(pageCount = { screenCount })

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
            positionIndicator = {
                if (screenCount > 1) {
                    PositionIndicator(value = { pagerState.currentPage / (screenCount - 1f) })
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
                        if (abs(rotaryAcc) >= detentPx / 2f && screenCount > 0) {
                            val step = if (rotaryAcc > 0) 1 else -1
                            rotaryAcc = 0f
                            val from = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
                            val to = (from + step).coerceIn(0, screenCount - 1)
                            if (to != from) scope.launch { pagerState.animateScrollToPage(to, animationSpec = tween(180)) }
                        }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable()
            ) { page ->
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
                                        if (ayah != null) doubleTapInfoText = "$surahName (${ayah.verse})"
                                    },
                                    onLongPress = { pos ->
                                        selectedAyahForAction = ayahAt(pos)
                                        actionToastText = null
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

                    // Top cap: the last words of the screen before, as a bridge.
                    val bridge = screens.getOrNull(page - 1)?.tail
                    if (bridge != null) {
                        Text(
                            text = bridge,
                            fontFamily = ReaderTypography.QuranFont,
                            fontSize = 11.sp,
                            color = quranTextColor.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp)
                                .fillMaxWidth(0.5f)
                        )
                    }

                    // Bottom cap: where this screen is.
                    val firstVerse = ayahs.getOrNull(screen.firstAyah)?.verse
                    Text(
                        text = if (firstVerse != null) "$surahName • ${ReaderTypography.arabicDigits(firstVerse)}" else surahName,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                    )
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
