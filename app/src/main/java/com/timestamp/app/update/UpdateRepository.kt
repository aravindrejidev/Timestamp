package com.timestamp.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.timestamp.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class NoReleaseException : IOException("No release published yet")

/** Talks to GitHub Releases, downloads the APK and hands it to the system installer. */
class UpdateRepository(context: Context) {
    private val app = context.applicationContext

    val installedVersionName: String
    val installedVersionCode: Long

    init {
        val pi = app.packageManager.getPackageInfo(app.packageName, 0)
        installedVersionName = pi.versionName ?: "0"
        installedVersionCode = PackageInfoCompat.getLongVersionCode(pi)
    }

    val isConfigured: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    private val updatesDir: File get() = File(app.cacheDir, "updates").apply { mkdirs() }

    // ------------------------------------------------------------ check

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Timestamp-Updater")
            val code = c.responseCode
            if (code == 404) throw NoReleaseException()
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val digits = Regex("\\d+")
        val pa = digits.findAll(a).map { it.value.toLong() }.toList()
        val pb = digits.findAll(b).map { it.value.toLong() }.toList()
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0L }
            val y = pb.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** Returns the newest release if it is newer than the installed app, otherwise null. Throws on network errors. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val json = JSONObject(httpGet("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"))
        val tag = json.optString("tag_name")
        val notes = json.optString("body")
        val pageUrl = json.optString("html_url")
        val assets = json.optJSONArray("assets") ?: JSONArray()

        var apkUrl: String? = null
        var apkSize = 0L
        var metaUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (name == "update.json") {
                metaUrl = url
            } else if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) {
                apkUrl = url
                apkSize = a.optLong("size", 0L)
            }
        }
        val apk = apkUrl ?: return@withContext null
        if (!apk.startsWith("https://")) return@withContext null

        var versionName = tag.removePrefix("v").removePrefix("V")
        var versionCode = -1L
        var sha: String? = null
        if (metaUrl != null) {
            try {
                val m = JSONObject(httpGet(metaUrl))
                versionCode = m.optLong("versionCode", -1L)
                m.optString("versionName").takeIf { it.isNotBlank() }?.let { versionName = it }
                sha = m.optString("sha256").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                // fall back to comparing the tag name
            }
        }

        val newer = if (versionCode >= 0) {
            versionCode > installedVersionCode
        } else {
            compareVersions(versionName, installedVersionName) > 0
        }
        if (!newer) return@withContext null
        UpdateInfo(versionName, versionCode, notes, apk, apkSize, sha, pageUrl)
    }

    // ------------------------------------------------------------ download

    suspend fun download(info: UpdateInfo, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = updatesDir
            dir.listFiles()?.forEach { it.delete() }
            val safeName = info.versionName.filter { it.isLetterOrDigit() || it == '.' || it == '-' }
            val target = File(dir, "Timestamp-$safeName.apk")
            val part = File(dir, target.name + ".part")
            val c = URL(info.apkUrl).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Timestamp-Updater")
                val code = c.responseCode
                if (code !in 200..299) throw IOException("Download failed (HTTP $code)")
                val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var lastTick = 0L
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 100) {
                                lastTick = now
                                onProgress(done, total)
                            }
                        }
                    }
                }
                onProgress(done, total)
                val expected = info.sha256
                if (expected != null) {
                    val actual = digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw IOException("The downloaded file is damaged. Please try again.")
                    }
                }
                if (!part.renameTo(target)) throw IOException("Could not save the update file.")
                target
            } catch (e: Exception) {
                part.delete()
                throw e
            } finally {
                c.disconnect()
            }
        }

    fun cleanup() {
        File(app.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    // ------------------------------------------------------------ install

    /** Returns a readable problem, or null when the APK is fine to install. */
    fun validate(file: File): String? {
        val pm = app.packageManager
        val archive = try {
            pm.getPackageArchiveInfo(file.absolutePath, 0)
        } catch (e: Exception) {
            null
        }
        if (archive == null) return "The downloaded file is not a valid app package."
        if (archive.packageName != app.packageName) return "The downloaded file belongs to a different app."
        if (PackageInfoCompat.getLongVersionCode(archive) <= installedVersionCode) {
            return "This update is not newer than the installed version."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && signersDiffer(file)) {
            return "This update is signed with a different key than the installed app " +
                "(for example a test build vs a release build). Uninstall the app once, then install the new version."
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun signersDiffer(file: File): Boolean {
        return try {
            val pm = app.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
            val installed = pm.getPackageInfo(app.packageName, flags)
            val a = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val b = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            if (a == null || b == null || a.isEmpty() || b.isEmpty()) false else a.intersect(b).isEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    fun openInstallSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(i)
        }
    }

    fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(i)
    }
}
