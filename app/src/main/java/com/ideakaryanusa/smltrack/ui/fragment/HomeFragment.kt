package com.ideakaryanusa.smltrack.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ideakaryanusa.smltrack.BuildConfig
import com.ideakaryanusa.smltrack.R
import com.ideakaryanusa.smltrack.databinding.FragmentHomeBinding
import com.ideakaryanusa.smltrack.network.BackendClient
import com.ideakaryanusa.smltrack.service.LocationTrackingService
import com.ideakaryanusa.smltrack.service.WatchdogReceiver
import com.ideakaryanusa.smltrack.sync.PeriodicLocationWorker
import com.ideakaryanusa.smltrack.sync.TraceLogSyncWorker
import com.ideakaryanusa.smltrack.ui.LoginActivity
import com.ideakaryanusa.smltrack.ui.adapter.SiteAdapter
import com.ideakaryanusa.smltrack.util.FakeGpsDetector
import com.ideakaryanusa.smltrack.util.ManufacturerSettingsHelper
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private lateinit var siteAdapter: SiteAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTracking()
        } else {
            updateTrackingUi(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())

        siteAdapter = SiteAdapter(emptyList())
        binding.rvSites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSites.adapter = siteAdapter

        // Header
        binding.tvGreeting.text = "Halo, ${session.username ?: "-"}!"
        binding.tvDate.text = SimpleDateFormat("EEEE, d MMM yyyy", Locale("id")).format(Date())

        schedulePeriodicSync()
        schedulePeriodicLocationBackup()

        updateTrackingUi(session.trackingEnabled)

        binding.btnToggleTracking.setOnClickListener {
            if (session.trackingEnabled) stopTracking() else requestAndStart()
        }
        binding.btnBattery.setOnClickListener {
            ManufacturerSettingsHelper.requestIgnoreBatteryOptimizations(requireContext())
            ManufacturerSettingsHelper.openManufacturerAutostartSettings(requireContext())
        }
        binding.btnLogout.setOnClickListener { logout() }
    }

    override fun onResume() {
        super.onResume()
        updateTrackingUi(session.trackingEnabled)
        loadHomeData()
    }

    private fun loadHomeData() {
        val username = session.username ?: return
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    BackendClient.api.getHome(secret = BuildConfig.APP_SECRET, username = username)
                }
                val data = response.body()?.data ?: return@launch

                // Lokasi terakhir + maps
                if (data.lastLat != null && data.lastLng != null) {
                    binding.tvLocation.text = "📍 ${data.lastArea ?: "Lokasi"}: ${data.lastLat}, ${data.lastLng}" +
                        (data.lastTime?.let { " (jam $it)" } ?: "")
                    showMap(data.lastLat, data.lastLng)
                }

                // Aktivitas hari ini (site dikunjungi)
                binding.containerAktivitas.removeAllViews()
                val akt = data.aktivitas ?: emptyList()
                if (akt.isEmpty()) {
                    binding.tvAktivitasEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvAktivitasEmpty.visibility = View.GONE
                    akt.forEach { item ->
                        val row = layoutInflater.inflate(R.layout.item_aktivitas, binding.containerAktivitas, false)
                        row.findViewById<TextView>(R.id.tvJam).text = item.jamMasuk ?: "-"
                        row.findViewById<TextView>(R.id.tvSite).text =
                            "${item.projectName ?: "-"}  (${item.jamMasuk}–${item.jamAkhir})"
                        binding.containerAktivitas.addView(row)
                    }
                }

                // Lokasi site (CP)
                val sites = data.sites ?: emptyList()
                if (sites.isEmpty()) {
                    binding.tvSitesEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvSitesEmpty.visibility = View.GONE
                    siteAdapter.update(sites)
                }
            } catch (e: Exception) {
                // diam saja - biarkan tampilan apa adanya
            }
        }
    }

    /** Peta interaktif Leaflet + OpenStreetMap di WebView - gratis, tanpa API key. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun showMap(lat: Double, lng: Double) {
        binding.tvMapPlaceholder.visibility = View.GONE
        val web = binding.mapWebView
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        val html = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
            </head><body><div id="map"></div>
            <script>
              var map = L.map('map', {zoomControl:false, attributionControl:false}).setView([$lat,$lng], 16);
              L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
              L.marker([$lat,$lng]).addTo(map);
            </script></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
    }

    private fun requestAndStart() {
        // Cek fake GPS dulu - kalau ada, tolak dan beri tahu user
        val fakeApp = FakeGpsDetector.findFakeGpsApp(requireContext())
        if (fakeApp != null) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Aplikasi Lokasi Palsu Terdeteksi")
                .setMessage("Terdeteksi aplikasi \"$fakeApp\" yang bisa memalsukan lokasi.\n\n" +
                    "Mergawe tidak bisa diaktifkan selama aplikasi ini terpasang. " +
                    "Silakan hapus/nonaktifkan dulu aplikasi tersebut.")
                .setPositiveButton("Mengerti", null)
                .show()
            return
        }

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startTracking() else permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startTracking() {
        session.trackingEnabled = true
        val intent = Intent(requireContext(), LocationTrackingService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        WatchdogReceiver.scheduleNext(requireContext())
        updateTrackingUi(true)
    }

    private fun stopTracking() {
        session.trackingEnabled = false
        requireContext().stopService(Intent(requireContext(), LocationTrackingService::class.java))
        WatchdogReceiver.cancel(requireContext())
        updateTrackingUi(false)
    }

    private fun updateTrackingUi(active: Boolean) {
        if (active) {
            binding.tvStatus.text = "Status: Aktif"
            binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            binding.tvStatusSub.text = "Sedang Mergawe"
            binding.tvOnline.visibility = View.VISIBLE
            binding.btnToggleTracking.text = "■ BERHENTI MERGAWE"
        } else {
            binding.tvStatus.text = "Status: Tidak Aktif"
            binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.tvStatusSub.text = "Belum Mergawe"
            binding.tvOnline.visibility = View.GONE
            binding.btnToggleTracking.text = "▶ MULAI MERGAWE"
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<TraceLogSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "trace_log_sync", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    private fun schedulePeriodicLocationBackup() {
        val request = PeriodicWorkRequestBuilder<PeriodicLocationWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "periodic_location_backup", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    private fun logout() {
        stopTracking()
        session.clearToken()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
