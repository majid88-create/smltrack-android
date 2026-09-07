package com.ideakaryanusa.smltrack.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Satu area terdaftar (proyek) beserta poligon batasnya.
 */
data class GeofenceArea(
    val projectId: String,
    val projectName: String,
    val polygon: List<LatLng>
)

data class LatLng(val lat: Double, val lng: Double)

/**
 * Menyimpan daftar geofence yang di-cache dari backend, dan mengecek
 * "titik GPS ini ada di dalam area mana".
 *
 * Deteksi dilakukan DI HP (bukan di server) supaya:
 * - tetap jalan walau sinyal putus
 * - hemat kuota backend (HP cukup lapor "titik ini di area X", tidak perlu
 *   server yang menghitung ulang tiap titik)
 */
class GeofenceManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "smltrack_geofence", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    fun saveAreas(areas: List<GeofenceArea>) {
        prefs.edit().putString(KEY_AREAS, gson.toJson(areas)).apply()
    }

    fun getAreas(): List<GeofenceArea> {
        val json = prefs.getString(KEY_AREAS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<GeofenceArea>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Cari area terdaftar yang mengandung titik ini. Kembalikan null kalau
     * titik berada di luar semua area (di jalan, dsb).
     */
    fun findContainingArea(lat: Double, lng: Double): GeofenceArea? {
        for (area in getAreas()) {
            if (pointInPolygon(lat, lng, area.polygon)) return area
        }
        return null
    }

    /**
     * Algoritma ray-casting standar: tarik garis horizontal dari titik ke
     * kanan tak hingga, hitung berapa kali garis itu memotong sisi poligon.
     * Ganjil = di dalam, genap = di luar.
     */
    private fun pointInPolygon(lat: Double, lng: Double, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val vi = polygon[i]
            val vj = polygon[j]
            val intersect = (vi.lng > lng) != (vj.lng > lng) &&
                lat < (vj.lat - vi.lat) * (lng - vi.lng) / (vj.lng - vi.lng) + vi.lat
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        private const val KEY_AREAS = "cached_areas"
    }
}
