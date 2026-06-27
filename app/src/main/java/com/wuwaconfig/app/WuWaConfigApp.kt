package com.wuwaconfig.app

import android.app.Application
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.RootBackend
import com.wuwaconfig.app.backend.SafBackend
import com.wuwaconfig.app.backend.ShizukuBackend
import kotlinx.coroutines.flow.MutableStateFlow

class WuWaConfigApp : Application() {
    lateinit var adbCrypto: AdbCrypto
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

    override fun onCreate() {
        super.onCreate()
        adbCrypto = AdbCrypto(this)
        instance = this
        _backend = null
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

    val backgroundImageUri = MutableStateFlow<String?>(null)
    val backgroundVideoUri = MutableStateFlow<String?>(null)
    val backgroundOpacity = MutableStateFlow(0.25f)

    companion object {
        lateinit var instance: WuWaConfigApp
            private set
    }
}
