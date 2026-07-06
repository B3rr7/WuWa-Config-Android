package com.wuwaconfig.app.model

data class AppSettings(
    val autoDetectPort: Boolean = true,
    val adbPort: Int = 0,
    val backupBeforeApply: Boolean = true,
    val overwriteExisting: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showNotifications: Boolean = true,
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
