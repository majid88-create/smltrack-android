package com.ideakaryanusa.smltrack.util

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "smltrack_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun getOrCreateDeviceId(context: Context): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        // Coba pakai ANDROID_ID dulu, fallback ke UUID acak kalau kosong/tidak konsisten.
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString()
        }
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    // trackingEnabled = niat user (switch di layar aktif/tidak), dipakai BootReceiver
    // dan Watchdog untuk memutuskan apakah service seharusnya hidup.
    var trackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACKING_ENABLED, value).apply()

    // Heartbeat: ditulis oleh LocationTrackingService tiap siklus.
    // Kalau Watchdog cek dan ternyata sudah lama tidak update padahal
    // trackingEnabled=true, berarti service mati dan perlu di-restart paksa.
    var lastHeartbeatMillis: Long
        get() = prefs.getLong(KEY_HEARTBEAT, 0L)
        set(value) = prefs.edit().putLong(KEY_HEARTBEAT, value).apply()

    // Lokasi terakhir yang berhasil diambil - dibaca TrackingActivity untuk
    // ditampilkan di layar utama ("Lokasi terakhir: ..."). Sebelumnya nilai
    // ini tidak pernah ditulis sama sekali, makanya selalu tampil "-".
    fun setLastLocation(lat: Double, lng: Double, timeLabel: String) {
        prefs.edit()
            .putString(KEY_LAST_LAT, lat.toString())
            .putString(KEY_LAST_LNG, lng.toString())
            .putString(KEY_LAST_LOCATION_TIME, timeLabel)
            .apply()
    }

    fun getLastLocationLabel(): String? {
        val lat = prefs.getString(KEY_LAST_LAT, null) ?: return null
        val lng = prefs.getString(KEY_LAST_LNG, null) ?: return null
        val time = prefs.getString(KEY_LAST_LOCATION_TIME, null) ?: return null
        return "$lat, $lng (jam $time)"
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
        private const val KEY_HEARTBEAT = "last_heartbeat"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LNG = "last_lng"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
    }
}
