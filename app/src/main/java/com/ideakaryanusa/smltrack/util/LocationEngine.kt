package com.ideakaryanusa.smltrack.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Kenapa perlu lapisan ini:
 *
 * FusedLocationProviderClient (punya Google Play Services) adalah cara terbaik
 * ambil lokasi - hemat baterai, akurat, gabungkan GPS+WiFi+seluler. TAPI dia
 * TIDAK ADA di semua HP:
 * - HP Huawei/Honor keluaran setelah 2019 (tidak boleh pasang layanan Google)
 * - HP China grey market / ROM abal-abal
 * - HP yang Play Services-nya rusak, dinonaktifkan user, atau versinya kadaluarsa
 *
 * Di HP seperti itu, app yang cuma mengandalkan Fused akan DIAM TOTAL - tidak
 * pernah dapat satu titik pun, dan diam-diam saja tanpa pesan error jelas.
 *
 * Kelas ini mengecek dulu ketersediaan Play Services. Kalau ada, pakai Fused.
 * Kalau tidak ada, otomatis turun ke LocationManager bawaan Android (GPS chip
 * langsung + Network provider) yang SELALU ada di semua HP Android. Lebih boros
 * baterai sedikit, tapi jauh lebih baik daripada tidak dapat data sama sekali.
 */
class LocationEngine(private val context: Context) {

    enum class Mode { FUSED, LOCATION_MANAGER }

    val mode: Mode = detectMode()

    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null

    private var locationManager: LocationManager? = null
    private var rawListener: LocationListener? = null

    private fun detectMode(): Mode {
        return try {
            val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (status == ConnectionResult.SUCCESS) Mode.FUSED else Mode.LOCATION_MANAGER
        } catch (e: Throwable) {
            // Kalau library Play Services sendiri bermasalah di device ini, jangan
            // sampai app crash - langsung pakai jalur bawaan Android.
            Mode.LOCATION_MANAGER
        }
    }

    /**
     * @return true kalau berhasil mulai; false kalau izin belum ada / tidak ada
     *         provider aktif sama sekali (misal GPS dimatikan total oleh user).
     */
    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long, minIntervalMs: Long, onLocation: (Location) -> Unit): Boolean {
        return when (mode) {
            Mode.FUSED -> startFused(intervalMs, minIntervalMs, onLocation)
            Mode.LOCATION_MANAGER -> startRaw(intervalMs, onLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFused(intervalMs: Long, minIntervalMs: Long, onLocation: (Location) -> Unit): Boolean {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(minIntervalMs)
                // Kalau HP sempat mati sinyal GPS lalu dapat lagi, jangan menunggu
                // interval penuh - kirim begitu ada fix pertama.
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(onLocation)
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            fusedClient = client
            fusedCallback = callback
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Jalur cadangan: minta update langsung ke GPS chip DAN ke Network provider
     * sekaligus. Dua-duanya didaftarkan supaya kalau user sedang di dalam gedung
     * (GPS tidak dapat sinyal satelit), masih ada titik kasar dari menara seluler/WiFi.
     */
    @SuppressLint("MissingPermission")
    private fun startRaw(intervalMs: Long, onLocation: (Location) -> Unit): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = onLocation(location)
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Wajib di-override untuk API lama")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            var registered = false

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, intervalMs, 0f, listener, Looper.getMainLooper()
                )
                registered = true
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, intervalMs, 0f, listener, Looper.getMainLooper()
                )
                registered = true
            }

            if (registered) {
                locationManager = lm
                rawListener = listener
            }
            registered
        } catch (e: SecurityException) {
            false
        } catch (e: Throwable) {
            false
        }
    }

    /** Cek apakah layanan lokasi HP sedang dimatikan total oleh user. */
    fun isLocationEnabled(): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Throwable) {
            false
        }
    }

    fun stop() {
        try {
            fusedCallback?.let { fusedClient?.removeLocationUpdates(it) }
        } catch (e: Throwable) { }
        try {
            rawListener?.let { locationManager?.removeUpdates(it) }
        } catch (e: Throwable) { }
        fusedCallback = null
        fusedClient = null
        rawListener = null
        locationManager = null
    }
}
