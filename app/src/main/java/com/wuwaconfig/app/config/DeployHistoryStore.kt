package com.wuwaconfig.app.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.model.DeployComparison
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.LogInfo
import java.io.File

object DeployHistoryStore {
    private const val MAX_RECORDS = 20
    private val gson = Gson()
    private var records: MutableList<DeployRecord> = mutableListOf()
    private var storeFile: File? = null

    fun init(context: Context) {
        storeFile = File(context.filesDir, "deploy_history.json")
        load()
    }

    fun addRecord(record: DeployRecord) {
        records.add(0, record)
        if (records.size > MAX_RECORDS) records.removeAt(records.size - 1)
        save()
    }

    fun getLatestDeploy(): DeployRecord? = records.firstOrNull()

    fun getRecord(id: String): DeployRecord? = records.find { it.id == id }

    fun getAllRecords(): List<DeployRecord> = records.toList()

    fun updateOutcome(
        id: String,
        outcome: LogInfo,
        snippet: String = "",
    ): Boolean {
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
        save()
        return true
    }

    fun compare(id: String): DeployComparison? {
        val record = getRecord(id) ?: return null
        return if (record.hasOutcome) record.comparison() else null
    }

    fun deleteRecord(id: String) {
        records.removeAll { it.id == id }
        save()
    }

    fun clear() {
        records.clear()
        save()
    }

    private fun load() {
        try {
            val file = storeFile ?: return
            if (!file.exists()) return
            val text = file.readText().trim()
            if (text.isEmpty()) return
            val type = object : TypeToken<List<DeployRecord>>() {}.type
            val loaded: List<DeployRecord> = gson.fromJson(text, type) ?: return
            records = loaded.toMutableList()
        } catch (_: Exception) {
            records = mutableListOf()
        }
    }

    private fun save() {
        try {
            storeFile?.writeText(gson.toJson(records))
        } catch (_: Exception) {
        }
    }
}
