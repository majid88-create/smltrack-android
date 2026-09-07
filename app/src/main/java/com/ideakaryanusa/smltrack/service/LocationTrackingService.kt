package com.ideakaryanusa.smltrack.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ideakaryanusa.smltrack.BuildConfig
import com.ideakaryanusa.smltrack.R
import com.ideakaryanusa.smltrack.data.AppDatabase
import com.ideakaryanusa.smltrack.data.TraceLogEntity
import com.ideakaryanusa.smltrack.model.BackendTraceRequest
import com.ideakaryanusa.smltrack.network.BackendClient
import com.ideakaryanusa.smltrack.sync.TraceLogSyncWorker
import com.ideakaryanusa.smltrack.ui.LoginActivity
import com.ideakaryanusa.smltrack.util.GeofenceManager
import com.ideakaryanusa.smltrack.util.LocationEngine
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lapisan inti keandalan GPS. Skenario yang ditangani di sini:
 *
 * | Skenario                              | Penanganan                                    |
 * |---------------------------------------|-----------------------------------------------|
 * | Tidak ada sinyal data                 | Simpan ke Room, kirim nanti                   |
 * | Sinyal balik setelah lama offline     | NetworkCallback picu sync SAAT ITU juga       |
 * | HP tanpa Google Play Services         | LocationEngine turun ke LocationManager       |
 * | GPS/lokasi dimatikan user             | Notifikasi berubah jadi peringatan            |
 * | App di-swipe dari recent apps         | onTaskRemoved jadwalkan restart 1 detik       |
 * | ROM vendor bunuh service diam-diam    | Watchdog alarm restart (maks ~20 menit)       |
 * | Service DAN alarm sama-sama diblokir  | PeriodicLocationWorker rekam tiap 15 menit    |
 * | HP reboot / app di-update             | BootReceiver nyalakan ulang                   |
 * | CPU tidur saat layar mati             | Wake lock singkat tiap siklus                 |
 * | Token kedaluwarsa (401)               | Berhenti spam, notif minta login ulang        |
 * | Offline berhari-hari, antrian numpuk  | Batas 20.000 titik, yang terlama dibuang      |
 * | HP diam lama di satu titik            | Titik identik berturut-turut tidak digandakan |
 */
class LocationTrackingService : Service() {

    private lateinit var engine: LocationEngine
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager
    private lateinit var geofence: GeofenceManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile private var lastSentLabel: String = "belum ada"

