package com.quran.watch8.ui.screens

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.db.entities.ReadingPositionEntity
import com.quran.watch8.data.model.SlotItem
import com.quran.watch8.data.model.TileActionCatalog
import com.quran.watch8.data.model.TileConfig
import com.quran.watch8.data.model.WatchAppearance
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin

private val WATCH_AUTO_LAYOUT_PATTERNS = listOf(
    listOf(listOf(6, 6), listOf(4, 8), listOf(4, 4, 4)),
    listOf(listOf(12), listOf(5, 7), listOf(4, 4, 4)),
    listOf(listOf(4, 8), listOf(12), listOf(6, 6)),
    listOf(listOf(5, 7), listOf(12), listOf(3, 3, 6)),
    listOf(listOf(6, 6), listOf(12), listOf(4, 4, 4)),
    listOf(listOf(3, 9), listOf(8, 4), listOf(5, 3, 4)),
    listOf(listOf(9, 3), listOf(4, 8), listOf(4, 3, 5)),
    listOf(listOf(4, 4, 4), listOf(3, 3, 3, 3), listOf(6, 6)),
    listOf(listOf(12), listOf(4, 4, 4), listOf(6, 6)),
    listOf(listOf(7, 5), listOf(3, 6, 3), listOf(5, 7)),
    listOf(listOf(6, 6), listOf(4, 8), listOf(8, 4), listOf(6, 6)),
    listOf(listOf(7, 5), listOf(4, 4, 4), listOf(12), listOf(6, 6), listOf(3, 3, 3, 3))
)

fun generateAutomaticLayout(config: TileConfig, variant: Int): TileConfig {
    val baseRows = WATCH_AUTO_LAYOUT_PATTERNS[Math.floorMod(variant, WATCH_AUTO_LAYOUT_PATTERNS.size)]
    val rows = baseRows.toMutableList()
    while (rows.sumOf { it.size } < config.tiles.size && rows.size < 5) {
        rows.add(listOf(4, 4, 4))
    }
    val placements = rows.flatMapIndexed { rowIndex, spans ->
        var unitX = 0
        spans.map { span ->
            val placement = Triple(rowIndex, unitX, span)
            unitX += span
            placement
        }
    }
    val rowHeight = 100f / rows.size.coerceAtLeast(1)
    val nextTiles = config.tiles.mapIndexed { index, tile ->
        val placement = placements.getOrElse(index) { placements.last() }
        tile.copy(
            x = placement.second / 12f * 100f,
            y = placement.first * rowHeight,
            width = placement.third / 12f * 100f,
            height = rowHeight,
            colSpan = placement.third,
            rowIndex = placement.first
        )
    }
    return config.copy(tiles = nextTiles, version = System.currentTimeMillis())
}

