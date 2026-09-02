package com.quran.watch8.data.model

import org.json.JSONArray
import org.json.JSONObject

data class TileActionItem(
    val id: String,
    val title: String,
    val icon: String = "⭐",
    val route: String = ""
)

object TileActionCatalog {
    val actions = listOf(
        TileActionItem("color_only", "🎨 بلاطة لون فقط (تزيينية)", "🎨", ""),
        TileActionItem("clock_big", "⏰ الساعة الرقمية", "⏰", ""),
        TileActionItem("prayer_countdown", "⏳ متبقي الصلاة القادمة", "⏳", "prayer"),
        TileActionItem("prayer_elapsed", "⌛ الوقت المنقضي على الصلاة", "⌛", "prayer"),
        TileActionItem("prayer", "🕌 مواقيت الصلاة اليومية", "🕌", "prayer"),
        TileActionItem("prayer_strip_5", "🕌 جدول المواقيت الـ 5 المدمج", "🕌", "prayer"),
        TileActionItem("auto_layout", "✦ ترتيب تلقائي جديد", "✦", "auto_layout"),
        TileActionItem("auto_layout_shuffle", "✦ توليد ترتيب جديد", "✦", "auto_layout"),
        TileActionItem("palette_shuffle", "🎨 تبديل الألوان عشوائياً", "🎨", "palette_shuffle"),
        TileActionItem("presets", "📑 القوالب الجاهزة", "📑", "presets"),
        TileActionItem("auto_layout_restore", "↺ استعادة الترتيب الأول", "↺", ""),
        TileActionItem("quran_resume", "📖 موضع القراءة الأخير", "📖", "reader_resume"),
        TileActionItem("quran", "📖 المصحف الشريف (الفهرس)", "📖", "quran"),
        TileActionItem("tasbih", "📿 السبحة الإلكترونية", "📿", "tasbih"),
        TileActionItem("qibla", "🕋 بوصلة القبلة", "🕋", "qibla"),
        TileActionItem("folder_islamic", "📁 مجلد إسلاميات", "📁", ""),
        TileActionItem("folder_tools", "📁 مجلد الأدوات", "📁", ""),
        TileActionItem("folder_custom", "📁 مجلد مخصص", "📁", ""),
        TileActionItem("date_big", "📅 التاريخ الهجري والميلادي", "📅", ""),
        TileActionItem("bookmarks", "🔖 العلامات المرجعية", "🔖", "bookmarks"),
        TileActionItem("voice_notes", "🎤 استوديو التسجيل الصوتي", "🎤", "voice_notes"),
        TileActionItem("locations", "📍 المواقع المحفوظة", "📍", "locations"),
        TileActionItem("settings", "⚙️ الإعدادات والمزامنة", "⚙️", "settings"),
        TileActionItem("battery", "🔋 نسبة شحن البطارية", "🔋", ""),
        TileActionItem("weather", "⛅ حالة الطقس", "⛅", "prayer"),
        TileActionItem("reader_resume", "▶ متابعة القراءة", "📖", "reader_resume"),
        TileActionItem("reader_next_ayah", "› الآية التالية", "📖", "reader_resume"),
        TileActionItem("reader_bookmark", "🔖 إضافة علامة", "🔖", "reader_resume"),
        TileActionItem("reader_last_surah", "☰ فهرس السورة", "📖", "quran"),
        TileActionItem("reader_index", "☰ فهرس المصحف", "📖", "quran"),
        TileActionItem("reader_search", "⌕ بحث في القرآن", "🔎", "quran"),
        TileActionItem("reader_bookmarks", "🔖 العلامات المرجعية", "🔖", "bookmarks"),
        TileActionItem("locations_recent", "◷ المواقع الأخيرة", "📍", "locations"),
        TileActionItem("locations_active", "⌖ الموقع النشط", "📍", "locations"),
        TileActionItem("locations_navigate", "↗ فتح الملاحة", "🧭", "locations"),
        TileActionItem("locations_add_current", "＋ حفظ موقعي", "📍", "locations"),
        TileActionItem("qibla_compass", "🕋 بوصلة القبلة", "🧭", "qibla"),
        TileActionItem("qibla_calibrate", "◌ معايرة البوصلة", "🧭", "qibla"),
        TileActionItem("tasbih_increment", "＋ تسبيحة", "📿", "tasbih"),
        TileActionItem("tasbih_reset", "↺ تصفير التسبيح", "📿", "tasbih"),
        TileActionItem("tasbih_select_dhikr", "☰ اختيار الذكر", "📿", "tasbih"),
        TileActionItem("prayer_schedule", "◫ المواقيت", "🕌", "prayer"),
        TileActionItem("prayer_next", "› الصلاة التالية", "⏳", "prayer"),
        TileActionItem("prayer_reminders", "🔔 التنبيهات", "🔔", "settings"),
        TileActionItem("settings_open", "⚙ الإعدادات", "⚙", "settings"),
        TileActionItem("settings_notifications", "🔔 إعداد التنبيهات", "🔔", "settings"),
        TileActionItem("battery_status", "🔋 حالة البطارية", "🔋", "settings"),
        TileActionItem("battery_saver", "◐ توفير الطاقة", "🔋", "settings")
    )

