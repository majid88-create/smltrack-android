package com.ideakaryanusa.smltrack.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ideakaryanusa.smltrack.util.SessionManager

/**
 * Menangani 2 skenario yang bisa mematikan tracking tanpa sepengetahuan user:
 * 1. HP restart (BOOT_COMPLETED) - semua service otomatis mati saat reboot.
 * 2. App di-update lewat Play Store/APK baru (MY_PACKAGE_REPLACED) - proses
 *    lama dimatikan sistem saat instalasi versi baru.
 *
 * Di kedua kasus, kalau user SEBELUMNYA mengaktifkan tracking (trackingEnabled,
 * bukan sekadar isLoggedIn - orang bisa login tapi tracking-nya lagi dimatikan
 * manual), service dan watchdog dinyalakan ulang otomatis.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action !in validActions) return

        val session = SessionManager(context)
        if (session.isLoggedIn() && session.trackingEnabled) {
            val serviceIntent = Intent(context, LocationTrackingService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            WatchdogReceiver.scheduleNext(context)
        }
    }
}
