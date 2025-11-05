package com.example.strawberry2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.core.view.GravityCompat
import com.example.strawberry2.Chat.ChatBottomSheetDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import java.io.IOException
import kotlinx.coroutines.*
import java.net.URL
import java.util.logging.Handler
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private var aiInsightsText: String? = null
    private lateinit var fabChat: ExtendedFloatingActionButton

    private lateinit var navHeaderView: View

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var detector: ObjectDetector
    private lateinit var imageView: ImageView
    private lateinit var btnSelectImage: MaterialCardView
    private lateinit var tvResults: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var diagnosisRepository: DiagnosisRepository

    private var selectedBitmap: Bitmap? = null
    private var currentDetections: List<ObjectDetector.Detection>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    // Camera capture launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                selectedBitmap = it
                imageView.setImageBitmap(it)
                tvResults.text = "Analyzing captured image..."

                // Clear old detections and reset button
                currentDetections = null
                resetSaveButton()  // Add this line
                updateSaveButtonState()  // This will hide the button
                aiInsightsText = null
                performDetection(it)
            }
        }
    }


    // Image picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchImagePicker()
        } else {
            Toast.makeText(this, "Permission denied to read images", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "GrowMate"
        // Initialize Firebase Auth and Repository FIRST - BEFORE anything else
        auth = Firebase.auth
        diagnosisRepository = DiagnosisRepository()



        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        navHeaderView = navigationView.getHeaderView(0)

        setupNavigationHeader()
        setSupportActionBar(toolbar)

        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { item ->
            val currentActivity = this::class.java.simpleName
            when (item.itemId) {
                R.id.nav_about -> {
                    drawerLayout.closeDrawer(GravityCompat.START) // ✅ close the drawer first

                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        showAboutDialog() // ✅ open the alert after a short delay
                    }, 250) // wait a bit for the drawer to close animation

                    true
                }
                R.id.nav_settings -> {
                    if (currentActivity != "SettingsActivity") {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                }
                R.id.nav_about -> {
                    if (currentActivity != "AboutActivity") {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                }
                R.id.nav_logout -> {
                    // Sign out from Firebase
                    auth.signOut()

                    // Also sign out from Google (to allow account switching)
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(this, gso)
                    googleSignInClient.signOut()

                    // Navigate to login screen
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                R.id.nav_history -> {
                    // Navigate to history activity
                    if (currentActivity != "DiagnosisHistoryActivity") {
                        startActivity(Intent(this, DiagnosisHistoryActivity::class.java))
                    }
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        // Initialize views
        imageView = findViewById(R.id.imageView)
        btnSelectImage = findViewById(R.id.cardSelectImage)
        tvResults = findViewById(R.id.tvResults)

        // Initialize detector
        try {
            detector = ObjectDetector(this)
            Toast.makeText(this, "Model loaded successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading model: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }

        // Set up button listener
        btnSelectImage.setOnClickListener {
            checkPermissionAndPickImage()
        }

        val btnTakeImage: MaterialCardView = findViewById(R.id.cardTakeImage)
        btnTakeImage.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }
        // Add Save Diagnosis button
        val layoutActionButtons: LinearLayout = findViewById(R.id.layoutActionButtons)
        val btnSaveDiagnosis: Button = findViewById(R.id.btnSaveDiagnosis)
        val btnConsultAI: Button = findViewById(R.id.btnConsultAI)
        btnSaveDiagnosis.setOnClickListener {
            saveDiagnosis()
        }
        btnConsultAI.setOnClickListener {
            consultAI()
        }
        fabChat = findViewById(R.id.fabChat)
        fabChat.setOnClickListener {
            openChatDialog()
        }

        // Set click listener for FAB
        fabChat.setOnTouchListener(object : View.OnTouchListener {
            private var dX = 0f
            private var dY = 0f
            private var startX = 0f
            private var startY = 0f
            private var hasMoved = false
            private val moveThreshold = 10f  // small pixel buffer to detect drag

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // remember initial positions
                        dX = view.x - event.rawX
                        dY = view.y - event.rawY
                        startX = event.rawX
                        startY = event.rawY
                        hasMoved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY

                        // check if movement exceeds threshold
                        if (Math.abs(event.rawX - startX) > moveThreshold ||
                            Math.abs(event.rawY - startY) > moveThreshold) {
                            hasMoved = true
                        }

                        // constrain within screen bounds
                        val parent = view.parent as View
                        val maxX = (parent.width - view.width).toFloat()
                        val maxY = (parent.height - view.height).toFloat()

                        view.animate()
                            .x(newX.coerceIn(0f, maxX))
                            .y(newY.coerceIn(0f, maxY))
                            .setDuration(0)
                            .start()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!hasMoved) {
                            // treat as click if not moved
                            view.performClick()
                        } else {
                            // snap to nearest horizontal edge
                            val parentWidth = (view.parent as View).width
                            val targetX = if (view.x + view.width / 2 < parentWidth / 2)
                                0f
                            else
                                (parentWidth - view.width).toFloat()

                            view.animate()
                                .x(targetX)
                                .setDuration(200)
                                .start()
                        }
                        return true
                    }

                    else -> return false
                }
            }
        })


    }

    private fun saveDiagnosis() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please login to save diagnosis", Toast.LENGTH_SHORT).show()
            return
        }

        val detections = currentDetections
        if (detections == null) {
            Toast.makeText(this, "No diagnosis to save", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = selectedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            return
        }

        println("DEBUG MainActivity: Bitmap dimensions: ${bitmap.width}x${bitmap.height}")

        // Show loading
        val btnSaveDiagnosis = findViewById<Button>(R.id.btnSaveDiagnosis)
        val progressBarSave = findViewById<ProgressBar>(R.id.progressBarSave)

// Disable the button but show a spinner
        btnSaveDiagnosis.isEnabled = false
        btnSaveDiagnosis.text = "Saving..."
        progressBarSave.visibility = View.VISIBLE

        // Capture the current text BEFORE adding "Saving..."
        val originalText = tvResults.text.toString()
        tvResults.text = "$originalText\n\nSaving diagnosis and uploading image..."

        coroutineScope.launch {
            try {
                // Convert detections to DiagnosisData
                val detectionResults = detections.map { detection ->
                    DetectionResult(
                        label = detection.label,
                        confidence = detection.score,
                        boundingBox = BoundingBoxData(
                            left = detection.boundingBox.left,
                            top = detection.boundingBox.top,
                            right = detection.boundingBox.right,
                            bottom = detection.boundingBox.bottom
                        )
                    )
                }

                val diagnosis = DiagnosisData(
                    userId = user.uid,
                    timestamp = System.currentTimeMillis(),
                    detections = detectionResults,
                    totalIssuesFound = detections.size,
                    aiInsights = aiInsightsText // ✅ include AI insights if available
                )

                // IMPORTANT: Pass the annotated bitmap (the one with bounding boxes)
                // If you want to save the original, use selectedBitmap
                // If you want to save with bounding boxes, get it from imageView
                val bitmapToSave = (imageView.drawable as? BitmapDrawable)?.bitmap ?: bitmap

                println("DEBUG MainActivity: Saving diagnosis with bitmap ${bitmapToSave.width}x${bitmapToSave.height}")

                // Run save operation in IO thread with image
                val result = withContext(Dispatchers.IO) {
                    diagnosisRepository.saveDiagnosis(diagnosis, bitmapToSave)
                }

                // Back on Main thread now
                result.onSuccess { id ->
                    tvResults.text = "$originalText\n\n✓ Saved! You can view it in Past Reports."
                    Toast.makeText(this@MainActivity, "Diagnosis saved successfully!", Toast.LENGTH_SHORT).show()

                    btnSaveDiagnosis.text = "Saved ✓"
                    btnSaveDiagnosis.isEnabled = false
                    progressBarSave.visibility = View.GONE // ✅ hide spinner
                }
                    .onFailure { exception ->
                    println("ERROR MainActivity: Save failed: ${exception.message}")
                    exception.printStackTrace()
                    tvResults.text = "$originalText\n\n✗ Save failed: ${exception.message}"
                    Toast.makeText(
                        this@MainActivity,
                        "Error saving diagnosis: ${exception.message}",
                        Toast.LENGTH_LONG

                    ).show()
                    // Re-enable button to allow retry
                        btnSaveDiagnosis.text = "Save Diagnosis"
                        btnSaveDiagnosis.isEnabled = true
                        progressBarSave.visibility = View.GONE // ✅ hide spinner
                }

            } catch (e: Exception) {
                println("ERROR MainActivity: Exception during save: ${e.message}")
                e.printStackTrace()
                tvResults.text = "$originalText\n\n✗ Error: ${e.message}"
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                progressBarSave.visibility = View.GONE
                btnSaveDiagnosis.text = "Save Diagnosis"
                // Re-enable button to allow retry
                btnSaveDiagnosis.isEnabled = true
            }
        }
    }
    private fun resetSaveButton() {
        val btnSaveDiagnosis = findViewById<Button>(R.id.btnSaveDiagnosis)
        btnSaveDiagnosis.text = "Save Diagnosis"
        btnSaveDiagnosis.isEnabled = false
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
    // Helper method to check if we should enable the Save button
    private fun updateSaveButtonState() {
        val layoutActionButtons = findViewById<LinearLayout>(R.id.layoutActionButtons)
        val btnSaveDiagnosis = findViewById<Button>(R.id.btnSaveDiagnosis)

        val hasImage = selectedBitmap != null
        val hasDetections = currentDetections != null
        val resultsText = tvResults.text.toString()
        val hasValidResults = resultsText.isNotEmpty() &&
                !resultsText.contains("Analyzing") &&
                !resultsText.contains("Detecting") &&
                !resultsText.contains("Saving")

        if (hasImage && hasDetections && hasValidResults) {
            layoutActionButtons.visibility = View.VISIBLE
            btnSaveDiagnosis.isEnabled = true
        } else {
            layoutActionButtons.visibility = View.GONE
        }
    }

    private fun checkPermissionAndPickImage() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        launchImagePicker()
                    }
                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6 to 12
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        launchImagePicker()
                    }
                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }
            else -> {
                launchImagePicker()
            }
        }
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Display image
            imageView.setImageBitmap(selectedBitmap)
            tvResults.text = "Analyzing image..."

            // Clear old detections and reset button
            currentDetections = null
            resetSaveButton()  // Add this line
            updateSaveButtonState()  // This will hide the button
            aiInsightsText = null
            // Automatically perform detection
            selectedBitmap?.let { bitmap ->
                performDetection(bitmap)
            }

        } catch (e: IOException) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun performDetection(bitmap: Bitmap) {
        // Disable select button during detection
        btnSelectImage.isEnabled = false
        tvResults.text = "Detecting diseases..."

        // Hide button while detecting
        updateSaveButtonState()

        // Run detection in background thread
        coroutineScope.launch {
            try {
                val detections = withContext(Dispatchers.Default) {
                    detector.detect(bitmap, confidenceThreshold = 0.5f)
                }

                // IMPORTANT: Store the detections for saving later
                currentDetections = detections

                if (detections.isEmpty()) {
                    tvResults.text = "No diseases detected. Plant appears healthy! Or please take a picture of a strawberry plant"
                    btnSelectImage.isEnabled = true
                    // Let updateSaveButtonState decide if button should show
                    updateSaveButtonState()
                    return@launch
                }

                // Draw bounding boxes on image
                val annotatedBitmap = withContext(Dispatchers.Default) {
                    drawBoundingBoxes(bitmap, detections)
                }
                imageView.setImageBitmap(annotatedBitmap)

                // Display results text
                val resultsText = buildString {
                    append("Found ${detections.size} issue(s):\n\n")
                    detections.forEachIndexed { index, detection ->
                        append("${index + 1}. ${detection.label}\n")
                        append("   Confidence: ${String.format("%.1f%%", detection.score * 100)}\n")
                        append("   Location: (${detection.boundingBox.left.toInt()}, ")
                        append("${detection.boundingBox.top.toInt()}) to ")
                        append("(${detection.boundingBox.right.toInt()}, ")
                        append("${detection.boundingBox.bottom.toInt()})\n\n")
                    }
                }
                tvResults.text = resultsText

                btnSelectImage.isEnabled = true
                // Let updateSaveButtonState decide if button should show
                updateSaveButtonState()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error during detection: ${e.message}", Toast.LENGTH_LONG).show()
                tvResults.text = "Error: ${e.message}"
                btnSelectImage.isEnabled = true
                // Button will stay hidden due to error state
                updateSaveButtonState()
                e.printStackTrace()
            }
        }
    }
    private fun checkCameraPermissionAndOpenCamera() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            takePictureLauncher.launch(cameraIntent)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, detections: List<ObjectDetector.Detection>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Paint for semi-transparent overlay (to highlight disease area)
        val overlayPaint = Paint().apply {
            style = Paint.Style.FILL
            alpha = 40 // Very subtle semi-transparent (0-255)
        }

        // Paint for bounding box outline
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f // Thick for visibility
            isAntiAlias = true
        }

        // Paint for text (much smaller and compact)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f // Significantly smaller text
            style = Paint.Style.FILL
            isAntiAlias = true
            isFakeBoldText = false // Remove bold for smaller appearance
        }

        // Paint for text background
        val backgroundPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        // Colors for different disease types (use warning colors)
        val diseaseColors = mapOf(
            "healthy" to Color.GREEN,
            "leaf_spot" to Color.parseColor("#FF6B6B"), // Red
            "powdery_mildew" to Color.parseColor("#FFA500"), // Orange
            "blight" to Color.parseColor("#FF4444"), // Bright red
            "rust" to Color.parseColor("#D2691E"), // Brown/rust
            "anthracnose" to Color.parseColor("#8B0000") // Dark red
        )

        detections.forEachIndexed { index, detection ->
            // Get color based on disease type, or use default warning color
            val diseaseColor = diseaseColors[detection.label.lowercase().replace(" ", "_")]
                ?: Color.parseColor("#FF6347") // Tomato red as default

            boxPaint.color = diseaseColor
            backgroundPaint.color = diseaseColor
            overlayPaint.color = diseaseColor

            // Draw semi-transparent overlay to highlight the diseased area (OPTIONAL - comment out if you don't want it)
            // canvas.drawRect(detection.boundingBox, overlayPaint)

            // Draw thick bounding box outline - THIS IS THE MAIN HIGHLIGHT
            canvas.drawRect(detection.boundingBox, boxPaint)

            // Add a contrasting inner border for better visibility
            val innerBoxPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.WHITE  // This is fine - it's setting the Paint's color property, not reassigning the variable
                alpha = 255
                isAntiAlias = true
            }
            val innerRect = RectF(
                detection.boundingBox.left + 3,
                detection.boundingBox.top + 3,
                detection.boundingBox.right - 3,
                detection.boundingBox.bottom - 3
            )
            canvas.drawRect(innerRect, innerBoxPaint)

            // Draw label with background (positioned ABOVE the box)
            val label = "${detection.label} ${String.format("%.0f%%", detection.score * 100)}"
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)

            val labelPadding = 4f // Reduced padding
            val labelHeight = textBounds.height() + labelPadding * 2

            // Position label above the bounding box
            val labelLeft = detection.boundingBox.left
            val labelTop = detection.boundingBox.top - labelHeight

            // If label would go off-screen at top, position it inside the box at the top
            val finalLabelTop = if (labelTop < 0) {
                detection.boundingBox.top + labelHeight
            } else {
                labelTop
            }

            // Draw text background (compact)
            val textBackgroundRect = RectF(
                labelLeft,
                finalLabelTop - labelHeight,
                labelLeft + textBounds.width() + labelPadding * 2,
                finalLabelTop
            )
            canvas.drawRect(textBackgroundRect, backgroundPaint)

            // Add a border around the text background (thinner)
            val textBorderPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f // Thinner border
                color = Color.WHITE
            }
            canvas.drawRect(textBackgroundRect, textBorderPaint)

            // Draw text (vertically centered in the background)
            canvas.drawText(
                label,
                labelLeft + labelPadding,
                finalLabelTop - labelPadding - textBounds.bottom,
                textPaint
            )
        }

        return mutableBitmap
    }
    private fun setupNavigationHeader() {
        val user = auth.currentUser
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)

        if (user != null) {
            // Get views from nav header
            val ivUserProfile = navHeaderView.findViewById<ImageView>(R.id.ivUserProfile)
            val tvUserName = navHeaderView.findViewById<TextView>(R.id.tvUserName)
            val tvUserEmail = navHeaderView.findViewById<TextView>(R.id.tvUserEmail)
            val navHeaderBackground = navHeaderView.findViewById<LinearLayout>(R.id.navHeaderBackground)

            // Set user name
            val displayName = user.displayName ?: googleAccount?.displayName ?: "User"
            tvUserName.text = displayName

            // Set user email
            val email = user.email ?: googleAccount?.email ?: "No email"
            tvUserEmail.text = email

            // Load profile image
            val photoUrl = user.photoUrl ?: googleAccount?.photoUrl
            if (photoUrl != null) {
                loadProfileImage(ivUserProfile, photoUrl.toString())
            } else {
                // Use default avatar
                ivUserProfile.setImageResource(R.mipmap.ic_launcher_round)
            }

            // Set themed background based on user's profile
            setThemedBackground(navHeaderBackground, displayName)
        }
    }

    private fun loadProfileImage(imageView: ImageView, url: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)

                withContext(Dispatchers.Main) {
                    // CardView handles the circular clipping, so just set the bitmap directly
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

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawOval(rect, paint)

        return output
    }

    private fun setThemedBackground(layout: LinearLayout, userName: String) {
        // Generate a color based on the user's name (consistent across sessions)
        val hash = userName.hashCode()

        // Create gradient colors based on hash
        val gradients = listOf(
            intArrayOf(Color.parseColor("#667eea"), Color.parseColor("#764ba2")), // Purple
            intArrayOf(Color.parseColor("#f093fb"), Color.parseColor("#f5576c")), // Pink
            intArrayOf(Color.parseColor("#4facfe"), Color.parseColor("#00f2fe")), // Blue
            intArrayOf(Color.parseColor("#43e97b"), Color.parseColor("#38f9d7")), // Green
            intArrayOf(Color.parseColor("#fa709a"), Color.parseColor("#fee140")), // Orange-Pink
            intArrayOf(Color.parseColor("#30cfd0"), Color.parseColor("#330867")), // Teal-Purple
            intArrayOf(Color.parseColor("#a8edea"), Color.parseColor("#fed6e3")), // Mint-Pink
            intArrayOf(Color.parseColor("#ff9a9e"), Color.parseColor("#fecfef"))  // Coral-Pink
        )

        // Select gradient based on hash
        val selectedGradient = gradients[abs(hash) % gradients.size]

        // Create and apply gradient drawable
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            selectedGradient
        )
        layout.background = gradientDrawable
    }
    private fun consultAI() {
        val detections = currentDetections
        if (detections == null || detections.isEmpty()) {
            Toast.makeText(this, "No diagnosis to consult about", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = selectedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "No image available", Toast.LENGTH_SHORT).show()
            return
        }

        val diagnosisSummary = buildString {
            append("I have a diagnosis from my trained disease detection model:\n\n")
            append("Total issues found: ${detections.size}\n\n")
            detections.forEachIndexed { index, detection ->
                append("${index + 1}. ${detection.label}\n")
                append("   Confidence: ${String.format("%.1f%%", detection.score * 100)}\n")
            }
            append("\nCan you provide insights about these detected issues and recommend treatment options?")
        }

        // Open chat dialog with callback to receive AI response
        val chatDialog = ChatBottomSheetDialog.newInstance(
            initialMessage = diagnosisSummary,
            image = bitmap,
            onAiResponseSaved = { aiResponse ->
                appendAiInsightsToDiagnosis(aiResponse)
            }
        )

        chatDialog.show(supportFragmentManager, "ChatBottomSheetDialog")
    }

    // Add this new method to MainActivity to handle AI insights
    private fun appendAiInsightsToDiagnosis(aiInsights: String) {
        val currentText = tvResults.text.toString()
        aiInsightsText = aiInsights
        // Add separator and AI insights
        val updatedText = buildString {
            append(currentText)
            append("\n\n")
            append("═".repeat(30))
            append("\n🤖 AI EXPERT INSIGHTS:\n")
            append("═".repeat(30))
            append("\n\n")
            append(aiInsights)
        }

        tvResults.text = updatedText

        // Scroll to show the new content

    }
    private fun showAboutDialog() {
        val aboutMessage = """
        🌿 About This App
        
        This application helps farmers and gardeners identify plant diseases 
        by analyzing images of leaves and fruits using Machine Learning.

        Once a plant image is uploaded or captured, the Model detects possible 
        diseases. You can also consult the AI or further insights and treatment suggestions.
        
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
            .setIcon(R.mipmap.ic_launcher_round) // optional app icon
            .show()
    }


    private fun openChatDialog() {
        val chatDialog = ChatBottomSheetDialog()
        chatDialog.show(supportFragmentManager, "ChatBottomSheetDialog")
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        detector.close()
        selectedBitmap?.recycle()
    }
}


