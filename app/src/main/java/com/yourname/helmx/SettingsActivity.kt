package com.yourname.helmx

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.yourname.helmx.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("HelmXSettings", Context.MODE_PRIVATE)

        val isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", true)
        binding.switchDarkMode.isChecked = isDarkModeEnabled

        if (isDarkModeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setupClickListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply()
            
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        binding.cardCrashAlerts.setOnClickListener {
            startActivity(Intent(this, CrashAlertsActivity::class.java))
        }

        binding.cardAccountSetup.setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        binding.cardDrowsiness.setOnClickListener {
            startActivity(Intent(this, DrowsinessActivity::class.java))
        }
        binding.cardVoiceEntertainment.setOnClickListener {
            startActivity(Intent(this, VoiceEntertainmentActivity::class.java))
        }
        binding.cardPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPermissionsActivity::class.java))
        }

        binding.btnLogoutSettings.setOnClickListener {
            AuthManager().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = android.content.Intent(this, DashboardActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_analytics -> {
                    val intent = android.content.Intent(this, AnalyticsActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_navigation -> {
                    val intent = android.content.Intent(this, NavigationActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }
}
