package com.wuwaconfig.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.GachaApi
import com.wuwaconfig.app.config.GachaHistoryStore
import com.wuwaconfig.app.config.LogParser
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GachaRecord
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

    private val _gachaError = MutableStateFlow<String?>(null)
    val gachaError: StateFlow<String?> = _gachaError.asStateFlow()

    private var readJob: Job? = null

    private fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }

    init {
        // JSON file read + Gson parse — keep off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = GachaHistoryStore.load(getApplication())
            _gachaHistory.value = loaded
            _gachaHistoryAge.value = loaded?.let { GachaHistoryStore.ageHours(it) }
            _gachaHistoryStale.value = loaded?.let { GachaHistoryStore.isStale(it) } ?: false
        }
    }

    fun clearGachaHistory() {
        viewModelScope.launch(Dispatchers.IO) { GachaHistoryStore.delete(getApplication()) }
        _gachaHistory.value = null
        _gachaHistoryAge.value = null
        _gachaHistoryStale.value = false
        addLog("Gacha history cleared")
    }

    /**
     * How long ago the stored history was fetched, in hours, or null when the entry
     * predates `fetchedAt` and its age cannot be known.
     *
     * This replaced a countdown to [com.wuwaconfig.app.model.GachaHistoryEntry.expiresAt].
     * That field is now a *retention* deadline a year out, so counting down to it
     * would have read "expires in 8760h" — technically true and completely useless.
     * What the banner needs to say is how old the numbers are.
     */
    fun gachaHistoryAgeHours(): Long? = _gachaHistory.value?.let { GachaHistoryStore.ageHours(it) }

    /** True when the stored history is past its freshness window. */
    fun gachaHistoryIsStale(): Boolean = _gachaHistory.value?.let { GachaHistoryStore.isStale(it) } ?: false

    /**
     * Snapshot of [gachaHistoryAgeHours]. Reading `System.currentTimeMillis()`
     * straight from composition is an impure, non-snapshot read, so the age
     * froze at whatever value it had on the first frame — the UI must collect
     * this instead and call [refreshGachaHistoryAge] on a timer.
     */
    private val _gachaHistoryAge = MutableStateFlow<Long?>(null)
    val gachaHistoryAgeHours: StateFlow<Long?> = _gachaHistoryAge.asStateFlow()

    private val _gachaHistoryStale = MutableStateFlow(false)
    val gachaHistoryIsStaleFlow: StateFlow<Boolean> = _gachaHistoryStale.asStateFlow()

    fun refreshGachaHistoryAge() {
        _gachaHistoryAge.value = gachaHistoryAgeHours()
        _gachaHistoryStale.value = gachaHistoryIsStale()
    }

    /**
     * [GachaData.records] pre-grouped by `cardPoolType`. The screen previously
     * filtered the whole record list once per pool inside a LazyColumn content
     * lambda, i.e. O(pools x records) on every structural recomposition.
     */
    private val _recordsByPool = MutableStateFlow<Map<String, List<GachaRecord>>>(emptyMap())
    val recordsByPool: StateFlow<Map<String, List<GachaRecord>>> = _recordsByPool.asStateFlow()

    private fun publishGachaData(data: GachaData?) {
        _gachaData.value = data
        _recordsByPool.value = data?.records?.groupBy { it.cardPoolType } ?: emptyMap()
    }

    fun restoreGachaFromHistory() {
        val entry = _gachaHistory.value ?: return
        try {
            val type = object : TypeToken<GachaData>() {}.type
            val data =
                GachaHistoryStore.gson.fromJson<GachaData>(entry.fullDataJson, type)
                    ?: run {
                        addLog("Failed to restore history: stored data is empty or corrupt")
                        return
                    }
            // Guard against legacy caches where predictions list was null/absent.
            val safeData = data.copy(predictions = data.predictions ?: emptyList())
            publishGachaData(safeData)
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
                    publishGachaData(null)
                    _conveneUrlLoading.value = true
                    _gachaError.value = null
                    var attempt = 1
                    while (attempt <= retryCount) {
                        addLog("Reading Client.log for Convene URL (attempt $attempt/$retryCount)...")
                        val result =
                            configManager.readClientLogTextWithMetadata { pct ->
                                if (pct % 25 == 0 && attempt == 1) addLog("Reading... $pct%")
                            }
                        if (result.isFailure) {
                            val msg = result.exceptionOrNull()?.message ?: "unknown"
                            addLog("Failed to read Client.log: $msg")
                            _gachaError.value = "Failed to read Client.log: $msg"
                            return@launch
                        }
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
                        if (attempt < retryCount) {
                            addLog("URL not found yet — retrying in 10s...")
                            kotlinx.coroutines.delay(10_000)
                        } else {
                            addLog("No Convene URL found after $retryCount attempts.")
                            addLog("Open Convene History in-game, wait a moment, then tap again.")
                            _gachaError.value =
                                "No Convene URL found after $retryCount attempts. Open Convene History in-game, wait a moment, then tap again."
                        }
                        attempt++
                    }
                } catch (e: Exception) {
                    addLog("CRASH: ${e.message}")
                    Log.e("WuWaConfig", "extractConveneUrl crashed", e)
                    _gachaError.value = "Failed to read Client.log: ${e.message}"
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
        _gachaError.value = null
        addLog("Reading stopped")
    }

    fun clearGachaError() {
        _gachaError.value = null
    }

    private suspend fun fetchGachaData(url: String) {
        _gachaLoading.value = true
        _gachaError.value = null
        addLog("Parsing gacha URL...")
        try {
            val params = GachaApi.parseUrl(url)
            if (params == null) {
                addLog("Failed to parse gacha URL")
                _gachaError.value = "Could not parse the Convene URL. Try extracting it again."
                return
            }
            addLog("Fetching gacha records from server...")
            val result =
                withContext(Dispatchers.IO) {
                    GachaApi.fetchAllRecords(params)
                }
            if (result.isSuccess) {
                val data = result.getOrThrow()
                publishGachaData(data)
                withContext(Dispatchers.IO) {
                    _gachaHistory.value = GachaHistoryStore.save(getApplication(), data)
                }
                addLog("Loaded ${data.totalPulls} pulls (${data.fiveStars}★5, ${data.fourStars}★4)")
                if (data.poolsWithData.isNotEmpty()) {
                    addLog("Pools: ${data.poolsWithData.size} with records")
                }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                addLog("API failed: $msg")
                _gachaError.value = "Failed to fetch gacha records: $msg"
            }
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("WuWaConfig", "fetchGachaData crashed", e)
            _gachaError.value = "Failed to fetch gacha records: ${e.message}"
        } finally {
            _gachaLoading.value = false
        }
    }
}
