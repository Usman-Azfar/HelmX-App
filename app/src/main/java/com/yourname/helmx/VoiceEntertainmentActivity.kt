package com.yourname.helmx

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.helmx.databinding.ActivityVoiceEntertainmentBinding

class VoiceEntertainmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceEntertainmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceEntertainmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Voice settings saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
