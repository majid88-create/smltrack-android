package com.ideakaryanusa.smltrack.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.databinding.ActivityLoginBinding
import com.ideakaryanusa.smltrack.model.LoginRequest
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.launch

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
        val deviceId = session.getOrCreateDeviceId(this)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.login(
                    LoginRequest(username = username, password = password, deviceId = deviceId)
                )
                if (response.isSuccessful && response.body()?.token != null) {
                    session.token = response.body()!!.token
                    goToTracking()
                } else {
                    val detail = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    showError("Login gagal (kode ${response.code()}):\n${detail ?: "(tidak ada detail dari server)"}")
                }
            } catch (e: Exception) {
                showError("Tidak bisa terhubung ke server: ${e.message}")
            } finally {
                setLoading(false)
            }
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
}
