package com.wuwaconfig.app

import android.app.Application
import android.os.Environment
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.RootBackend
import com.wuwaconfig.app.backend.SafBackend
import com.wuwaconfig.app.backend.ShizukuBackend
import com.wuwaconfig.app.config.ConfigGenerator
import com.wuwaconfig.app.config.CvarDatabase
import com.wuwaconfig.app.config.DeployHistoryStore
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class WuWaConfigApp : Application() {
    lateinit var adbCrypto: AdbCrypto
        private set

    lateinit var cvarDatabase: CvarDatabase
        private set

    lateinit var configGenerator: ConfigGenerator
        private set

    private var _backend: AccessBackend? = null
    private val backendLock = Any()
    val backend: AccessBackend get() {
        synchronized(backendLock) {
            if (_backend == null) {
                _backend = createBackend(currentMethod)
            }
            return _backend!!
        }
    }

    var currentMethod: AccessMethod = AccessMethod.ADB
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        adbCrypto = AdbCrypto(this)
        instance = this
        _backend = null
        LogRepository.init()
        cleanupOldClientLogs()
        cvarDatabase = CvarDatabase(assets)
        configGenerator = ConfigGenerator(cvarDatabase)
        appScope.launch { cvarDatabase.load() }
        DeployHistoryStore.init(this)
    }

    fun switchTo(method: AccessMethod): AccessBackend {
        synchronized(backendLock) {
            currentMethod = method
            _backend?.disconnect()
            _backend = null
            val newBackend = createBackend(method)
            _backend = newBackend
            return newBackend
        }
    }

    private fun createBackend(method: AccessMethod): AccessBackend {
        return when (method) {
            AccessMethod.ADB -> AdbBackend(adbCrypto)
            AccessMethod.SHIZUKU -> ShizukuBackend()
            AccessMethod.ROOT -> RootBackend()
            AccessMethod.SAF -> SafBackend(this).also { it.restoreTreeUri() }
        }
    }

    private fun cleanupOldClientLogs() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val dirs =
            listOf(
                File(filesDir, "backups"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WuWaConfig"),
            )
        for (dir in dirs) {
            val file = File(dir, "Client.log")
            if (file.exists() && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    val backgroundImageUri = MutableStateFlow<String?>(null)
    val backgroundVideoUri = MutableStateFlow<String?>(null)
    val backgroundOpacity = MutableStateFlow(0.25f)

    companion object {
        lateinit var instance: WuWaConfigApp
            private set
    }
}
