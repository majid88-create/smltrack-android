package com.ideakaryanusa.smltrack.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ideakaryanusa.smltrack.BuildConfig
import com.ideakaryanusa.smltrack.data.AppDatabase
import com.ideakaryanusa.smltrack.model.BackendTraceBatchRequest
import com.ideakaryanusa.smltrack.model.BackendTracePoint
import com.ideakaryanusa.smltrack.network.BackendClient
import com.ideakaryanusa.smltrack.util.SessionManager

/**
 * Kirim ulang semua trace log yang masih synced=false ke backend SENDIRI
 * (Apps Script). Dipanggil dari 2 arah:
 * 1. Jadwal periodik WorkManager (tiap 15 menit, dengan constraint ada internet)
 * 2. Sekali-tembak dari LocationTrackingService begitu koneksi baru tersedia.
 *
 * Pakai endpoint BATCH: semua titik tertunda dikirim dalam SATU request
 * (bukan satu request per titik) - jauh lebih hemat kuota Apps Script yang
 * dibatasi ~20.000 request/hari. Penting kalau HP habis offline berjam-jam.
 */
class TraceLogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionManager(applicationContext)
        val username = session.username ?: return Result.success() // belum login
        val deviceId = session.getOrCreateDeviceId(applicationContext)

        val db = AppDatabase.getInstance(applicationContext)

        while (true) {
            val batch = db.traceLogDao().getUnsynced()
            if (batch.isEmpty()) break

            val points = batch.map { log ->
                BackendTracePoint(
                    latitude = log.latitude,
                    longitude = log.longitude,
                    accuracy = log.accuracy,
                    speed = log.speed,
                    timestamp = log.timestamp,
                    projectId = log.projectId,
                    projectName = log.projectName
                )
            }

            try {
                val response = BackendClient.api.sendTraceBatch(
                    BackendTraceBatchRequest(
                        secret = BuildConfig.APP_SECRET,
                        username = username,
                        deviceId = deviceId,
                        points = points
                    )
                )
                if (response.isSuccessful && response.body()?.status == "ok") {
                    // Tandai semua titik di batch ini sebagai terkirim
                    batch.forEach { db.traceLogDao().update(it.copy(synced = true)) }
                    db.traceLogDao().clearSynced()
                } else {
                    // Backend menolak - berhenti, biarkan WorkManager retry nanti
                    return Result.retry()
                }
            } catch (e: Exception) {
                // Tidak ada koneksi - retry nanti
                return Result.retry()
            }
        }

        return Result.success()
    }
}
