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
import java.io.File

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
        ConfigManager(getApplication(), { app.backend }, { _backupStorageDir.value })
    }

    private val prefs =
        application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

    private fun readBackupDir(): String = prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir

    /**
     * The backup directory, as observable state.
     *
     * It used to be a plain `prefs.getString` getter read during composition, which
     * is not observable: saving a new path wrote the pref and closed the dialog,
     * but nothing recomposed, so the Settings label and the dialog's seeded value
     * kept showing the OLD path until the user left and re-entered Settings.
     * MainViewModel already had a correct StateFlow for this that nothing called.
     */
    private val _backupStorageDir = MutableStateFlow(readBackupDir())
    val backupStorageDirFlow: StateFlow<String> = _backupStorageDir.asStateFlow()

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
        _backupStorageDir.value = newDir
        refreshBackups()
        LogRepository.add("Backup dir changed to $newDir")
    }

    fun initDownloadBackupDir() {
        if (prefs.getBoolean("setup_done", false) && prefs.contains("backup_dir")) return
        // DEFAULT to app-private storage, unconditionally.
        //
        // This used to be getExternalFilesDir("backups"), which resolves to
        // /storage/emulated/0/Android/data/<pkg>/files/backups — world-readable to
        // any app holding READ_EXTERNAL_STORAGE on API 26-29 (a normal, install-time
        // permission there, auto-granted with no dialog). That directory received
        // backups/{id}.json AND the collected plaintext Client.log. filesDir is
        // 0700 and cannot be traversed by another app on any API level, and it
        // needs no runtime grant at all.
        //
        // The copy of this function in MainViewModel carried exactly this comment
        // while being dead code, so the documented privacy fix was never actually
        // in effect. It has been deleted rather than kept as a second owner.
        //
        // Users who explicitly want backups in Downloads can still pick that
        // directory in Settings; it just is no longer the silent default.
        val targetDir = File(getApplication<Application>().filesDir, "backups")
        runCatching { targetDir.mkdirs() }
        changeBackupDir(targetDir.absolutePath)
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
