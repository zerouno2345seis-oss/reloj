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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates the app from the repository's GitHub releases, so a new build
 * reaches the watch over whatever internet it has -- no Mac, no wireless
 * debugging, which the watch drops whenever its screen sleeps.
 *
 * A release is tagged `v<versionCode>` and carries one `.apk` asset, signed
 * with the same key as the installed app (Android refuses anything else, and
 * the same key keeps bookmarks and settings).
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
        data class Available(val versionCode: Long, val apkUrl: String, val size: Long) : Check
        data object UpToDate : Check
        data class Failed(val reason: String) : Check
    }

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** `v42` -> 42; anything without a number is no release of ours. */
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
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext Check.Failed("no apk in ${release.tagName}")
            if (remote <= installedVersionCode(context)) Check.UpToDate
            else Check.Available(remote, apk.url, apk.size)
        } catch (e: Exception) {
            Log.w(TAG, "check failed", e)
            Check.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, "update.apk")
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
     * Hands the APK to the system installer. The system then asks the wearer
     * to confirm -- and, the first time, to allow installs from this app --
     * through [InstallStatusReceiver].
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's answer; shows its confirmation screen when it asks for one. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("SelfUpdater", "update installed")
            else -> Log.w(
                "SelfUpdater",
                "install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
            )
        }
    }
}