    override fun onCreate() {
        super.onCreate()
        engine = LocationEngine(this)
        db = AppDatabase.getInstance(this)
        session = SessionManager(this)
        geofence = GeofenceManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
        registerNetworkCallback()
        WatchdogReceiver.scheduleNext(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        session.trackingEnabled = true
        session.lastHeartbeatMillis = System.currentTimeMillis()
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val started = engine.start(INTERVAL_MS, MIN_INTERVAL_MS) { location ->
            scope.launch { handleNewLocation(location) }
        }

        if (!started) {
            // Bisa karena izin dicabut user di tengah jalan, atau layanan lokasi
            // HP dimatikan total. Jangan diam - ubah notifikasi jadi peringatan
            // supaya user/admin sadar, dan JANGAN stopSelf (biar watchdog tetap
            // punya kesempatan mencoba lagi nanti kalau user menyalakan GPS).
            updateNotification(
                title = "SML Track: lokasi tidak aktif",
                text = if (!engine.isLocationEnabled())
                    "Nyalakan GPS/Lokasi di HP agar tracking jalan"
                else
                    "Izin lokasi belum diberikan - buka app untuk mengizinkan"
            )
        }
    }

    private suspend fun handleNewLocation(location: Location) {
        val wl = acquireWakeLock()
        try {
            session.lastHeartbeatMillis = System.currentTimeMillis()

            // Tolak titik dari fake GPS / mock location
            if (com.ideakaryanusa.smltrack.util.FakeGpsDetector.isMockLocation(location)) {
                updateNotification(
                    title = "SML Track: lokasi palsu terdeteksi",
                    text = "Titik dari fake GPS diabaikan. Matikan aplikasi lokasi palsu."
                )
                return
            }

            session.setLastLocation(location.latitude, location.longitude, timeLabelNow())

            // --- Anti-duplikat DINONAKTIFKAN SEMENTARA (atas permintaan, untuk analisa) ---
            // Kode aslinya masih ada di bawah, tinggal un-comment kalau nanti mau
            // diaktifkan lagi setelah analisa selesai (menghemat kuota/baterai).
            //
            // val latest = db.traceLogDao().getLatest()
            // if (latest != null && isSameSpot(latest, location)) {
            //     updateNotification(text = "Diam di lokasi yang sama - terakhir kirim: $lastSentLabel")
            //     return
            // }

            // --- Batas antrian: jaga penyimpanan HP kalau offline berhari-hari.
            val total = db.traceLogDao().getTotalCount()
            if (total >= MAX_QUEUE_SIZE) {
                db.traceLogDao().deleteOldest(total - MAX_QUEUE_SIZE + PRUNE_BATCH)
            }

            // --- Deteksi geofence: titik ini ada di dalam area terdaftar mana?
            val area = geofence.findContainingArea(location.latitude, location.longitude)

            val deviceId = session.getOrCreateDeviceId(this)
            val entity = TraceLogEntity(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = location.altitude,
                heading = location.bearing,
                speed = location.speed,
                timestamp = isoNow(),
                deviceId = deviceId,
                synced = false,
                projectId = area?.projectId,
                projectName = area?.projectName
            )
            val rowId = db.traceLogDao().insert(entity)

            val username = session.username
            if (username == null) {
                updateNotification(text = "Belum login - data disimpan lokal")
                return
            }

            // Kirim ke backend SENDIRI (Apps Script), bukan server SML asli.
            try {
                val response = BackendClient.api.sendTrace(
                    BackendTraceRequest(
                        secret = BuildConfig.APP_SECRET,
                        username = username,
                        deviceId = deviceId,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        accuracy = entity.accuracy,
                        speed = entity.speed,
                        timestamp = entity.timestamp,
                        projectId = entity.projectId,
                        projectName = entity.projectName
                    )
                )

                if (response.isSuccessful && response.body()?.status == "ok") {
                    db.traceLogDao().update(entity.copy(id = rowId, synced = true))
                    lastSentLabel = timeLabelNow()
                    val areaLabel = area?.projectName?.let { " di $it" } ?: " (di luar area)"
                    updateNotification(text = "Terkirim: $lastSentLabel$areaLabel")
                } else {
                    val pending = db.traceLogDao().getUnsyncedCount()
                    val msg = response.body()?.message ?: "kode ${response.code()}"
                    updateNotification(
                        title = "SML Track: backend tolak",
                        text = "$pending titik tertunda. $msg"
                    )
                }
            } catch (e: Exception) {
                val pending = db.traceLogDao().getUnsyncedCount()
                updateNotification(text = "Tidak ada sinyal - $pending titik menunggu")
            }
        } catch (e: Exception) {
            // Jangan sampai satu error tak terduga membunuh seluruh service.
        } finally {
            wl?.release()
        }
    }

    /**
     * Dianggap "tempat yang sama" kalau geser < 10 meter. Ambang ini juga menyaring
     * "GPS drift" - HP yang diam tapi koordinatnya loncat-loncat sedikit karena
     * ketidakpastian sinyal satelit.
     */
    private fun isSameSpot(previous: TraceLogEntity, current: Location): Boolean {
        val result = FloatArray(1)
        Location.distanceBetween(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude,
            result
        )
        return result[0] < SAME_SPOT_METERS
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val work = OneTimeWorkRequestBuilder<TraceLogSyncWorker>().build()
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork("trace_log_sync_immediate", ExistingWorkPolicy.REPLACE, work)
            }
        }
        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) { }
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smltrack:location_fix").apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun timeLabelNow(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date())

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String = getString(R.string.notif_tracking_title),
        text: String = getString(R.string.notif_tracking_text)
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, LoginActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val modeLabel = if (engine.mode == LocationEngine.Mode.FUSED) "" else " (mode dasar)"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title + modeLabel)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    /**
     * Notifikasi dipakai sebagai papan diagnosa: user/admin bisa lihat langsung
     * dari layar kunci apakah data masih terkirim, atau ada masalah (GPS mati,
     * sesi habis, sinyal hilang) tanpa harus buka app.
     */
    private fun updateNotification(
        title: String = getString(R.string.notif_tracking_title),
        text: String
    ) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(title, text))
        } catch (e: Exception) { }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (session.trackingEnabled) {
            val pendingIntent = PendingIntent.getService(
                applicationContext, 1,
                Intent(applicationContext, LocationTrackingService::class.java),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (e: Exception) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "smltrack_location_channel"
        private const val NOTIF_ID = 1001

        private const val INTERVAL_MS = 60_000L
        private const val MIN_INTERVAL_MS = 30_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L

        private const val SAME_SPOT_METERS = 10f
        private const val MAX_QUEUE_SIZE = 20_000
        private const val PRUNE_BATCH = 1_000
    }
}
