package com.ideakaryanusa.smltrack.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ideakaryanusa.smltrack.data.AppDatabase
import com.ideakaryanusa.smltrack.model.TraceLogRequest
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.SessionManager

/**
 * Kirim ulang semua trace log yang masih synced=false. Dipanggil dari 2 arah:
 * 1. Jadwal periodik WorkManager (tiap 15 menit, dengan constraint ada internet)
 * 2. Sekali-tembak dari LocationTrackingService begitu ConnectivityManager
 *    melaporkan koneksi baru tersedia - supaya data tidak nunggu lama nganggur
 *    di antrian begitu HP keluar dari area tanpa sinyal.
 *
 * MENGURAS SELURUH antrian dalam satu jalan (bukan cuma satu batch 50 item) -
 * penting kalau HP offline berjam-jam dan antrian menumpuk banyak.
 */
class TraceLogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionManager(applicationContext)
        val token = session.token ?: return Result.success() // belum login, tidak ada yang perlu disinkronkan

        val db = AppDatabase.getInstance(applicationContext)

        var anyFailure = false

        while (true) {
            val batch = db.traceLogDao().getUnsynced()
            if (batch.isEmpty()) break

            var batchHadSuccess = false

            for (log in batch) {
                try {
                    val response = RetrofitClient.api.sendTraceLog(
                        token = token,
                        request = TraceLogRequest(
                            latitude = log.latitude,
                            longitude = log.longitude,
                            accuracy = log.accuracy,
                            altitude = log.altitude,
                            heading = log.heading,
                            speed = log.speed,
                            timestamp = log.timestamp,
                            deviceId = log.deviceId
                        )
                    )
                    if (response.isSuccessful) {
                        db.traceLogDao().update(log.copy(synced = true))
                        batchHadSuccess = true
                    } else if (response.code() == 401 || response.code() == 403) {
                        // Token kedaluwarsa. Percuma retry - server akan terus menolak
                        // sampai user login ulang. Data TETAP tersimpan (synced=false),
                        // jadi begitu login ulang semuanya akan terkirim, tidak hilang.
                        return Result.success()
                    } else {
                        anyFailure = true
                    }
                } catch (e: Exception) {
                    anyFailure = true
                }
            }

            db.traceLogDao().clearSynced()

            // Kalau satu batch penuh gagal semua (misal internet putus lagi di
            // tengah jalan), berhenti - jangan diulang tanpa henti, biarkan
            // WorkManager yang menjadwalkan retry dengan backoff.
            if (!batchHadSuccess) break
        }

        return if (anyFailure) Result.retry() else Result.success()
    }
}