fun parseHexColor(hexString: String?, fallback: Color): Color {
    if (hexString.isNullOrBlank() || !hexString.startsWith("#")) return fallback
    return try {
        val colorInt = android.graphics.Color.parseColor(hexString)
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

fun watchBackgroundFor(pattern: String): Color = when (pattern) {
    "andalusian", "moroccan" -> Color(0xFF18313B)
    "damascene", "arabesque" -> Color(0xFF172030)
    "egyptian", "african" -> Color(0xFF2C1D14)
    "ottoman", "persian" -> Color(0xFF1B1730)
    "central-asian", "indonesian", "indian" -> Color(0xFF102A27)
    "geometric", "global" -> Color(0xFF0B1821)
    "none" -> Color(0xFF101216)
    else -> Color(0xFF101B2B)
}

fun getActionIcon(actionId: String, iconType: String?): String {
    if (!iconType.isNullOrBlank() && iconType != "default") {
        return when (iconType) {
            "quran"          -> "📖"
            "kaaba"          -> "🕋"
            "mosque"         -> "🕌"
            "tasbih"         -> "📿"
            "crescent"       -> "🌙"
            "star_islamic"   -> "✨"
            "dua"            -> "🤲"
            "clock"          -> "⏰"
            "stopwatch"      -> "⏱️"
            "hourglass"      -> "⏳"
            "hourglass_done" -> "⌛"
            "calendar"       -> "📅"
            "battery"        -> "🔋"
            "settings"       -> "⚙️"
            "pin"            -> "📍"
            "compass"        -> "🧭"
            "mic"            -> "🎤"
            "bookmark"       -> "🔖"
            "folder"         -> "📁"
            "bolt"           -> "⚡"
            "bell"           -> "🔔"
            "heart"          -> "❤️"
            "sun"            -> "☀️"
            "cloud_sun"      -> "⛅"
            "rain"           -> "🌧️"
            "cloud"          -> "☁️"
            "auto_layout"    -> "✦"
            else             -> "⭐"
        }
    }
    return when (actionId) {
        "folder_islamic", "folder_tools", "folder_custom" -> "📁"
        "clock_big"        -> "⏰"
        "date_big"         -> "📅"
        "prayer_countdown" -> "⏳"
        "prayer_elapsed"   -> "⌛"
        "prayer"           -> "🕌"
        "tasbih"           -> "📿"
        "qibla"            -> "🕋"
        "quran", "quran_resume" -> "📖"
        "bookmarks"        -> "🔖"
        "voice_notes"      -> "🎤"
        "locations"        -> "📍"
        "settings"         -> "⚙️"
        "battery"          -> "🔋"
        "weather"          -> "⛅"
        "auto_layout"      -> "✦"
        else               -> "⭐"
    }
}

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tileConfig by viewModel.tilesConfig.collectAsState()
    val lastPosition by viewModel.lastReadingPosition.collectAsState()
    val prayers = viewModel.prayerTimes

    var tasbihCount by remember { mutableStateOf(0) }
    var tasbihDhikrIndex by remember { mutableStateOf(0) }
    val dhikrList = listOf("سبحان الله", "الحمد لله", "لا إله إلا الله", "الله أكبر", "أستغفر الله")
    var liveStep by remember { mutableStateOf(0) }

    var activeFolderSlot by remember { mutableStateOf<SlotItem?>(null) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var firstLayoutSnapshot by remember { mutableStateOf<TileConfig?>(null) }
    var autoLayoutSequence by remember { mutableStateOf(0) }

    // Real-time second/minute clock ticker
    var currentTimeStr by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))) }
    var currentSecondsStr by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("ss", Locale.US))) }
    var amPmStr by remember { mutableStateOf(if (LocalTime.now().hour < 12) "AM" else "PM") }

    val hhmmFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.US) }
    val secondsFormatter = remember { DateTimeFormatter.ofPattern("ss", Locale.US) }

    // The tiles only ever render HH:mm, so tick once a minute on the boundary
    // instead of waking the whole grid every second.
    LaunchedEffect(Unit) {
        viewModel.refreshPrayerTimes()
        while (true) {
            val now = LocalTime.now()
            currentTimeStr = now.format(hhmmFormatter)
            currentSecondsStr = now.format(secondsFormatter)
            amPmStr = if (now.hour < 12) "AM" else "PM"
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    // Rotates a live tile through its sub-actions. This used to hang off a 60fps
    // infinite animation whose LaunchedEffect key changed hundreds of times a
    // second, tearing down and relaunching a coroutine on every change.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000L)
            liveStep++
        }
    }

    // Safe navigation helper
    fun safeNavigate(actionId: String) {
        try {
            when (actionId) {
                "quran" -> onNavigate("quran")
                "quran_resume", "reader_resume", "reader_next_ayah", "reader_bookmark", "reader_last_surah", "reader_index", "reader_search", "reader_bookmarks" -> {
                    val pos = lastPosition
                    if (pos != null) {
                        onNavigate("reader/${pos.surah}?listIndex=${pos.ayahIndex}&startAyah=${pos.ayahNumber}")
                    } else {
                        onNavigate("quran")
                    }
                }
                "watchface", "clock_big", "clock_top" -> onNavigate("watchface")
                "prayer", "prayer_countdown", "prayer_elapsed", "date_big", "weather", "prayer_schedule", "prayer_next", "prayer_reminders", "weather_details", "weather_refresh" -> onNavigate("prayer")
                "bookmarks" -> onNavigate("bookmarks")
                "locations", "locations_recent", "locations_active", "locations_navigate", "locations_add_current" -> onNavigate("locations")
                "qibla", "qibla_compass", "qibla_calibrate" -> onNavigate("qibla")
                "voice_notes" -> onNavigate("voice_notes")
                "presets" -> onNavigate("presets")
                "settings", "battery", "settings_open", "settings_notifications", "battery_status", "battery_saver" -> onNavigate("settings")
                "tasbih", "tasbih_increment", "quick_tasbih_increment" -> {
                    tasbihCount++
                    if (tasbihCount % 33 == 0) tasbihDhikrIndex++
                }
                "tasbih_reset" -> tasbihCount = 0
                "tasbih_select_dhikr" -> tasbihDhikrIndex++
                "auto_layout", "auto_layout_shuffle" -> {
                    if (firstLayoutSnapshot == null) firstLayoutSnapshot = tileConfig
                    val newConfig = tileConfig.generateSmartLayout()
                    viewModel.setTileConfig(newConfig)
                    Toast.makeText(context, "✦ تم توليد ترتيب جديد", Toast.LENGTH_SHORT).show()
                }
                "palette_shuffle" -> {
                    val newConfig = tileConfig.shufflePalette()
                    viewModel.setTileConfig(newConfig)
                    Toast.makeText(context, "🎨 تم تبديل الألوان", Toast.LENGTH_SHORT).show()
                }
                "auto_layout_restore" -> firstLayoutSnapshot?.let {
                    viewModel.setTileConfig(it)
                }
                "folder_islamic_open", "folder_tools_open" -> onNavigate("quran")
                else -> {
                    val def = TileActionCatalog.getDef(actionId)
                    if (def.route.isNotBlank() && def.route != "reader_resume") {
                        onNavigate(def.route)
                    } else if (def.route == "reader_resume") {
                        val pos = lastPosition
                        if (pos != null) onNavigate("reader/${pos.surah}?listIndex=${pos.ayahIndex}&startAyah=${pos.ayahNumber}")
                        else onNavigate("quran")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Hardware Back Button returns to WatchFace Home
    BackHandler {
        onNavigate("watchface")
    }

    var totalDragY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    Scaffold(
        timeText = {},
        vignette = {}
    ) {
        // Force LTR coordinate system so x=0% is Left and matches Web Studio exactly
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(watchBackgroundFor(tileConfig.appearance.pattern))
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                totalDragY = 0f
                                totalDragX = 0f
                            },
                            onDragEnd = {
                                val absY = kotlin.math.abs(totalDragY)
                                val absX = kotlin.math.abs(totalDragX)
                                // Mirror of Layer 1: swipe up for the drawer,
                                // swipe right to go back to the watch face (the
                                // same direction Wear OS uses for dismiss).
                                if (absY > absX && totalDragY < -25f) {
                                    onNavigate("app_drawer")
                                } else if (absX > absY && totalDragX > 30f) {
                                    onNavigate("watchface")
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragY += dragAmount.y
                                totalDragX += dragAmount.x
                            }
                        )
                    }
            ) {
                val screenWidth = maxWidth.value
                val screenHeight = maxHeight.value

                tileConfig.tiles.forEachIndexed { index, slot ->
                    val rawWidth = slot.width / 100f * screenWidth
                    val rawHeight = slot.height / 100f * screenHeight
                    val offsetX = slot.x / 100f * screenWidth
                    val offsetY = slot.y / 100f * screenHeight
                    SmartWatchFaceTile(
                        slot = slot,
                        appearance = tileConfig.appearance,
                        index = index,
                        lastPos = lastPosition,
                        prayers = prayers,
                        currentTime = currentTimeStr,
                        currentSec = currentSecondsStr,
                        amPm = amPmStr,
                        tasbihCount = tasbihCount,
                        currentDhikr = dhikrList[tasbihDhikrIndex % dhikrList.size],
                        onTasbihClick = {
                            tasbihCount++
                            if (tasbihCount % 33 == 0) tasbihDhikrIndex++
                        },
                        onFolderClick = { activeFolderSlot = slot },
                        liveStep = liveStep,
                        screenWidth = screenWidth,
                        modifier = Modifier
                            .absoluteOffset(
                                x = Dp(offsetX),
                                y = Dp(offsetY)
                            )
                            .size(
                                width = Dp(rawWidth),
                                height = Dp(rawHeight)
                            ),
                        onLongClick = {
                            val lp = if (slot.longPressAction.isNotBlank()) slot.longPressAction else if (slot.id == "settings") "cloud_sync_pull" else "quick_edit"
                            when (lp) {
                                "cloud_sync_pull" -> {
                                    scope.launch(Dispatchers.IO) {
                                        com.quran.watch8.util.LocalSyncServer.syncWithCloud(context, "pull")
                                    }
                                    Toast.makeText(context, "جاري المزامنة مع السحابة...", Toast.LENGTH_SHORT).show()
                                }
                                "open_settings" -> safeNavigate("settings")
                                "auto_layout_restore" -> safeNavigate("auto_layout_restore")
                                else -> editingSlotIndex = index
                            }
                        },
                        onActionClick = { actionId ->
                            val tapAct = if (slot.tapAction.isNotBlank()) slot.tapAction else actionId
                            safeNavigate(tapAct)
                        }
                    )
                }

                // ── Interactive Folder Modal Overlay ──
                if (activeFolderSlot != null) {
                    val folder = activeFolderSlot!!
                    val items = if (folder.folderItems.isNotEmpty()) folder.folderItems else when (folder.id) {
                        "folder_islamic" -> listOf("quran", "tasbih", "qibla", "prayer")
                        "folder_tools"   -> listOf("voice_notes", "bookmarks", "locations", "settings")
                        else             -> listOf("quran", "prayer", "tasbih", "settings")
                    }
                    FolderLauncherOverlay(
                        items = items.take(6),
                        onDismiss = { activeFolderSlot = null },
                        onItemClick = { actionId ->
                            activeFolderSlot = null
                            safeNavigate(actionId)
                        }
                    )
                }

                // ── Watch-Native Quick Edit Modal Dialog ──
                if (editingSlotIndex != null && editingSlotIndex!! in tileConfig.tiles.indices) {
                    val currentIdx = editingSlotIndex!!
                    val currentSlot = tileConfig.tiles[currentIdx]

                    QuickEditTileModal(
                        slot = currentSlot,
                        onDismiss = { editingSlotIndex = null },
                        onSave = { updatedSlot ->
                            val list = tileConfig.tiles.toMutableList()
                            list[currentIdx] = updatedSlot
                            val newConfig = tileConfig.copy(tiles = list, version = System.currentTimeMillis())
                            viewModel.setTileConfig(newConfig)
                            editingSlotIndex = null
                            Toast.makeText(context, "تم حفظ تعديل البلاطة ✓", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderLauncherOverlay(
    items: List<String>,
    onDismiss: () -> Unit,
    onItemClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize(0.86f)
                .clip(CircleShape)
                .background(Color(0xE6213147))
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable { }
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            Text(
                text = "التطبيقات",
                fontSize = 10.sp,
                color = AccentGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 5.dp)
            )
            items.chunked(if (items.size > 4) 3 else 2).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    rowItems.forEach { actionId ->
                        FolderItemButton(
                            actionId = actionId,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) { onItemClick(actionId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickEditTileModal(
    slot: SlotItem,
    onDismiss: () -> Unit,
    onSave: (SlotItem) -> Unit
) {
    var selectedAction by remember { mutableStateOf(slot.id) }
    var selectedColor by remember { mutableStateOf(slot.colorHex) }
    var selectedStyle by remember { mutableStateOf(slot.displayStyle) }
    var currentFontSize by remember { mutableStateOf(slot.fontSize) }
    var selectedFolderItems by remember {
        mutableStateOf(
            slot.folderItems.ifEmpty {
                if (slot.id == "folder_tools") listOf("voice_notes", "bookmarks", "locations", "settings")
                else listOf("quran", "tasbih", "qibla", "prayer")
            }
        )
    }

    val listState = rememberScalingLazyListState()

    val colors = listOf(
        "#10B981", "#7C3AED", "#0E7490", "#0284C7",
        "#DC2626", "#F59E0B", "#EA580C", "#334155", "#000000"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp)
        ) {
            item {
                Text(
                    text = "تعديل البلاطة",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // 1. Feature Selector
            item {
                Text("الميزة المعروضة:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
            items(TileActionCatalog.assignableTiles) { act ->
                Chip(
                    onClick = { selectedAction = act.id },
                    label = {
                        Text(
                            text = act.title,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (selectedAction == act.id) Color(0xFF0D9488) else Color(0xFF1E293B),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }

            // 2. Display Style
            item {
                Text("نمط العرض:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    listOf("text" to "نص فقط", "both" to "نص+أيقونة", "icon" to "أيقونة فقط").forEach { (st, label) ->
                        CompactChip(
                            onClick = { selectedStyle = st },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (selectedStyle == st) Color(0xFF0D9488) else Color(0xFF1E293B),
                                contentColor = Color.White
                            )
                        )
                    }
                }
            }

            if (selectedAction.startsWith("folder")) {
                item {
                    Text("عناصر المجلد (حتى 6):", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }
                items(listOf("quran", "quran_resume", "tasbih", "qibla", "prayer", "prayer_strip_5", "bookmarks", "voice_notes", "locations", "settings")) { actionId ->
                    val selected = actionId in selectedFolderItems
                    CompactChip(
                        onClick = {
                            selectedFolderItems = when {
                                selected -> selectedFolderItems.filterNot { it == actionId }
                                selectedFolderItems.size < 6 -> selectedFolderItems + actionId
                                else -> selectedFolderItems
                            }
                        },
                        label = {
                            Text(
                                text = "${getActionIcon(actionId, null)} ${TileActionCatalog.getDef(actionId).title.replace(Regex("^[^\\s]+\\s+"), "")}",
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (selected) Color(0xFF0D9488) else Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Color Palette
            item {
                Text("لون الخلفية:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    colors.take(4).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(parseHexColor(hex, Color.Gray), CircleShape)
                                .border(
                                    width = if (selectedColor.equals(hex, ignoreCase = true)) 2.dp else 0.dp,
                                    color = if (selectedColor.equals(hex, ignoreCase = true)) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    colors.drop(4).take(4).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(parseHexColor(hex, Color.Gray), CircleShape)
                                .border(
                                    width = if (selectedColor.equals(hex, ignoreCase = true)) 2.dp else 0.dp,
                                    color = if (selectedColor.equals(hex, ignoreCase = true)) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }

            // 4. Save / Cancel Buttons
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val updated = slot.copy(
                                id = selectedAction,
                                colorHex = selectedColor,
                                displayStyle = selectedStyle,
                                fontSize = currentFontSize,
                                folderItems = if (selectedAction.startsWith("folder")) selectedFolderItems else slot.folderItems,
                                tapAction = when (selectedAction) {
                                    "tasbih" -> "tasbih_increment"
                                    "qibla" -> "qibla_compass"
                                    "auto_layout" -> "auto_layout"
                                    else -> slot.tapAction
                                },
                                longPressAction = if (selectedAction == "auto_layout") "auto_layout_restore" else slot.longPressAction
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderItemButton(actionId: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val def = TileActionCatalog.getDef(actionId)
    val icon = getActionIcon(actionId, null)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = def.title.replace(Regex("^[^\\s]+\\s+"), ""),
                fontSize = 7.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Pulses a tile icon only when that tile actually asked for an animated one. */
@Composable
private fun rememberIconPulse(enabled: Boolean): Float =
    if (enabled) {
        val transition = rememberInfiniteTransition(label = "icon_pulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "icon_pulse_scale"
        )
        scale
    } else {
        1f
    }

@Composable
private fun SmartWatchFaceTile(
    slot: SlotItem,
    appearance: WatchAppearance,
    index: Int,
    lastPos: ReadingPositionEntity?,
    prayers: PrayerTimesHelper.DayPrayers?,
    currentTime: String,
    currentSec: String,
    amPm: String,
    tasbihCount: Int,
    currentDhikr: String,
    screenWidth: Float,
    onTasbihClick: () -> Unit,
    onFolderClick: () -> Unit,
    liveStep: Int,
    modifier: Modifier,
    onLongClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    if (slot.id == "none") {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val activeActionId = if (!slot.isLive || slot.subActions.isEmpty()) {
        slot.id
    } else {
        val faces = slot.subActions
        faces[liveStep % faces.size]
    }

    val isFolder = slot.id.startsWith("folder") || slot.folderItems.isNotEmpty() || activeActionId.startsWith("folder")
    val def = TileActionCatalog.getDef(activeActionId)
    val bgColor = parseHexColor(slot.colorHex, Color(0xFF1E293B))

    // Prayer calculations
    val now = Instant.now()
    val allPrayers = if (prayers != null) listOf(prayers.fajr, prayers.sunrise, prayers.dhuhr, prayers.asr, prayers.maghrib, prayers.isha) else emptyList()
    val pastPrayers = allPrayers.filter { it.time.isBefore(now) }
    val lastPrayer = pastPrayers.lastOrNull() ?: prayers?.isha
    // PrayerTimesHelper already rolls this over to tomorrow's Fajr after Isha.
    // Re-scanning today's list here fell back to a time ~18h in the past and
    // the countdown clamped to "0m" all night.
    val nextPrayer = prayers?.nextPrayer ?: prayers?.fajr

    val elapsedSec = if (lastPrayer != null) (now.epochSecond - lastPrayer.time.epochSecond).coerceAtLeast(0) else 0L
    val elapsedH = elapsedSec / 3600
    val elapsedM = (elapsedSec % 3600) / 60
    val remainingSec = if (nextPrayer != null) (nextPrayer.time.epochSecond - now.epochSecond).coerceAtLeast(0) else 0L
    val remainingH = remainingSec / 3600
    val remainingM = (remainingSec % 3600) / 60
    val elapsedStr = PrayerTimesHelper.formatCountdown((elapsedSec / 60).toInt())
    val countdownStr = if (nextPrayer != null) {
        PrayerTimesHelper.formatCountdown((remainingSec / 60).toInt())
    } else {
        "1h 23m"
    }

    val isColorOnly = slot.displayStyle == "color_only" || activeActionId == "color_only"
    val showIcon = slot.displayStyle == "icon" || slot.displayStyle == "both" || slot.displayStyle == "full"
    val showText = slot.displayStyle == "text" || slot.displayStyle == "both" || slot.displayStyle == "full"

    val displayTitle = when (activeActionId) {
        "folder_islamic"   -> "إسلاميات"
        "folder_tools"     -> "الأدوات"
        "folder_custom"    -> "مجلد"
        "clock_big"        -> currentTime
        "date_big"         -> "30 Aug"
        "prayer"           -> "المواقيت"
        "prayer_countdown" -> "${nextPrayer?.nameAr ?: "الصلاة"} $countdownStr"
        "prayer_elapsed"   -> "${nextPrayer?.nameAr ?: "الصلاة"} $elapsedStr"
        "tasbih"           -> "$currentDhikr $tasbihCount"
        "qibla"            -> "72° NE"
        "quran"            -> "المصحف"
        "quran_resume"     -> lastPos?.surahNameAr ?: "آل عمران"
        "voice_notes"      -> "تسجيل"
        "bookmarks"        -> "العلامات"
        "locations"        -> "المواقع"
        "settings"         -> "الإعدادات"
        "battery"          -> "100%"
        "weather"          -> "24°C"
        "auto_layout"      -> "ترتيب جديد"
        else               -> def.title.replace(Regex("^[^\\\\s]+\\\\s+"), "")
    }

    val iconText = getActionIcon(activeActionId, slot.iconType)
    val fontColor = parseHexColor(slot.fontColorHex, Color.White)
    val iconColor = parseHexColor(slot.iconColorHex, Color.White)

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tile_scale"
    )

    // Every tile used to run this animation even with a static icon (1f -> 1f),
    // which kept the whole grid invalidating at the display refresh rate.
    val animatedIconScale = rememberIconPulse(slot.iconStyle == "animated")

    // Honour the shape the web studio set. "mixed" alternates oval and square
    // so a connected grid still gets some rhythm; "square-connected" stays a
    // plain rectangle so neighbouring tiles read as one surface.
    val tileShape = when (appearance.tileShape) {
        "circle" -> CircleShape
        "oval" -> RoundedCornerShape(percent = 50)
        "square-gapped" -> RoundedCornerShape(8.dp)
        "mixed" -> if (index % 3 == 1) RoundedCornerShape(percent = 50) else RectangleShape
        else -> RectangleShape
    }
    val resolvedBg = when (appearance.iconPalette) {
        "monochrome" -> Color(0xFF344253)
        "night" -> Color(0xFF17263A)
        "warm" -> Color(0xFF8C591D)
        else -> bgColor
    }

    Box(
        modifier = modifier
            .scale(scale)
            .background(resolvedBg, tileShape)
            .clip(tileShape)
            .clipToBounds()
            .pointerInput(activeActionId, isFolder) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        if (isFolder) {
                            onFolderClick()
                        } else {
                            onActionClick(activeActionId)
                        }
                    },
                    onLongPress = {
                        onLongClick()
                    }
                )
            }
    ) {
        val tx = if (slot.textX != 0f) slot.textX else 50f
        val ty = if (slot.textY != 0f) slot.textY else 50f
        val ix = if (slot.iconX != 0f) slot.iconX else 50f
        val iy = if (slot.iconY != 0f) slot.iconY else 30f

        val isPrayerStrip = slot.displayStyle == "prayer_strip_5" || activeActionId == "prayer_strip_5"

        if (isPrayerStrip) {
            val pList = listOf(
                "فجر" to (prayers?.fajr?.formatted ?: "05:30"),
                "ظهر" to (prayers?.dhuhr?.formatted ?: "13:00"),
                "عصر" to (prayers?.asr?.formatted ?: "16:30"),
                "مغرب" to (prayers?.maghrib?.formatted ?: "19:15"),
                "عشاء" to (prayers?.isha?.formatted ?: "20:45")
            )
            PrayerStripTable(pList)
        } else {
            val textHorizBias = when (slot.textAlign) {
                "right" -> 0.75f
                "left" -> -0.75f
                else -> (tx - 50f) / 50f
            }
            val textAlignment = androidx.compose.ui.BiasAlignment(textHorizBias, (ty - 50f) / 50f)
            val iconAlignment = androidx.compose.ui.BiasAlignment((ix - 50f) / 50f, (iy - 50f) / 50f)
            val textAlignEnum = when (slot.textAlign) {
                "right" -> TextAlign.End
                "left" -> TextAlign.Start
                else -> TextAlign.Center
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // ── Icon Render ──
                if (showIcon && slot.displayStyle != "text") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(iconAlignment)
                            .scale(animatedIconScale)
                    ) {
                        if (activeActionId == "clock_big") {
                            Text(
                                text = currentTime,
                                fontSize = (slot.fontSize * 1.5).sp,
                                color = iconColor,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (activeActionId == "date_big") {
                            Text(
                                text = "30 Aug",
                                fontSize = (slot.fontSize * 1.2).sp,
                                color = iconColor,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = iconText,
                                fontSize = (slot.iconSize ?: 24).sp,
                                color = iconColor
                            )
                        }
                    }
                }

                val fontScale = (screenWidth / 380f).coerceIn(0.45f, 1.2f)
                val calibFontSize = (slot.fontSize * fontScale).coerceAtLeast(7f).sp

                // ── Text Render ──
                if (showText) {
                    if (activeActionId == "quran_resume") {
                        val readingLine = buildAnnotatedString {
                            withStyle(SpanStyle(color = AccentGold, fontWeight = FontWeight.Bold)) {
                                append("${lastPos?.surahNameAr ?: "سورة الكهف"} · ${lastPos?.ayahNumber ?: 18} ")
                            }
                            withStyle(SpanStyle(color = fontColor)) {
                                append(lastPos?.ayahSnippet?.takeIf { it.isNotBlank() } ?: "وَتَحْسَبُهُمْ أَيْقَاظًا وَهُمْ رُقُودٌ وَنُقَلِّبُهُمْ ذَاتَ الْيَمِينِ وَذَاتَ الشِّمَالِ…")
                            }
                        }
                        Text(
                            text = readingLine,
                            fontSize = calibFontSize,
                            lineHeight = (slot.fontSize * fontScale * 1.3f).coerceAtLeast(8f).sp,
                            textAlign = TextAlign.Start,
                            // The reading tile is the primary loop; give the
                            // verse a third line before it ellipsises.
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    } else Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(textAlignment)
                    ) {
                        if (activeActionId == "clock_big") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = currentTime,
                                    fontSize = calibFontSize,
                                    color = fontColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = amPm,
                                    fontSize = (slot.fontSize * fontScale * 0.45f).coerceAtLeast(6f).sp,
                                    color = AccentGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = displayTitle,
                                fontSize = calibFontSize,
                                color = fontColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerStripTable(pList: List<Pair<String, String>>) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        val columns = pList.size.coerceAtLeast(1)
        val columnWidth = maxWidth / columns
        // Five Arabic names need real width. In a narrow tile each column got
        // ~15dp, which clipped every name to a letter or two and rendered the
        // whole strip as noise -- so below that, drop the names and keep the
        // times, which stay readable and hold the same information.
        val showNames = columnWidth >= 30.dp
        val nameSize = (columnWidth.value * 0.26f).coerceIn(7f, 11f).sp
        val timeSize = (columnWidth.value * 0.30f).coerceIn(7f, 12f).sp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            if (showNames) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    pList.forEach { (name, _) ->
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            color = AccentGold,
                            fontSize = nameSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                pList.forEach { (_, time) ->
                    Text(
                        time,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = timeSize,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
