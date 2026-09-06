package com.ideakaryanusa.smltrack.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.databinding.ActivityRecapBinding
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.launch

class RecapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecapBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Rekap Absensi"
        session = SessionManager(this)
        loadRecap()
    }

    private fun loadRecap() {
        val token = session.token ?: return
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getAbsenceRecap(token)
                val items = if (response.isSuccessful) response.body()?.data ?: emptyList() else emptyList()

                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                val labels = items.map {
                    "${it.date ?: "-"}  •  ${it.status ?: "-"}\nMasuk: ${it.checkIn ?: "-"}   Pulang: ${it.checkOut ?: "-"}"
                }
                binding.listView.adapter = ArrayAdapter(
                    this@RecapActivity, android.R.layout.simple_list_item_1, labels
                )
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Gagal memuat rekap: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
