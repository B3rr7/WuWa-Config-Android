package com.wuwaconfig.app.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.model.DeployComparison
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.LogInfo
import java.io.File

class DeployHistoryStore(private val storeFile: File) {
    private val gson = Gson()
    private var records: MutableList<DeployRecord> = mutableListOf()
    private val lock = Any()

    init {
        load()
    }

    fun addRecord(record: DeployRecord) {
        synchronized(lock) {
            records.add(0, record)
            if (records.size > MAX_RECORDS) records.removeAt(records.size - 1)
            saveLocked()
        }
    }

    fun getLatestDeploy(): DeployRecord? = synchronized(lock) { records.firstOrNull() }

    fun getRecord(id: String): DeployRecord? = synchronized(lock) { records.find { it.id == id } }

    fun getAllRecords(): List<DeployRecord> = synchronized(lock) { records.toList() }

    fun updateOutcome(
        id: String,
        outcome: LogInfo,
        snippet: String = "",
    ): Boolean {
        return synchronized(lock) {
            val idx = records.indexOfFirst { it.id == id }
            if (idx < 0) return false
            records[idx] =
                records[idx].copy(
                    outcomeFps = outcome.fpsActual,
                    outcomeThermal = outcome.thermalEvents,
                    outcomeOom = outcome.gpuOom,
                    outcomeDrops = outcome.dropFrames,
                    outcomeTimestamp = System.currentTimeMillis(),
                    baselineClientLogSnippet = if (records[idx].baselineClientLogSnippet.isEmpty()) snippet else records[idx].baselineClientLogSnippet,
                )
            saveLocked()
            return true
        }
    }

    fun compare(id: String): DeployComparison? {
        val record = getRecord(id) ?: return null
        return if (record.hasOutcome) record.comparison() else null
    }

    fun deleteRecord(id: String) {
        synchronized(lock) {
            records.removeAll { it.id == id }
            saveLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            records.clear()
            saveLocked()
        }
    }

    private fun load() {
        try {
            if (!storeFile.exists()) return
            val text = storeFile.readText().trim()
            if (text.isEmpty()) return
            val type = object : TypeToken<List<DeployRecord>>() {}.type
            val loaded: List<DeployRecord> = gson.fromJson(text, type) ?: return
            synchronized(lock) { records = loaded.toMutableList() }
        } catch (_: Exception) {
            synchronized(lock) { records = mutableListOf() }
        }
    }

    private fun saveLocked() {
        try {
            storeFile.writeText(gson.toJson(records))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MAX_RECORDS = 20
    }
}
