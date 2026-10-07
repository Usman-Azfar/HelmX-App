package com.yourname.helmx

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.helmx.databinding.ActivityCrashAlertsBinding

class CrashAlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashAlertsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Configurations Saved Successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
