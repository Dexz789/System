package com.example.strawberry2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.strawberry2.Otp.EmailService
import com.example.strawberry2.Otp.OtpVerificationActivity
import com.example.strawberry2.databinding.ActivityRegisterBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "RegisterActivity"
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
            Toast.makeText(
                this,
                "Google sign in failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            binding.btnGoogleSignUp.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnGoogleSignUp.setOnClickListener {
            signUpWithGoogle()
        }

        binding.btnEmailSignUp.setOnClickListener {
            signUpWithEmail()
        }

        binding.tvSignIn.setOnClickListener {
            finish() // Go back to login
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun signUpWithGoogle() {
        binding.btnGoogleSignUp.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun signUpWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Validation
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email"
            binding.etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm your password"
            binding.etConfirmPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }

        // Check if email already exists before sending OTP
        checkEmailAndSendOtp(email, password)
    }

    private fun checkEmailAndSendOtp(email: String, password: String) {
        binding.btnEmailSignUp.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // Check if email is already registered
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods
                    if (signInMethods.isNullOrEmpty()) {
                        // Email is not registered, proceed to send OTP
                        sendOtpToEmail(email, password)
                    } else {
                        // Email is already registered
                        binding.btnEmailSignUp.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "This email is already registered. Please login instead.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    binding.btnEmailSignUp.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Error checking email: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun sendOtpToEmail(email: String, password: String) {
        // Generate 6-digit OTP
        val otp = generateOtp()

        Log.d(TAG, "Generated OTP: $otp") // For debugging - remove in production

        // Send OTP via email
        EmailService.sendOtpEmail(
            email = email,
            otp = otp,
            onSuccess = {
                runOnUiThread {
                    binding.btnEmailSignUp.isEnabled = true
                    binding.progressBar.visibility = View.GONE

                    Toast.makeText(
                        this,
                        "Verification code sent to your email",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to OTP verification activity
                    navigateToOtpVerification(email, password, otp)
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    binding.btnEmailSignUp.isEnabled = true
                    binding.progressBar.visibility = View.GONE

                    Toast.makeText(
                        this,
                        "Failed to send verification code: $error",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun generateOtp(): String {
        return (100000..999999).random().toString()
    }

    private fun navigateToOtpVerification(email: String, password: String, otp: String) {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra(OtpVerificationActivity.EXTRA_EMAIL, email)
            putExtra(OtpVerificationActivity.EXTRA_PASSWORD, password)
            putExtra(OtpVerificationActivity.EXTRA_OTP, otp)
        }
        startActivity(intent)
        finish()
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + account?.id)

        val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.btnGoogleSignUp.isEnabled = true
                binding.progressBar.visibility = View.GONE

                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    Toast.makeText(
                        this,
                        "Welcome ${user?.displayName}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Log.w(
                        TAG,
                        "Authentication failed: ${task.exception?.message}",
                        task.exception
                    )
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}