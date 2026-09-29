package com.wuwaconfig.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Opens the settings screen where the user can stop the ROM from killing a
 * background process.
 *
 * Shizuku's UserService is a process this app spawns, not a foreground service.
 * Chinese ROMs (HyperOS/MIUI, OriginOS, ColorOS, MagicOS) ship an app-launch
 * manager that kills background processes and, by default, keeps an app out of
 * autostart. When that happens the shell service never starts and the user sees
 * "UserService bind timed out" with nothing to act on.
 *
 * Every vendor key below is a private, undocumented activity: the intents are
 * wrapped individually and the first one that resolves wins, so a miss just
 * falls through to the next rather than crashing.
 */
object RomBackgroundSettings {
    /**
     * Vendor autostart / app-launch-manager activities, tried in order. These
     * are the only screens where "Autostart" or "后台弹出" lives; the AOSP
     * battery-optimisation screen below does not expose it.
     */
    private val VENDOR_COMPONENTS =
        listOf(
            // Xiaomi / HyperOS / MIUI
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            // Xiaomi fallback
            "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings",
            // vivo / OriginOS
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            // OPPO / ColorOS / OnePlus / realme
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.oplus.battery" to "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
            // Huawei / EMUI / HarmonyOS
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            // Samsung
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            // Letv / cross-brand
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        )

    /**
     * True when the OS itself considers this app battery-optimised. Only
     * meaningful above API 23, which is always true here (minSdk 26).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Sends the user to the most specific screen available, falling back through
     * vendor autostart screens to the AOSP battery-optimisation list and finally
     * to this app's own details page.
     *
     * The AOSP fallback uses `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`
     * (the *list*) rather than `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     * (the per-app dialog): the request variant needs `REQUEST_IGNORE_BATTERY_
     * OPTIMIZATIONS` and is frequently a no-op or a crash on vendor ROMs, while
     * the list always opens.
     */
    fun open(context: Context): Boolean {
        vendorAutostartIntent(context)?.let { intent ->
            runCatching { context.startActivity(intent) }.onSuccess { return true }
        }
        batteryOptimizationListIntent()?.let { intent ->
            runCatching { context.startActivity(intent) }.onSuccess { return true }
        }
        return runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }.isSuccess
    }

    private fun vendorAutostartIntent(context: Context): Intent? {
        for ((pkg, cls) in VENDOR_COMPONENTS) {
            val intent =
                Intent().apply {
                    component = ComponentName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            if (context.packageManager.resolveActivity(intent, 0) != null) return intent
        }
        return null
    }

    private fun batteryOptimizationListIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val intent =
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
