package com.yourname.helmx

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yourname.helmx.databinding.ActivityDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val authManager = AuthManager()
    private lateinit var helmetBleManager: HelmetBleManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBluetoothAndScan()
        } else {
            Toast.makeText(this, "Bluetooth and Location permissions are required for pairing", Toast.LENGTH_LONG).show()
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            helmetBleManager.startScan()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to pair with the helmet", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        helmetBleManager = HelmetBleManager.getInstance(this)

        // Check if user is logged in
        val currentUser = authManager.getCurrentUser()
        if (currentUser == null) {
            navigateToLogin()
            return
        }

        // Load user data
        loadUserData(currentUser.uid)

        // Setup UI listeners
        setupClickListeners()
        setupBottomNavigation()
        
        // Observe real-time data from BLE
        observeHelmetData()
        
        // Setup Helmet Display
        setupHelmetDisplay()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun setupHelmetDisplay() {
        binding.ivHelmetDisplay.setImageResource(R.mipmap.helmet_image)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_analytics -> {
                    val intent = Intent(this, AnalyticsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_navigation -> {
                    val intent = Intent(this, NavigationActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.ivProfile.setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        binding.btnNotifications.setOnClickListener {
            Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show()
        }

        binding.btnPair.setOnClickListener {
            if (helmetBleManager.helmetData.value.connectionStatus == "Connected") {
                helmetBleManager.disconnect()
                Toast.makeText(this, "Disconnected from HelmX", Toast.LENGTH_SHORT).show()
            } else {
                checkPermissionsAndScan()
            }
        }

        binding.cardDrowsinessAlert.setOnClickListener {
            startActivity(Intent(this, DrowsinessActivity::class.java))
        }

        binding.cardCrashAlert.setOnClickListener {
            startActivity(Intent(this, CrashAlertsActivity::class.java))
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            checkBluetoothAndScan()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkBluetoothAndScan() {
        if (helmetBleManager.isBluetoothEnabled()) {
            helmetBleManager.startScan()
            Toast.makeText(this, "Scanning for HelmX Pro v1...", Toast.LENGTH_SHORT).show()
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        }
    }

    private var lastHandledDestination = ""

    private fun observeHelmetData() {
        lifecycleScope.launch {
            helmetBleManager.helmetData.collectLatest { data ->
                updateUI(data)
                
                // Auto-navigate to Navigation page if a destination is received
                if (data.destination.isNotEmpty() && data.destination != lastHandledDestination) {
                    lastHandledDestination = data.destination
                    val intent = Intent(this@DashboardActivity, NavigationActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                }
            }
        }
    }

    private fun updateUI(data: HelmetData) {
        binding.tvConnectionStatus.text = data.connectionStatus
        
        when (data.connectionStatus) {
            "Connected" -> {
                binding.tvConnectionStatus.setTextColor(Color.parseColor("#43A047")) // Green
                binding.btnPair.text = "Unpair"
                binding.btnPair.isEnabled = true
            }
            "Searching...", "Connecting..." -> {
                binding.tvConnectionStatus.setTextColor(Color.parseColor("#FB8C00")) // Orange
                binding.btnPair.text = "Wait"
                binding.btnPair.isEnabled = false
            }
            else -> {
                binding.tvConnectionStatus.setTextColor(Color.parseColor("#E53935")) // Red
                binding.btnPair.text = "Pair"
                binding.btnPair.isEnabled = true
            }
        }


        binding.tvBatteryStat.text = "${data.batteryLevel}%"
        binding.tvSpeedStat.text = "${data.speed.toInt()} km/h"
        binding.tvDistanceStat.text = "${String.format("%.1f", data.distance)} km"
        
        binding.tvDrowsinessStatus.text = if (data.isDrowsy) "ALERT!" else "Normal"
        binding.tvDrowsinessStatus.setTextColor(if (data.isDrowsy) Color.RED else Color.parseColor("#43A047"))
        
        binding.tvCrashStatus.text = if (data.isCrashDetected) "CRASH!" else "Active"
        binding.tvCrashStatus.setTextColor(if (data.isCrashDetected) Color.RED else Color.parseColor("#43A047"))
    }

    private fun loadUserData(uid: String) {
        lifecycleScope.launch {
            val result = authManager.getUserData(uid)
            result.fold(
                onSuccess = { user ->
                    binding.tvWelcome.text = "Hello, ${user.fullname.split(" ")[0]}!"
                },
                onFailure = {
                    binding.tvWelcome.text = "Hello, Rider!"
                }
            )
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

