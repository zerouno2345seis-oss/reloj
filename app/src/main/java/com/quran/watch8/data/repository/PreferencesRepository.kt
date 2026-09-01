package com.quran.watch8.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quran.watch8.data.model.Bookmark
import com.quran.watch8.data.model.SavedLocation
import com.quran.watch8.data.model.VoiceNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quran_watch_prefs")

class PreferencesRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val FONT_SIZE = floatPreferencesKey("font_size")
        private val AYAH_NUMBER_COLOR = stringPreferencesKey("ayah_number_color") // "yellow" or "green"
        private val READER_BG_COLOR = stringPreferencesKey("reader_bg_color")     // "black", "navy", "sepia", "forest"
        private val READER_TEXT_COLOR = stringPreferencesKey("reader_text_color") // "white", "ivory", "mint", "golden"
        private val CUSTOM_AYAH_COLOR = stringPreferencesKey("custom_ayah_color")
        private val CUSTOM_READER_BG_COLOR = stringPreferencesKey("custom_reader_bg_color")
        private val CUSTOM_READER_TEXT_COLOR = stringPreferencesKey("custom_reader_text_color")
        private val LAST_SURAH = intPreferencesKey("last_surah")
        private val LAST_AYAH = intPreferencesKey("last_ayah")
        private val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
        private val LOCATIONS_JSON = stringPreferencesKey("locations_json")
        private val VOICE_NOTES_JSON = stringPreferencesKey("voice_notes_json")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        private val SELECTED_LOCATION_ID = stringPreferencesKey("selected_location_id")
        private val SELECTED_LAT = doublePreferencesKey("selected_lat")
        private val SELECTED_LNG = doublePreferencesKey("selected_lng")
        private val SELECTED_LOCATION_NAME = stringPreferencesKey("selected_location_name")
        private val USE_GPS = booleanPreferencesKey("use_gps")
        private val PERMISSIONS_REQUESTED = booleanPreferencesKey("permissions_requested")
        private val FONT_FAMILY = stringPreferencesKey("font_family")  // "default", "uthmani", "kufi", "sansserif", "serif"
        private val HOME_STYLE = stringPreferencesKey("home_style")    // "metro", "dashboard"
        private val TILES_CONFIG_JSON = stringPreferencesKey("tiles_config_json")
        private val TILES_DISPLAY_MODE = stringPreferencesKey("tiles_display_mode") // "icons_only", "icons_and_text"
        private val PRAYER_REMINDERS_JSON = stringPreferencesKey("prayer_reminders_json")
    }

    val fontSize: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 18f }
    val ayahNumberColor: Flow<String> = context.dataStore.data.map { it[AYAH_NUMBER_COLOR] ?: "yellow" }
    val readerBgColor: Flow<String> = context.dataStore.data.map { it[READER_BG_COLOR] ?: "black" }
    val readerTextColor: Flow<String> = context.dataStore.data.map { it[READER_TEXT_COLOR] ?: "white" }
    val customAyahColor: Flow<String> = context.dataStore.data.map { it[CUSTOM_AYAH_COLOR] ?: "#FFD60A" }
    val customReaderBgColor: Flow<String> = context.dataStore.data.map { it[CUSTOM_READER_BG_COLOR] ?: "#111214" }
    val customReaderTextColor: Flow<String> = context.dataStore.data.map { it[CUSTOM_READER_TEXT_COLOR] ?: "#FFFFFF" }
    val fontFamily: Flow<String> = context.dataStore.data.map { it[FONT_FAMILY] ?: "default" }
    val homeStyle: Flow<String> = context.dataStore.data.map { it[HOME_STYLE] ?: "metro" }
    val tilesConfigJson: Flow<String> = context.dataStore.data.map { it[TILES_CONFIG_JSON] ?: "" }
    val tilesDisplayMode: Flow<String> = context.dataStore.data.map { it[TILES_DISPLAY_MODE] ?: "icons_only" }
    val prayerRemindersJson: Flow<String> = context.dataStore.data.map { it[PRAYER_REMINDERS_JSON] ?: "" }

    val lastPosition: Flow<Pair<Int, Int>> = context.dataStore.data.map {
        (it[LAST_SURAH] ?: 1) to (it[LAST_AYAH] ?: 1)
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val calculationMethod: Flow<String> = context.dataStore.data.map { it[CALCULATION_METHOD] ?: "ISNA" }
    val selectedLocationId: Flow<String> = context.dataStore.data.map { it[SELECTED_LOCATION_ID] ?: "ba_caba" }
    val selectedLat: Flow<Double> = context.dataStore.data.map { it[SELECTED_LAT] ?: -34.6037 }
    val selectedLng: Flow<Double> = context.dataStore.data.map { it[SELECTED_LNG] ?: -58.3816 }
    val selectedLocationName: Flow<String> = context.dataStore.data.map {
        it[SELECTED_LOCATION_NAME] ?: "بوينس آيرس (العاصمة)"
    }
    val useGps: Flow<Boolean> = context.dataStore.data.map { it[USE_GPS] ?: true }
    val permissionsRequested: Flow<Boolean> = context.dataStore.data.map { it[PERMISSIONS_REQUESTED] ?: false }

    suspend fun setFontSize(size: Float) {
        context.dataStore.edit { it[FONT_SIZE] = size.coerceIn(8f, 48f) }
    }

    suspend fun setAyahNumberColor(color: String) {
        context.dataStore.edit { it[AYAH_NUMBER_COLOR] = color }
    }

    suspend fun setReaderBgColor(color: String) {
        context.dataStore.edit { it[READER_BG_COLOR] = color }
    }

    suspend fun setReaderTextColor(color: String) {
        context.dataStore.edit { it[READER_TEXT_COLOR] = color }
    }

    suspend fun setCustomAyahColor(color: String) {
        context.dataStore.edit { it[CUSTOM_AYAH_COLOR] = color }
    }

    suspend fun setCustomReaderBgColor(color: String) {
        context.dataStore.edit { it[CUSTOM_READER_BG_COLOR] = color }
    }

    suspend fun setCustomReaderTextColor(color: String) {
        context.dataStore.edit { it[CUSTOM_READER_TEXT_COLOR] = color }
    }

    suspend fun setFontFamily(name: String) {
        context.dataStore.edit { it[FONT_FAMILY] = name }
    }

    suspend fun setHomeStyle(style: String) {
        context.dataStore.edit { it[HOME_STYLE] = style }
    }

    suspend fun setTilesConfigJson(json: String) {
        context.dataStore.edit { it[TILES_CONFIG_JSON] = json }
    }

    suspend fun setTilesDisplayMode(mode: String) {
        context.dataStore.edit { it[TILES_DISPLAY_MODE] = mode }
    }

    suspend fun saveLastPosition(surah: Int, ayah: Int) {
        context.dataStore.edit {
            it[LAST_SURAH] = surah
            it[LAST_AYAH] = ayah
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setCalculationMethod(method: String) {
        context.dataStore.edit { it[CALCULATION_METHOD] = method }
    }

    suspend fun setSelectedLocation(
        id: String,
        name: String,
        lat: Double,
        lng: Double,
        useGps: Boolean = false
    ) {
        context.dataStore.edit {
            it[SELECTED_LOCATION_ID] = id
            it[SELECTED_LOCATION_NAME] = name
            it[SELECTED_LAT] = lat
            it[SELECTED_LNG] = lng
            it[USE_GPS] = useGps
        }
    }

    suspend fun setUseGps(use: Boolean) {
        context.dataStore.edit { it[USE_GPS] = use }
    }

    suspend fun setPermissionsRequested(requested: Boolean) {
        context.dataStore.edit { it[PERMISSIONS_REQUESTED] = requested }
    }

    suspend fun setPrayerRemindersJson(json: String) {
        context.dataStore.edit { it[PRAYER_REMINDERS_JSON] = json }
    }
}
