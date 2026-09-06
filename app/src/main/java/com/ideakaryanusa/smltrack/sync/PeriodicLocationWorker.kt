package com.ideakaryanusa.smltrack.sync

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ideakaryanusa.smltrack.data.AppDatabase
import com.ideakaryanusa.smltrack.data.TraceLogEntity
import com.ideakaryanusa.smltrack.util.LocationEngine
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * JARING PENGAMAN KETIGA.
 *
 * Lapisan 1 = foreground service (utama, tiap 1 menit)
 * Lapisan 2 = watchdog alarm (restart service kalau mati)
 * Lapisan 3 = worker ini
 *
 * Kenapa masih perlu lapisan 3: di sebagian kecil HP dengan ROM sangat agresif,
 * ada kemungkinan foreground service DAN alarm sama-sama diblokir, tapi
 * WorkManager (yang pakai JobScheduler bawaan sistem) masih diizinkan jalan
 * karena dianggap "pekerjaan sistem yang wajar". Worker ini merekam minimal
 * 1 titik lokasi tiap ~15 menit sebagai data cadangan.
 *
 * Jadi skenario terburuk bukan lagi "tidak ada data sama sekali", melainkan
 * "data lebih jarang (tiap 15 menit, bukan tiap 1 menit)" - masih jauh lebih
 * berguna untuk pengawasan lapangan daripada kosong.
 */
class PeriodicLocationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionManager(applicationContext)

        // Kalau user memang mematikan tracking, jangan diam-diam merekam.
        if (!session.trackingEnabled || !session.isLoggedIn()) return Result.success()

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return Result.success()

        val engine = LocationEngine(applicationContext)
        if (!engine.isLocationEnabled()) return Result.success()

        val location = try {
            withTimeout(LOCATION_TIMEOUT_MS) { awaitSingleLocation(engine) }
        } catch (e: TimeoutCancellationException) {
            engine.stop()
            null
        } catch (e: Exception) {
            engine.stop()
            null
        }

        if (location != null) {
            val db = AppDatabase.getInstance(applicationContext)
            db.traceLogDao().insert(
                TraceLogEntity(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    altitude = location.altitude,
                    heading = location.bearing,
                    speed = location.speed,
                    timestamp = isoNow(),
                    deviceId = session.getOrCreateDeviceId(applicationContext),
                    synced = false
                )
            )
            // Titik yang baru direkam ini akan ikut terkirim oleh TraceLogSyncWorker.
        }

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleLocation(engine: LocationEngine): Location? =
        suspendCancellableCoroutine { cont ->
            var resumed = false
            val started = engine.start(1000L, 1000L) { loc ->
                if (!resumed) {
                    resumed = true
                    engine.stop()
                    cont.resume(loc)
                }
            }
            if (!started && !resumed) {
                resumed = true
                cont.resume(null)
            }
            cont.invokeOnCancellation { engine.stop() }
        }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 45_000L
    }
}
