package com.yourname.helmx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourname.helmx.databinding.ActivityPrivacyPermissionsBinding

class PrivacyPermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyPermissionsBinding

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        binding.switchCamera.isChecked = isGranted
    }

    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        binding.switchMic.isChecked = isGranted
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        binding.switchLocation.isChecked = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Update states when returning to layout to ensure accurate visual toggles
        binding.switchCamera.isChecked = checkPermission(Manifest.permission.CAMERA)
        binding.switchMic.isChecked = checkPermission(Manifest.permission.RECORD_AUDIO)
        binding.switchLocation.isChecked = checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        
        binding.switchCamera.setOnClickListener {
            val isChecked = binding.switchCamera.isChecked
            if (isChecked) {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                openAppSettings()
            }
        }
        
        binding.switchMic.setOnClickListener {
            val isChecked = binding.switchMic.isChecked
            if (isChecked) {
                requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                openAppSettings()
            }
        }

        binding.switchLocation.setOnClickListener {
            val isChecked = binding.switchLocation.isChecked
            if (isChecked) {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                openAppSettings()
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }
}
