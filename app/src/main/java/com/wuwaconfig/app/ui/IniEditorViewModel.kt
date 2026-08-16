package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.backend.computeMd5
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.extractHash
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IniEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("IniEditorViewModel requires WuWaConfigApp application")

    private val configManager: ConfigManager by lazy { ConfigManager(app, { app.backend }) }

    private val _editingFileName = MutableStateFlow<String?>(null)
    val editingFileName: StateFlow<String?> = _editingFileName.asStateFlow()

    private val _iniEditorContent = MutableStateFlow<String?>(null)
    val iniEditorContent: StateFlow<String?> = _iniEditorContent.asStateFlow()

    private val _iniEditorLoading = MutableStateFlow(false)
    val iniEditorLoading: StateFlow<Boolean> = _iniEditorLoading.asStateFlow()

    private val _iniEditorError = MutableStateFlow<String?>(null)
    val iniEditorError: StateFlow<String?> = _iniEditorError.asStateFlow()

    private val _iniEditorSuccess = MutableStateFlow<String?>(null)
    val iniEditorSuccess: StateFlow<String?> = _iniEditorSuccess.asStateFlow()

    private fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }

    fun clearIniEditorError() {
        _iniEditorError.value = null
    }

    fun clearIniEditorSuccess() {
        _iniEditorSuccess.value = null
    }

    fun readIniFile(fileName: String) {
        viewModelScope.launch {
            _iniEditorLoading.value = true
            _iniEditorError.value = null
            addLog("INI Editor: reading $fileName from device")
            configManager.readCurrentConfig(fileName).onSuccess { content ->
                _editingFileName.value = fileName
                _iniEditorContent.value = content
                addLog("INI Editor: $fileName loaded (${content.length} chars)", LogLevel.SUCCESS)
            }.onFailure { e ->
                _iniEditorContent.value = null
                _iniEditorError.value = "Failed to read $fileName: ${e.message}"
                addLog("INI Editor: failed to read $fileName: ${e.message}", LogLevel.ERROR)
            }
            _iniEditorLoading.value = false
        }
    }

    fun returnToFileList() {
        _editingFileName.value = null
        _iniEditorContent.value = null
        _iniEditorError.value = null
    }

    fun saveIniFile(content: String) {
        val fileName = _editingFileName.value ?: return
        viewModelScope.launch {
            _iniEditorLoading.value = true
            _iniEditorError.value = null
            addLog("INI Editor: saving $fileName to device")
            val preSnapshot = configManager.snapshotHashFile().getOrNull()
            configManager.pushSingleFile(fileName, content) {}
                .onSuccess {
                    addLog("INI Editor: $fileName pushed, refreshing hashes...", LogLevel.SUCCESS)
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { hashMsg ->
                        addLog("$fileName saved. $hashMsg", LogLevel.SUCCESS)
                        _iniEditorSuccess.value = "$fileName saved successfully"
                    }.onFailure { e ->
                        addLog("INI Editor: hash refresh warning: ${e.message}", LogLevel.WARNING)
                        _iniEditorSuccess.value = "$fileName saved (hash refresh: ${e.message})"
                    }
                }.onFailure { e ->
                    _iniEditorError.value = "Failed to save $fileName: ${e.message}"
                    addLog("INI Editor: failed to save $fileName: ${e.message}", LogLevel.ERROR)
                }
            _iniEditorLoading.value = false
        }
    }

    fun syncConfigHashes(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                addLog("Hash sync: checking device config hashes...")
                val hashContent = app.backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
                if (hashContent.isBlank()) {
                    addLog("Hash sync: no hash file found, creating fresh...")
                    configManager.refreshConfigHashes().onSuccess { addLog(it) }
                    onResult(true)
                    return@launch
                }
                var needsRefresh = false
                for (name in GamePaths.MONITORED_FILES) {
                    val actualHashResult = computeIniHash(name)
                    if (actualHashResult.isFailure) {
                        addLog("Hash sync: SKIPPING $name — cannot compute hash", LogLevel.ERROR)
                        needsRefresh = true
                        continue
                    }
                    val actualHash = actualHashResult.getOrThrow()
                    val storedHash = extractHash(hashContent, name)
                    if (storedHash != null && storedHash != actualHash) {
                        addLog("Hash sync: $name hash mismatch (stored=$storedHash, actual=$actualHash)", LogLevel.WARNING)
                        needsRefresh = true
                    } else if (storedHash == null) {
                        addLog("Hash sync: $name has no stored hash", LogLevel.WARNING)
                        needsRefresh = true
                    } else {
                        addLog("Hash sync: $name hash OK ($actualHash)")
                    }
                }
                if (needsRefresh) {
                    addLog("Hash sync: refreshing hashes on device...")
                    configManager.refreshConfigHashes().onSuccess { addLog(it) }
                }
                onResult(!needsRefresh)
            } catch (e: Exception) {
                addLog("Hash sync: error: ${e.message}", LogLevel.ERROR)
                onResult(false)
            }
        }
    }

    private suspend fun computeIniHash(name: String): Result<String> {
        val path = "${GamePaths.TARGET_DIR}/$name"
        val bytesResult = app.backend.readFileBytes(path)
        if (bytesResult.isFailure) {
            addLog("Hash sync: readFileBytes FAILED for $name: ${bytesResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            return Result.failure(bytesResult.exceptionOrNull()!!)
        }
        val bytes = bytesResult.getOrThrow()
        val hash = computeMd5(bytes)
        addLog("Hash sync: computed hash for $name = $hash (${bytes.size} bytes)")
        return Result.success(hash)
    }
}
