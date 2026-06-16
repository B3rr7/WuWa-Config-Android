package com.wuwaconfig.app

import android.app.Application
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.RootBackend
import com.wuwaconfig.app.adb.AdbCrypto

class WuWaConfigApp : Application() {
    lateinit var adbCrypto: AdbCrypto
        private set

    private var _backend: AccessBackend? = null
    val backend: AccessBackend get() = _backend ?: createBackend(currentMethod)

    var currentMethod: AccessMethod = AccessMethod.ADB
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        adbCrypto = AdbCrypto(this)
        _backend = null
    }

    fun switchTo(method: AccessMethod): AccessBackend {
        currentMethod = method
        _backend?.disconnect()
        _backend = null
        val newBackend = createBackend(method)
        _backend = newBackend
        return newBackend
    }

    private fun createBackend(method: AccessMethod): AccessBackend {
        return when (method) {
            AccessMethod.ADB -> AdbBackend(adbCrypto)
            AccessMethod.ROOT -> RootBackend()
        }
    }

    companion object {
        lateinit var instance: WuWaConfigApp
            private set
    }
}
