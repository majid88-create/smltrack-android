package com.ideakaryanusa.smltrack.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.ideakaryanusa.smltrack.util.SessionManager

/**
 * Ini lapisan pengaman TERPISAH dari mekanisme restart bawaan Android
 * (START_STICKY, onTaskRemoved). Di HP-HP dengan ROM yang agresif membunuh
 * background process, kadang service benar-benar mati total tanpa sempat
 * memicu callback restart apa pun. Watchdog ini jalan independen lewat
 * AlarmManager (yang punya jalur sendiri untuk bangun walau HP di Doze mode),
 * cek "kapan terakhir kali service ini melapor hidup (heartbeat)", dan kalau
 * sudah lewat ambang batas padahal user masih mengaktifkan tracking, service
 * di-restart paksa.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val session = SessionManager(context)

        if (session.trackingEnabled) {
            val elapsed = System.currentTimeMillis() - session.lastHeartbeatMillis
            if (elapsed > STALE_THRESHOLD_MS) {
                val serviceIntent = Intent(context, LocationTrackingService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        // Selalu jadwalkan ulang - AlarmManager.setExactAndAllowWhileIdle cuma sekali tembak.
        scheduleNext(context)
    }

    companion object {
        private const val STALE_THRESHOLD_MS = 20 * 60 * 1000L // 20 menit tanpa heartbeat = dianggap mati
        private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L  // cek tiap 15 menit
        private const val REQUEST_CODE = 4242

        fun scheduleNext(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS

            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            try {
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    // Tidak punya izin exact alarm (Android 12+ tanpa izin manual) -
                    // pakai inexact, tetap jauh lebih baik daripada tidak ada watchdog sama sekali.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
