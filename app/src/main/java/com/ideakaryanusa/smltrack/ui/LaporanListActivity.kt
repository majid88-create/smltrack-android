package com.ideakaryanusa.smltrack.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ideakaryanusa.smltrack.databinding.ActivityLaporanListBinding
import com.ideakaryanusa.smltrack.model.Laporan
import com.ideakaryanusa.smltrack.network.RetrofitClient
import com.ideakaryanusa.smltrack.util.SessionManager
import kotlinx.coroutines.launch

class LaporanListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaporanListBinding
    private lateinit var session: SessionManager
    private var items: List<Laporan> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaporanListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Laporan"
        session = SessionManager(this)

        binding.btnCreateLaporan.setOnClickListener {
            startActivity(Intent(this, LaporanCreateActivity::class.java))
        }

        binding.listView.setOnItemClickListener { _, _, position, _ ->
            val laporan = items.getOrNull(position) ?: return@setOnItemClickListener
            val id = laporan.id ?: return@setOnItemClickListener
            startActivity(Intent(this, LaporanDetailActivity::class.java).putExtra("laporan_id", id))
        }
    }

    override fun onResume() {
        super.onResume()
        loadLaporan()
    }

    private fun loadLaporan() {
        val token = session.token ?: return
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getLaporanList(token)
                if (response.isSuccessful) {
                    items = response.body()?.data ?: emptyList()
                    renderList()
                } else {
                    items = emptyList()
                    renderList()
                }
            } catch (e: Exception) {
                items = emptyList()
                renderList()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun renderList() {
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        val labels = items.map { "${it.title ?: "(tanpa judul)"}  [${it.status ?: "-"}]" }
        binding.listView.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, labels
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
