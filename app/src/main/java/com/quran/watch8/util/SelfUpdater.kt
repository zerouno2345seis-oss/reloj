package com.quran.watch8.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates the app from the repository's GitHub releases, so a new build
 * reaches the watch over whatever internet it has -- no Mac, no wireless
 * debugging, which the watch drops whenever its screen sleeps.
 *
 * A release is tagged `v<versionCode>` and carries the app's `.apk`, signed
 * with the same key as the installed app (Android refuses anything else, and
 * the same key keeps bookmarks and settings). It may also carry watch faces,
 * each a separate package named `face-<package>-v<versionCode>.apk`; a face
 * missing or older on the watch is installed with the app.
 */
object SelfUpdater {
    private const val TAG = "SelfUpdater"
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/zerouno2345seis-oss/reloj/releases/latest"
    const val ACTION_INSTALL_STATUS = "com.quran.watch8.UPDATE_INSTALL_STATUS"

    data class Release(
        @SerializedName("tag_name") val tagName: String = "",
        @SerializedName("assets") val assets: List<Asset> = emptyList(),
    )
    data class Asset(
        @SerializedName("name") val name: String = "",
        @SerializedName("browser_download_url") val url: String = "",
        @SerializedName("size") val size: Long = 0,
    )

    sealed interface Check {
        /** [apkUrl] is null when only faces are new. */
        data class Available(val versionCode: Long, val apkUrl: String?, val size: Long, val faces: List<Face> = emptyList()) : Check
        data object UpToDate : Check
        data class Failed(val reason: String) : Check
    }

    /** A watch face shipped in a release: its package and where to fetch it. */
    data class Face(val packageName: String, val url: String)

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** The installed version of another package, or -1 when it is not installed. */
    fun installedVersionCode(context: Context, packageName: String): Long =
        try { context.packageManager.getPackageInfo(packageName, 0).longVersionCode } catch (_: Exception) { -1L }

    /** `face-com.example.face-v42.apk` -> `com.example.face`; null for anything else. */
    fun facePackage(assetName: String): String? =
        Regex("""^face-(.+)-v\d+\.apk$""").find(assetName)?.groupValues?.get(1)

    /** `v42` -> 42; anything without a number is no release of ours. */
    /** `face-com.example.face-v1042.apk` -> 1042: the face's own version, not the release's. */
    fun faceVersion(assetName: String): Long? =
        Regex("""^face-.+-v(\d+)\.apk$""").find(assetName)?.groupValues?.get(1)?.toLongOrNull()

