package com.yourname.helmx

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.helmx.databinding.ActivityDrowsinessBinding

class DrowsinessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrowsinessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrowsinessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Drowsiness configurations updated!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
