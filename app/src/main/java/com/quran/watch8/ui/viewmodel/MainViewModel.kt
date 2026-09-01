package com.quran.watch8.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.speech.RecognizerIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.quran.watch8.QuranWatchApplication
import com.quran.watch8.data.db.entities.ReadingPositionEntity
import com.quran.watch8.data.model.Bookmark
import com.quran.watch8.data.model.LocationPreset
import com.quran.watch8.data.model.PrayerReminderConfig
import com.quran.watch8.data.model.SavedLocation
import com.quran.watch8.data.model.*
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QuranWatchApplication
    private val quranRepo = app.quranRepository
    private val prefs     = app.prefsRepository
    private val db        = app.databaseRepository
    private val appContext = application.applicationContext

    // ─────────────────────────────── Quran state ───────────────────────────────
    var isQuranLoaded by mutableStateOf(false)
        private set
    var currentSurahAyahs by mutableStateOf<List<Ayah>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<Ayah>>(emptyList())
        private set
    var surahSearchResults by mutableStateOf<List<SurahInfo>>(emptyList())
        private set

    // ─────────────────────────────── Settings (DataStore) ──────────────────────
    val fontSize        = prefs.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)
    val ayahColor       = prefs.ayahNumberColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "yellow")
    val readerBgColor   = prefs.readerBgColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "black")
    val readerTextColor = prefs.readerTextColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "white")
    val customAyahColor = prefs.customAyahColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#FFD60A")
    val customReaderBgColor = prefs.customReaderBgColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#111214")
    val customReaderTextColor = prefs.customReaderTextColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#FFFFFF")
    val fontFamily      = prefs.fontFamily.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")
    val homeStyle       = prefs.homeStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "metro")
    val tilesConfig     = prefs.tilesConfigJson.map { com.quran.watch8.data.model.TileConfig.fromJson(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.quran.watch8.data.model.TileConfig())
    val tilesDisplayMode= prefs.tilesDisplayMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "icons_only")
    val watchFaceConfig = prefs.watchFaceConfigJson.map { com.quran.watch8.data.model.WatchFaceConfig.fromJson(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, com.quran.watch8.data.model.WatchFaceConfig())
    val pinnedApps      = prefs.pinnedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val drawerViewMode  = prefs.drawerViewMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "list")
    val notificationsEnabled = prefs.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Prayer / location
    val selectedLocationName = prefs.selectedLocationName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "بوينس آيرس (العاصمة)")
    val selectedLat  = prefs.selectedLat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -34.6037)
    val selectedLng  = prefs.selectedLng.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -58.3816)
    val calculationMethod = prefs.calculationMethod.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ISNA")
    val useGps       = prefs.useGps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ─────────────────────────────── Room flows ────────────────────────────────
    val bookmarks   = db.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val locations   = db.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val voiceNotes  = db.voiceNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Last saved reading position — null until user has read something. */
    val lastReadingPosition: StateFlow<ReadingPositionEntity?> =
        db.lastReadingPosition.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val prayerReminderConfig: StateFlow<PrayerReminderConfig> = prefs.prayerRemindersJson
        .map { PrayerReminderConfig.fromJson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrayerReminderConfig.DEFAULT)

    // ─────────────────────────────── Prayer ────────────────────────────────────
    var prayerTimes by mutableStateOf<PrayerTimesHelper.DayPrayers?>(null)
        private set
    var currentLocation by mutableStateOf<Location?>(null)
        private set
    var isLoadingLocation by mutableStateOf(false)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    // ─────────────────────────────── Permissions ───────────────────────────────
    var hasLocationPermission by mutableStateOf(false)
        private set
    var hasAudioPermission by mutableStateOf(false)
        private set
    var hasNotificationPermission by mutableStateOf(false)
        private set

    // ─────────────────────────────── Recording ─────────────────────────────────
    private var mediaRecorder: MediaRecorder? = null
    var isRecording by mutableStateOf(false)
        private set
    private var currentRecordingPath: String? = null

    // ─────────────────────────────── Live Watch Info ──────────────────────────
    var batteryPercentage by mutableStateOf(85)
        private set
    var todayDateFormatted by mutableStateOf("")
        private set
    var weatherSummary by mutableStateOf("جاري تحديث الطقس...")
        private set

    // ───────────────────────────────────────────────────────────────────────────
    init {
        checkPermissions()
        initBatteryAndDate()
        viewModelScope.launch { refreshPrayerTimes() }
        refreshWeather()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val lat = currentLocation?.latitude ?: selectedLat.value
            val lng = currentLocation?.longitude ?: selectedLng.value
            weatherSummary = com.quran.watch8.util.WeatherHelper.fetchWeatherSummary(lat, lng)
        }
    }

    private fun initBatteryAndDate() {
        // Battery
        try {
            val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = appContext.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPercentage = ((level / scale.toFloat()) * 100).toInt()
            }
        } catch (_: Exception) {}

        // Date
        val sdf = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale("ar"))
        val easternDate = sdf.format(java.util.Date())
        todayDateFormatted = easternDate
            .replace('٠', '0')
            .replace('١', '1')
            .replace('٢', '2')
            .replace('٣', '3')
            .replace('٤', '4')
            .replace('٥', '5')
            .replace('٦', '6')
            .replace('٧', '7')
            .replace('٨', '8')
            .replace('٩', '9')
    }

    // ═══════════════════════════ Quran ════════════════════════════

    fun loadQuran() {
        viewModelScope.launch { isQuranLoaded = quranRepo.loadQuran() }
    }

    fun loadSurah(surahNumber: Int) {
        currentSurahAyahs = quranRepo.getSurahAyahs(surahNumber)
        viewModelScope.launch { prefs.saveLastPosition(surahNumber, 1) }
    }

    fun searchQuran(query: String) {
        searchResults      = quranRepo.searchText(query)
        surahSearchResults = quranRepo.searchSurah(query)
    }

    fun clearSearch() {
        searchResults      = emptyList()
        surahSearchResults = emptyList()
    }

    // ═══════════════════════════ Settings ════════════════════════

    fun setFontSize(size: Float)       { viewModelScope.launch { prefs.setFontSize(size) } }
    fun setAyahColor(color: String)    { viewModelScope.launch { prefs.setAyahNumberColor(color) } }
    fun setReaderBgColor(color: String){ viewModelScope.launch { prefs.setReaderBgColor(color) } }
    fun setReaderTextColor(color: String){ viewModelScope.launch { prefs.setReaderTextColor(color) } }
    fun setCustomAyahColor(color: String){ viewModelScope.launch { prefs.setCustomAyahColor(color) } }
    fun setCustomReaderBgColor(color: String){ viewModelScope.launch { prefs.setCustomReaderBgColor(color) } }
    fun setCustomReaderTextColor(color: String){ viewModelScope.launch { prefs.setCustomReaderTextColor(color) } }
    fun setFontFamily(name: String)    { viewModelScope.launch { prefs.setFontFamily(name) } }
    fun setHomeStyle(style: String)    { viewModelScope.launch { prefs.setHomeStyle(style) } }
    fun setTileConfig(config: com.quran.watch8.data.model.TileConfig) { viewModelScope.launch { prefs.setTilesConfigJson(config.toJson()) } }
    fun setTilesDisplayMode(mode: String) { viewModelScope.launch { prefs.setTilesDisplayMode(mode) } }
    fun setWatchFaceConfig(config: com.quran.watch8.data.model.WatchFaceConfig) { viewModelScope.launch { prefs.setWatchFaceConfigJson(config.toJson()) } }
    fun setComplicationSlot(slot: String, type: com.quran.watch8.data.model.ComplicationType) {
        val current = watchFaceConfig.value
        val updated = when (slot) {
            "top" -> current.copy(topSlot = type)
            "right" -> current.copy(rightSlot = type)
            "left" -> current.copy(leftSlot = type)
            "bottom" -> current.copy(bottomSlot = type)
            else -> current
        }
        setWatchFaceConfig(updated)
    }
    fun cycleComplicationSlot(slot: String) {
        val current = watchFaceConfig.value
        val updated = when (slot) {
            "top" -> current.copy(topSlot = current.topSlot.next())
            "right" -> current.copy(rightSlot = current.rightSlot.next())
            "left" -> current.copy(leftSlot = current.leftSlot.next())
            "bottom" -> current.copy(bottomSlot = current.bottomSlot.next())
            else -> current
        }
        setWatchFaceConfig(updated)
    }
    fun setWatchFaceModel(model: com.quran.watch8.data.model.WatchFaceModelId) {
        viewModelScope.launch {
            val current = prefs.watchFaceConfigJson.first().let { com.quran.watch8.data.model.WatchFaceConfig.fromJson(it) }
            prefs.setWatchFaceConfigJson(current.copy(modelId = model).toJson())
        }
    }
    fun togglePinnedApp(pkg: String) {
        viewModelScope.launch { prefs.togglePinnedApp(pkg) }
    }
    fun setDrawerViewMode(mode: String) {
        viewModelScope.launch { prefs.setDrawerViewMode(mode) }
    }
    fun setNotifications(enabled: Boolean){
        viewModelScope.launch {
            prefs.setNotificationsEnabled(enabled)
            if (enabled) com.quran.watch8.util.PrayerAlarmScheduler.scheduleAll(appContext)
            else com.quran.watch8.util.PrayerAlarmScheduler.cancelAll(appContext)
        }
    }

    fun setPrayerReminderConfig(config: PrayerReminderConfig) {
        viewModelScope.launch {
            prefs.setPrayerRemindersJson(config.toJson())
            com.quran.watch8.util.PrayerAlarmScheduler.scheduleAll(appContext)
        }
    }

    // ═══════════════════════════ Bookmarks (Room) ═════════════════

    fun addBookmark(surah: Int, ayah: Int, text: String) {
        viewModelScope.launch {
            db.addBookmark(
                Bookmark(surah = surah, ayah = ayah, textSnippet = text.take(60))
            )
        }
    }

    fun removeBookmark(id: String) { viewModelScope.launch { db.removeBookmark(id) } }

    // ═══════════════════════════ Reading Position (Room) ══════════

    /**
     * Called from QuranReaderScreen whenever the visible ayah changes.
     * [ayahIndex] is the 0-based index of the currently centred item in the ScalingLazyColumn.
     * [ayahNumber] is the 1-based verse number.
     * [ayahSnippet] is the first 2-3 words of the verse.
     */
    fun saveReadingPosition(
        surah: Int,
        ayahIndex: Int,
        ayahNumber: Int,
        surahNameAr: String,
        ayahSnippet: String
    ) {
        viewModelScope.launch {
            db.saveReadingPosition(surah, ayahIndex, ayahNumber, surahNameAr, ayahSnippet)
        }
    }

    // ═══════════════════════════ Location & Prayer ════════════════

    fun selectPreset(preset: LocationPreset) {
        viewModelScope.launch {
            prefs.setSelectedLocation(preset.id, preset.nameAr, preset.latitude, preset.longitude, useGps = false)
            refreshPrayerTimes(preset.latitude, preset.longitude, preset.nameAr)
        }
    }

    fun selectGpsLocation() {
        viewModelScope.launch { prefs.setUseGps(true); fetchCurrentLocation() }
    }

    fun setCalculationMethod(method: String) {
        viewModelScope.launch { prefs.setCalculationMethod(method); refreshPrayerTimes() }
    }

    fun refreshPrayerTimes(lat: Double? = null, lng: Double? = null, name: String? = null) {
        viewModelScope.launch {
            val useLat    = lat  ?: selectedLat.first()
            val useLng    = lng  ?: selectedLng.first()
            val useName   = name ?: selectedLocationName.first()
            val method    = calculationMethod.first()
            prayerTimes = PrayerTimesHelper.calculate(useLat, useLng, method, locationName = useName)
            com.quran.watch8.util.PrayerAlarmScheduler.scheduleAll(appContext)
        }
    }

    fun fetchCurrentLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            locationError = "يحتاج إذن الموقع"
            refreshPrayerTimes()
            return
        }
        viewModelScope.launch {
            isLoadingLocation = true; locationError = null
            try {
                val fused = LocationServices.getFusedLocationProviderClient(appContext)
                val loc   = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                if (loc != null) {
                    currentLocation = loc
                    prefs.setSelectedLocation("gps", "الموقع الحالي (GPS)", loc.latitude, loc.longitude, true)
                    refreshPrayerTimes(loc.latitude, loc.longitude, "الموقع الحالي (GPS)")
                } else {
                    locationError = "تعذر GPS – استخدام بوينس آيرس"
                    selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
                }
            } catch (e: Exception) {
                locationError = "خطأ في الموقع – استخدام بوينس آيرس"
                selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
            } finally {
                isLoadingLocation = false
            }
        }
    }

    // ═══════════════════════════ Saved Locations (Room) ══════════

    fun saveLocation(name: String, type: LocationType) {
        val loc = currentLocation
        val lat = loc?.latitude  ?: selectedLat.value
        val lng = loc?.longitude ?: selectedLng.value
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val address = try {
                val geocoder = android.location.Geocoder(appContext, java.util.Locale("ar"))
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val street = addr.thoroughfare ?: addr.featureName ?: ""
                    val number = addr.subThoroughfare ?: ""
                    val subLocality = addr.subLocality ?: addr.locality ?: ""
                    buildString {
                        if (street.isNotBlank()) append(street)
                        if (number.isNotBlank()) append(" $number")
                        if (subLocality.isNotBlank()) {
                            if (isNotEmpty()) append("، ")
                            append(subLocality)
                        }
                    }.ifBlank { "موقع تقريبي" }
                } else {
                    "موقع تقريبي"
                }
            } catch (e: Exception) {
                "موقع تقريبي"
            }

            db.addLocation(
                SavedLocation(
                    name      = name,
                    address   = address,
                    latitude  = lat,
                    longitude = lng,
                    type      = type
                )
            )
        }
    }

    fun updateLocationName(id: String, newName: String) {
        viewModelScope.launch { db.updateLocationName(id, newName) }
    }

    fun removeLocation(id: String) { viewModelScope.launch { db.removeLocation(id) } }

    // ═══════════════════════════ Voice Notes (Room) ═══════════════

    fun startRecording(context: Context) {
        if (!hasAudioPermission || isRecording) return
        try {
            val dir  = File(context.filesDir, "voice_notes").also { if (!it.exists()) it.mkdirs() }
            val file = File(dir, "note_${System.currentTimeMillis()}.m4a")
            currentRecordingPath = file.absolutePath
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare(); start()
            }
            isRecording = true
        } catch (e: Exception) { e.printStackTrace(); isRecording = false }
    }

    fun stopRecording(title: String = "ملاحظة صوتية", transcription: String? = null): String? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null; isRecording = false
            val path = currentRecordingPath
            if (path != null) {
                val noteId = UUID.randomUUID().toString()
                viewModelScope.launch {
                    db.addVoiceNote(
                        VoiceNote(
                            id = noteId,
                            title = title,
                            filePath = path,
                            transcription = transcription ?: ""
                        )
                    )
                }
                noteId
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            isRecording = false
            null
        }
    }

    fun updateVoiceNoteTranscription(id: String, transcription: String) {
        viewModelScope.launch {
            db.updateVoiceNoteTranscription(id, transcription)
        }
    }

    fun addVoiceNote(title: String, transcription: String) {
        viewModelScope.launch {
            db.addVoiceNote(
                VoiceNote(
                    title = title,
                    filePath = "",
                    transcription = transcription
                )
            )
        }
    }

    fun removeVoiceNote(id: String) { viewModelScope.launch { db.removeVoiceNote(id) } }

    // ═══════════════════════════ Speech ══════════════════════════

    fun createSpeechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "ابحث في القرآن...")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }

    fun handleSpeechResult(results: List<String>?) {
        val best = results?.firstOrNull() ?: return
        searchQuran(best)
    }

    // ═══════════════════════════ Permissions ══════════════════════

    fun checkPermissions() {
        hasLocationPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasNotificationPermission = if (android.os.Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        checkPermissions()
        viewModelScope.launch {
            prefs.setPermissionsRequested(true)
            if (hasLocationPermission) fetchCurrentLocation()
            else selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
        }
    }
}
