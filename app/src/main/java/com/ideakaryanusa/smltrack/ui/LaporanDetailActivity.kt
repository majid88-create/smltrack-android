package com.ideakaryanusa.smltrack.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.databinding.ActivityLaporanDetailBinding
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class LaporanDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaporanDetailBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaporanDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Detail Laporan"
        session = SessionManager(this)

        val id = intent.getStringExtra("laporan_id")
        if (id == null) {
            finish()
            return
        }
        loadDetail(id)
    }

    private fun loadDetail(id: String) {
        val token = session.token ?: return
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getLaporanDetail(token, id)
                val laporan = response.body()?.data
                if (response.isSuccessful && laporan != null) {
                    binding.tvTitle.text = laporan.title ?: "(tanpa judul)"
                    binding.tvStatus.text = "Status: ${laporan.status ?: "-"}"
                    binding.tvCategory.text = "Kategori: ${laporan.category ?: "-"}"
                    binding.tvDescription.text = laporan.description ?: "-"
                    binding.tvLocation.text = "Lokasi: ${laporan.location ?: "-"}"
                    binding.tvCreatedAt.text = "Dibuat: ${laporan.createdAt ?: "-"}"

                    val imageUrl = laporan.imageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        loadImage(imageUrl)
                    }
                } else {
                    binding.tvTitle.text = "Gagal memuat laporan"
                }
            } catch (e: Exception) {
                binding.tvTitle.text = "Gagal memuat laporan: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun loadImage(url: String) {
        val bitmap = withContext(Dispatchers.IO) {
            try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            binding.ivPhoto.visibility = View.VISIBLE
            binding.ivPhoto.setImageBitmap(bitmap)
        }
    }
}
