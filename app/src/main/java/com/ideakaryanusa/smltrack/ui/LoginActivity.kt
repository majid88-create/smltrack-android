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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Format request dikonfirmasi dari menangkap traffic asli (DevTools browser
 * saat login berhasil di dashboard web ideakaryanusa.softindopp.com):
 * - Field: "Username" dan "Password" (huruf besar di depan)
 * - Password yang dikirim adalah HASH SHA-256 dari password asli, bukan teks polos
 * - Tidak ada deviceId di request login
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

        val passwordHash = customPasswordHash(password)
        val jsonBody = JSONObject().apply {
            put("Username", username)
            put("Password", passwordHash)
        }

        lifecycleScope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(BuildConfig.API_BASE_URL + "api/auth")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val bodyText = withContext(Dispatchers.IO) { response.body?.string() }.orEmpty()
                response.close()

                if (response.isSuccessful) {
                    val token = extractToken(bodyText)
                    if (token != null) {
                        session.token = token
                        session.username = username   // simpan untuk dipakai service
                        goToTracking()
                    } else {
                        showError("Login sukses (${response.code}) tapi token tidak ditemukan di response:\n\n$bodyText")
                    }
                } else {
                    showError("Login gagal (kode ${response.code}):\n\n${bodyText.take(300)}")
                }
            } catch (e: Exception) {
                showError("Tidak bisa terhubung ke server: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * Algoritma hash password DIKONFIRMASI dari membaca langsung kode
     * main.dart.js versi web (fungsi bu0/bEt): password dipotong 2 bagian
     * (titik potong dibulatkan ke ATAS / ceil), masing-masing bagian dibalik
     * (reverse), lalu ditempel [bagian_kedua_dibalik] + [password_asli] +
     * [bagian_pertama_dibalik], baru di-SHA-256.
     *
     * Contoh: "ideakaryanusa" (13 huruf) -> potong di huruf ke-7 (ceil 13/2)
     * -> "ideakar" + "yanusa" -> dibalik jadi "rakaedi" + "asunay"
     * -> digabung: "asunay" + "ideakaryanusa" + "rakaedi" -> SHA-256.
     * Sudah dicocokkan manual dan hasilnya identik dengan yang dikirim
     * app/web asli.
     */
    private fun customPasswordHash(password: String): String {
        val n = password.length
        val splitAt = (n + 1) / 2 // ceil(n/2) untuk bilangan bulat
        val firstHalf = password.substring(0, splitAt)
        val secondHalf = password.substring(splitAt)
        val combined = secondHalf.reversed() + password + firstHalf.reversed()
        val digest = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun extractToken(bodyText: String): String? {
        return try {
            val json = JSONObject(bodyText)
            // Coba beberapa kemungkinan casing/lokasi, karena terbukti API ini
            // pakai "Token" (huruf besar) nested di dalam "data".
            listOf(
                json.optString("token"),
                json.optString("Token"),
                json.optJSONObject("data")?.optString("token").orEmpty(),
                json.optJSONObject("data")?.optString("Token").orEmpty()
            ).firstOrNull { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun goToTracking() {
        startActivity(Intent(this, MainActivity::class.java))
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
