package com.wuwaconfig.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-update helper. Fetches the latest GitHub Release, downloads the APK, and
 * opens it for installation. Integrity is enforced by comparing the downloaded
 * APK's signing certificate to the certificate of the currently installed app —
 * a release signed by any other key is refused. No external hash is required.
 */
object UpdateManager {
    private const val REPO = "B3rr7/WuWa-Config-Android"
    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
    )

    /** Parses a version string like "v1.2.0" or "1.11.0" into comparable ints. */
    fun parseVersion(raw: String): List<Int> =
        raw.trim().lowercase().removePrefix("v")
            .split(".", "-")
            .mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

    /** True when [remote] describes a strictly newer version than [current]. */
    fun isNewer(
        remote: String,
        current: String,
    ): Boolean {
        val r = parseVersion(remote)
        val c = parseVersion(current)
        if (r.isEmpty()) return false
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    suspend fun fetchLatest(): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github+json")

                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return@withContext Result.failure(Exception("GitHub API HTTP $code"))
                }

                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseRelease(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Parses a GitHub "latest release" JSON payload into [UpdateInfo]. Pure/testable. */
    fun parseRelease(json: String): Result<UpdateInfo> {
        return try {
            val map = Gson().fromJson(json, Map::class.java) ?: return Result.failure(Exception("Invalid release payload"))

            val tag = (map["tag_name"] as? String).orEmpty()
            if (tag.isBlank()) return Result.failure(Exception("Release missing tag_name"))

            @Suppress("UNCHECKED_CAST")
            val assets = (map["assets"] as? List<Map<String, Any?>>) ?: emptyList()
            val apkAsset =
                assets.firstOrNull { asset ->
                    val contentType = (asset["content_type"] as? String).orEmpty()
                    val name = (asset["name"] as? String).orEmpty()
                    contentType == "application/vnd.android.package-archive" || name.endsWith(".apk", ignoreCase = true)
                }
            val apkUrl = (apkAsset?.get("browser_download_url") as? String).orEmpty()
            if (apkUrl.isBlank()) return Result.failure(Exception("No APK asset in latest release"))

            val notes = (map["body"] as? String).orEmpty()
            val versionName = tag.trim().removePrefix("v")

            Result.success(UpdateInfo(tag, versionName, notes, apkUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(
        apkUrl: String,
        destFile: File,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                destFile.parentFile?.mkdirs()
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000

                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return@withContext Result.failure(Exception("Download HTTP $code"))
                }

                val total = conn.contentLengthLong.coerceAtLeast(0L)
                var downloaded = 0L

                conn.inputStream.use { input ->
                    BufferedInputStream(input).use { bis ->
                        FileOutputStream(destFile).use { fos ->
                            val buffer = ByteArray(8 * 1024)
                            var read: Int
                            while (bis.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                conn.disconnect()
                if (destFile.length() == 0L) {
                    return@withContext Result.failure(Exception("Downloaded file is empty"))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Refuses any APK not signed by the same certificate as the installed app. */
    fun verifySignatureMatchesInstalled(
        context: Context,
        apkFile: File,
    ): Boolean {
        return try {
            val pm = context.packageManager

            @Suppress("DEPRECATION")
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            val installed = pm.getPackageInfo(context.packageName, flags) ?: return false
            val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
            val installedCerts = signingCerts(installed)
            val archiveCerts = signingCerts(archive)
            if (installedCerts.isEmpty() || archiveCerts.isEmpty()) return false
            val installedHashes = installedCerts.map { sha256Hex(it.toByteArray()) }.toSet()
            val archiveHashes = archiveCerts.map { sha256Hex(it.toByteArray()) }.toSet()
            val matches = installedHashes == archiveHashes
            if (!matches) {
                LogRepository.add("UpdateManager: APK signature mismatch — refusing update", LogLevel.ERROR)
            }
            matches
        } catch (e: Exception) {
            LogRepository.add("UpdateManager: signature check failed: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCerts(pi: PackageInfo): Array<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            pi.signatures ?: emptyArray()
        }
    }

    fun openForInstall(
        context: Context,
        apkFile: File,
    ) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", apkFile)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    fun updatesDir(context: Context): File = File(context.cacheDir, "updates")

    fun downloadedApk(context: Context): File = File(updatesDir(context), "WuWaConfig.apk")

    /** SHA-256 hex of [bytes]; used for integrity logging and unit tests. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
