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
import com.quran.watch8.data.model.*
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QuranWatchApplication
    private val quranRepo = app.quranRepository
    private val prefs = app.prefsRepository
    private val appContext = application.applicationContext

    // Quran state
    var isQuranLoaded by mutableStateOf(false)
        private set
    var currentSurahAyahs by mutableStateOf<List<Ayah>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<Ayah>>(emptyList())
        private set
    var surahSearchResults by mutableStateOf<List<SurahInfo>>(emptyList())
        private set

    // Settings
    val fontSize = prefs.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)
    val ayahColor = prefs.ayahNumberColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "yellow")
    val bookmarks = prefs.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val locations = prefs.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val voiceNotes = prefs.voiceNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notificationsEnabled = prefs.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Prayer location (default Buenos Aires CABA)
    val selectedLocationName = prefs.selectedLocationName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "بوينس آيرس (العاصمة)")
    val selectedLat = prefs.selectedLat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -34.6037)
    val selectedLng = prefs.selectedLng.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -58.3816)
    val calculationMethod = prefs.calculationMethod.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ISNA")
    val useGps = prefs.useGps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Prayer
    var prayerTimes by mutableStateOf<PrayerTimesHelper.DayPrayers?>(null)
        private set
    var currentLocation by mutableStateOf<Location?>(null)
        private set
    var isLoadingLocation by mutableStateOf(false)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    // Permissions state
    var hasLocationPermission by mutableStateOf(false)
        private set
    var hasAudioPermission by mutableStateOf(false)
        private set
    var hasNotificationPermission by mutableStateOf(false)
        private set
    var showPermissionRationale by mutableStateOf(false)
        private set

    // Voice recording
    private var mediaRecorder: MediaRecorder? = null
    var isRecording by mutableStateOf(false)
        private set
    private var currentRecordingPath: String? = null

    init {
        checkPermissions()
        // On start: load last selected location (defaults to Buenos Aires) and calculate
        viewModelScope.launch {
            refreshPrayerTimes()
        }
    }

    fun checkPermissions() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    appContext, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        hasAudioPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasNotificationPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        checkPermissions()
        viewModelScope.launch {
            prefs.setPermissionsRequested(true)
            // After granting location, try GPS then fallback to Buenos Aires
            if (hasLocationPermission) {
                fetchCurrentLocation()
            } else {
                // Default to Buenos Aires CABA for Argentina users
                selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
            }
        }
    }

    fun loadQuran() {
        viewModelScope.launch {
            isQuranLoaded = quranRepo.loadQuran()
        }
    }

    fun loadSurah(surahNumber: Int) {
        currentSurahAyahs = quranRepo.getSurahAyahs(surahNumber)
        viewModelScope.launch {
            prefs.saveLastPosition(surahNumber, 1)
        }
    }

    fun searchQuran(query: String) {
        searchResults = quranRepo.searchText(query)
        surahSearchResults = quranRepo.searchSurah(query)
    }

    fun clearSearch() {
        searchResults = emptyList()
        surahSearchResults = emptyList()
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch { prefs.setFontSize(size) }
    }

    fun setAyahColor(color: String) {
        viewModelScope.launch { prefs.setAyahNumberColor(color) }
    }

    fun addBookmark(surah: Int, ayah: Int, text: String) {
        viewModelScope.launch {
            prefs.addBookmark(
                Bookmark(
                    surah = surah,
                    ayah = ayah,
                    textSnippet = text.take(60)
                )
            )
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch { prefs.removeBookmark(id) }
    }

    // ---------- Location & Prayer ----------

    fun selectPreset(preset: LocationPreset) {
        viewModelScope.launch {
            prefs.setSelectedLocation(
                id = preset.id,
                name = preset.nameAr,
                lat = preset.latitude,
                lng = preset.longitude,
                useGps = false
            )
            refreshPrayerTimes(
                lat = preset.latitude,
                lng = preset.longitude,
                name = preset.nameAr
            )
        }
    }

    fun selectGpsLocation() {
        viewModelScope.launch {
            prefs.setUseGps(true)
            fetchCurrentLocation()
        }
    }

    fun setCalculationMethod(method: String) {
        viewModelScope.launch {
            prefs.setCalculationMethod(method)
            refreshPrayerTimes()
        }
    }

    fun refreshPrayerTimes(
        lat: Double? = null,
        lng: Double? = null,
        name: String? = null
    ) {
        viewModelScope.launch {
            val useLat = lat ?: selectedLat.first()
            val useLng = lng ?: selectedLng.first()
            val useName = name ?: selectedLocationName.first()
            val method = calculationMethod.first()
            prayerTimes = PrayerTimesHelper.calculate(
                latitude = useLat,
                longitude = useLng,
                methodName = method,
                locationName = useName
            )
        }
    }

    fun fetchCurrentLocation() {
        if (!hasLocationPermission) {
            locationError = "يحتاج إذن الموقع"
            // Fallback to last selected or Buenos Aires
            refreshPrayerTimes()
            return
        }
        viewModelScope.launch {
            isLoadingLocation = true
            locationError = null
            try {
                val fused = LocationServices.getFusedLocationProviderClient(appContext)
                val loc = fused.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
                if (loc != null) {
                    currentLocation = loc
                    prefs.setSelectedLocation(
                        id = "gps",
                        name = "الموقع الحالي (GPS)",
                        lat = loc.latitude,
                        lng = loc.longitude,
                        useGps = true
                    )
                    refreshPrayerTimes(loc.latitude, loc.longitude, "الموقع الحالي (GPS)")
                } else {
                    // No fix → keep Buenos Aires default
                    locationError = "تعذر الحصول على GPS – استخدام بوينس آيرس"
                    selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                locationError = "خطأ في الموقع – استخدام بوينس آيرس"
                selectPreset(ArgentinaLocations.BUENOS_AIRES_CABA)
            } finally {
                isLoadingLocation = false
            }
        }
    }

    fun saveLocation(name: String, type: LocationType) {
        val lat = selectedLat.value // StateFlow
        val lng = selectedLng.value
        // Prefer live GPS if available
        val loc = currentLocation
        viewModelScope.launch {
            prefs.addLocation(
                SavedLocation(
                    name = name,
                    latitude = loc?.latitude ?: lat,
                    longitude = loc?.longitude ?: lng,
                    type = type
                )
            )
        }
    }

    fun removeLocation(id: String) {
        viewModelScope.launch { prefs.removeLocation(id) }
    }

    // Voice notes
    fun startRecording(context: Context) {
        if (!hasAudioPermission) {
            return
        }
        if (isRecording) return
        try {
            val dir = File(context.filesDir, "voice_notes")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "note_${System.currentTimeMillis()}.m4a")
            currentRecordingPath = file.absolutePath

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    fun stopRecording(title: String = "ملاحظة صوتية") {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            val path = currentRecordingPath ?: return
            viewModelScope.launch {
                prefs.addVoiceNote(
                    VoiceNote(
                        title = title,
                        filePath = path,
                        durationMs = 0
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeVoiceNote(id: String) {
        viewModelScope.launch { prefs.removeVoiceNote(id) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    }

    fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "ابحث في القرآن...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }

    fun handleSpeechResult(results: List<String>?) {
        val best = results?.firstOrNull() ?: return
        searchQuran(best)
    }
}
