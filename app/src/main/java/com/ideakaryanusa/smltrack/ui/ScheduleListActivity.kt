package com.ideakaryanusa.smltrack.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.databinding.ActivityScheduleListBinding
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.launch

class ScheduleListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleListBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Jadwal"
        session = SessionManager(this)
        loadSchedules()
    }

    private fun loadSchedules() {
        val token = session.token ?: return
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getSchedules(token)
                val items = if (response.isSuccessful) response.body()?.data ?: emptyList() else emptyList()

                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                val labels = items.map {
                    val title = it.title ?: "(tanpa judul)"
                    val range = listOfNotNull(it.startDate, it.startTime).joinToString(" ")
                    "$title\n$range  •  ${it.location ?: "-"}"
                }
                binding.listView.adapter = ArrayAdapter(
                    this@ScheduleListActivity, android.R.layout.simple_list_item_1, labels
                )
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Gagal memuat jadwal: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
