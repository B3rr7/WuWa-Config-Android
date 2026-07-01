package com.wuwaconfig.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.GachaApi
import com.wuwaconfig.app.config.LogParser
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GachaPollService : Service() {
    companion object {
        const val CHANNEL_ID = "gacha_poll"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.wuwaconfig.app.STOP_GACHA_POLL"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        LogRepository.add("GachaPollService: onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogRepository.add("GachaPollService: onStartCommand")
        if (intent?.action == ACTION_STOP) {
            LogRepository.add("GachaPollService: stopping via ACTION_STOP")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("WuWaConfig — Pity Tracker", "Scanning Client.log for Convene URL...")
        startForeground(NOTIFICATION_ID, notification)
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogRepository.add("GachaPollService: onDestroy")
        pollJob?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startPolling() {
        LogRepository.add("GachaPollService: starting polling (max 30 attempts)")
        pollJob = scope.launch {
            val app = application as WuWaConfigApp
            val backend = app.backend
            val configManager = ConfigManager(this@GachaPollService, backend, null)
            var attempts = 0
            val maxAttempts = 30

            while (attempts < maxAttempts) {
                attempts++
                updateNotification("Scanning Client.log (attempt $attempts/$maxAttempts)...")

                try {
                    val result = withContext(Dispatchers.IO) {
                        configManager.readClientLogTextWithMetadata {}
                    }
                    if (result.isSuccess) {
                        val (text, _) = result.getOrThrow()
                        val url = withContext(Dispatchers.Default) {
                            LogParser.extractConveneUrl(text)
                        }
                        if (url != null) {
                            LogRepository.add("GachaPollService: Convene URL found")
                            updateNotification("URL found! Fetching gacha history...")

                            val params = GachaApi.parseUrl(url)
                            if (params != null) {
                                    val data = GachaApi.fetchAllRecords(params)
                                    if (data.isSuccess) {
                                        val d = data.getOrThrow()
                                        LogRepository.add("GachaPollService: fetched ${d.totalPulls} records", LogLevel.SUCCESS)
                                        sendBroadcast(Intent("com.wuwaconfig.app.GACHA_DATA_READY").apply {
                                            putExtra("totalPulls", d.totalPulls)
                                            putExtra("fiveStars", d.fiveStars)
                                            putExtra("fourStars", d.fourStars)
                                            putExtra("json", Gson().toJson(d))
                                        })
                                        showNotification(
                                            "Gacha data ready!",
                                            "${d.totalPulls} pulls (${d.fiveStars}★5, ${d.fourStars}★4)"
                                        )
                                    } else {
                                        LogRepository.add("GachaPollService: fetch failed: ${data.exceptionOrNull()?.message}", LogLevel.ERROR)
                                        showNotification("Gacha fetch failed", data.exceptionOrNull()?.message ?: "Error")
                                    }
                            } else {
                                showNotification("URL parse failed", "Could not parse Convene URL")
                            }
                            stopSelf()
                            return@launch
                        }
                    }
                } catch (_: Exception) { }

                delay(10_000)
            }

            LogRepository.add("GachaPollService: polling exhausted after $maxAttempts attempts", LogLevel.WARNING)
            showNotification("Polling complete", "Convene URL not found after $maxAttempts attempts")
            stopSelf()
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification("WuWaConfig — Pity Tracker", text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pity Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background gacha URL polling"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
