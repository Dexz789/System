package com.example.strawberry2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.strawberry2.Otp.EmailService
import com.example.strawberry2.Otp.LoginOtpVerificationActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.example.strawberry2.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "LoginActivity"
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed, Check your internet connection", e)
            Toast.makeText(
                this,
                "Google sign in failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            binding.btnGoogleSignIn.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Check if user is already logged in
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        binding.btnEmailSignIn.setOnClickListener {
            signInWithEmail()
        }

        binding.tvSignUp.setOnClickListener {
            navigateToRegister()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun signInWithGoogle() {
        binding.btnGoogleSignIn.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun signInWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

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

        // Verify credentials first, then send OTP
        verifyCredentialsAndSendOtp(email, password)
    }

    private fun verifyCredentialsAndSendOtp(email: String, password: String) {
        binding.btnEmailSignIn.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // First verify that credentials are correct
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Credentials are correct, sign out temporarily and send OTP
                    Log.d(TAG, "Credentials verified, sending OTP")
                    auth.signOut()

                    // Generate and send OTP
                    sendLoginOtp(email, password)
                } else {
                    // Credentials are incorrect
                    binding.btnEmailSignIn.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun sendLoginOtp(email: String, password: String) {
        // Generate 6-digit OTP
        val otp = generateOtp()

        Log.d(TAG, "Generated Login OTP: $otp") // For debugging - remove in production

        // Send OTP via email
        EmailService.sendLoginOtpEmail(
            email = email,
            otp = otp,
            onSuccess = {
                runOnUiThread {
                    binding.btnEmailSignIn.isEnabled = true
                    binding.progressBar.visibility = View.GONE

                    Toast.makeText(
                        this,
                        "Verification code sent to your email",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to OTP verification activity
                    navigateToLoginOtpVerification(email, password, otp)
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    binding.btnEmailSignIn.isEnabled = true
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

    private fun navigateToLoginOtpVerification(email: String, password: String, otp: String) {
        val intent = Intent(this, LoginOtpVerificationActivity::class.java).apply {
            putExtra(LoginOtpVerificationActivity.EXTRA_EMAIL, email)
            putExtra(LoginOtpVerificationActivity.EXTRA_PASSWORD, password)
            putExtra(LoginOtpVerificationActivity.EXTRA_OTP, otp)
        }
        startActivity(intent)
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        input.hint = "Enter your email"
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(50, 20, 50, 20)
        input.layoutParams = params
        container.addView(input)

        builder.setTitle("Reset Password")
            .setMessage("Enter your email address to receive a password reset link")
            .setView(container)
            .setPositiveButton("Send") { dialog, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Show loading
                binding.progressBar.visibility = View.VISIBLE

                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        binding.progressBar.visibility = View.GONE
                        if (task.isSuccessful) {
                            Toast.makeText(
                                this,
                                "Password reset email sent! Check your inbox.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Failed: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + account?.id)

        val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.btnGoogleSignIn.isEnabled = true
                binding.progressBar.visibility = View.GONE

                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    Toast.makeText(
                        this,
                        "Welcome back ${user?.displayName}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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