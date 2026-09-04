package com.quran.watch8.util

import android.content.Context
import android.util.Log
import com.quran.watch8.data.db.QuranDatabase
import com.quran.watch8.data.db.entities.BookmarkEntity
import com.quran.watch8.data.db.entities.ReadingPositionEntity
import com.quran.watch8.data.db.entities.SavedLocationEntity
import com.quran.watch8.data.model.TileConfig
import com.quran.watch8.data.repository.DatabaseRepository
import com.quran.watch8.data.repository.PreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.util.*

/**
 * Cloud sync relay for the web studio.
 *
 * There used to be a second, local channel here: a ServerSocket on port 41331
 * that the studio would POST to directly over the LAN. It could never work —
 * the studio is served over HTTPS, so the browser blocks a plain-http request
 * to the watch as mixed content — and its `accept()` loop held a thread and a
 * listening socket open for the entire life of the process. Since this app
 * declares `category.HOME`, that life is "until the watch reboots". Removed;
 * the HTTPS relay below is the only channel.
 */
object LocalSyncServer {
    private const val TAG = "LocalSyncServer"
    /** Marks a pull result that actually changed local state. */
    const val APPLIED_PREFIX = "APPLIED:"
    const val CLOUD_RELAY_URL = "https://quran-watch8-hub.vercel.app/api/sync?code=41331"

    private var lastSyncedVersion: Long = 0L

    suspend fun syncWithCloud(context: Context, mode: String = "pull"): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val db = QuranDatabase.getInstance(context)
            val dbRepo = DatabaseRepository(
                bookmarkDao = db.bookmarkDao(),
                locationDao = db.savedLocationDao(),
                voiceNoteDao = db.voiceNoteDao(),
                readingPositionDao = db.readingPositionDao()
            )
            val prefs = PreferencesRepository(context)

