package com.wuwaconfig.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

    /** GitHub rejects/garbles API responses sent with the default java HttpURLConnection agent. */
    private const val USER_AGENT = "WuWaConfig-Android (self-update)"

    /** Hard cap on the release JSON body — an unbounded read is an OOM vector. */
    private const val MAX_JSON_BYTES = 4L * 1024 * 1024

    /** Hard cap on the downloaded APK. A real release APK is a few MB. */
    private const val MAX_APK_BYTES = 150L * 1024 * 1024

    /**
     * Failure marker returned by [openForInstall] when the per-app "allow from
     * this source" grant has not been given. Callers must route this to
     * `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:<pkg>"))`.
     */
    const val NEEDS_INSTALL_PERMISSION = "NEEDS_INSTALL_PERMISSION"

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
    )

    /**
     * Parses a version string like "v1.2.0" or "1.11.0" into comparable ints.
     *
     * A pre-release suffix is preserved as a LOW-ORDER tiebreaker, appended
     * after the release number: `0` for a final release, `1` for anything with
     * a `-suffix`. So a final release always sorts above its own pre-releases,
     * and `1.1.5-1` (an Android build suffix) sorts below `1.1.5` instead of
     * above it. Pre-releases additionally carry a small alphabetical rank of
     * their tag (`beta` < `rc`) at an even lower order.
     */
    // Ordering sentinels appended to the numeric release vector so a suffix can only
    // ever be a low-order tiebreaker. The order is:
    //   BUILD_SUFFIX (-1) < PRE_RELEASE (0) < FINAL_RELEASE (1)
    // i.e. "1.1.5-42" (an Android buildNumber of 1.1.5) and "1.2.0-rc1" both sort
    // BELOW the matching bare "1.1.5" / "1.2.0", while ordering correctly among
    // their own siblings.
    private const val BUILD_SUFFIX = -1
    private const val PRE_RELEASE = 0
    private const val FINAL_RELEASE = 1

    // Unknown tags sort just below a final release, above every known pre-release.
    private const val UNKNOWN_TAG = 4

    private val PRE_RELEASE_RANKS =
        mapOf(
            "dev" to 0,
            "snapshot" to 0,
            "nightly" to 0,
            "a" to 1,
            "alpha" to 1,
            "b" to 2,
            "beta" to 2,
            "rc" to 3,
            "pre" to 3,
            "preview" to 3,
        )

    fun parseVersion(raw: String): List<Int> {
        val parts = raw.trim().lowercase().removePrefix("v").split("-", limit = 2)
        val release = parts[0].split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        if (release.isEmpty()) return emptyList()
        val suffix = parts.getOrNull(1)
        if (suffix.isNullOrBlank()) return release + FINAL_RELEASE + 0

        // "1.1.5-42" is an Android buildNumber: a build OF 1.1.5, not a version after
        // it. The previous parser turned it into [1,1,5,1], i.e. NEWER than "1.1.5".
        val build = suffix.toIntOrNull()
        if (build != null) return release + BUILD_SUFFIX + build

        // "1.2.0-rc1" / "1.2.0-beta3". Rank by an explicit table rather than by
        // letter value: lexicographic order is wrong (alpha < beta < rc, but 'a' <
        // 'b' < 'r' happens to agree while a numeric fold of two letters saturates
        // and collapsed every 2-letter tag to the same value).
        val tag = suffix.takeWhile { it.isLetter() }
        val rank = PRE_RELEASE_RANKS[tag] ?: UNKNOWN_TAG
        val num = suffix.drop(tag.length).filter { it.isDigit() }.toIntOrNull() ?: 0
        return release + PRE_RELEASE + rank + num
    }

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
                // The connection is opened outside the try so a malformed URL is
                // still caught by the outer catch, while every path that owns a
                // live socket is guaranteed to release it — the IOException case
                // on mobile is the common one, and it used to leak the pooled
                // connection until GC.
                try {
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", USER_AGENT)

                    val code = conn.responseCode
                    if (code != 200) {
                        return@withContext Result.failure(Exception("GitHub API HTTP $code"))
                    }

                    val text = readBoundedText(conn.inputStream, MAX_JSON_BYTES)
                    parseRelease(text)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Reads at most [maxBytes] from [input] as UTF-8. Fails loudly rather than
     * buffering an unbounded body into the heap.
     */
    private fun readBoundedText(
        input: java.io.InputStream,
        maxBytes: Long,
    ): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (out.size().toLong() + read > maxBytes) {
                throw IOException("Response exceeds the $maxBytes byte cap")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Parses a GitHub "latest release" JSON payload into [UpdateInfo]. Pure/testable. */
    fun parseRelease(json: String): Result<UpdateInfo> {
        return try {
            val map = Gson().fromJson(json, Map::class.java) ?: return Result.failure(Exception("Invalid release payload"))

            val tag = (map["tag_name"] as? String).orEmpty()
            if (tag.isBlank()) return Result.failure(Exception("Release missing tag_name"))
            val versionName = tag.trim().removePrefix("v")

            @Suppress("UNCHECKED_CAST")
            val assets = (map["assets"] as? List<Map<String, Any?>>) ?: emptyList()
            // Prefer the declared package-archive content type. The fallback is
            // the EXACT release asset name emitted by app/build.gradle.kts —
            // a generic ".apk" suffix match would happily accept a decoy
            // sibling such as the WuWaConfig-debug.apk this repo also builds.
            val expectedAssetName = "WuWaConfig-v$versionName-release.apk"
            val apkAsset =
                assets.firstOrNull { (it["content_type"] as? String) == APK_CONTENT_TYPE }
                    ?: assets.firstOrNull { (it["name"] as? String) == expectedAssetName }
            val apkUrl = (apkAsset?.get("browser_download_url") as? String).orEmpty()
            if (apkUrl.isBlank()) return Result.failure(Exception("No APK asset in latest release"))

            val notes = (map["body"] as? String).orEmpty()

            Result.success(UpdateInfo(tag, versionName, notes, apkUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(
        apkUrl: String,
        destFile: File,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) { downloadInternal(apkUrl, destFile, onProgress) }

    private fun downloadInternal(
        apkUrl: String,
        destFile: File,
        onProgress: (Int) -> Unit,
    ): Result<Unit> {
        try {
            destFile.parentFile?.mkdirs()
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", USER_AGENT)

                val code = conn.responseCode
                if (code != 200) {
                    return Result.failure(Exception("Download HTTP $code"))
                }

                // Validate against the advertised length BEFORE spending any
                // bandwidth, and refuse an absent length: without it a truncated
                // transfer is indistinguishable from a complete one.
                val expected = conn.contentLengthLong
                if (expected <= 0L) {
                    return Result.failure(Exception("Download reports no usable content length"))
                }
                if (expected > MAX_APK_BYTES) {
                    return Result.failure(Exception("APK too large: $expected bytes (cap $MAX_APK_BYTES)"))
                }

                // NEVER write in place. A kill, socket drop or read timeout
                // mid-transfer would otherwise leave a truncated APK at exactly
                // the path openForInstall() hands to the package installer, and
                // would clobber the last good download.
                val partFile = File(destFile.parentFile, "${destFile.name}.part")
                if (partFile.exists()) partFile.delete()

                var downloaded = 0L
                try {
                    conn.inputStream.use { input ->
                        BufferedInputStream(input).use { bis ->
                            FileOutputStream(partFile).use { fos ->
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                while (bis.read(buffer).also { read = it } != -1) {
                                    downloaded += read
                                    if (downloaded > MAX_APK_BYTES) {
                                        throw IOException("APK exceeds the $MAX_APK_BYTES byte cap")
                                    }
                                    fos.write(buffer, 0, read)
                                    onProgress(((downloaded * 100) / expected).toInt().coerceIn(0, 100))
                                }
                                fos.flush()
                                fos.fd.sync()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // A corrupt partial must never become the install target.
                    partFile.delete()
                    throw e
                }

                if (downloaded != expected) {
                    partFile.delete()
                    return Result.failure(Exception("Truncated download: $downloaded of $expected bytes"))
                }
                if (partFile.length() == 0L) {
                    partFile.delete()
                    return Result.failure(Exception("Downloaded file is empty"))
                }
                if (!partFile.renameTo(destFile)) {
                    partFile.delete()
                    return Result.failure(Exception("Could not move the download into place"))
                }
                if (destFile.length() == 0L) {
                    destFile.delete()
                    return Result.failure(Exception("Downloaded file is empty"))
                }
                return Result.success(Unit)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            return Result.failure(e)
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
            val installedHistory = signingHistory(installed)
            if (installedCerts.isEmpty() || archiveCerts.isEmpty()) return false

            val installedHashes = (installedCerts + installedHistory).map { sha256Hex(it) }.toSet()
            val archiveHashes = archiveCerts.map { sha256Hex(it) }.toSet()

            // Every cert signing the downloaded APK must also be present in the
            // installed app's active certs or its signing history (key rotation).
            val matches = archiveHashes.all { it in installedHashes }
            if (!matches) {
                LogRepository.add("UpdateManager: APK signature mismatch — refusing update", LogLevel.ERROR)
            }
            matches
        } catch (e: Exception) {
            LogRepository.add("UpdateManager: signature check failed: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    /** Active signing certificates (X.509 DER) of [pi]. Robust across API levels and v1/v2/v3 schemes. */
    @android.annotation.SuppressLint("NewApi")
    // Legacy signing read: `PackageInfo.signatures` is the pre-API 28 field and
    // is deprecated in favour of `signingInfo.apkContentsSigners` (API 28+,
    // GET_SIGNING_CERTIFICATES), which is the primary path above. This is kept
    // only as a fallback for API 26/27 devices, where `signingInfo` does not
    // exist and can throw NoSuchMethodError. Suppressed narrowly on this
    // function so the rest of the file stays warning-clean.
    @Suppress("DEPRECATION")
    private fun signingCerts(pi: PackageInfo): List<ByteArray> {
        val info =
            try {
                pi.signingInfo
            } catch (e: NoSuchMethodError) {
                // signingInfo is API 28+; on 26/27 fall back to deprecated signatures below.
                null
            }
        val signers = info?.apkContentsSigners
        // getPackageArchiveInfo sometimes leaves signingInfo null/empty while
        // still populating the deprecated `signatures` field — fall back to it.
        if (!signers.isNullOrEmpty()) return signers.map { it.toByteArray() }
        if (!pi.signatures.isNullOrEmpty()) return pi.signatures!!.map { it.toByteArray() }
        if (info != null && info.signingCertificateHistory?.isNotEmpty() == true) {
            return info.signingCertificateHistory!!.map { it.toByteArray() }
        }
        return emptyList()
    }

    /** Prior signing certificates (key-rotation history) of [pi], for subset matching. */
    @android.annotation.SuppressLint("NewApi")
    private fun signingHistory(pi: PackageInfo): List<ByteArray> {
        val info =
            try {
                pi.signingInfo
            } catch (e: NoSuchMethodError) {
                return emptyList()
            }
        val history = info?.signingCertificateHistory ?: return emptyList()
        return history.map { it.toByteArray() }
    }

    /**
     * Hands [apkFile] to the system package installer.
     *
     * The caller MUST handle the distinct [NEEDS_INSTALL_PERMISSION] failure
     * marker (compare `exceptionOrNull()?.message`) by launching
     * `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:<pkg>"))`
     * and then retrying — the per-app "allow from this source" grant is never
     * requested implicitly, so the ACTION_VIEW would otherwise dead-end in the
     * installer.
     */
    fun openForInstall(
        context: Context,
        apkFile: File,
    ): Result<Unit> {
        // Re-verify here rather than relying on the caller's earlier check:
        // this is the last point before a user-driven installer touches the
        // file, and a TOCTOU window between verify and install would otherwise
        // live in a ViewModel instead of the component that installs.
        if (!verifySignatureMatchesInstalled(context, apkFile)) {
            return Result.failure(Exception("APK signature does not match the installed app"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return Result.failure(Exception(NEEDS_INSTALL_PERMISSION))
        }
        return try {
            // Deliberately INSIDE the try: getUriForFile throws
            // IllegalArgumentException when the path is outside <cache-path>
            // (see res/xml/file_paths.xml), and not every call site catches.
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", apkFile)
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_CONTENT_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            // Kiosk / stripped ROMs may ship no package installer — fail as a
            // Result instead of crashing with ActivityNotFoundException.
            if (intent.resolveActivity(context.packageManager) == null) {
                return Result.failure(Exception("No package installer found on device"))
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updatesDir(context: Context): File = File(context.cacheDir, "updates")

    fun downloadedApk(context: Context): File = File(updatesDir(context), "WuWaConfig.apk")

    /** SHA-256 hex of [bytes]; used for integrity logging and unit tests. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
