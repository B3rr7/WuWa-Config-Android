package com.wuwaconfig.app.model

enum class LogLevel {
    INFO, SUCCESS, WARNING, ERROR
}

data class LogEntry(
    val message: String,
    val timestamp: String,
    val level: LogLevel
)
