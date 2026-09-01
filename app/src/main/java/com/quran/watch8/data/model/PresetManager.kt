package com.quran.watch8.data.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class PresetItem(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val config: TileConfig,
    val isCustom: Boolean = false
)

object PresetManager {

    private const val PREFS_NAME = "quran_watch_presets"
    private const val KEY_CUSTOM_PRESETS = "custom_presets_json"

    val DEFAULT_PRESETS = listOf(
        PresetItem(
            id = "preset_prayer_strip",
            title = "المواقيت المدمج",
            icon = "🕌",
            description = "جدول مواقيت الصلاة الكاملة مع الساعة والعد التنازلي",
            config = TileConfig(
                tiles = listOf(
                    SlotItem(id = "clock_big", colorHex = "#6366F1", x = 0f, y = 0f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 0, fontSize = 20, displayStyle = "text"),
                    SlotItem(id = "prayer_countdown", colorHex = "#10B981", x = 50f, y = 0f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 0, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "prayer_strip_5", colorHex = "#047857", x = 0f, y = 33.33f, width = 100f, height = 33.33f, colSpan = 12, rowIndex = 1, fontSize = 12, displayStyle = "prayer_strip_5"),
                    SlotItem(id = "folder_islamic", colorHex = "#0284C7", folderItems = listOf("quran", "tasbih", "qibla", "prayer"), x = 0f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "qibla", colorHex = "#D97706", x = 50f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text")
                )
            )
        ),
        PresetItem(
            id = "preset_quran_focus",
            title = "المصحف والختمة",
            icon = "📖",
            description = "متابعة القراءة والورد اليومي مع التسبيح والعلامات",
            config = TileConfig(
                tiles = listOf(
                    SlotItem(id = "clock_big", colorHex = "#4F46E5", x = 0f, y = 0f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 0, fontSize = 16, displayStyle = "text"),
                    SlotItem(id = "quran_resume", colorHex = "#0E7490", x = 33.33f, y = 0f, width = 66.67f, height = 33.33f, colSpan = 8, rowIndex = 0, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "folder_islamic", colorHex = "#0284C7", folderItems = listOf("quran", "tasbih", "qibla", "prayer"), x = 0f, y = 33.33f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "tasbih", colorHex = "#059669", x = 50f, y = 33.33f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "bookmarks", colorHex = "#D97706", x = 0f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "settings", colorHex = "#334155", x = 50f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text")
                )
            )
        ),
        PresetItem(
            id = "preset_big_clock",
            title = "الساعة الكلاسيكي",
            icon = "⏰",
            description = "ساعة رقمية كبيرة بارزة مع التاريخ والمواقيت",
            config = TileConfig(
                tiles = listOf(
                    SlotItem(id = "clock_big", colorHex = "#7C3AED", x = 0f, y = 0f, width = 100f, height = 33.33f, colSpan = 12, rowIndex = 0, fontSize = 24, displayStyle = "text"),
                    SlotItem(id = "date_big", colorHex = "#0284C7", x = 0f, y = 33.33f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "prayer_countdown", colorHex = "#10B981", x = 50f, y = 33.33f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "folder_islamic", colorHex = "#0E7490", folderItems = listOf("quran", "tasbih", "qibla", "prayer"), x = 0f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "settings", colorHex = "#334155", x = 50f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text")
                )
            )
        ),
        PresetItem(
            id = "preset_smart_tools",
            title = "الأدوات الذكية",
            icon = "📁",
            description = "المجلدات الذكية، التسجيل الصوتي، والمواقع",
            config = TileConfig(
                tiles = listOf(
                    SlotItem(id = "clock_big", colorHex = "#6366F1", x = 0f, y = 0f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 0, fontSize = 18, displayStyle = "text"),
                    SlotItem(id = "prayer_countdown", colorHex = "#10B981", x = 50f, y = 0f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 0, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "folder_islamic", colorHex = "#0284C7", folderItems = listOf("quran", "tasbih", "qibla", "prayer"), x = 0f, y = 33.33f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "folder_tools", colorHex = "#EA580C", folderItems = listOf("voice_notes", "bookmarks", "locations", "settings"), x = 50f, y = 33.33f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "voice_notes", colorHex = "#E11D48", x = 0f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "settings", colorHex = "#334155", x = 50f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text")
                )
            )
        ),
        PresetItem(
            id = "preset_color_accent",
            title = "الجمالي الملون",
            icon = "🎨",
            description = "بلاطات لونية تزيينية راقية مع لمسات هادئة",
            config = TileConfig(
                tiles = listOf(
                    SlotItem(id = "color_only", colorHex = "#EC4899", x = 0f, y = 0f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 0, displayStyle = "color_only"),
                    SlotItem(id = "clock_big", colorHex = "#8B5CF6", x = 33.33f, y = 0f, width = 66.67f, height = 33.33f, colSpan = 8, rowIndex = 0, fontSize = 20, displayStyle = "text"),
                    SlotItem(id = "quran_resume", colorHex = "#06B6D4", x = 0f, y = 33.33f, width = 66.67f, height = 33.33f, colSpan = 8, rowIndex = 1, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "color_only", colorHex = "#F59E0B", x = 66.67f, y = 33.33f, width = 33.33f, height = 33.33f, colSpan = 4, rowIndex = 1, displayStyle = "color_only"),
                    SlotItem(id = "tasbih", colorHex = "#10B981", x = 0f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text"),
                    SlotItem(id = "settings", colorHex = "#334155", x = 50f, y = 66.67f, width = 50f, height = 33.33f, colSpan = 6, rowIndex = 2, fontSize = 14, displayStyle = "text")
                )
            )
        )
    )

    fun getAllPresets(context: Context): List<PresetItem> {
        val list = mutableListOf<PresetItem>()
        list.addAll(DEFAULT_PRESETS)
        list.addAll(getCustomPresets(context))
        return list
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCustomPresets(context: Context): List<PresetItem> {
        val jsonStr = getPrefs(context).getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        val list = mutableListOf<PresetItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val icon = obj.optString("icon", "⭐")
                val desc = obj.optString("description", "قالب مخصص")
                val config = TileConfig.fromJson(obj.getJSONObject("config").toString())
                list.add(PresetItem(id, title, icon, desc, config, isCustom = true))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomPreset(context: Context, title: String, icon: String, config: TileConfig): PresetItem {
        val list = getCustomPresets(context).toMutableList()
        val newPreset = PresetItem(
            id = "custom_" + System.currentTimeMillis(),
            title = title,
            icon = icon,
            description = "تم حفظه بواسطة المستخدم",
            config = config,
            isCustom = true
        )
        list.add(newPreset)

        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("icon", p.icon)
                put("description", p.description)
                put("config", JSONObject(p.config.toJson()))
            }
            arr.put(obj)
        }

        getPrefs(context).edit().putString(KEY_CUSTOM_PRESETS, arr.toString()).apply()
        return newPreset
    }

    fun applyPreset(context: Context, presetId: String): TileConfig? {
        val preset = getAllPresets(context).find { it.id == presetId } ?: return null
        val newConfig = preset.config.copy(version = System.currentTimeMillis())
        
        kotlinx.coroutines.runBlocking {
            com.quran.watch8.data.repository.PreferencesRepository(context).setTilesConfigJson(newConfig.toJson())
        }
        
        return newConfig
    }
}
