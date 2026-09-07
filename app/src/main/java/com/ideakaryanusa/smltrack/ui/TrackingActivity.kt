package com.ideakaryanusa.smltrack.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.ideakaryanusa.smltrack.R
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
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
            binding.tvStatus.text = "Status: Tidak aktif"
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
        refreshHeader()
        refreshLastLocation()
        renderSites()
        refreshGeofenceFromBackend()

        binding.switchTracking.setOnCheckedChangeListener(null)
        binding.switchTracking.isChecked = session.trackingEnabled
        updateStatusText()

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
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
        refreshLastLocation()
        renderSites()
    }

    private fun refreshHeader() {
        binding.tvUserName.text = session.username?.takeIf { it.isNotBlank() } ?: "Karyawan"
        binding.tvDate.text = SimpleDateFormat(
            "EEEE, d MMMM yyyy",
            Locale("id", "ID")
        ).format(Date())
    }

    private fun refreshLastLocation() {
        val label = session.getLastLocationLabel()
        binding.tvLastLocation.text = label ?: "Belum ada lokasi"
    }

    private fun updateStatusText() {
        binding.tvStatus.text = if (session.trackingEnabled) {
            "Status: Aktif"
        } else {
            "Status: Tidak aktif"
        }
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

        if (allGranted) startTracking()
        else permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startTracking() {
        session.trackingEnabled = true
        ContextCompat.startForegroundService(
            this,
            Intent(this, LocationTrackingService::class.java)
        )
        WatchdogReceiver.scheduleNext(this)
        updateStatusText()
    }

    private fun stopTracking() {
        session.trackingEnabled = false
        stopService(Intent(this, LocationTrackingService::class.java))
        WatchdogReceiver.cancel(this)
        updateStatusText()
    }

    private fun schedulePeriodicSync() {
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
                        renderSites()
                    }
                }
            } catch (_: Exception) {
                // Offline: tetap gunakan cache geofence terakhir.
            }
        }
    }

    private fun renderSites() {
        lifecycleScope.launch {
            val areas = withContext(Dispatchers.IO) { geofence.getAreas() }
            val dao = AppDatabase.getInstance(this@TrackingActivity).traceLogDao()
            val latest = withContext(Dispatchers.IO) { dao.getLatest() }

            // Ambil riwayat terakhir dari backend agar tetap tersedia setelah
            // aplikasi dihapus/reinstall. Kalau offline, gunakan data lokal.
            val remoteVisits = try {
                val username = session.username
                if (username.isNullOrBlank()) emptyMap() else {
                    val response = withContext(Dispatchers.IO) {
                        BackendClient.api.getLastVisits(
                            secret = BuildConfig.APP_SECRET,
                            username = username
                        )
                    }
                    if (response.isSuccessful) {
                        response.body()?.data.orEmpty().mapNotNull { item ->
                            val id = item.projectId
                            val ts = item.timestamp
                            if (id.isNullOrBlank() || ts.isNullOrBlank()) null else id to ts
                        }.toMap()
                    } else emptyMap()
                }
            } catch (_: Exception) {
                emptyMap()
            }

            binding.siteListContainer.removeAllViews()

            if (areas.isEmpty()) {
                binding.siteListContainer.addView(TextView(this@TrackingActivity).apply {
                    text = "Belum ada data lokasi site."
                    setTextColor(Color.rgb(100, 116, 139))
                    textSize = 14f
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                })
                return@launch
            }

            areas.forEachIndexed { index, area ->
                val localLastVisit = withContext(Dispatchers.IO) {
                    dao.getLatestForProject(area.projectId)
                }
                val lastVisitTimestamp = remoteVisits[area.projectId] ?: localLastVisit?.timestamp
                addSiteRow(
                    number = index + 1,
                    area = area,
                    lastVisitTimestamp = lastVisitTimestamp,
                    selected = latest?.projectId == area.projectId
                )
            }
        }
    }

    private fun addSiteRow(
        number: Int,
        area: GeofenceArea,
        lastVisitTimestamp: String?,
        selected: Boolean
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(10), dp(8))
            if (selected) setBackgroundResource(R.drawable.bg_site_selected)
        }

        row.addView(TextView(this).apply {
            text = "$number."
            setTextColor(Color.rgb(51, 65, 85))
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(dp(34), dp(68)))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(68), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }

        texts.addView(TextView(this).apply {
            text = area.projectName.ifBlank { "Site tanpa nama" }
            setTextColor(Color.rgb(15, 23, 42))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        })
        texts.addView(TextView(this).apply {
            text = formatLastVisit(lastVisitTimestamp)
            setTextColor(Color.rgb(100, 116, 139))
            textSize = 12.5f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(texts)

        if (selected) {
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_check_circle)
                contentDescription = "Site aktif"
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
        }

        val rowParams = LinearLayout.LayoutParams(-1, dp(76))
        if (number > 1) rowParams.topMargin = dp(1)
        binding.siteListContainer.addView(row, rowParams)

        if (number < geofence.getAreas().size) {
            binding.siteListContainer.addView(View(this).apply {
                setBackgroundColor(Color.rgb(226, 232, 240))
            }, LinearLayout.LayoutParams(-1, 1))
        }
    }

    private fun formatLastVisit(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "Last visit: Belum pernah"
        return try {
            val zone = ZoneId.of("Asia/Jakarta")
            val dateTime = java.time.ZonedDateTime.ofInstant(Instant.parse(timestamp), zone)
            val day = dateTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("id", "ID"))
            val today = java.time.LocalDate.now(zone)
            val days = ChronoUnit.DAYS.between(dateTime.toLocalDate(), today)
            val ago = when {
                days <= 0L -> "Hari ini"
                days == 1L -> "1 Hari Lalu"
                else -> "$days Hari Lalu"
            }
            "Last visit: $day | $ago"
        } catch (_: Exception) {
            "Last visit: Data tidak valid"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun logout() {
        stopTracking()
        session.clearToken()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
