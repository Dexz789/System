package com.example.strawberry2

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    // Drawer
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navHeaderView: android.view.View

    // Views
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvDisplayName: TextView
    private lateinit var tvEmailAddress: TextView
    private lateinit var btnEditName: Button
    private lateinit var btnChangePassword: MaterialCardView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Setup toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup navigation drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        navHeaderView = navigationView.getHeaderView(0)

        // Setup drawer toggle
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.open,
            R.string.close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Setup navigation header
        setupNavigationHeader()

        // Setup navigation menu
        setupNavigation()

        // Highlight current menu item
        navigationView.setCheckedItem(R.id.nav_profile)

        // Initialize views
        ivProfileImage = findViewById(R.id.ivProfileImage)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvDisplayName = findViewById(R.id.tvDisplayName)
        tvEmailAddress = findViewById(R.id.tvEmailAddress)
        btnEditName = findViewById(R.id.btnEditName)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnLogout = findViewById(R.id.btnLogout)

        // Load user data
        loadUserProfile()

        // Setup click listeners
        btnEditName.setOnClickListener {
            showEditNameDialog()
        }

        btnChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupNavigationHeader() {
        val user = auth.currentUser
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)

        if (user != null) {
            // Get views from nav header
            val ivUserProfile = navHeaderView.findViewById<ImageView>(R.id.ivUserProfile)
            val tvUserName = navHeaderView.findViewById<TextView>(R.id.tvUserName)
            val tvUserEmail = navHeaderView.findViewById<TextView>(R.id.tvUserEmail)
            val navHeaderBackground = navHeaderView.findViewById<android.widget.LinearLayout>(R.id.navHeaderBackground)

            // Set user name
            val displayName = user.displayName ?: googleAccount?.displayName ?: "User"
            tvUserName.text = displayName

            // Set user email
            val email = user.email ?: googleAccount?.email ?: "No email"
            tvUserEmail.text = email

            // Load profile image
            val photoUrl = user.photoUrl ?: googleAccount?.photoUrl
            if (photoUrl != null) {
                loadNavHeaderProfileImage(ivUserProfile, photoUrl.toString())
            } else {
                ivUserProfile.setImageResource(R.mipmap.ic_launcher_round)
            }

            // Set themed background
            setThemedBackground(navHeaderBackground, displayName)
        }
    }

    private fun loadNavHeaderProfileImage(imageView: ImageView, url: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)

                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(R.mipmap.ic_launcher_round)
                }
            }
        }
    }

    private fun setThemedBackground(layout: android.widget.LinearLayout, userName: String) {
        // Generate a color based on the user's name
        val hash = userName.hashCode()

        val gradients = listOf(
            intArrayOf(android.graphics.Color.parseColor("#667eea"), android.graphics.Color.parseColor("#764ba2")),
            intArrayOf(android.graphics.Color.parseColor("#f093fb"), android.graphics.Color.parseColor("#f5576c")),
            intArrayOf(android.graphics.Color.parseColor("#4facfe"), android.graphics.Color.parseColor("#00f2fe")),
            intArrayOf(android.graphics.Color.parseColor("#43e97b"), android.graphics.Color.parseColor("#38f9d7")),
            intArrayOf(android.graphics.Color.parseColor("#fa709a"), android.graphics.Color.parseColor("#fee140")),
            intArrayOf(android.graphics.Color.parseColor("#30cfd0"), android.graphics.Color.parseColor("#330867")),
            intArrayOf(android.graphics.Color.parseColor("#a8edea"), android.graphics.Color.parseColor("#fed6e3")),
            intArrayOf(android.graphics.Color.parseColor("#ff9a9e"), android.graphics.Color.parseColor("#fecfef"))
        )

        val selectedGradient = gradients[kotlin.math.abs(hash) % gradients.size]

        val gradientDrawable = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            selectedGradient
        )
        layout.background = gradientDrawable
    }

    private fun setupNavigation() {
        navigationView.setNavigationItemSelectedListener { item ->
            val currentActivity = this::class.java.simpleName
            when (item.itemId) {

                R.id.nav_profile -> {
                    // Already on profile, just close drawer
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_home -> {
                    if (currentActivity != "MainActivity") {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    drawerLayout.closeDrawers()
                    true
                }

                R.id.nav_about -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        showAboutDialog()
                    }, 250)
                    true
                }

                R.id.nav_history -> {
                    if (currentActivity != "DiagnosisHistoryActivity") {
                        startActivity(Intent(this, DiagnosisHistoryActivity::class.java))
                        finish()
                    }
                    drawerLayout.closeDrawers()
                    true
                }

                R.id.nav_logout -> {
                    showLogoutConfirmation()
                    drawerLayout.closeDrawers()
                    true
                }

                else -> false
            }
        }
        val headerView = navigationView.getHeaderView(0)

        val imageView1 = headerView.findViewById<ImageView>(R.id.ivUserProfile)
        val textView = headerView.findViewById<TextView>(R.id.tvUserName)
        val textView1 = headerView.findViewById<TextView>(R.id.tvUserEmail)

        val openProfile = {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        imageView1.setOnClickListener { openProfile() }
        textView.setOnClickListener { openProfile() }
        textView1.setOnClickListener { openProfile() }
    }

    private fun showAboutDialog() {
        val aboutMessage = """
        🌿 About This App
        
        This application helps farmers and gardeners identify plant diseases 
        by analyzing images of leaves and fruits using Machine Learning.

        Once a plant image is uploaded or captured, the Model detects possible 
        diseases. You can also consult the AI for further insights and treatment suggestions.
        
        🧠 Model & Dataset:
        - The dataset used for training was sourced from Roboflow.
        - The AI detection model was trained using YOLOv8 in Google Colab.

        🤖 Built With:
        - TensorFlow Lite / Gemini AI API
        - Firebase Storage & Firestore
        - Kotlin + Android Studio

        👨‍💻 Developed by:
        GrowMate Inc.
        
        📅 Version: 1.0.0
        © 2025 GrowMate Inc.. All Rights Reserved.
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("About Strawberry AI Diagnoser")
            .setMessage(aboutMessage)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(R.mipmap.ic_launcher_round)
            .show()
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)

        if (user != null) {
            // Get display name
            val displayName = user.displayName ?: googleAccount?.displayName ?: "User"
            tvUserName.text = displayName
            tvDisplayName.text = displayName

            // Get email
            val email = user.email ?: googleAccount?.email ?: "No email"
            tvUserEmail.text = email
            tvEmailAddress.text = email

            // Load profile image
            val photoUrl = user.photoUrl ?: googleAccount?.photoUrl
            if (photoUrl != null) {
                loadProfileImage(photoUrl.toString())
            }
        }
    }

    private fun loadProfileImage(url: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)

                withContext(Dispatchers.Main) {
                    ivProfileImage.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep default image if loading fails
            }
        }
    }

    private fun showEditNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)

        // Pre-fill with current name
        etName.setText(auth.currentUser?.displayName ?: "")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Name")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateUserName(newName)
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun updateUserName(newName: String) {
        val user = auth.currentUser
        if (user != null) {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            user.updateProfile(profileUpdates)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Update UI
                        tvUserName.text = newName
                        tvDisplayName.text = newName

                        // Also update navigation header
                        val navUserName = navHeaderView.findViewById<TextView>(R.id.tvUserName)
                        navUserName.text = newName

                        Toast.makeText(this, "Name updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Failed to update name: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { dialog, _ ->
                performLogout()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performLogout() {
        // Sign out from Firebase
        auth.signOut()

        // Also sign out from Google (to allow account switching)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        // Navigate to login screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}