package com.wuwaconfig.app.model

enum class AdbConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

data class AdbStatus(
    val state: AdbConnectionState = AdbConnectionState.DISCONNECTED,
    val port: Int = 0,
    val errorMessage: String = "",
    val isServiceRunning: Boolean = false
)
