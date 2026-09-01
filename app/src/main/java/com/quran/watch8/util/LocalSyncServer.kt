package com.quran.watch8.util

import android.content.Context
import android.net.wifi.WifiManager
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*
import java.util.*

/**
 * Ultra-Low Power & High Performance Dual-Sync Engine for Galaxy Watch 8
 *
 * Battery Optimized:
 *  - ZERO permanent WakeLocks or WifiLocks (allows CPU and Wi-Fi to enter deep sleep/Doze mode).
 *  - ZERO aggressive infinite background polling loops.
 *  - Passive lightweight ServerSocket on port 41331 for instant local push from Web.
 *  - On-demand Cloud Relay sync (HTTPS).
 */
object LocalSyncServer {
    const val FIXED_PORT = 41331
    const val CLOUD_RELAY_URL = "https://quran-watch8-hub.vercel.app/api/sync?code=41331"

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastSyncedVersion: Long = 0L

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(FIXED_PORT))
                }

                val db = QuranDatabase.getInstance(context)
                val dbRepo = DatabaseRepository(
                    bookmarkDao = db.bookmarkDao(),
                    locationDao = db.savedLocationDao(),
                    voiceNoteDao = db.voiceNoteDao(),
                    readingPositionDao = db.readingPositionDao()
                )
                val prefs = PreferencesRepository(context)

                // Gentle one-time check on startup
                launch {
                    try {
                        syncWithCloud(context, "pull")
                    } catch (_: Exception) {}
                }

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client, dbRepo, prefs)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /**
     * Returns current Wi-Fi IPv4 address of the watch
     */
    fun getWatchIpAddress(context: Context): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.name.contains("wlan") || intf.name.contains("eth")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: ""
                        }
                    }
                }
            }
            // Fallback to any non-loopback IPv4
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.190"
    }

    private fun handleClient(socket: Socket, dbRepo: DatabaseRepository, prefs: PreferencesRepository) {
        scope.launch {
            try {
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) break
                    if (line!!.lowercase().startsWith("content-length:")) {
                        contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                if (method.equals("OPTIONS", ignoreCase = true)) {
                    sendResponse(writer, 200, "OK", "application/json", "{}")
                    socket.close()
                    return@launch
                }

                if (path.startsWith("/api/sync") || path == "/") {
                    if (method.equals("GET", ignoreCase = true)) {
                        val json = exportDataJson(dbRepo, prefs)
                        sendResponse(writer, 200, "OK", "application/json; charset=utf-8", json)
                    } else if (method.equals("POST", ignoreCase = true)) {
                        val bodyChars = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val r = reader.read(bodyChars, read, contentLength - read)
                            if (r <= 0) break
                            read += r
                        }
                        val body = String(bodyChars)
                        importDataJson(body, dbRepo, prefs)
                        sendResponse(writer, 200, "OK", "application/json; charset=utf-8", """{"status":"synced","time":${System.currentTimeMillis()}}""")
                    }
                } else if (path.startsWith("/api/ping")) {
                    sendResponse(writer, 200, "OK", "application/json; charset=utf-8", """{"status":"online","device":"Galaxy Watch 8"}""")
                } else {
                    sendResponse(writer, 404, "Not Found", "text/plain", "Not Found")
                }

                socket.close()
            } catch (_: Exception) {}
        }
    }

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
                    }
                    Pair(true, "✓ تمت المزامنة وتحديث البلاطات")
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
        root.put("version", System.currentTimeMillis())

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
                put("surahNameAr", "سورة ${bm.surah}")
                put("ayahText", bm.textSnippet)
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

        val tilesObj = root.optJSONObject("tilesConfig") ?: root.optJSONObject("tiles")
        if (tilesObj != null) {
            val config = TileConfig.fromJson(tilesObj.toString())
            if (config.isValid()) {
                prefs.setTilesConfigJson(config.toJson())
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

            val updated = current.copy(
                modelId = if (modelId.isNotBlank()) runCatching { com.quran.watch8.data.model.WatchFaceModelId.valueOf(modelId) }.getOrDefault(current.modelId) else current.modelId,
                topSlot = if (topSlot.isNotBlank()) runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(topSlot) }.getOrDefault(current.topSlot) else current.topSlot,
                rightSlot = if (rightSlot.isNotBlank()) runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(rightSlot) }.getOrDefault(current.rightSlot) else current.rightSlot,
                leftSlot = if (leftSlot.isNotBlank()) runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(leftSlot) }.getOrDefault(current.leftSlot) else current.leftSlot,
                bottomSlot = if (bottomSlot.isNotBlank()) runCatching { com.quran.watch8.data.model.ComplicationType.valueOf(bottomSlot) }.getOrDefault(current.bottomSlot) else current.bottomSlot
            )
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

        val bookmarksArr = root.optJSONArray("bookmarks")
        if (bookmarksArr != null) {
            for (i in 0 until bookmarksArr.length()) {
                val b = bookmarksArr.getJSONObject(i)
                val bm = com.quran.watch8.data.model.Bookmark(
                    id = b.optString("id", "bm_${System.currentTimeMillis()}_$i"),
                    surah = b.optInt("surah", 1),
                    ayah = if (b.has("ayahNumber")) b.getInt("ayahNumber") else b.optInt("ayah", 1),
                    textSnippet = if (b.has("ayahText")) b.getString("ayahText") else b.optString("textSnippet", ""),
                    timestamp = b.optLong("createdAt", System.currentTimeMillis())
                )
                dbRepo.addBookmark(bm)
            }
        }

        val locationsArr = root.optJSONArray("locations")
        if (locationsArr != null) {
            for (i in 0 until locationsArr.length()) {
                val l = locationsArr.getJSONObject(i)
                val loc = com.quran.watch8.data.model.SavedLocation(
                    id = l.optString("id", "loc_${System.currentTimeMillis()}_$i"),
                    name = l.optString("name", "موقع"),
                    latitude = l.optDouble("latitude", -34.57),
                    longitude = l.optDouble("longitude", -58.42),
                    type = com.quran.watch8.data.model.LocationType.IMPORTANT
                )
                dbRepo.addLocation(loc)
            }
        }
    }

    private fun sendResponse(writer: PrintWriter, code: Int, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $code $status\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: *\r\n")
        writer.print("Access-Control-Allow-Private-Network: true\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(body)
        writer.flush()
    }
}