    fun versionFromTag(tag: String): Long? = tag.filter { it.isDigit() }.toLongOrNull()

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "quran-watch8")
            }
            val code = conn.responseCode
            if (code == 404) return@withContext Check.UpToDate // no release published yet
            if (code != 200) return@withContext Check.Failed("GitHub $code")
            val release = conn.inputStream.bufferedReader().use { Gson().fromJson(it, Release::class.java) }
            val remote = versionFromTag(release.tagName) ?: return@withContext Check.Failed("tag ${release.tagName}")
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && facePackage(it.name) == null }
                ?: return@withContext Check.Failed("no apk in ${release.tagName}")
            val faces = release.assets.mapNotNull { asset ->
                facePackage(asset.name)?.takeIf { installedVersionCode(context, it) < (faceVersion(asset.name) ?: remote) }?.let { Face(it, asset.url) }
            }
            val appNew = remote > installedVersionCode(context)
            if (!appNew && faces.isEmpty()) Check.UpToDate
            else Check.Available(remote, apk.url.takeIf { appNew }, apk.size, faces)
        } catch (e: Exception) {
            Log.w(TAG, "check failed", e)
            Check.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, url: String, fileName: String = "update.apk", onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, fileName)
            var conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "quran-watch8")
            // GitHub sends assets through a redirect to its storage host.
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val next = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = URL(next).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "quran-watch8")
            }
            if (conn.responseCode != 200) error("download ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            out
        }

    /**
     * Abandons every install session this app left open, except those still being waited
     * for. Android allows an app only 50 open sessions; failed and unanswered ones were
     * never closed, piled up, and every later install failed with "Too many active
     * sessions" (2026-11-05). Called before each new session and at each update press.
     */
    fun clearStaleSessions(context: Context): Int {
        val installer = context.packageManager.packageInstaller
        var n = 0
        for (info in installer.mySessions) {
            if (pending.containsKey(info.sessionId)) continue
            try { installer.abandonSession(info.sessionId); n++ } catch (e: Exception) { Log.w(TAG, "abandon ${info.sessionId}", e) }
        }
        if (n > 0) Log.i(TAG, "abandoned $n stale install sessions")
        return n
    }

    private fun abandon(installer: PackageInstaller, id: Int) {
        try { installer.abandonSession(id) } catch (_: Exception) { }
    }

    private fun writeApk(installer: PackageInstaller, id: Int, apk: File) {
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
        }
    }

    private fun params(packageName: String?) =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (packageName != null) setAppPackageName(packageName) else setMultiPackage()
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

    /** Commits [id] and waits for its result; on failure or no answer the session is abandoned. */
    private suspend fun commitAndWait(context: Context, installer: PackageInstaller, id: Int, done: CompletableDeferred<Boolean>): Boolean {
        try {
            val intent = Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            val status = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            installer.openSession(id).use { it.commit(status.intentSender) }
            // Wait for this one to finish (the wearer may first confirm it) before the next
            // is handed over: started together, each confirmation screen replaced the one
            // before, and only one face went in per press.
            val ok = withTimeoutOrNull(INSTALL_WAIT_MS) { done.await() } ?: false
            if (!ok) abandon(installer, id)
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "install session $id failed", e)
            abandon(installer, id)
            return false
        } finally {
            pending.remove(id)
        }
    }

    /**
     * Installs several packages (the watch faces) as ONE multi-package session, so
     * Android asks for a single confirmation instead of one per face. Each face is
     * its own package (Wear OS allows one face per package), hence the children.
     * false when the watch refuses it: the caller then installs them one at a time.
     */
    suspend fun installAll(context: Context, apks: List<Pair<File, String>>): Boolean = withContext(Dispatchers.IO) {
        if (apks.isEmpty()) return@withContext true
        if (apks.size == 1) return@withContext install(context, apks[0].first, apks[0].second)
        val installer = context.packageManager.packageInstaller
        clearStaleSessions(context)
        val children = mutableListOf<Int>()
        var parentId = -1
        try {
            parentId = installer.createSession(params(null))
            for ((apk, pkg) in apks) {
                val childId = installer.createSession(params(pkg))
                children += childId
                writeApk(installer, childId, apk)
                installer.openSession(parentId).use { it.addChildSessionId(childId) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "multi-package install refused", e)
            if (parentId >= 0) abandon(installer, parentId)
            children.forEach { abandon(installer, it) }
            return@withContext false
        }
        val done = CompletableDeferred<Boolean>()
        pending[parentId] = done
        val ok = commitAndWait(context, installer, parentId, done)
        if (!ok) children.forEach { abandon(installer, it) }
        ok
    }

    /**
     * Hands the APK to the system installer. The system then asks the wearer
     * to confirm -- and, the first time, to allow installs from this app --
     * through [InstallStatusReceiver]. Returns once it is installed (true) or failed.
     */
    suspend fun install(context: Context, apk: File, packageName: String = context.packageName): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        clearStaleSessions(context)
        val sessionId = try {
            installer.createSession(params(packageName))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "createSession", e)
            return@withContext false
        }
        try {
            writeApk(installer, sessionId, apk)
        } catch (e: Exception) {
            Log.w(TAG, "write $sessionId", e)
            abandon(installer, sessionId)
            return@withContext false
        }
        val done = CompletableDeferred<Boolean>()
        pending[sessionId] = done
        commitAndWait(context, installer, sessionId, done)
    }

    /** How long one install may wait for the wearer to confirm it. */
    private const val INSTALL_WAIT_MS = 5 * 60 * 1000L

    /** Installs in progress, by session id, completed by [InstallStatusReceiver]. */
    internal val pending = java.util.concurrent.ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
}

/** Receives the installer's answer; shows its confirmation screen when it asks for one. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i("SelfUpdater", "update installed")
                SelfUpdater.pending[sessionId]?.complete(true)
            }
            else -> {
                Log.w(
                    "SelfUpdater",
                    "install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
                )
                SelfUpdater.pending[sessionId]?.complete(false)
            }
        }
    }
}