    fun getDef(id: String): TileActionItem {
        return actions.find { it.id == id } ?: TileActionItem(id, id, "⭐", id)
    }

    /**
     * Ids a tile can actually *be*: places you open and live readings you glance
     * at. The rest of [actions] are in-tile commands (reset the tasbih, next
     * ayah, calibrate the compass…) that only make sense inside their own
     * screen or as a tap/long-press action, never as a standalone tile. Kept in
     * step with tileActionsList in pwa-web/app.js so the watch editor and the
     * web studio offer the same set.
     */
    private val assignableIds = listOf(
        "color_only", "clock_big", "prayer_countdown", "prayer_elapsed", "prayer",
        "prayer_strip_5", "quran_resume", "quran", "tasbih", "qibla",
        "folder_islamic", "folder_tools", "folder_custom", "date_big", "bookmarks",
        "voice_notes", "locations", "settings", "battery", "weather",
        "auto_layout", "palette_shuffle", "presets"
    )

    /** The curated list shown in the watch's tile editor. */
    val assignableTiles: List<TileActionItem> =
        assignableIds.mapNotNull { id -> actions.find { it.id == id } }
}

data class SlotItem(
    val id: String,
    val colorHex: String,
    val isLive: Boolean = false,
    val subActions: List<String> = emptyList(),
    val folderItems: List<String> = emptyList(),
    val displayStyle: String = "text",
    val fontSize: Int = 14,
    val fontColorHex: String = "#FFFFFF",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 33f,
    val height: Float = 33f,
    val textX: Float = 50f,
    val textY: Float = 50f,
    val iconX: Float = 50f,
    val iconY: Float = 30f,
    val iconStyle: String = "static",
    val iconType: String = "default",
    val iconColorHex: String = "#FFFFFF",
    val iconSize: Int = 24,
    val fontFamily: String = "Uthmanic",
    val tapAction: String = "",
    val longPressAction: String = "quick_edit",
    val colSpan: Int = 4,
    val rowIndex: Int = 0,
    val weight: Float = 0.5f,
    val fontSizeOffset: Int = 0,
    val textAlign: String = "center"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("colorHex", colorHex)
        put("isLive", isLive)
        put("subActions", JSONArray(subActions))
        put("folderItems", JSONArray(folderItems))
        put("displayStyle", displayStyle)
        put("fontSize", fontSize)
        put("fontColorHex", fontColorHex)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("width", width.toDouble())
        put("height", height.toDouble())
        put("textX", textX.toDouble())
        put("textY", textY.toDouble())
        put("iconX", iconX.toDouble())
        put("iconY", iconY.toDouble())
        put("iconStyle", iconStyle)
        put("iconType", iconType)
        put("iconColorHex", iconColorHex)
        put("iconSize", iconSize)
        put("fontFamily", fontFamily)
        put("tapAction", tapAction)
        put("longPressAction", longPressAction)
        put("colSpan", colSpan)
        put("rowIndex", rowIndex)
        put("weight", weight.toDouble())
        put("fontSizeOffset", fontSizeOffset)
        put("textAlign", textAlign)
    }

    companion object {
        fun fromJson(json: JSONObject): SlotItem {
            val subList = mutableListOf<String>()
            val arr = json.optJSONArray("subActions")
            if (arr != null) {
                for (i in 0 until arr.length()) subList.add(arr.optString(i))
            }

            val foldList = mutableListOf<String>()
            val fArr = json.optJSONArray("folderItems")
            if (fArr != null) {
                for (i in 0 until fArr.length()) foldList.add(fArr.optString(i))
            }

            return SlotItem(
                id = json.optString("id", ""),
                colorHex = json.optString("colorHex", "#334155"),
                isLive = json.optBoolean("isLive", false),
                subActions = subList,
                folderItems = foldList,
                displayStyle = json.optString("displayStyle", "text"),
                fontSize = json.optInt("fontSize", 14),
                fontColorHex = json.optString("fontColorHex", "#FFFFFF"),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                width = json.optDouble("width", 33.0).toFloat(),
                height = json.optDouble("height", 33.0).toFloat(),
                textX = json.optDouble("textX", 50.0).toFloat(),
                textY = json.optDouble("textY", 50.0).toFloat(),
                iconX = json.optDouble("iconX", 50.0).toFloat(),
                iconY = json.optDouble("iconY", 30.0).toFloat(),
                iconStyle = json.optString("iconStyle", "static"),
                iconType = json.optString("iconType", "default"),
                iconColorHex = json.optString("iconColorHex", "#FFFFFF"),
                iconSize = json.optInt("iconSize", 24),
                fontFamily = json.optString("fontFamily", "Uthmanic"),
                tapAction = json.optString("tapAction", ""),
                longPressAction = json.optString("longPressAction", "quick_edit"),
                colSpan = json.optInt("colSpan", 4),
                rowIndex = json.optInt("rowIndex", 0),
                weight = json.optDouble("weight", 0.5).toFloat(),
                fontSizeOffset = json.optInt("fontSizeOffset", 0),
                textAlign = json.optString("textAlign", "center")
            )
        }
    }
}

