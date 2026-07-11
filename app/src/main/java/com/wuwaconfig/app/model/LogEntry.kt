package com.wuwaconfig.app.model

import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class LogEntry(
    val message: String,
    val timestamp: String,
    val level: LogLevel,
    val id: Long = nextId(),
) {
    companion object {
        private val counter = AtomicLong(0)

        fun nextId() = counter.incrementAndGet()
    }
}
