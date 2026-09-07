package com.ideakaryanusa.smltrack.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.BuildConfig
import com.ideakaryanusa.smltrack.databinding.ActivityLoginBinding
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * CATATAN DIAGNOSA (sementara):
 * Akun terbukti VALID di app SML asli, tapi ditolak (401) lewat model
 * request yang saya susun dari tebakan string di binary. Daripada build
 * ulang berkali-kali untuk ganti satu nama field, di sini SEKALIGUS dicoba
 * beberapa kemungkinan bentuk body yang paling masuk akal, lalu semua
 * hasilnya ditampilkan - supaya sekali test langsung ketahuan yang mana
 * yang benar. Begitu ketemu formatnya, bagian ini akan disederhanakan lagi
 * jadi satu request saja.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        if (session.isLoggedIn()) {
            goToTracking()
            return
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username dan password wajib diisi")
            return
        }

        setLoading(true)
        hideError()
        val deviceId = session.getOrCreateDeviceId(this)

        val variants = listOf(
            "V1 username+password+deviceId" to JSONObject().apply {
                put("username", username); put("password", password); put("deviceId", deviceId)
            },
            "V2 email+password+deviceId" to JSONObject().apply {
                put("email", username); put("password", password); put("deviceId", deviceId)
            },
            "V3 username+password (tanpa deviceId)" to JSONObject().apply {
                put("username", username); put("password", password)
            }
        )

        lifecycleScope.launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val log = StringBuilder()
            var success = false

            for ((label, jsonBody) in variants) {
                if (success) break
                try {
                    val request = Request.Builder()
                        .url(BuildConfig.API_BASE_URL + "api/auth")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    val bodyText = withContext(Dispatchers.IO) { response.body?.string() }.orEmpty()

                    log.append("$label\n-> HTTP ${response.code}: ${bodyText.take(200)}\n\n")

                    if (response.isSuccessful) {
                        val token = extractToken(bodyText)
                        if (token != null) {
                            session.token = token
                            success = true
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    log.append("$label\n-> error: ${e.message}\n\n")
                }
            }

            setLoading(false)
            if (success) {
                goToTracking()
            } else {
                showError("Semua percobaan gagal:\n\n$log")
            }
        }
    }

    private fun extractToken(bodyText: String): String? {
        return try {
            val json = JSONObject(bodyText)
            val direct = json.optString("token")
            if (direct.isNotBlank()) return direct
            val nested = json.optJSONObject("data")?.optString("token")
            if (!nested.isNullOrBlank()) return nested
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun goToTracking() {
        startActivity(Intent(this, TrackingActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = android.view.View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = android.view.View.GONE
    }
}
