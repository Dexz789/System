package com.example.strawberry2

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // Views
    private lateinit var tilCurrentPassword: TextInputLayout
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSubmitChange: Button
    private lateinit var progressBar: ProgressBar

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Setup toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Initialize views
        tilCurrentPassword = findViewById(R.id.tilCurrentPassword)
        tilNewPassword = findViewById(R.id.tilNewPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSubmitChange = findViewById(R.id.btnSubmitChange)
        progressBar = findViewById(R.id.progressBar)

        // Setup click listener
        btnSubmitChange.setOnClickListener {
            changePassword()
        }
    }

    private fun changePassword() {
        // Clear previous errors
        tilCurrentPassword.error = null
        tilNewPassword.error = null
        tilConfirmPassword.error = null

        // Get input values
        val currentPassword = etCurrentPassword.text.toString().trim()
        val newPassword = etNewPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        // Validate inputs
        if (!validateInputs(currentPassword, newPassword, confirmPassword)) {
            return
        }

        // Show loading
        showLoading(true)

        // Get current user
        val user = auth.currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "User not found. Please login again.", Toast.LENGTH_LONG).show()
            showLoading(false)
            return
        }

        // Re-authenticate user with current password
        val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)

        user.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    // Re-authentication successful, now update password
                    user.updatePassword(newPassword)
                        .addOnCompleteListener { updateTask ->
                            showLoading(false)
                            if (updateTask.isSuccessful) {
                                // Password updated successfully
                                showSuccessDialog()
                            } else {
                                // Password update failed
                                handlePasswordUpdateError(updateTask.exception)
                            }
                        }
                } else {
                    // Re-authentication failed
                    showLoading(false)
                    handleReauthenticationError(reauthTask.exception)
                }
            }
    }

    private fun validateInputs(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // Validate current password
        if (currentPassword.isEmpty()) {
            tilCurrentPassword.error = "Please enter your current password"
            isValid = false
        }

        // Validate new password
        if (newPassword.isEmpty()) {
            tilNewPassword.error = "Please enter a new password"
            isValid = false
        } else if (newPassword.length < 6) {
            tilNewPassword.error = "Password must be at least 6 characters"
            isValid = false
        }

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.error = "Please confirm your new password"
            isValid = false
        } else if (newPassword != confirmPassword) {
            tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        }

        // Check if new password is same as current
        if (currentPassword == newPassword && currentPassword.isNotEmpty()) {
            tilNewPassword.error = "New password must be different from current password"
            isValid = false
        }

        return isValid
    }

    private fun handleReauthenticationError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthInvalidCredentialsException -> {
                tilCurrentPassword.error = "Incorrect current password"
            }
            else -> {
                Toast.makeText(
                    this,
                    "Authentication failed: ${exception?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handlePasswordUpdateError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthWeakPasswordException -> {
                tilNewPassword.error = "Password is too weak. Please use a stronger password."
            }
            else -> {
                Toast.makeText(
                    this,
                    "Failed to update password: ${exception?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Success")
            .setMessage("Your password has been changed successfully!")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finish() // Return to profile screen
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            btnSubmitChange.isEnabled = false
            btnSubmitChange.text = "Changing Password..."
            progressBar.visibility = View.VISIBLE
        } else {
            btnSubmitChange.isEnabled = true
            btnSubmitChange.text = "Change Password"
            progressBar.visibility = View.GONE
        }
    }
}