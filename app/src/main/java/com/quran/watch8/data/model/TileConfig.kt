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
        TileActionItem("clock_big", "⏰ الساعة الرقمية", "⏰", ""),
        TileActionItem("prayer_countdown", "⏳ متبقي الصلاة القادمة", "⏳", "prayer"),
        TileActionItem("prayer_elapsed", "⌛ الوقت المنقضي على الصلاة", "⌛", "prayer"),
        TileActionItem("prayer", "🕌 مواقيت الصلاة اليومية", "🕌", "prayer"),
        TileActionItem("prayer_strip_5", "🕌 جدول المواقيت الـ 5 المدمج", "🕌", "prayer"),
        TileActionItem("auto_layout", "✦ ترتيب تلقائي جديد", "✦", ""),
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
        SlotItem(id = "clock_big", colorHex = "#7C3AED", isLive = true, subActions = listOf("clock_big", "date_big"), x = 0f, y = 0f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 0, fontSize = 24, displayStyle = "text"),
        SlotItem(id = "prayer_countdown", colorHex = "#10B981", isLive = true, subActions = listOf("prayer_countdown", "prayer", "prayer_elapsed"), x = 50f, y = 0f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 0, fontSize = 14, displayStyle = "text"),
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
