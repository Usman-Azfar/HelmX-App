package com.yourname.helmx

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yourname.helmx.databinding.ActivityAnalyticsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("HelmXSettings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomNavigation()
        setupRideHistory()
        setupEnvironmentalStats()
        observeHelmetData()
    }

    private fun observeHelmetData() {
        val helmetBleManager = HelmetBleManager.getInstance(this)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                helmetBleManager.helmetData.collectLatest { data ->
                    android.util.Log.d("AnalyticsActivity", "Observed update: Temp=${data.temperature}, Hum=${data.humidity}, Air=${data.airQuality}")
                    
                    // Update Temp and Humidity
                    binding.tvTempValue.text = String.format("%.1f", data.temperature)
                    binding.tvHumidityValue.text = String.format("%.1f", data.humidity)
                    binding.progressHumidity.progress = data.humidity.toInt()

                    // Update Air Quality
                    binding.tvAirQualityValue.text = data.airQuality
                    
                    // Optional: Change color based on status
                    if (data.airQuality.contains("Contaminated", ignoreCase = true)) {
                        binding.tvAirQualityValue.setTextColor(android.graphics.Color.RED)
                    } else {
                        binding.tvAirQualityValue.setTextColor(android.graphics.Color.parseColor("#4ADE80")) // Green
                    }
                }
            }
        }
    }

    private fun setupEnvironmentalStats() {
        binding.tvTempValue.text = "--"
        binding.tvHumidityValue.text = "--"
        binding.progressHumidity.progress = 0
        binding.tvAirQualityValue.text = "Waiting..."
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
    }

    private fun setupRideHistory() {
        val mockRides = listOf(
            Ride("1", "May 1, 2024", "12.4 km", "25 mins", "30 km/h", "Saddar Market"),
            Ride("2", "Apr 30, 2024", "5.2 km", "12 mins", "26 km/h", "Home"),
            Ride("3", "Apr 29, 2024", "15.8 km", "35 mins", "27 km/h", "Office Block B"),
            Ride("4", "Apr 28, 2024", "3.1 km", "8 mins", "24 km/h", "Gym Central"),
            Ride("5", "Apr 27, 2024", "9.5 km", "18 mins", "31 km/h", "Shahrah-e-Faisal"),
            Ride("6", "Apr 26, 2024", "21.0 km", "45 mins", "28 km/h", "Sea View Road"),
            Ride("7", "Apr 25, 2024", "4.2 km", "10 mins", "25 km/h", "University Road")
        )

        binding.rvRideHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvRideHistory.adapter = RideHistoryAdapter(mockRides)
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = android.content.Intent(this, DashboardActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_analytics -> true
                R.id.nav_navigation -> {
                    val intent = android.content.Intent(this, NavigationActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_settings -> {
                    val intent = android.content.Intent(this, SettingsActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
}
