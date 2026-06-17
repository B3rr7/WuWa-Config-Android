package com.wuwaconfig.app.backend

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class SafBackend(private val context: Context) : AccessBackend {
    private var _treeUri: Uri? = null
    private val prefs = context.getSharedPreferences("wuwaconfig", Context.MODE_PRIVATE)
    private val knownRoot = com.wuwaconfig.app.model.GamePaths.TARGET_DIR

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
        _treeUri = null
        prefs.edit().remove("saf_tree_uri").apply()
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    override val isConnected: Boolean
        get() = _treeUri != null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        val uri = _treeUri ?: return@withContext Result.failure(Exception("No SAF directory selected. Tap Pick Directory to choose the game config folder."))
        try {
            val doc = DocumentFile.fromTreeUri(context, uri)
            if (doc == null || !doc.exists() || !doc.isDirectory) {
                clearTreeUri()
                return@withContext Result.failure(Exception("SAF directory no longer accessible. Pick again."))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            clearTreeUri()
            Result.failure(Exception("SAF access error: ${e.message}"))
        }
    }

    override fun disconnect() {
        _treeUri = null
    }

    override suspend fun executeShellCommand(command: String): Result<String> {
        return Result.failure(Exception("Shell commands not available in SAF mode. Use ROOT, ADB, or Shizuku."))
    }

    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetDoc = resolveDocument(targetPath) ?: return@withContext Result.failure(Exception("Cannot resolve target path: $targetPath"))
            val bytes = File(sourcePath).readBytes()
            writeDocument(targetDoc, bytes)
            Result.success("Written to $targetPath")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            resolveOrCreateDocument(dirPath)
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fileExists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(path)
            Result.success(doc != null && doc.exists() && doc.isFile)
        } catch (_: Exception) {
            Result.success(false)
        }
    }

    override suspend fun listDirectory(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
            val files = doc.listFiles().map { it.name ?: it.uri.toString() }
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun backupFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
            val backupName = "${doc.name}.backup_${System.currentTimeMillis()}"
            val parent = doc.parentFile ?: return@withContext Result.failure(Exception("Cannot determine parent"))
            val backupDoc = parent.createFile("*/*", backupName)
                ?: return@withContext Result.failure(Exception("Cannot create backup"))
            val bytes = readDocumentBytes(doc)
            writeDocument(backupDoc, bytes)
            Result.success(backupDoc.uri.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val doc = resolveDocument(path) ?: return@withContext Result.failure(Exception("Not found: $path"))
            val text = context.contentResolver.openInputStream(doc.uri)
                ?.use { BufferedReader(InputStreamReader(it)).readText() }
                ?: return@withContext Result.failure(Exception("Cannot open: $path"))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readDocumentBytes(doc: DocumentFile): ByteArray {
        return context.contentResolver.openInputStream(doc.uri)
            ?.use { it.readBytes() }
            ?: throw Exception("Cannot read: ${doc.name}")
    }

    private fun writeDocument(doc: DocumentFile, data: ByteArray) {
        context.contentResolver.openOutputStream(doc.uri)
            ?.use { it.write(data) }
            ?: throw Exception("Cannot write: ${doc.name}")
    }

    private fun resolveDocument(path: String): DocumentFile? {
        val tree = _treeUri ?: return null
        val root = DocumentFile.fromTreeUri(context, tree) ?: return null
        val relative = path.removePrefix(knownRoot).trimStart('/')
        if (relative.isEmpty()) return root
        var current = root
        for (part in relative.split("/")) {
            current = current.findFile(part) ?: return null
        }
        return current
    }

    private fun resolveOrCreateDocument(path: String): DocumentFile {
        val tree = _treeUri ?: throw Exception("No SAF directory selected")
        val root = DocumentFile.fromTreeUri(context, tree) ?: throw Exception("Cannot access tree")
        val relative = path.removePrefix(knownRoot).trimStart('/')
        if (relative.isEmpty()) return root
        val parts = relative.split("/")
        var current = root
        for (part in parts) {
            var child = current.findFile(part)
            if (child == null) {
                child = current.createDirectory(part)
                    ?: throw Exception("Cannot create: $part")
            }
            current = child
        }
        return current
    }
}
