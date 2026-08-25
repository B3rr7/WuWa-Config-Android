package com.wuwaconfig.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.PREFS_NAME
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup CRUD and backup-directory preferences — extracted from
 * DeployHistoryViewModel. Device work is serialized through the app-scoped
 * [DeviceOps].
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("BackupViewModel requires WuWaConfigApp application")

    private val ops = app.deviceOps

    val configManager: ConfigManager by lazy {
        ConfigManager(getApplication(), { app.backend }, backupStorageDir)
    }

    private val prefs =
        application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

    val backupStorageDir: String
        get() = prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir

    private val _backups = MutableStateFlow<List<ConfigBackup>>(emptyList())
    val backups: StateFlow<List<ConfigBackup>> = _backups.asStateFlow()

    private val _backupFeedback = MutableStateFlow<String?>(null)
    val backupFeedback: StateFlow<String?> = _backupFeedback.asStateFlow()

    fun clearBackupFeedback() {
        _backupFeedback.value = null
    }

    init {
        refreshBackups()
    }

    fun changeBackupDir(newDir: String) {
        prefs.edit().putString("backup_dir", newDir).apply()
        refreshBackups()
        LogRepository.add("Backup dir changed to $newDir")
    }

    fun initDownloadBackupDir() {
        if (prefs.getBoolean("setup_done", false) && prefs.contains("backup_dir")) return
        val targetDir = getApplication<Application>().getExternalFilesDir("backups")
        if (targetDir != null) {
            targetDir.mkdirs()
            changeBackupDir(targetDir.absolutePath)
        }
    }

    /** Re-lists backups from disk off the main thread. */
    fun refreshBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = configManager.getLocalBackups()
            _backups.value = list
        }
    }

    private suspend fun loadBackups() {
        _backups.value = withContext(Dispatchers.IO) { configManager.getLocalBackups() }
    }

    fun createBackup(
        name: String,
        selectedFiles: Set<String>? = null,
    ) {
        if (ops.isApplying.value || !app.backendStatusValue.connected) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                LogRepository.add("Creating backup: $name...")
                val result = configManager.createBackup(name, selectedFiles = selectedFiles)
                if (result.isSuccess) {
                    LogRepository.add("Backup created", LogLevel.SUCCESS)
                    _backupFeedback.value = "Backup '$name' created (${selectedFiles?.size ?: 5} files)"
                    loadBackups()
                } else {
                    _backupFeedback.value = "Backup failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: SecurityException) {
                Log.e("BackupViewModel", "createBackup permission denied", e)
                _backupFeedback.value = "Permission denied — check Shizuku/ADB authorization."
            } catch (e: java.io.IOException) {
                Log.e("BackupViewModel", "createBackup I/O error", e)
                _backupFeedback.value = "I/O error: ${e.message}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BackupViewModel", "createBackup crashed", e)
                _backupFeedback.value = "Backup failed: ${e.message}"
            } finally {
                ops.setApplying(false)
            }
        }
    }

    fun restoreBackup(
        backup: ConfigBackup,
        selectedFiles: Set<String>? = null,
    ) {
        if (ops.isApplying.value || !app.backendStatusValue.connected) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                LogRepository.add("Restoring backup: ${backup.name}...")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val result = configManager.restoreBackup(backup, { msg -> LogRepository.add(msg) }, selectedFiles = selectedFiles)
                if (result.isSuccess) {
                    LogRepository.add("SUCCESS: ${result.getOrThrow()}", LogLevel.SUCCESS)
                    _backupFeedback.value = "Backup '${backup.name}' restored"
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { LogRepository.add(it) }
                        .onFailure { e -> LogRepository.add("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    _backupFeedback.value = "Restore failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: SecurityException) {
                Log.e("BackupViewModel", "restoreBackup permission denied", e)
                _backupFeedback.value = "Permission denied — check Shizuku/ADB authorization."
            } catch (e: java.io.IOException) {
                Log.e("BackupViewModel", "restoreBackup I/O error", e)
                _backupFeedback.value = "I/O error: ${e.message}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BackupViewModel", "restoreBackup crashed", e)
                _backupFeedback.value = "Restore failed: ${e.message}"
            } finally {
                ops.setApplying(false)
                loadBackups()
            }
        }
    }

    fun deleteBackup(backup: ConfigBackup) {
        ops.launchBackendOp(managesBusyFlag = false) {
            try {
                LogRepository.add("Deleting backup: ${backup.name}...")
                configManager.deleteLocalBackup(backup)
                loadBackups()
                LogRepository.add("Backup deleted", LogLevel.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("CRASH: ${e.message}", LogLevel.ERROR)
                Log.e("BackupViewModel", "deleteBackup crashed", e)
            }
        }
    }
}
