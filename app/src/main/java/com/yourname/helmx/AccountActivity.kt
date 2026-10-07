package com.yourname.helmx

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.helmx.databinding.ActivityAccountBinding
import kotlinx.coroutines.launch

class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding
    private val authManager = AuthManager()
    private val firestore = FirebaseFirestore.getInstance()
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = authManager.getCurrentUser()
        currentUserId = user?.uid

        binding.btnBack.setOnClickListener { finish() }

        if (currentUserId != null) {
            loadUserData(currentUserId!!)
        } else {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnSave.setOnClickListener {
            binding.btnSave.isEnabled = false
            saveUserData()
        }

        binding.btnLogout.setOnClickListener {
            logoutUser()
        }
    }
    
    private fun logoutUser() {
        authManager.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun loadUserData(uid: String) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = document.toObject(User::class.java)
                    if (user != null) {
                        binding.etFullName.setText(user.fullname)
                        binding.etPhone.setText(user.phone)
                        binding.tvEmail.text = "Email: ${user.email}"
                        // Pre-fill email in newEmail if they want to edit
                        binding.etNewEmail.setText(user.email)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserData() {
        val uid = currentUserId ?: return
        val newName = binding.etFullName.text.toString().trim()
        val newPhone = binding.etPhone.text.toString().trim()
        
        val currentPass = binding.etCurrentPassword.text.toString()
        val newEmail = binding.etNewEmail.text.toString().trim()
        val newPass = binding.etNewPassword.text.toString()

        if (newName.isEmpty()) {
            binding.etFullName.error = "Name cannot be empty"
            binding.btnSave.isEnabled = true
            return
        }

        if (newPhone.isEmpty()) {
            binding.etPhone.error = "Phone number is required"
            binding.btnSave.isEnabled = true
            return
        }
        if (newPhone.length < 11) {
            binding.etPhone.error = "Invalid phone number (must be 11 digits)"
            binding.btnSave.isEnabled = true
            return
        }

        var updateEmailFlag = false
        var updatePassFlag = false

        if (newEmail.isNotEmpty() && !binding.tvEmail.text.contains(newEmail)) {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                binding.etNewEmail.error = "Invalid email format"
                binding.btnSave.isEnabled = true
                return
            }
            updateEmailFlag = true
        }
        if (newPass.isNotEmpty()) {
            if (newPass.length < 6) {
                binding.etNewPassword.error = "Password must be at least 6 characters"
                binding.btnSave.isEnabled = true
                return
            }
            updatePassFlag = true
        }

        lifecycleScope.launch {
            try {
                if (updateEmailFlag || updatePassFlag) {
                    if (currentPass.isEmpty()) {
                        binding.etCurrentPassword.error = "Required to change email/password"
                        Toast.makeText(this@AccountActivity, "Entering Current Password is required for sensible changes", Toast.LENGTH_SHORT).show()
                        binding.btnSave.isEnabled = true
                        return@launch
                    }

                    // Re-authenticate
                    val authResult = authManager.reauthenticate(currentPass)
                    if (authResult.isFailure) {
                        binding.etCurrentPassword.error = "Incorrect password"
                        Toast.makeText(this@AccountActivity, "Authentication failed. Incorrect password.", Toast.LENGTH_SHORT).show()
                        binding.btnSave.isEnabled = true
                        return@launch
                    }

                    if (updateEmailFlag) {
                        val emailResult = authManager.updateEmail(newEmail)
                        if (emailResult.isFailure) {
                            Toast.makeText(this@AccountActivity, "Failed to update email.", Toast.LENGTH_SHORT).show()
                            binding.btnSave.isEnabled = true
                            return@launch
                        }
                    }

                    if (updatePassFlag) {
                        val passResult = authManager.updatePassword(newPass)
                        if (passResult.isFailure) {
                            Toast.makeText(this@AccountActivity, "Failed to update password.", Toast.LENGTH_SHORT).show()
                            binding.btnSave.isEnabled = true
                            return@launch
                        }
                    }
                }

                // Finally update Firestore document
                val updates = mutableMapOf<String, Any>(
                    "fullname" to newName,
                    "phone" to newPhone
                )
                if (updateEmailFlag) {
                    updates["email"] = newEmail
                }

                firestore.collection("users").document(uid).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this@AccountActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        binding.btnSave.isEnabled = true
                        // clear passwords
                        binding.etCurrentPassword.text?.clear()
                        binding.etNewPassword.text?.clear()
                        if (updateEmailFlag) {
                            binding.tvEmail.text = "Email: $newEmail"
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@AccountActivity, "Failed to update profile", Toast.LENGTH_SHORT).show()
                        binding.btnSave.isEnabled = true
                    }
            } catch (e: Exception) {
                Toast.makeText(this@AccountActivity, "Error saving updates: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
            }
        }
    }
}
