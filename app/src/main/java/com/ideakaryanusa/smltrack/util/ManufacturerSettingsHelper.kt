package com.ideakaryanusa.smltrack.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Kenapa ini penting: banyak HP Android di Indonesia (Xiaomi/MIUI, Oppo/ColorOS,
 * Vivo/FuntouchOS, dll) punya optimasi baterai vendor SENDIRI yang terpisah dari
 * pengaturan baterai bawaan Android. Foreground service + START_STICKY saja
 * SERING tidak cukup di HP-HP ini - servicenya tetap dibunuh diam-diam.
 * Satu-satunya cara yang benar-benar manjur: user manual whitelist app ini di
 * halaman khusus pabrikan (biasanya disebut "Autostart", "App yang tidak dibatasi
 * baterai", dsb). Fungsi di bawah mencoba buka halaman itu langsung; kalau gagal
 * (beda versi ROM, dsb), fallback ke halaman battery optimization Android standar.
 */
object ManufacturerSettingsHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openGenericBatterySettings(context)
        }
    }

    /**
     * Buka halaman autostart/battery vendor sesuai Build.MANUFACTURER.
     * Daftar intent ini terkumpul dari referensi umum yang dipakai banyak
     * developer Android - tidak semua ROM/versi pasti punya activity persis ini,
     * makanya selalu dibungkus try-catch dengan fallback ke Settings umum.
     */
    fun openManufacturerAutostartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()

        val candidates: List<Intent> = when {
            manufacturer.contains("xiaomi") -> listOf(
                intentFor("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                intentFor("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            )
            manufacturer.contains("oppo") -> listOf(
                intentFor("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                intentFor("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                intentFor("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            manufacturer.contains("vivo") -> listOf(
                intentFor("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                intentFor("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            manufacturer.contains("samsung") -> listOf(
                intentFor("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            )
            manufacturer.contains("oneplus") -> listOf(
                intentFor("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            manufacturer.contains("asus") -> listOf(
                intentFor("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
            )
            else -> emptyList()
        }

        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // coba kandidat berikutnya
            }
        }

        // Tidak ada yang cocok - arahkan ke halaman battery optimization Android standar.
        openGenericBatterySettings(context)
    }

    private fun intentFor(packageName: String, className: String): Intent {
        return Intent().apply {
            component = android.content.ComponentName(packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun openGenericBatterySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
