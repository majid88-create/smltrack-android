package com.ideakaryanusa.smltrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TraceLogDao {

    @Insert
    suspend fun insert(log: TraceLogEntity): Long

    @Query("SELECT * FROM trace_logs WHERE synced = 0 ORDER BY id ASC LIMIT 50")
    suspend fun getUnsynced(): List<TraceLogEntity>

    @Query("SELECT COUNT(*) FROM trace_logs WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Update
    suspend fun update(log: TraceLogEntity)

    @Query("DELETE FROM trace_logs WHERE synced = 1")
    suspend fun clearSynced()

    // ---- Perlindungan terhadap antrian membengkak ----

    @Query("SELECT COUNT(*) FROM trace_logs")
    suspend fun getTotalCount(): Int

    /**
     * Kalau HP offline berhari-hari, antrian bisa menumpuk sampai puluhan ribu
     * baris dan memakan penyimpanan HP. Fungsi ini membuang titik PALING LAMA
     * kalau sudah melewati batas - data terbaru selalu diprioritaskan karena
     * lebih relevan untuk pengawasan.
     */
    @Query("DELETE FROM trace_logs WHERE id IN (SELECT id FROM trace_logs ORDER BY id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    /**
     * Anti-duplikat: cek apakah titik terakhir yang tersimpan posisinya sama persis.
     * Berguna saat HP diam di satu tempat lama - tidak perlu simpan ratusan baris
     * dengan koordinat identik.
     */
    @Query("SELECT * FROM trace_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): TraceLogEntity?
}
