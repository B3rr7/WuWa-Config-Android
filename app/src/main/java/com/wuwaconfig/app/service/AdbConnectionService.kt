package com.wuwaconfig.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository

class AdbConnectionService : Service() {
    companion object {
        const val CHANNEL_ID = "adb_connection"
        const val NOTIFICATION_ID = 1

        /**
         * Safety net for the wake lock below: a process kill that skips
         * [onDestroy] would otherwise leak it. Set well beyond any realistic
         * ADB session so it never fires in practice.
         */
        private const val WAKELOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        LogRepository.add("AdbConnectionService: onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        LogRepository.add("AdbConnectionService: onStartCommand")
        acquireWakeLock()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        // Connection state is owned by DeployHistoryViewModel (sole start/stop
        // authority). NOT_STICKY avoids an orphan "ADB connection active"
        // notification if the system kills and restarts us with a null intent
        // when no ADB socket exists.
        return START_NOT_STICKY
    }

    /**
     * API 34+ invokes this when the `dataSync` foreground-service budget is
     * exhausted (~6h in 24h). An app that targets SDK 35+ and does NOT override
     * it is CRASHED by the platform. `START_NOT_STICKY` does not protect us
     * here — this is a system-initiated timeout, not a process restart, and a
     * long-lived wireless-ADB session is exactly the shape that exhausts it.
     *
     * Safe to declare unconditionally: the platform simply never calls it below
     * API 34, and `minSdk` here is 26.
     */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        LogRepository.add(
            "AdbConnectionService: onTimeout(startId=$startId, fgsType=$fgsType) — dataSync budget exhausted, stopping service",
            LogLevel.WARNING,
        )
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogRepository.add("AdbConnectionService: onDestroy")
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * A `dataSync` foreground service does not exempt the app from Doze/suspend,
     * so the keepalive heartbeat in AdbClient (a coroutine `delay` on a
     * ViewModel scope) can stop being scheduled and the wireless-ADB socket dies
     * silently while this notification is still up. A partial wake lock keeps
     * the CPU alive for the connection lifetime.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock =
            powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WuWaConfig:adb")
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        try {
            if (lock.isHeld) lock.release()
        } catch (e: Exception) {
            LogRepository.add("AdbConnectionService: wake lock release failed: ${e.message}", LogLevel.WARNING)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "ADB Connection",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Maintains ADB wireless debugging connection"
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WuWaConfig")
            .setContentText("ADB connection active")
            // res/ ships only adaptive launcher mipmaps (ic_launcher /
            // ic_launcher_round), which are NOT valid notification small icons —
            // the platform requires a monochrome, alpha-only silhouette. Left as
            // the framework silhouette until a real drawable exists.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
