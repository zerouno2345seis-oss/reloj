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
        private val LAST_SURAH = intPreferencesKey("last_surah")
        private val LAST_AYAH = intPreferencesKey("last_ayah")
        private val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
        private val LOCATIONS_JSON = stringPreferencesKey("locations_json")
        private val VOICE_NOTES_JSON = stringPreferencesKey("voice_notes_json")
        private val PRAYER_METHOD = stringPreferencesKey("prayer_method")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val CALCULATION_METHOD = stringPreferencesKey("calculation_method") // ISNA default for Argentina
        private val SELECTED_LOCATION_ID = stringPreferencesKey("selected_location_id")
        private val SELECTED_LAT = doublePreferencesKey("selected_lat")
        private val SELECTED_LNG = doublePreferencesKey("selected_lng")
        private val SELECTED_LOCATION_NAME = stringPreferencesKey("selected_location_name")
        private val USE_GPS = booleanPreferencesKey("use_gps")
        private val PERMISSIONS_REQUESTED = booleanPreferencesKey("permissions_requested")
    }

    val fontSize: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 18f }
    val ayahNumberColor: Flow<String> = context.dataStore.data.map { it[AYAH_NUMBER_COLOR] ?: "yellow" }
    val lastPosition: Flow<Pair<Int, Int>> = context.dataStore.data.map {
        (it[LAST_SURAH] ?: 1) to (it[LAST_AYAH] ?: 1)
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    // Default to ISNA for Argentina / Buenos Aires
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
        context.dataStore.edit { it[FONT_SIZE] = size.coerceIn(12f, 32f) }
    }

    suspend fun setAyahNumberColor(color: String) {
        context.dataStore.edit { it[AYAH_NUMBER_COLOR] = color }
    }

    suspend fun saveLastPosition(surah: Int, ayah: Int) {
        context.dataStore.edit {
            it[LAST_SURAH] = surah
            it[LAST_AYAH] = ayah
        }
    }

    // --- Bookmarks ---
    val bookmarks: Flow<List<Bookmark>> = context.dataStore.data.map { prefs ->
        val json = prefs[BOOKMARKS_JSON] ?: "[]"
        try {
            val type = object : TypeToken<List<Bookmark>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        context.dataStore.edit { prefs ->
            val current = getBookmarksSync(prefs)
            val updated = current + bookmark
            prefs[BOOKMARKS_JSON] = gson.toJson(updated)
        }
    }

    suspend fun removeBookmark(id: String) {
        context.dataStore.edit { prefs ->
            val current = getBookmarksSync(prefs)
            prefs[BOOKMARKS_JSON] = gson.toJson(current.filter { it.id != id })
        }
    }

    private fun getBookmarksSync(prefs: Preferences): List<Bookmark> {
        val json = prefs[BOOKMARKS_JSON] ?: "[]"
        return try {
            val type = object : TypeToken<List<Bookmark>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Locations ---
    val locations: Flow<List<SavedLocation>> = context.dataStore.data.map { prefs ->
        val json = prefs[LOCATIONS_JSON] ?: "[]"
        try {
            val type = object : TypeToken<List<SavedLocation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addLocation(location: SavedLocation) {
        context.dataStore.edit { prefs ->
            val current = getLocationsSync(prefs)
            // Keep only one CAR location
            val filtered = if (location.type == com.quran.watch8.data.model.LocationType.CAR) {
                current.filter { it.type != com.quran.watch8.data.model.LocationType.CAR }
            } else current
            prefs[LOCATIONS_JSON] = gson.toJson(filtered + location)
        }
    }

    suspend fun removeLocation(id: String) {
        context.dataStore.edit { prefs ->
            val current = getLocationsSync(prefs)
            prefs[LOCATIONS_JSON] = gson.toJson(current.filter { it.id != id })
        }
    }

    private fun getLocationsSync(prefs: Preferences): List<SavedLocation> {
        val json = prefs[LOCATIONS_JSON] ?: "[]"
        return try {
            val type = object : TypeToken<List<SavedLocation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Voice Notes ---
    val voiceNotes: Flow<List<VoiceNote>> = context.dataStore.data.map { prefs ->
        val json = prefs[VOICE_NOTES_JSON] ?: "[]"
        try {
            val type = object : TypeToken<List<VoiceNote>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addVoiceNote(note: VoiceNote) {
        context.dataStore.edit { prefs ->
            val current = getVoiceNotesSync(prefs)
            prefs[VOICE_NOTES_JSON] = gson.toJson(current + note)
        }
    }

    suspend fun removeVoiceNote(id: String) {
        context.dataStore.edit { prefs ->
            val current = getVoiceNotesSync(prefs)
            prefs[VOICE_NOTES_JSON] = gson.toJson(current.filter { it.id != id })
        }
    }

    private fun getVoiceNotesSync(prefs: Preferences): List<VoiceNote> {
        val json = prefs[VOICE_NOTES_JSON] ?: "[]"
        return try {
            val type = object : TypeToken<List<VoiceNote>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
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
}
