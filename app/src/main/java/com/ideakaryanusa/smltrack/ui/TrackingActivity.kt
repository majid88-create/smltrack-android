package com.ideakaryanusa.smltrack.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ideakaryanusa.smltrack.BuildConfig
import com.ideakaryanusa.smltrack.data.AppDatabase
import com.ideakaryanusa.smltrack.databinding.ActivityTrackingBinding
import com.ideakaryanusa.smltrack.network.BackendClient
import com.ideakaryanusa.smltrack.service.LocationTrackingService
import com.ideakaryanusa.smltrack.service.WatchdogReceiver
import com.ideakaryanusa.smltrack.sync.PeriodicLocationWorker
import com.ideakaryanusa.smltrack.sync.TraceLogSyncWorker
import com.ideakaryanusa.smltrack.util.GeofenceArea
import com.ideakaryanusa.smltrack.util.GeofenceManager
import com.ideakaryanusa.smltrack.util.LatLng
import com.ideakaryanusa.smltrack.util.ManufacturerSettingsHelper
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackingBinding
    private lateinit var session: SessionManager
    private lateinit var geofence: GeofenceManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            startTracking()
        } else {
            binding.switchTracking.isChecked = false
            binding.tvStatus.text = "Status: izin lokasi ditolak"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)
        geofence = GeofenceManager(this)

        schedulePeriodicSync()
        schedulePeriodicLocationBackup()
        refreshPendingCount()
        refreshGeofenceFromBackend()

        // Kalau tracking sudah aktif dari sesi sebelumnya (misal Activity dibuka
        // ulang setelah app di-kill sistem lalu di-restart Watchdog), cerminkan
        // statusnya di switch tanpa memicu ulang start service.
        binding.switchTracking.setOnCheckedChangeListener(null)
        binding.switchTracking.isChecked = session.trackingEnabled
        binding.tvStatus.text = if (session.trackingEnabled) "Status: aktif" else "Status: tidak aktif"

        binding.switchTracking.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestPermissionsAndStart() else stopTracking()
        }

        binding.btnLogout.setOnClickListener { logout() }

        binding.btnLaporan.setOnClickListener {
            startActivity(Intent(this, LaporanListActivity::class.java))
        }
        binding.btnSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }
        binding.btnRecap.setOnClickListener {
            startActivity(Intent(this, RecapActivity::class.java))
        }
        binding.btnBatteryOptimization.setOnClickListener {
            ManufacturerSettingsHelper.requestIgnoreBatteryOptimizations(this)
        }
        binding.btnManufacturerSettings.setOnClickListener {
            ManufacturerSettingsHelper.openManufacturerAutostartSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPendingCount()
        refreshBatteryWarning()
        refreshLastLocation()
    }

    private fun refreshLastLocation() {
        val label = session.getLastLocationLabel()
        binding.tvLastLocation.text = "Lokasi terakhir: ${label ?: "-"}"
    }

    private fun refreshBatteryWarning() {
        val exempted = ManufacturerSettingsHelper.isIgnoringBatteryOptimizations(this)
        binding.tvBatteryWarning.visibility = if (exempted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startTracking()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
        // Catatan: ACCESS_BACKGROUND_LOCATION di Android 10+ perlu diminta terpisah
        // lewat halaman setting "Allow all the time" - tambahkan alur khusus untuk itu
        // kalau nanti terbukti perlu (tergantung target minSdk/behaviour perangkat).
    }

    private fun startTracking() {
        session.trackingEnabled = true
        val intent = Intent(this, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        WatchdogReceiver.scheduleNext(this)
        binding.tvStatus.text = "Status: aktif"
    }

    private fun stopTracking() {
        session.trackingEnabled = false
        stopService(Intent(this, LocationTrackingService::class.java))
        WatchdogReceiver.cancel(this)
        binding.tvStatus.text = "Status: tidak aktif"
    }

    private fun schedulePeriodicSync() {
        // Constraint jaringan: tidak ada gunanya coba kirim kalau memang belum ada
        // internet - biar WorkManager sendiri yang menahan sampai tersedia.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<TraceLogSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trace_log_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun schedulePeriodicLocationBackup() {
        // Jaring pengaman ketiga: rekam lokasi tiap 15 menit lewat WorkManager,
        // sebagai cadangan kalau foreground service diblokir ROM. Tidak pakai
        // constraint jaringan karena tujuannya MEREKAM, bukan mengirim -
        // pengirimannya diurus TraceLogSyncWorker.
        val request = PeriodicWorkRequestBuilder<PeriodicLocationWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_location_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun refreshGeofenceFromBackend() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    BackendClient.api.getGeofence(secret = BuildConfig.APP_SECRET)
                }
                val data = response.body()?.data
                if (response.isSuccessful && data != null) {
                    val areas = data.mapNotNull { dto ->
                        val id = dto.projectId ?: return@mapNotNull null
                        val poly = dto.polygon?.map { LatLng(it.lat, it.lng) } ?: emptyList()
                        if (poly.size < 3) return@mapNotNull null
                        GeofenceArea(id, dto.projectName ?: "", poly)
                    }
                    if (areas.isNotEmpty()) {
                        geofence.saveAreas(areas)
                    }
                }
            } catch (e: Exception) {
                // Gagal ambil geofence (misal offline) - pakai cache lama yang
                // sudah tersimpan sebelumnya. Tidak fatal.
            }
        }
    }

    private fun refreshPendingCount() {
        lifecycleScope.launch {
            val count = AppDatabase.getInstance(this@TrackingActivity).traceLogDao().getUnsyncedCount()
            binding.tvPendingCount.text = "Antrian belum terkirim: $count"
        }
    }

    private fun logout() {
        stopTracking()
        session.clearToken()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
