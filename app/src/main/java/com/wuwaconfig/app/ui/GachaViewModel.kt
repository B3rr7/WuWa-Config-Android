package com.wuwaconfig.app.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.GachaApi
import com.wuwaconfig.app.config.GachaHistoryStore
import com.wuwaconfig.app.config.LogParser
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GachaViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("GachaViewModel requires WuWaConfigApp application")

    private val configManager: ConfigManager by lazy { ConfigManager(app, { app.backend }) }

    private val _conveneUrl = MutableStateFlow<String?>(null)
    val conveneUrl: StateFlow<String?> = _conveneUrl.asStateFlow()

    private val _conveneUrlLoading = MutableStateFlow(false)
    val conveneUrlLoading: StateFlow<Boolean> = _conveneUrlLoading.asStateFlow()

    private val _gachaData = MutableStateFlow<GachaData?>(null)
    val gachaData: StateFlow<GachaData?> = _gachaData.asStateFlow()

    private val _gachaLoading = MutableStateFlow(false)
    val gachaLoading: StateFlow<Boolean> = _gachaLoading.asStateFlow()

    private val _gachaHistory = MutableStateFlow<GachaHistoryEntry?>(null)
    val gachaHistory: StateFlow<GachaHistoryEntry?> = _gachaHistory.asStateFlow()

    private var readJob: Job? = null

    private fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }

    private val gachaReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val json = intent.getStringExtra("json") ?: return
                try {
                    val type = object : TypeToken<GachaData>() {}.type
                    val data = Gson().fromJson<GachaData>(json, type)
                    _gachaData.value = data
                    _conveneUrl.value = "found"
                    viewModelScope.launch(Dispatchers.IO) {
                        GachaHistoryStore.save(getApplication(), data)
                        _gachaHistory.value = GachaHistoryStore.load(getApplication())
                        addLog("Background poll: loaded ${data.totalPulls} pulls (${data.fiveStars}★5)")
                    }
                } catch (_: Exception) {
                }
            }
        }

    init {
        try {
            LocalBroadcastManager.getInstance(getApplication()).registerReceiver(
                gachaReceiver,
                IntentFilter("com.wuwaconfig.app.GACHA_DATA_READY"),
            )
        } catch (_: Exception) {
        }
        _gachaHistory.value = GachaHistoryStore.load(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        try {
            LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(gachaReceiver)
        } catch (_: Exception) {
        }
    }

    fun clearGachaHistory() {
        GachaHistoryStore.delete(getApplication())
        _gachaHistory.value = null
        addLog("Gacha history cleared")
    }

    fun gachaHistoryRemainingHours(): Long = GachaHistoryStore.getRemainingHours(getApplication())

    fun restoreGachaFromHistory() {
        val entry = _gachaHistory.value ?: return
        try {
            val type = object : TypeToken<GachaData>() {}.type
            val data = Gson().fromJson<GachaData>(entry.fullDataJson, type)
            _gachaData.value = data
            addLog("Restored history: ${data.totalPulls} pulls")
        } catch (e: Exception) {
            addLog("Failed to restore history: ${e.message}")
        }
    }

    fun extractConveneUrl(retryCount: Int = 6) {
        if (_conveneUrlLoading.value || _gachaLoading.value) return
        readJob =
            viewModelScope.launch {
                try {
                    _conveneUrl.value = null
                    _gachaData.value = null
                    _conveneUrlLoading.value = true
                    var remaining = retryCount
                    while (remaining >= 0) {
                        addLog(
                            "Reading Client.log for Convene URL${if (remaining < retryCount) {
                                " (attempt ${retryCount - remaining + 1}/$retryCount)"
                            } else {
                                ""
                            }}...",
                        )
                        try {
                            val result =
                                configManager.readClientLogTextWithMetadata { pct ->
                                    if (pct % 25 == 0 && remaining == retryCount) addLog("Reading... $pct%")
                                }
                            if (result.isSuccess) {
                                val (text, _) = result.getOrThrow()
                                val url =
                                    withContext(Dispatchers.Default) {
                                        LogParser.extractConveneUrl(text)
                                    }
                                if (url != null) {
                                    addLog("Found Convene URL")
                                    _conveneUrl.value = url
                                    _conveneUrlLoading.value = false
                                    fetchGachaData(url)
                                    return@launch
                                }
                            }
                            if (remaining > 0) {
                                addLog("URL not found yet — retrying in 10s...")
                                kotlinx.coroutines.delay(10_000)
                            } else {
                                addLog("No Convene URL found after $retryCount attempts.")
                                addLog("Open Convene History in-game, wait a moment, then tap again.")
                            }
                        } catch (e: Exception) {
                            addLog("CRASH: ${e.message}")
                            Log.e("WuWaConfig", "extractConveneUrl crashed", e)
                            break
                        }
                        remaining--
                    }
                } finally {
                    _conveneUrlLoading.value = false
                    readJob = null
                }
            }
    }

    fun stopReading() {
        if (readJob == null && !_conveneUrlLoading.value && !_gachaLoading.value) return
        readJob?.cancel()
        readJob = null
        _conveneUrlLoading.value = false
        _gachaLoading.value = false
        addLog("Reading stopped")
    }

    private suspend fun fetchGachaData(url: String) {
        _gachaLoading.value = true
        addLog("Parsing gacha URL...")
        try {
            val params = GachaApi.parseUrl(url)
            if (params == null) {
                addLog("Failed to parse gacha URL")
                return
            }
            addLog("Fetching gacha records from server...")
            val result =
                withContext(Dispatchers.IO) {
                    GachaApi.fetchAllRecords(params)
                }
            if (result.isSuccess) {
                val data = result.getOrThrow()
                _gachaData.value = data
                GachaHistoryStore.save(getApplication(), data)
                _gachaHistory.value = GachaHistoryStore.load(getApplication())
                addLog("Loaded ${data.totalPulls} pulls (${data.fiveStars}★5, ${data.fourStars}★4)")
                if (data.poolsWithData.isNotEmpty()) {
                    addLog("Pools: ${data.poolsWithData.size} with records")
                }
            } else {
                addLog("API failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("WuWaConfig", "fetchGachaData crashed", e)
        } finally {
            _gachaLoading.value = false
        }
    }
}
