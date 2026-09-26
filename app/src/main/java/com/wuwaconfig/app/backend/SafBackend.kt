package com.wuwaconfig.app.backend

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.wuwaconfig.app.PREFS_NAME
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader

class SafBackend(private val context: Context) : AccessBackend {
    @Volatile
    private var _treeUri: Uri? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val knownRoot = GamePaths.TARGET_DIR

    /**
     * Guards [resolveOrCreateDocument]: two concurrent pushes into the same not-yet-existing
     * directory both call `createDirectory` and end up with duplicate sibling directories.
     * One SafBackend holds one tree, so an instance-level mutex is the "keyed on the tree"
     * lock.
     */
    private val resolveMutex = Mutex()

    val treeUri: Uri?
        get() = _treeUri

    fun restoreTreeUri(): Uri? {
        val uriStr = prefs.getString("saf_tree_uri", null) ?: return null
        val uri = Uri.parse(uriStr)
        _treeUri = uri
        return uri
    }

    fun saveTreeUri(uri: Uri) {
        _treeUri = uri
        prefs.edit().putString("saf_tree_uri", uri.toString()).apply()
        takePersistablePermission(uri)
    }

    fun clearTreeUri() {
        // Releasing first: the platform caps persistable grants at 128/UID, so every
        // re-pick without a release permanently exhausts them.
        val old = _treeUri
        if (old != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure {
                LogRepository.add("SAF: failed to release old directory grant: ${it.message}", LogLevel.WARNING)
            }
        }
        _treeUri = null
        prefs.edit().remove("saf_tree_uri").apply()
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Without the persisted grant the tree works only until reboot — surface
            // it now instead of a confusing "directory no longer accessible" later.
            LogRepository.add("SAF: grant is not persistable (may stop working after reboot): ${e.message}", LogLevel.WARNING)
        }
    }

    override val isConnected: Boolean
        get() = _treeUri != null

    override suspend fun connect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val uri =
                _treeUri ?: run {
                    LogRepository.add("SAF connect: no directory selected", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("No SAF directory selected. Tap Pick Directory to choose the game config folder."))
                }
            LogRepository.add("SAF connect: verifying $uri")
            try {
                val doc = DocumentFile.fromTreeUri(context, uri)
                if (doc == null || !doc.exists() || !doc.isDirectory) {
                    clearTreeUri()
                    LogRepository.add("SAF directory no longer accessible", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("SAF directory no longer accessible. Pick again."))
                }
                LogRepository.add("SAF connected successfully", LogLevel.SUCCESS)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clearTreeUri()
                LogRepository.add("SAF connect failed: ${e.message}", LogLevel.ERROR)
                Result.failure(Exception("SAF access error: ${e.message}"))
            }
        }

    override fun disconnect() {
        LogRepository.add("SAF disconnect")
        _treeUri = null
    }

    override suspend fun executeShellCommand(command: String): Result<String> {
        LogRepository.add("SAF shell not available: ${command.take(60)}", LogLevel.ERROR)
        return Result.failure(Exception("Shell commands not available in SAF mode. Use ROOT, ADB, or Shizuku."))
    }

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF push: $sourcePath -> $targetPath")
            try {
                val parent = resolveOrCreateDocument(targetPath)
                val nameOnly = targetPath.substringAfterLast('/')
                val targetDoc =
                    parent.findFile(nameOnly) ?: parent.createFile("*/*", nameOnly)
                        ?: throw Exception("Cannot create file: $nameOnly")
                val bytes = File(sourcePath).readBytes()
                writeDocument(targetDoc, bytes)
                LogRepository.add("SAF push completed: $targetPath", LogLevel.SUCCESS)
                Result.success("Written to $targetPath")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF push failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF ensureDir: $dirPath")
            try {
                resolveOrCreateDocument(dirPath, isDirectory = true)
                LogRepository.add("SAF ensureDir succeeded", LogLevel.SUCCESS)
                Result.success("")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF ensureDir failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun fileExists(path: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val doc = resolveDocument(path)
                val exists = doc != null && doc.exists() && doc.isFile
                LogRepository.add("SAF fileExists: $path -> $exists")
                Result.success(exists)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileNotFoundException) {
                LogRepository.add("SAF fileExists: $path not found", LogLevel.WARNING)
                Result.success(false)
            } catch (e: Exception) {
                // A revoked grant / SecurityException / provider death is NOT "absent":
                // callers do getOrElse { false } and would then disable or overwrite as if
                // the file were gone.
                LogRepository.add("SAF fileExists error: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun listDirectory(path: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF listDir: $path")
            try {
                val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
                val files = doc.listFiles().map { it.name ?: it.uri.toString() }
                LogRepository.add("SAF listDir: found ${files.size} entries", LogLevel.SUCCESS)
                Result.success(files)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF listDir failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun backupFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF backup: $path")
            try {
                val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
                val baseName = doc.name ?: return@withContext Result.failure(Exception("Cannot determine file name for $path"))
                val backupName = "$baseName.backup_${System.currentTimeMillis()}"
                val parent = doc.parentFile ?: return@withContext Result.failure(Exception("Cannot determine parent"))
                val backupDoc =
                    parent.createFile("*/*", backupName)
                        ?: return@withContext Result.failure(Exception("Cannot create backup"))
                val bytes = readDocumentBytes(doc)
                writeDocument(backupDoc, bytes)
                LogRepository.add("SAF backup completed: ${backupDoc.uri}", LogLevel.SUCCESS)
                Result.success(backupDoc.uri.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF backup failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun readFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF read: $path")
            try {
                val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
                val text =
                    context.contentResolver.openInputStream(doc.uri)
                        ?.use { BufferedReader(InputStreamReader(it)).readText() }
                        ?: return@withContext Result.failure(Exception("Cannot open: $path"))
                LogRepository.add("SAF read completed: ${text.length} chars")
                Result.success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF read failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun readFileBytes(path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF readFileBytes: $path")
            try {
                val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
                val bytes = readDocumentBytes(doc)
                LogRepository.add("SAF readFileBytes completed: ${bytes.size} bytes")
                Result.success(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF readFileBytes failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            LogRepository.add("SAF copyFile: $sourcePath -> $targetPath")
            try {
                val sourceDoc = resolveDocument(sourcePath) ?: return@withContext Result.failure(Exception("Source not found: $sourcePath"))
                val parent = resolveOrCreateDocument(targetPath)
                val nameOnly = targetPath.substringAfterLast('/')
                val targetDoc =
                    parent.findFile(nameOnly)
                        ?: parent.createFile(sourceDoc.type ?: "*/*", nameOnly)
                        ?: throw Exception("Cannot create file: $nameOnly")
                val bytes = readDocumentBytes(sourceDoc)
                writeDocument(targetDoc, bytes)
                LogRepository.add("SAF copyFile completed: ${bytes.size} bytes", LogLevel.SUCCESS)
                Result.success(targetPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF copyFile failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            LogRepository.add("SAF delete: $path")
            try {
                val doc = resolveDocument(path)
                if (doc == null || !doc.exists()) {
                    LogRepository.add("SAF delete: not found $path", LogLevel.WARNING)
                    return@withContext Result.success(Unit)
                }
                val deleted = doc.delete()
                if (deleted) {
                    LogRepository.add("SAF delete completed: $path", LogLevel.SUCCESS)
                    Result.success(Unit)
                } else {
                    LogRepository.add("SAF delete failed: $path", LogLevel.ERROR)
                    Result.failure(Exception("Failed to delete: $path"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("SAF delete error: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    private fun readDocumentBytes(doc: DocumentFile): ByteArray {
        return context.contentResolver.openInputStream(doc.uri)
            ?.use { it.readBytes() }
            ?: throw Exception("Cannot read: ${doc.name}")
    }

    /**
     * Writes [data] to a `…wuwa_new` sibling and renames it over [doc]. A provider write is
     * not atomic: truncating [doc] and streaming into it meant an IOException mid-write
     * left a truncated Engine.ini in the game's config dir with no rollback.
     */
    private fun writeDocument(
        doc: DocumentFile,
        data: ByteArray,
    ) {
        val parent = doc.parentFile ?: throw Exception("Cannot determine parent of ${doc.name}")
        val name = doc.name ?: throw Exception("Cannot determine name of ${doc.uri}")
        val stagingName = "$name$STAGING_SUFFIX"
        val staging =
            parent.findFile(stagingName)
                ?: parent.createFile(doc.type ?: "*/*", stagingName)
                ?: throw Exception("Cannot create staging file: $stagingName")
        val stream =
            context.contentResolver.openOutputStream(staging.uri, "wt")
                ?: throw Exception("Cannot write: $stagingName")
        try {
            stream.use { it.write(data) }
        } catch (e: Exception) {
            runCatching { staging.delete() }
            throw e
        }
        val written = staging.length()
        if (written != data.size.toLong()) {
            runCatching { staging.delete() }
            throw Exception("Short write for $name: $written of ${data.size} bytes")
        }
        if (doc.delete() && !staging.renameTo(name)) {
            runCatching { staging.delete() }
            throw Exception("Cannot replace $name with the staged copy")
        }
    }

    private fun resolveDocument(path: String): DocumentFile? {
        val tree = _treeUri ?: return null
        val root = DocumentFile.fromTreeUri(context, tree) ?: return null
        val nameOnly = path.substringAfterLast('/')
        val treeRoot = treeDeviceRoot() ?: knownRoot

        val strategies =
            listOf(
                { stripAndNavigate(path, root, treeRoot) },
                { stripAndNavigate(path, root, knownRoot) },
                { root.findFile(nameOnly) },
            )
        for (strategy in strategies) {
            val result = strategy()
            if (result != null && result.exists()) return result
        }
        return null
    }

    /**
     * Maps the granted SAF tree URI back to its on-disk path so we can navigate the *full*
     * relative path regardless of how deep the user granted access. On Android 11+ the picker
     * hides `Android/data`, so the only grantable folder is the game's `Android/data/<pkg>` tree —
     * whose document id is `primary:Android/data/<pkg>`. Deriving the real root from that id lets
     * us write into `.../<pkg>/files/UE4Game/...` even though the tree isn't rooted at the config dir.
     */
    private fun treeDeviceRoot(): String? {
        val uri = _treeUri ?: return null
        val docId =
            try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                return null
            }
        val split = docId.split(":", limit = 2)
        if (split.size != 2) return null
        val base =
            when (split[0]) {
                "primary" -> "/storage/emulated/0"
                else -> "/storage/${split[0]}"
            }
        return "$base/${split[1]}".replace(Regex("/{2,}"), "/")
    }

    private fun stripAndNavigate(
        path: String,
        root: DocumentFile,
        prefix: String,
    ): DocumentFile? {
        val relative = path.removePrefix(prefix).trimStart('/')
        if (relative.isEmpty()) return root
        if (!relative.contains("/")) return root.findFile(relative)
        var current = root
        for (part in relative.split("/")) {
            current = current.findFile(part) ?: return null
        }
        return current
    }

    private suspend fun resolveOrCreateDocument(
        path: String,
        isDirectory: Boolean = false,
    ): DocumentFile =
        resolveMutex.withLock {
            val tree = _treeUri ?: throw Exception("No SAF directory selected")
            val root = DocumentFile.fromTreeUri(context, tree) ?: throw Exception("Cannot access tree")
            val nameOnly = path.substringAfterLast('/')
            val treeRoot = treeDeviceRoot() ?: knownRoot

            val strategies =
                listOf(
                    { stripAndNavigate(path, root, treeRoot) },
                    { stripAndNavigate(path, root, knownRoot) },
                    { root.findFile(nameOnly) },
                )
            for (strategy in strategies) {
                // Type-filtered: a strategy may resolve the TREE ROOT (a directory) when the
                // path has no '/', and the caller would then operate one level too high.
                val result = strategy()
                if (result != null && (if (isDirectory) result.isDirectory else !result.isDirectory)) {
                    return@withLock result
                }
            }

            // Path doesn't exist yet — create intermediate directories, then the final segment.
            // `removePrefix` is a NO-OP when the prefix doesn't match, so without this guard a
            // non-descendant target (e.g. HashMonitor's Kuro/... pushed into a grant rooted at
            // .../Config/Android) would build a shadow tree of "storage/emulated/0/..." inside
            // the user's own folder and report success.
            if (path != treeRoot && !path.startsWith("$treeRoot/")) {
                throw Exception("Path $path is outside the granted SAF tree ($treeRoot)")
            }
            val parts = path.removePrefix(treeRoot).trimStart('/').split("/").filter { it.isNotBlank() }
            var current = root
            for (i in parts.indices) {
                val part = parts[i]
                val isLast = i == parts.lastIndex
                var child = current.findFile(part)
                if (child == null) {
                    if (isLast && !isDirectory) {
                        // File target: stop at the parent directory so the caller can createFile().
                        return@withLock current
                    }
                    child =
                        current.createDirectory(part)
                            ?: throw Exception("Cannot create directory: $part")
                }
                current = child
            }
            current
        }

    private companion object {
        const val STAGING_SUFFIX = ".wuwa_new"
    }
}
