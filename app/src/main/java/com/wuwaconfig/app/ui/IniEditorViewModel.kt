package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.HashSync
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IniEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("IniEditorViewModel requires WuWaConfigApp application")

    private val configManager: ConfigManager by lazy { ConfigManager(app, { app.backend }) }

    private val hashSync: HashSync by lazy { HashSync({ app.backend }, configManager) }

    private val ops get() = app.deviceOps

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
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // DeviceOps only rethrows CancellationException; anything else
                // escaping here would crash the process from the main thread.
                _iniEditorContent.value = null
                _iniEditorError.value = "Failed to read $fileName: ${e.message}"
                addLog("INI Editor: crashed reading $fileName: ${e.message}", LogLevel.ERROR)
            } finally {
                _iniEditorLoading.value = false
                ops.setApplying(false)
            }
        }
    }

    fun returnToFileList() {
        _editingFileName.value = null
        _iniEditorContent.value = null
        _iniEditorError.value = null
    }

    fun saveIniFile(content: String) {
        val fileName = _editingFileName.value ?: return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                _iniEditorLoading.value = true
                _iniEditorError.value = null
                addLog("INI Editor: saving $fileName to device")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                configManager.pushSingleFile(fileName, content, onProgress = {})
                    .onSuccess {
                        _iniEditorContent.value = content
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _iniEditorError.value = "Failed to save $fileName: ${e.message}"
                addLog("INI Editor: crashed saving $fileName: ${e.message}", LogLevel.ERROR)
            } finally {
                _iniEditorLoading.value = false
                ops.setApplying(false)
            }
        }
    }

    /**
     * Unified device-hash check (shared with the deploy pipeline).
     * [onResult] receives `true` when hashes were out of sync and a refresh ran.
     */
    fun syncConfigHashes(onResult: (Boolean) -> Unit = {}) {
        if (ops.isApplying.value) {
            // launchBackendOp would silently drop this; surface it instead so the
            // user knows the check did not run rather than seeing a stale badge.
            addLog("INI Editor: hash sync skipped — another device operation is running", LogLevel.WARNING)
            _iniEditorError.value = "Another device operation is running — hash sync skipped. Reopen the file to retry."
            onResult(false)
            return
        }
        ops.launchBackendOp(managesBusyFlag = false) {
            try {
                onResult(hashSync.syncIfNeeded())
            } catch (e: Exception) {
                addLog("Hash sync: error: ${e.message}", LogLevel.ERROR)
                onResult(false)
            }
        }
    }
}