/** Visual rules shared with the web studio and persisted alongside the tile layout. */
data class WatchAppearance(
    val tileShape: String = "square-connected",
    val pattern: String = "star-eight",
    val iconPalette: String = "jewel"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("tileShape", tileShape)
        put("pattern", pattern)
        put("iconPalette", iconPalette)
    }

    companion object {
        fun fromJson(json: JSONObject?): WatchAppearance = WatchAppearance(
            tileShape = json?.optString("tileShape", "square-connected") ?: "square-connected",
            pattern = json?.optString("pattern", "star-eight") ?: "star-eight",
            iconPalette = json?.optString("iconPalette", "jewel") ?: "jewel"
        )
    }
}

data class TileConfig(
    val tiles: List<SlotItem> = listOf(
        // Layer 1 already shows the time on wrist-raise, so the tiles open with
        // the prayer countdown across the top row instead of a second clock.
        SlotItem(id = "prayer_countdown", colorHex = "#10B981", isLive = true, subActions = listOf("prayer_countdown", "prayer", "prayer_elapsed"), x = 0f, y = 0f, width = 100f, height = 33.33f, colSpan = 12, rowIndex = 0, fontSize = 20, displayStyle = "text"),
        SlotItem(id = "folder_islamic", colorHex = "#0284C7", folderItems = listOf("quran", "tasbih", "qibla", "prayer"), x = 0f, y = 33.33f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 1, fontSize = 14, displayStyle = "text"),
        SlotItem(id = "quran_resume", colorHex = "#0E7490", isLive = true, subActions = listOf("quran_resume", "tasbih"), x = 33.33f, y = 33.33f, width = 66.67f, height = 33.33f, colSpan = 8, rowIndex = 1, fontSize = 14, displayStyle = "text"),
        SlotItem(id = "folder_tools", colorHex = "#EA580C", folderItems = listOf("voice_notes", "bookmarks", "locations", "settings"), x = 0f, y = 66.67f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 2, fontSize = 14, displayStyle = "text"),
        SlotItem(id = "locations", colorHex = "#F59E0B", x = 33.33f, y = 66.67f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 2, fontSize = 14, displayStyle = "text"),
        SlotItem(id = "settings", colorHex = "#334155", x = 66.67f, y = 66.67f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 2, fontSize = 14, displayStyle = "text")
    ),
    val version: Long = System.currentTimeMillis(),
    val appearance: WatchAppearance = WatchAppearance()
) {
    val topTiles: List<SlotItem> get() = tiles
    val midTiles: List<SlotItem> get() = emptyList()
    val botTiles: List<SlotItem> get() = emptyList()

    fun isValid(): Boolean = tiles.isNotEmpty()

    fun generateSmartLayout(): TileConfig {
        val patterns = listOf(
            listOf(listOf(6, 6), listOf(4, 8), listOf(4, 4, 4)),
            listOf(listOf(12), listOf(6, 6), listOf(4, 4, 4)),
            listOf(listOf(6, 6), listOf(12), listOf(4, 4, 4)),
            listOf(listOf(4, 4, 4), listOf(6, 6), listOf(12)),
            listOf(listOf(12), listOf(12), listOf(4, 4, 4)),
            listOf(listOf(12), listOf(3, 3, 3, 3), listOf(6, 6)),
            listOf(listOf(8, 4), listOf(4, 4, 4), listOf(6, 6)),
            listOf(listOf(4, 8), listOf(4, 4, 4), listOf(6, 6)),
            listOf(listOf(4, 4, 4), listOf(6, 6), listOf(4, 4, 4)),
            listOf(listOf(6, 6), listOf(6, 6), listOf(6, 6)),
            listOf(listOf(12), listOf(6, 6), listOf(3, 3, 3, 3)),
            listOf(listOf(7, 5), listOf(5, 7), listOf(12)),
            listOf(listOf(4, 4, 4), listOf(12), listOf(4, 4, 4)),
            listOf(listOf(6, 6), listOf(4, 4, 4), listOf(12)),
            listOf(listOf(12), listOf(6, 6), listOf(6, 6), listOf(12)),
            listOf(listOf(4, 4, 4), listOf(4, 4, 4), listOf(4, 4, 4)),
            listOf(listOf(12), listOf(6, 6)),
            listOf(listOf(12), listOf(4, 4, 4), listOf(6, 6))
        )
        val chosenPattern = patterns.random()
        val patternSlots = chosenPattern.flatMapIndexed { rowIndex, spans ->
            spans.map { colSpan -> rowIndex to colSpan }
        }
        val rowCount = chosenPattern.size.coerceAtLeast(1)

        val newTiles = tiles.mapIndexed { index, item ->
            val planned = patternSlots[index % patternSlots.size]
            val rIdx = (planned.first + (index / patternSlots.size) * rowCount).coerceAtMost(4)
            val cSpan = planned.second
            item.copy(
                rowIndex = rIdx,
                colSpan = cSpan,
                fontSize = if (cSpan == 12) 18 else if (cSpan >= 6) 14 else 12
            )
        }

        val rows = newTiles.groupBy { it.rowIndex }.toSortedMap()
        val actualRowCount = rows.size.coerceAtLeast(1)
        val actualRowHeight = 100f / actualRowCount.toFloat()

        val finalTiles = mutableListOf<SlotItem>()
        var currentY = 0f
        rows.values.forEach { rowTiles ->
            val totalCols = rowTiles.sumOf { it.colSpan }.toFloat().coerceAtLeast(1f)
            var currentX = 0f
            rowTiles.forEach { t ->
                val w = (t.colSpan.toFloat() / totalCols) * 100f
                finalTiles.add(
                    t.copy(
                        x = currentX,
                        y = currentY,
                        width = w,
                        height = actualRowHeight
                    )
                )
                currentX += w
            }
            currentY += actualRowHeight
        }

        return copy(tiles = finalTiles, version = System.currentTimeMillis())
    }

    fun shufflePalette(): TileConfig {
        val palettes = listOf(
            listOf("#1E293B", "#0E7490", "#D97706", "#334155", "#475569", "#155E75", "#1E3A8A"),
            listOf("#065F46", "#047857", "#059669", "#10B981", "#0F766E", "#115E59", "#044E45"),
            listOf("#292524", "#78350F", "#B45309", "#D97706", "#92400E", "#451A03", "#A16207"),
            listOf("#27272A", "#881337", "#BE123C", "#E11D48", "#9F1239", "#4C0519", "#701A75"),
            listOf("#1C2541", "#3A506B", "#5BC0BE", "#0E7490", "#2E4057", "#1D3557", "#0F2B48"),
            listOf("#312E81", "#4338CA", "#6366F1", "#7C3AED", "#5B21B6", "#4C1D95", "#3730A3"),
            listOf("#44403C", "#78350F", "#A16207", "#CA8A04", "#57534E", "#854D0E", "#6B390D"),
            listOf("#115E59", "#0D9488", "#14B8A6", "#2DD4BF", "#0F766E", "#065F46", "#134E4A")
        )
        val chosen = palettes.random()
        val newTiles = tiles.mapIndexed { index, slot ->
            slot.copy(
                colorHex = chosen[index % chosen.size],
                fontColorHex = "#FFFFFF",
                iconColorHex = "#FFFFFF"
            )
        }
        return copy(tiles = newTiles, version = System.currentTimeMillis())
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("appearance", appearance.toJson())
        obj.put("tiles", JSONArray().apply { tiles.forEach { put(it.toJson()) } })
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): TileConfig {
            return try {
                val obj = JSONObject(jsonStr)
                val ver = obj.optLong("version", System.currentTimeMillis())
                
                val tilesArr = obj.optJSONArray("tiles")
                val tilesList = mutableListOf<SlotItem>()
                
                if (tilesArr != null) {
                    for (i in 0 until tilesArr.length()) tilesList.add(SlotItem.fromJson(tilesArr.getJSONObject(i)))
                }
                
                TileConfig(
                    tiles = if (tilesList.isNotEmpty()) tilesList else TileConfig().tiles,
                    version = ver,
                    appearance = WatchAppearance.fromJson(obj.optJSONObject("appearance"))
                )
            } catch (_: Exception) {
                TileConfig()
            }
        }
    }
}
