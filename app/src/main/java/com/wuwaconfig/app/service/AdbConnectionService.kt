package com.wuwaconfig.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository

class AdbConnectionService : Service() {
    companion object {
        const val CHANNEL_ID = "adb_connection"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        LogRepository.add("AdbConnectionService: onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogRepository.add("AdbConnectionService: onStartCommand")
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogRepository.add("AdbConnectionService: onDestroy")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ADB Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains ADB wireless debugging connection"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WuWaP42")
            .setContentText("ADB connection active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
