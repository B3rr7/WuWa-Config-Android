package com.wuwaconfig.app.backend

enum class AccessMethod {
    ADB,
    SHIZUKU,
    ROOT,
    SAF,
}

data class BackendStatus(
    val method: AccessMethod = AccessMethod.ADB,
    val connected: Boolean = false,
    val port: Int = 0,
    val host: String = "",
    val errorMessage: String = "",
)

interface AccessBackend {
    val isConnected: Boolean

    suspend fun connect(): Result<Unit>

    fun disconnect()

    suspend fun executeShellCommand(command: String): Result<String>

    suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String>

    suspend fun ensureDirectoryExists(dirPath: String): Result<String>

    suspend fun fileExists(path: String): Result<Boolean>

    suspend fun listDirectory(path: String): Result<List<String>>

    suspend fun backupFile(path: String): Result<String>

    suspend fun readFile(path: String): Result<String>

    suspend fun readFileBytes(path: String): Result<ByteArray>
}
