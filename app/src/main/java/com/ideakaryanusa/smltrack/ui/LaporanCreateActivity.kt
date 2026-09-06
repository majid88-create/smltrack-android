package com.ideakaryanusa.smltrack.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.databinding.ActivityLaporanCreateBinding
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.LocationEngine
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class LaporanCreateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaporanCreateBinding
    private lateinit var session: SessionManager

    private var pickedImageUri: Uri? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pickedImageUri = uri
            binding.ivPreview.visibility = View.VISIBLE
            binding.ivPreview.setImageURI(uri)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fetchLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaporanCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Buat Laporan"
        session = SessionManager(this)

        binding.btnGetLocation.setOnClickListener { requestLocationAndFetch() }
        binding.btnPickImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun requestLocationAndFetch() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) fetchLocation() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun fetchLocation() {
        binding.tvLocation.text = "Lokasi: sedang mencari..."
        // Pakai LocationEngine (bukan Fused langsung) supaya tetap dapat lokasi
        // di HP yang tidak punya Google Play Services.
        val engine = LocationEngine(this)
        if (!engine.isLocationEnabled()) {
            binding.tvLocation.text = "Lokasi: GPS/Lokasi HP sedang mati - nyalakan dulu"
            return
        }

        var done = false
        val started = engine.start(1000L, 1000L) { loc ->
            if (!done) {
                done = true
                engine.stop()
                latitude = loc.latitude
                longitude = loc.longitude
                runOnUiThread {
                    binding.tvLocation.text = "Lokasi: ${loc.latitude}, ${loc.longitude}"
                }
            }
        }
        if (!started) {
            binding.tvLocation.text = "Lokasi: gagal mulai - cek izin lokasi"
        }
    }

    private fun submit() {
        val titleText = binding.etTitle.text?.toString()?.trim().orEmpty()
        val category = binding.etCategory.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()

        if (titleText.isEmpty()) {
            showError("Judul laporan wajib diisi")
            return
        }
        val token = session.token
        if (token == null) {
            showError("Sesi login tidak ditemukan, silakan login ulang")
            return
        }

        setLoading(true)
        hideError()

        lifecycleScope.launch {
            var success = false
            var lastErrorMessage = ""

            // App asli mencoba upload sampai 3 kali kalau timeout - ditiru di sini.
            for (attempt in 1..3) {
                try {
                    val imagePart = pickedImageUri?.let { uriToMultipart(it) }

                    val response = RetrofitClient.api.createLaporan(
                        token = token,
                        title = titleText.toPlainTextBody(),
                        description = description.toPlainTextBody(),
                        category = category.toPlainTextBody(),
                        location = "${latitude ?: 0.0},${longitude ?: 0.0}".toPlainTextBody(),
                        latitude = (latitude ?: 0.0).toString().toPlainTextBody(),
                        longitude = (longitude ?: 0.0).toString().toPlainTextBody(),
                        projectId = null,
                        image = imagePart
                    )

                    if (response.isSuccessful) {
                        success = true
                        break
                    } else {
                        lastErrorMessage = "Server menolak (kode ${response.code()})"
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.message ?: "Gagal terhubung ke server"
                    delay(1500L * attempt)
                }
            }

            setLoading(false)
            if (success) {
                finish()
            } else {
                showError("Gagal setelah 3 percobaan: $lastErrorMessage. Laporan mungkin sudah tersimpan di server - cek dulu sebelum kirim ulang.")
            }
        }
    }

    private suspend fun uriToMultipart(uri: Uri): MultipartBody.Part = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Tidak bisa membaca file foto")
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        MultipartBody.Part.createFormData("image", "laporan_${System.currentTimeMillis()}.jpg", body)
    }

    private fun String.toPlainTextBody() = this.toRequestBody("text/plain".toMediaTypeOrNull())

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }
}