            val url = URL(CLOUD_RELAY_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (mode == "push") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true

                val payload = exportDataJson(dbRepo, prefs)
                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    Pair(true, "✓ تم إرسال البيانات للسحابة بنجاح")
                } else {
                    Pair(false, "فشل الرفع السحابي ($code)")
                }
            } else {
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val root = JSONObject(body)
                    val dataObj = root.optJSONObject("data") ?: root
                    val remoteVer = dataObj.optJSONObject("tilesConfig")?.optLong("version", System.currentTimeMillis()) ?: dataObj.optLong("version", System.currentTimeMillis())
                    if (remoteVer > lastSyncedVersion) {
                        lastSyncedVersion = remoteVer
                        importDataJson(dataObj.toString(), dbRepo, prefs)
                        Log.i(TAG, "applied cloud config version $remoteVer")
                        // APPLIED_PREFIX lets the caller tell "something changed"
                        // from "checked, nothing new" and only speak up for the first.
                        Pair(true, "${APPLIED_PREFIX}✓ تم تطبيق التصميم من السحابة")
                    } else {
                        Pair(true, "لا جديد في السحابة")
                    }
                } else {
                    conn.disconnect()
                    Pair(false, "تعذر الجلب السحابي ($code)")
                }
            }
        } catch (e: Exception) {
            Pair(false, "خطأ بالاتصال: ${e.localizedMessage}")
        }
    }

    private suspend fun exportDataJson(dbRepo: DatabaseRepository, prefs: PreferencesRepository): String {
        val root = JSONObject()
        val version = System.currentTimeMillis()
        root.put("version", version)
        // Our own upload must not come back as "news" on the next pull.
        lastSyncedVersion = version

        val tilesJson = prefs.tilesConfigJson.first()
        if (tilesJson.isNotBlank()) {
            runCatching { root.put("tilesConfig", JSONObject(tilesJson)) }
        }

        val wfJson = prefs.watchFaceConfigJson.first()
        if (wfJson.isNotBlank()) {
            runCatching { root.put("watchFaceConfig", JSONObject(wfJson)) }
        }

        val settings = JSONObject().apply {
            put("fontSize", prefs.fontSize.first())
            put("ayahColor", prefs.ayahNumberColor.first())
            put("fontFamily", prefs.fontFamily.first())
            put("readerBgColor", prefs.readerBgColor.first())
            put("readerTextColor", prefs.readerTextColor.first())
            put("customAyahColor", prefs.customAyahColor.first())
            put("customReaderBgColor", prefs.customReaderBgColor.first())
            put("customReaderTextColor", prefs.customReaderTextColor.first())
            put("notificationsEnabled", prefs.notificationsEnabled.first())
            put("calculationMethod", prefs.calculationMethod.first())
            val remindersJson = prefs.prayerRemindersJson.first()
            put("prayerReminders", runCatching { JSONObject(remindersJson) }.getOrElse { JSONObject() })
        }
        root.put("settings", settings)

        val bookmarksList = dbRepo.bookmarks.first()
        val bmArr = JSONArray()
        bookmarksList.forEach { bm ->
            bmArr.put(JSONObject().apply {
                put("id", bm.id)
                put("surah", bm.surah)
                put("ayahNumber", bm.ayah)
                put("surahNameAr", com.quran.watch8.data.model.SurahMetadata.getSurah(bm.surah)?.nameAr ?: "سورة ${bm.surah}")
                put("ayahText", bm.textSnippet)
                put("note", bm.note ?: "")
                put("createdAt", bm.timestamp)
            })
        }
        root.put("bookmarks", bmArr)

        val locationsList = dbRepo.locations.first()
        val locArr = JSONArray()
        locationsList.forEach { loc ->
            locArr.put(JSONObject().apply {
                put("id", loc.id)
                put("name", loc.name)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("isDefault", false)
            })
        }
        root.put("locations", locArr)

        return root.toString()
    }

    suspend fun importDataJson(jsonStr: String, dbRepo: DatabaseRepository, prefs: PreferencesRepository) {
        val root = JSONObject(jsonStr)
        // When the payload was built. A watch-local entry created after this is
        // never deleted by a reconcile below.
        val payloadVersion = root.optLong("version", System.currentTimeMillis())

        val tilesObj = root.optJSONObject("tilesConfig") ?: root.optJSONObject("tiles")
        if (tilesObj != null) {
            // Only replace the watch's layout when the payload actually carries a
            // non-empty tiles array. Otherwise TileConfig.fromJson() falls back to
            // its 6-tile default and we would silently wipe the user's design.
            val incomingTiles = tilesObj.optJSONArray("tiles")
            if (incomingTiles != null && incomingTiles.length() > 0) {
                val config = TileConfig.fromJson(tilesObj.toString())
                if (config.isValid()) {
                    prefs.setTilesConfigJson(config.toJson())
                    Log.i(TAG, "applied ${config.tiles.size} tiles from cloud sync")
                }
            } else {
                Log.w(TAG, "cloud payload had no tiles array; keeping current watch layout")
            }
        }

        val wfObj = root.optJSONObject("watchFaceConfig") ?: root.optJSONObject("watchFace")
        if (wfObj != null) {
            val modelId = wfObj.optString("selectedModel", wfObj.optString("modelId", ""))
            val topSlot = wfObj.optString("topSlot", "")
            val rightSlot = wfObj.optString("rightSlot", "")
            val leftSlot = wfObj.optString("leftSlot", "")
            val bottomSlot = wfObj.optString("bottomSlot", "")

            val current = prefs.watchFaceConfigJson.first().let { 
                com.quran.watch8.data.model.WatchFaceConfig.fromJson(it) 
            }

            val selectedModel = if (modelId.isNotBlank()) runCatching {
                com.quran.watch8.data.model.WatchFaceModelId.valueOf(modelId)
            }.getOrDefault(current.modelId) else current.modelId
            var updated = current.withModel(selectedModel)
            val profileObject = wfObj.optJSONObject("slotProfiles")
            if (profileObject != null) {
                val incomingProfiles = buildMap {
                    profileObject.keys().forEach { id ->
                        val item = profileObject.optJSONObject(id) ?: return@forEach
                        val model = runCatching { com.quran.watch8.data.model.WatchFaceModelId.valueOf(id) }.getOrNull() ?: return@forEach
                        val fallback = com.quran.watch8.data.model.WatchFaceConfig.defaultSlotsFor(model)
                        fun slot(longKey: String, shortKey: String, default: com.quran.watch8.data.model.ComplicationType): com.quran.watch8.data.model.ComplicationType {
                            val raw = item.optString(longKey, item.optString(shortKey, default.name))
                            return runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(raw) }.getOrDefault(default)
                        }
                        put(id, com.quran.watch8.data.model.ComplicationSlots(
                            top = slot("topSlot", "top", fallback.top), right = slot("rightSlot", "right", fallback.right),
                            left = slot("leftSlot", "left", fallback.left), bottom = slot("bottomSlot", "bottom", fallback.bottom)
                        ))
                    }
                }
                updated = updated.copy(slotProfiles = updated.slotProfiles + incomingProfiles).withModel(selectedModel)
            }
            if (topSlot.isNotBlank()) updated = updated.withSlot("top", runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(topSlot) }.getOrDefault(updated.topSlot))
            if (rightSlot.isNotBlank()) updated = updated.withSlot("right", runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(rightSlot) }.getOrDefault(updated.rightSlot))
            if (leftSlot.isNotBlank()) updated = updated.withSlot("left", runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(leftSlot) }.getOrDefault(updated.leftSlot))
            if (bottomSlot.isNotBlank()) updated = updated.withSlot("bottom", runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(bottomSlot) }.getOrDefault(updated.bottomSlot))
            prefs.setWatchFaceConfigJson(updated.toJson())
        }

        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            if (settingsObj.has("fontSize")) prefs.setFontSize(settingsObj.getDouble("fontSize").toFloat())
            if (settingsObj.has("ayahColor")) prefs.setAyahNumberColor(settingsObj.getString("ayahColor"))
            if (settingsObj.has("fontFamily")) prefs.setFontFamily(settingsObj.getString("fontFamily"))
            if (settingsObj.has("readerBgColor")) prefs.setReaderBgColor(settingsObj.getString("readerBgColor"))
            if (settingsObj.has("readerTextColor")) prefs.setReaderTextColor(settingsObj.getString("readerTextColor"))
            if (settingsObj.has("customAyahColor")) prefs.setCustomAyahColor(settingsObj.getString("customAyahColor"))
            if (settingsObj.has("customReaderBgColor")) prefs.setCustomReaderBgColor(settingsObj.getString("customReaderBgColor"))
            if (settingsObj.has("customReaderTextColor")) prefs.setCustomReaderTextColor(settingsObj.getString("customReaderTextColor"))
            if (settingsObj.has("notificationsEnabled")) prefs.setNotificationsEnabled(settingsObj.getBoolean("notificationsEnabled"))
            if (settingsObj.has("calculationMethod")) prefs.setCalculationMethod(settingsObj.getString("calculationMethod"))
            settingsObj.optJSONObject("prayerReminders")?.let { remote ->
                fun minuteList(key: String): JSONArray = remote.optJSONArray(key) ?: JSONArray().put(remote.optInt(key, 10))
                val normalized = JSONObject().apply {
                    put("fajr", minuteList("fajr"))
                    put("dhuhr", minuteList("dhuhr"))
                    put("asr", minuteList("asr"))
                    put("maghrib", minuteList("maghrib"))
                    put("isha", minuteList("isha"))
                    put("isVibrationEnabled", remote.optBoolean("vibration", remote.optBoolean("isVibrationEnabled", true)))
                    put("isFullScreenEnabled", remote.optBoolean("fullScreen", remote.optBoolean("isFullScreenEnabled", true)))
                }
                prefs.setPrayerRemindersJson(normalized.toString())
            }
        }

        // An empty array is a real state -- "the last bookmark was deleted" --
        // so only a missing key means "this payload says nothing about them".
        val bookmarksArr = root.optJSONArray("bookmarks")
        if (bookmarksArr != null) {
            val incomingIds = HashSet<String>()
            for (i in 0 until bookmarksArr.length()) {
                val b = bookmarksArr.getJSONObject(i)
                val bm = com.quran.watch8.data.model.Bookmark(
                    id = b.optString("id", "bm_${System.currentTimeMillis()}_$i"),
                    surah = b.optInt("surah", 1),
                    ayah = if (b.has("ayahNumber")) b.getInt("ayahNumber") else b.optInt("ayah", 1),
                    textSnippet = if (b.has("ayahText")) b.getString("ayahText") else b.optString("textSnippet", ""),
                    timestamp = b.optLong("createdAt", System.currentTimeMillis()),
                    note = b.optString("note", "").ifBlank { null }
                )
                incomingIds.add(bm.id)
                dbRepo.addBookmark(bm)
            }
            // Propagate deletions: a bookmark removed in the web studio is gone
            // from this list, so drop it here too -- unless it was added on the
            // watch after the payload was built.
            dbRepo.bookmarks.first().forEach { existing ->
                if (existing.id !in incomingIds && existing.timestamp < payloadVersion) {
                    dbRepo.removeBookmark(existing.id)
                }
            }
        }

        val locationsArr = root.optJSONArray("locations")
        if (locationsArr != null) {
            val incomingIds = HashSet<String>()
            for (i in 0 until locationsArr.length()) {
                val l = locationsArr.getJSONObject(i)
                val loc = com.quran.watch8.data.model.SavedLocation(
                    id = l.optString("id", "loc_${System.currentTimeMillis()}_$i"),
                    name = l.optString("name", "موقع"),
                    latitude = l.optDouble("latitude", -34.57),
                    longitude = l.optDouble("longitude", -58.42),
                    type = com.quran.watch8.data.model.LocationType.IMPORTANT
                )
                incomingIds.add(loc.id)
                dbRepo.addLocation(loc)
            }
            dbRepo.locations.first().forEach { existing ->
                if (existing.id !in incomingIds && existing.timestamp < payloadVersion) {
                    dbRepo.removeLocation(existing.id)
                }
            }
        }
    }

}
