package com.example.strawberry2


import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.strawberry2.Tutorial.TutorialOverlayView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs


class DiagnosisHistoryActivity : AppCompatActivity() {
    private lateinit var navHeaderView: View
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DiagnosisAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: DiagnosisRepository
    private lateinit var emptyView: TextView
    private lateinit var progressBar: View
    private lateinit var deleteButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var selectionToolbar: LinearLayout

    private val showTutorialSpotlight by lazy {
        intent.getBooleanExtra("SHOW_TUTORIAL_SPOTLIGHT", false)
    }

    private val activityScope  = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnosis_history)
        title="Past Reports"
        auth = Firebase.auth



        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Diagnosis History"

        auth = Firebase.auth
        repository = DiagnosisRepository()

        recyclerView = findViewById(R.id.recyclerViewHistory)
        emptyView = findViewById(R.id.tvEmptyView)
        progressBar = findViewById(R.id.progressBar)
        deleteButton = findViewById(R.id.btnDeleteSelected)
        selectAllButton = findViewById(R.id.btnSelectAll)
        selectionToolbar = findViewById(R.id.selectionToolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        navHeaderView = navigationView.getHeaderView(0)

        setupNavigationHeader()
        setSupportActionBar(toolbar)

        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = Color.WHITE


        // Set up RecyclerView with delete callback
        adapter = DiagnosisAdapter { diagnosis ->
            showDeleteConfirmation(diagnosis)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Load diagnoses
        loadDiagnoses()


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
                R.id.nav_profile -> {
                    if (currentActivity != "ProfileActivity") {
                        startActivity(Intent(this, ProfileActivity::class.java))
                    }
                    drawerLayout.closeDrawers()
                    true
                }

                R.id.nav_home -> {
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
    private fun loadProfileImage(imageView: ImageView, url: String) {
        activityScope .launch(Dispatchers.IO) {
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

    private fun showDeleteConfirmation(diagnosis: DiagnosisData) {
        AlertDialog.Builder(this)
            .setTitle("Delete Diagnosis")
            .setMessage("Are you sure you want to delete this diagnosis record?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteDiagnosis(diagnosis)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteDiagnosis(diagnosis: DiagnosisData) {
        progressBar.visibility = View.VISIBLE

        activityScope .launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.deleteDiagnosis(diagnosis.id)
                }

                progressBar.visibility = View.GONE

                result.onSuccess {
                    Toast.makeText(
                        this@DiagnosisHistoryActivity,
                        "Diagnosis deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Remove from adapter
                    adapter.removeItem(diagnosis)

                    // Check if list is now empty
                    if (adapter.itemCount == 0) {
                        recyclerView.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = "No diagnosis history yet.\nStart by scanning some strawberries!"
                    }
                }.onFailure { exception ->
                    Toast.makeText(
                        this@DiagnosisHistoryActivity,
                        "Error deleting diagnosis: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@DiagnosisHistoryActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }

    private fun loadDiagnoses() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        activityScope.launch {
            try {
                val result = repository.getUserDiagnoses(user.uid)

                result.onSuccess { diagnoses ->
                    progressBar.visibility = View.GONE

                    if (diagnoses.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = "No diagnosis history yet.\nStart by scanning some strawberries!"
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(diagnoses)

                        // ── Tutorial Step 5 ──────────────────────────────────────
                        if (showTutorialSpotlight) {
                            recyclerView.viewTreeObserver.addOnGlobalLayoutListener(
                                object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                    override fun onGlobalLayout() {
                                        recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                        val firstItem = recyclerView.layoutManager?.findViewByPosition(0)
                                        if (firstItem != null) showDiagnosisSpotlight(firstItem)
                                    }
                                }
                            )
                        }
                        // ─────────────────────────────────────────────────────────
                    }
                }.onFailure { exception ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@DiagnosisHistoryActivity,
                        "Error loading history: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "Error loading history"
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@DiagnosisHistoryActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
    private fun showDiagnosisSpotlight(itemView: View) {
        val decorView = window.decorView as ViewGroup
        val overlay = com.example.strawberry2.Tutorial.TutorialOverlayView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tooltipTitle         = "🗂️  Your Saved Diagnosis"
            tooltipDescription   = "This is your most recently saved diagnosis. Tap it to open " +
                    "the full report and see your results, image, and AI insights!"
            showNextButton       = false
            spotlightInteractive = false   // ← false so onSpotlightClick fires
            onDimClick           = { /* block background taps */ }
            onSpotlightClick = {
                Log.d("TutorialDebug", "Diagnosis item tapped — opening bottom sheet")
                (window.decorView as ViewGroup).removeView(this)

                // Open bottom sheet directly, passing the tutorial flag
                val recyclerPos = recyclerView.getChildAdapterPosition(itemView)
                val diagnosis = /* get diagnosis from adapter */ adapter.getDiagnosis(recyclerPos)
                DiagnosisDetailsBottomSheet.newInstance(diagnosis, showTutorial = true)
                    .show(supportFragmentManager, "DiagnosisDetails")
            }
        }

        val loc = IntArray(2)
        itemView.getLocationOnScreen(loc)
        overlay.spotlightRect = android.graphics.RectF(
            loc[0].toFloat(),
            loc[1].toFloat(),
            (loc[0] + itemView.width).toFloat(),
            (loc[1] + itemView.height).toFloat()
        )

        decorView.addView(overlay)
    }

    private fun showFinalTourOverlay() {
        val activityDecor = window.decorView as ViewGroup

        val overlay = TutorialOverlayView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tooltipTitle = "🎉  Tutorial Complete!"
            tooltipDescription = "This bottom sheet shows your full saved diagnosis — " +
                    "image, detections, confidence scores, and AI insights. " +
                    "You're all set. Happy growing! 🌱"
            showNextButton = true
            nextButtonText = "Finish Tour  🎉"
            spotlightInteractive = false
            onNextClick = {
                Log.d("TutorialDebug", "Finish Tour tapped — tour complete")
                activityDecor.removeView(this)
            }
            onDimClick = { /* block background taps */ }

            // ← KEY FIX: give it a fake center spotlight so drawTooltip() is triggered
            post {
                spotlightRect = android.graphics.RectF(
                    width / 2f - 1f,
                    height / 2f - 1f,
                    width / 2f + 1f,
                    height / 2f + 1f
                )
            }
        }

        // Use elevation to float above the bottom sheet dialog
        overlay.elevation = 99999f
        activityDecor.addView(overlay)
        activityDecor.bringChildToFront(overlay)

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

        android.app.AlertDialog.Builder(this)
            .setTitle("About Strawberry AI Diagnoser")
            .setMessage(aboutMessage)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(R.mipmap.ic_launcher_round) // optional app icon
            .show()
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

}

class DiagnosisAdapter(
    private val onDeleteClick: (DiagnosisData) -> Unit
) : RecyclerView.Adapter<DiagnosisAdapter.ViewHolder>() {
    private var diagnoses = mutableListOf<DiagnosisData>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    fun getDiagnosis(position: Int): DiagnosisData = diagnoses[position]


    fun submitList(newDiagnoses: List<DiagnosisData>) {
        diagnoses.clear()
        diagnoses.addAll(newDiagnoses)
        notifyDataSetChanged()
    }

    fun removeItem(diagnosis: DiagnosisData) {
        val position = diagnoses.indexOfFirst { it.id == diagnosis.id }
        if (position != -1) {
            diagnoses.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, diagnoses.size)
        }
    }

    fun isEmpty(): Boolean = diagnoses.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diagnosis, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(diagnoses[position])
    }

    override fun getItemCount() = diagnoses.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvIssuesCount: TextView = itemView.findViewById(R.id.tvIssuesCount)
        private val tvDetections: TextView = itemView.findViewById(R.id.tvDetections)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val ivDiagnosisImage: ImageView = itemView.findViewById(R.id.ivDiagnosisImage)

        fun bind(diagnosis: DiagnosisData) {
            tvDate.text = dateFormat.format(Date(diagnosis.timestamp))

            if (diagnosis.totalIssuesFound == 0) {
                tvIssuesCount.text = "Healthy Plant ✓"
                tvIssuesCount.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
            } else {
                tvIssuesCount.text = "${diagnosis.totalIssuesFound} Issue(s) Found"
                tvIssuesCount.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            }

            // Hide detections & image in the list
            tvDetections.visibility = View.GONE
            ivDiagnosisImage.visibility = View.GONE

            // Open details bottom sheet when clicked
            itemView.setOnClickListener {
                DiagnosisDetailsBottomSheet.newInstance(diagnosis)
                    .show((itemView.context as AppCompatActivity).supportFragmentManager, "DiagnosisDetails")
            }

            // Delete button remains the same
            btnDelete.setOnClickListener {
                onDeleteClick(diagnosis)
            }
        }

        private fun loadImage(imageView: ImageView, url: String) {
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
                        imageView.visibility = View.GONE
                    }
                }
            }
        }
    }
}


