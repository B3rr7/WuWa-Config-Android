package com.wuwaconfig.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.ProfileStore
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.model.PlayerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("ProfileViewModel requires WuWaConfigApp application")

    private val configManager: ConfigManager
        get() = ConfigManager(app, app.backend)

    private val profileStore: ProfileStore = app.profileStore

    private val _playerProfile = MutableStateFlow<PlayerProfile?>(null)
    val playerProfile: StateFlow<PlayerProfile?> = _playerProfile.asStateFlow()

    private val _profileLoading = MutableStateFlow(false)
    val profileLoading: StateFlow<Boolean> = _profileLoading.asStateFlow()

    private val _configModifyCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val configModifyCounts: StateFlow<Map<String, Int>> = _configModifyCounts.asStateFlow()

    init {
        val cached = profileStore.load()
        if (cached != null) {
            _playerProfile.value = cached
        }
    }

    private fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }

    fun loadConfigModifyCounts() {
        viewModelScope.launch {
            val result = configManager.readConfigModifyCounts()
            if (result.isSuccess) {
                _configModifyCounts.value = result.getOrThrow().associate { it.fileName to it.modifyCount }
            } else {
                _configModifyCounts.value = emptyMap()
                addLog("Modify counts unavailable: ${result.exceptionOrNull()?.message}", LogLevel.WARNING)
            }
        }
    }

    fun loadProfile(forceRefresh: Boolean = false) {
        if (_profileLoading.value) return
        if (!forceRefresh && _playerProfile.value != null) return
        viewModelScope.launch {
            if (forceRefresh) _playerProfile.value = null
            _profileLoading.value = true
            addLog(if (forceRefresh) "Refreshing player profile..." else "Reading player profile (read-only)...")
            try {
                val result = configManager.readProfile()
                if (result.isSuccess) {
                    val profile = result.getOrThrow()
                    _playerProfile.value = profile
                    profileStore.save(profile)
                    addLog("Profile loaded")
                    loadConfigModifyCounts()
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "loadProfile crashed", e)
            } finally {
                _profileLoading.value = false
            }
        }
    }
}
