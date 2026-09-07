package com.ideakaryanusa.smltrack.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.provider.Settings

/**
 * Deteksi apakah ada aplikasi Fake GPS / mock location yang aktif atau
 * terpasang. Dipakai untuk mencegah tombol "Mergawe" ditekan kalau ada
 * kecurangan lokasi.
 *
 * Ada 2 lapis deteksi:
 * 1. Cek per-lokasi: Location.isFromMockProvider (Android 12+: isMock) -
 *    ini yang paling akurat, mendeteksi kalau titik GPS yang MASUK itu palsu.
 * 2. Cek daftar app terpasang yang punya izin "mock location" atau namanya
 *    mengandung kata kunci fake gps - jaring pengaman tambahan.
 */
object FakeGpsDetector {

    /** True kalau titik lokasi ini berasal dari mock provider (paling akurat). */
    fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    /**
     * Cari aplikasi terpasang yang kemungkinan besar fake GPS. Dua cara:
     * - punya izin ACCESS_MOCK_LOCATION di manifest-nya
     * - nama package mengandung kata kunci umum (fakegps, mock, dll)
     * Kembalikan nama app pertama yang terdeteksi, atau null kalau bersih.
     */
    fun findFakeGpsApp(context: Context): String? {
        val pm = context.packageManager
        val keywords = listOf("fakegps", "fake.gps", "mock", "mocklocation", "gpsjoystick",
            "lockito", "floater", "gpsfaker", "fly.gps", "fakegpsfree")

        try {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in packages) {
                // Lewati app sistem
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                val pkg = app.packageName.lowercase()
                if (keywords.any { pkg.contains(it) }) {
                    return getAppLabel(pm, app)
                }

                // Cek izin mock location
                try {
                    val info = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                    val perms = info.requestedPermissions
                    if (perms != null && perms.any {
                            it == "android.permission.ACCESS_MOCK_LOCATION"
                        }) {
                        return getAppLabel(pm, app)
                    }
                } catch (e: Exception) { /* lanjut */ }
            }
        } catch (e: Exception) {
            // gagal baca daftar app - abaikan, andalkan deteksi per-lokasi saja
        }
        return null
    }

    /** Cek apakah opsi "Pilih aplikasi lokasi tiruan" di Developer Options aktif. */
    fun isMockLocationEnabledInSettings(context: Context): Boolean {
        return try {
            // Di Android modern, ini tidak selalu bisa dibaca, tapi dicoba.
            @Suppress("DEPRECATION")
            val setting = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION
            )
            setting != null && setting != "0"
        } catch (e: Exception) {
            false
        }
    }

    private fun getAppLabel(pm: PackageManager, app: ApplicationInfo): String {
        return try {
            pm.getApplicationLabel(app).toString()
        } catch (e: Exception) {
            app.packageName
        }
    }
}
