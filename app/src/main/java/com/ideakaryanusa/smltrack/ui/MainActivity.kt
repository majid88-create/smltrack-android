package com.ideakaryanusa.smltrack.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ideakaryanusa.smltrack.R
import com.ideakaryanusa.smltrack.databinding.ActivityMainBinding
import com.ideakaryanusa.smltrack.ui.fragment.HomeFragment
import com.ideakaryanusa.smltrack.ui.fragment.JadwalFragment
import com.ideakaryanusa.smltrack.ui.fragment.LaporanFragment
import com.ideakaryanusa.smltrack.ui.fragment.RekapFragment

/**
 * Activity utama yang jadi "wadah" untuk 4 tab bottom navigation:
 * Home, Laporan, Jadwal, Rekap Absensi. Menggantikan TrackingActivity lama
 * sebagai layar setelah login.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_laporan -> LaporanFragment()
                R.id.nav_jadwal -> JadwalFragment()
                R.id.nav_rekap -> RekapFragment()
                else -> HomeFragment()
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
