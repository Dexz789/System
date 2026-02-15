package com.example.strawberry2.Otp

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.strawberry2.MainActivity
import com.example.strawberry2.R
import com.example.strawberry2.databinding.ActivityLoginOtpVerificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginOtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginOtpVerificationBinding
    private lateinit var auth: FirebaseAuth

    private var email: String = ""
    private var password: String = ""
    private var generatedOtp: String = ""
    private var countDownTimer: CountDownTimer? = null

    companion object {
        const val EXTRA_EMAIL = "email"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_OTP = "otp"
        private const val OTP_VALIDITY_TIME = 300000L // 5 minutes in milliseconds
        private const val COUNTDOWN_INTERVAL = 1000L // 1 second
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Get data from intent
        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        generatedOtp = intent.getStringExtra(EXTRA_OTP) ?: ""

        if (email.isEmpty() || password.isEmpty() || generatedOtp.isEmpty()) {
            Toast.makeText(this, "Invalid verification data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupClickListeners()
        startOtpTimer()
    }

    private fun setupUI() {
        // Display masked email
        binding.tvEmailDisplay.text = "We've sent a verification code to\n${maskEmail(email)}"

        // Update title for login
        binding.tvTitle.text = "Verify Login"

        // Setup OTP EditTexts
        setupOtpInputs()
    }

    private fun setupOtpInputs() {
        val otpFields = listOf(
            binding.etOtp1,
            binding.etOtp2,
            binding.etOtp3,
            binding.etOtp4,
            binding.etOtp5,
            binding.etOtp6
        )

        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1) {
                        // Move to next field
                        if (index < otpFields.size - 1) {
                            otpFields[index + 1].requestFocus()
                        } else {
                            // All fields filled, hide keyboard
                            editText.clearFocus()
                        }
                    } else if (s?.isEmpty() == true && before == 1) {
                        // Move to previous field on delete
                        if (index > 0) {
                            otpFields[index - 1].requestFocus()
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }

        // Request focus on first field
        otpFields[0].requestFocus()
    }

    private fun setupClickListeners() {
        binding.btnVerify.setOnClickListener {
            verifyOtp()
        }

        binding.tvResendOtp.setOnClickListener {
            if (binding.tvResendOtp.isEnabled) {
                resendOtp()
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun verifyOtp() {
        val enteredOtp = getEnteredOtp()

        if (enteredOtp.length != 6) {
            Toast.makeText(this, "Please enter complete OTP", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnVerify.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // Verify OTP
        if (enteredOtp == generatedOtp) {
            // OTP is correct, sign in to Firebase
            signInToFirebase()
        } else {
            // OTP is incorrect
            binding.btnVerify.isEnabled = true
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
            clearOtpFields()
        }
    }

    private fun signInToFirebase() {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.btnVerify.isEnabled = true
                binding.progressBar.visibility = View.GONE

                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Toast.makeText(
                        this,
                        "Welcome back ${user?.email}!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to main activity
                    navigateToMain()
                } else {
                    Toast.makeText(
                        this,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun resendOtp() {
        // Generate new OTP
        generatedOtp = generateOtp()

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.tvResendOtp.isEnabled = false

        // Send new OTP via email
        EmailService.sendLoginOtpEmail(
            email = email,
            otp = generatedOtp,
            onSuccess = {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "New OTP sent to your email", Toast.LENGTH_SHORT).show()
                    startOtpTimer()
                    clearOtpFields()
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvResendOtp.isEnabled = true
                    Toast.makeText(this, "Failed to send OTP: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun startOtpTimer() {
        binding.tvResendOtp.isEnabled = false

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(OTP_VALIDITY_TIME, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvTimer.text = String.format("Code expires in %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.tvTimer.text = "Code expired"
                binding.tvResendOtp.isEnabled = true
                binding.tvResendOtp.text = "Resend OTP"
            }
        }.start()
    }

    private fun getEnteredOtp(): String {
        return buildString {
            append(binding.etOtp1.text.toString())
            append(binding.etOtp2.text.toString())
            append(binding.etOtp3.text.toString())
            append(binding.etOtp4.text.toString())
            append(binding.etOtp5.text.toString())
            append(binding.etOtp6.text.toString())
        }
    }

    private fun clearOtpFields() {
        binding.etOtp1.text?.clear()
        binding.etOtp2.text?.clear()
        binding.etOtp3.text?.clear()
        binding.etOtp4.text?.clear()
        binding.etOtp5.text?.clear()
        binding.etOtp6.text?.clear()
        binding.etOtp1.requestFocus()
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email

        val username = parts[0]
        val domain = parts[1]

        return if (username.length <= 3) {
            "${username[0]}***@$domain"
        } else {
            "${username.substring(0, 2)}***${username.last()}@$domain"
        }
    }

    private fun generateOtp(): String {
        return (100000..999999).random().toString()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
