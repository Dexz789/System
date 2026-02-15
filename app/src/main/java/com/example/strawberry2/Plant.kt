package com.example.strawberry2


import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.strawberry2.Chat.ChatAdapter
import com.example.strawberry2.Chat.ChatMessage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.abs

class Plant : AppCompatActivity() {

    private lateinit var recyclerViewSteps: RecyclerView
    private lateinit var cardChatSection: MaterialCardView
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendChat: ImageButton
    private lateinit var btnStartChat: Button
    private lateinit var layoutChatInterface: LinearLayout
    private lateinit var layoutLoading: LinearLayout

    private lateinit var stepsAdapter: PlantingStepsAdapter
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var auth: FirebaseAuth

    private lateinit var navHeaderView: View

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val plantingSteps = listOf(
        PlantingStep(
            stepNumber = 1,
            title = "Choose the Right Variety",
            description = "Select strawberry varieties suited to your climate. June-bearing varieties produce one large crop, while everbearing varieties produce throughout the season.",
            icon = "🍓"
        ),
        PlantingStep(
            stepNumber = 2,
            title = "Select the Perfect Location",
            description = "Choose a spot with full sun (6-8 hours daily) and good air circulation. Ensure the soil drains well to prevent root rot.",
            icon = "☀️"
        ),
        PlantingStep(
            stepNumber = 3,
            title = "Prepare the Soil",
            description = "Test soil pH (ideal: 5.5-6.5). Mix in 2-3 inches of compost or aged manure. Add organic fertilizer rich in phosphorus for root development.",
            icon = "🌱"
        ),
        PlantingStep(
            stepNumber = 4,
            title = "Timing is Key",
            description = "Plant in early spring (after last frost) or fall. In tropical climates, plant during the cooler months for best results.",
            icon = "📅"
        ),
        PlantingStep(
            stepNumber = 5,
            title = "Proper Spacing",
            description = "Space plants 12-18 inches apart in rows 3-4 feet apart. This allows proper air circulation and room for runners.",
            icon = "📏"
        ),
        PlantingStep(
            stepNumber = 6,
            title = "Plant at the Right Depth",
            description = "Plant so the crown (where leaves meet roots) is at soil level. Roots should spread out and down. Don't bury the crown or leave roots exposed.",
            icon = "🌿"
        ),
        PlantingStep(
            stepNumber = 7,
            title = "Water Immediately",
            description = "Water thoroughly after planting. Keep soil consistently moist (not waterlogged) during the first few weeks to establish roots.",
            icon = "💧"
        ),
        PlantingStep(
            stepNumber = 8,
            title = "Apply Mulch",
            description = "Add 2-3 inches of straw or pine needle mulch around plants. This conserves moisture, suppresses weeds, and keeps fruit clean.",
            icon = "🍂"
        ),
        PlantingStep(
            stepNumber = 9,
            title = "Remove First Flowers",
            description = "For the first 4-6 weeks, pinch off flowers to encourage root and leaf growth instead of fruit production.",
            icon = "🌸"
        ),
        PlantingStep(
            stepNumber = 10,
            title = "Fertilize Regularly",
            description = "Apply balanced fertilizer (10-10-10) monthly during growing season. Switch to low-nitrogen fertilizer when flowering begins.",
            icon = "🥄"
        ),
        PlantingStep(
            stepNumber = 11,
            title = "Manage Runners",
            description = "Trim excess runners to keep plants productive. Leave 2-3 daughter plants per mother plant for renewal, remove others.",
            icon = "✂️"
        ),
        PlantingStep(
            stepNumber = 12,
            title = "Harvest Time! 🎉",
            description = "June-bearing varieties: Harvest 4-6 weeks after flowering (typically late spring/early summer).\n\nEverbearing varieties: First harvest in 8-10 weeks, then continuously.\n\nFrom planting to first harvest: 4-6 months depending on variety and conditions.\n\nPick berries when fully red, every 2-3 days during peak season.",
            icon = "🧺"
        )
    )

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 600
            }
        )
    }

    companion object {
        private const val GEMINI_API_KEY = "AIzaSyAP733BlqJCjVfeAuP0MjA5Y0qRH1cB8b0"

        private const val SYSTEM_PROMPT = """You are an expert agricultural AI assistant specializing in strawberry cultivation and planting.

IMPORTANT: Always format your responses using bullet points for easy reading.

Your role is to:
1. Answer questions about strawberry planting, growing, and care
2. Provide detailed information about the 12-step planting guide
3. Offer troubleshooting advice for common problems
4. Share tips for maximizing strawberry yields
5. Explain harvest timing and techniques
6. Provide climate-specific advice

The user has access to a 12-step strawberry planting guide covering:
- Variety selection
- Location and soil preparation
- Planting timing and techniques
- Spacing and depth
- Watering and mulching
- Flower removal and fertilization
- Runner management
- Harvest timeline (4-6 months from planting)

Always be:
- Helpful and informative
- Clear and concise (use bullet points)
- Practical with actionable advice
- Encouraging and supportive

Keep responses brief (3-4 sections maximum with bullet points) and focused on strawberry cultivation.

If asked about topics outside of strawberry planting and cultivation, politely redirect the conversation back to your area of expertise. Use 200 words maximum. Make it concise and easy to understand."""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant)

        auth = Firebase.auth

        title = "Strawberry Planting Guide"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initializeViews()
        setupRecyclerViews()
        setupClickListeners()


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
                R.id.nav_home -> {
                    if (currentActivity != "AboutActivity") {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
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

    private fun initializeViews() {
        recyclerViewSteps = findViewById(R.id.recyclerViewSteps)
        cardChatSection = findViewById(R.id.cardChatSection)
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        etChatMessage = findViewById(R.id.etChatMessage)
        btnSendChat = findViewById(R.id.btnSendChat)
        btnStartChat = findViewById(R.id.btnStartChat)
        layoutChatInterface = findViewById(R.id.layoutChatInterface)
        layoutLoading = findViewById(R.id.layoutLoading)

    }

    private fun setupRecyclerViews() {
        // Setup planting steps
        stepsAdapter = PlantingStepsAdapter(plantingSteps)
        recyclerViewSteps.apply {
            layoutManager = LinearLayoutManager(this@Plant)
            adapter = stepsAdapter
        }

        // Setup chat - with empty onSaveClick since we don't need save functionality here
        chatAdapter = ChatAdapter(chatMessages) {
            // No save functionality needed in planting guide
        }
        recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@Plant)
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        btnStartChat.setOnClickListener {
            startChatMode()
        }

        btnSendChat.setOnClickListener {
            sendMessage()
        }


        etChatMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun startChatMode() {
        btnStartChat.visibility = View.GONE
        layoutChatInterface.visibility = View.VISIBLE

        // Send welcome message
        val welcomeMessage = ChatMessage(
            message = "Hello! 🍓 I'm your Strawberry Planting Expert AI.\n\n" +
                    "**I can help you with:**\n" +
                    "• Questions about the 12-step planting guide\n" +
                    "• Soil preparation and fertilization\n" +
                    "• Watering and maintenance tips\n" +
                    "• Harvest timing (typically 4-6 months)\n" +
                    "• Troubleshooting growing problems\n" +
                    "• Climate-specific advice\n\n" +
                    "What would you like to know about growing strawberries?",
            isUser = false,
            canBeSaved = false
        )
        chatAdapter.addMessage(welcomeMessage)
        scrollChatToBottom()
    }

    private fun sendMessage() {
        val messageText = etChatMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Add user message
        val userMessage = ChatMessage(messageText, isUser = true)
        chatAdapter.addMessage(userMessage)
        scrollChatToBottom()

        etChatMessage.text?.clear()
        showLoading(true)

        // Get AI response
        getAIResponse(messageText)
    }

    private fun getAIResponse(userMessage: String) {
        lifecycleScope.launch {
            try {
                // Build conversation history
                val conversationHistory = buildString {
                    append(SYSTEM_PROMPT)
                    append("\n\n")

                    // Include recent messages for context
                    val recentMessages = chatMessages
                        .filter { !it.message.contains("Hello! 🍓") }
                        .takeLast(10)

                    recentMessages.forEach { msg ->
                        if (msg.isUser) {
                            append("User: ${msg.message}\n\n")
                        } else if (msg.canBeSaved == false || !msg.message.contains("Sorry, I encountered")) {
                            append("Assistant: ${msg.message}\n\n")
                        }
                    }

                    append("User: $userMessage\n\nAssistant:")
                }

                val response = generativeModel.generateContent(conversationHistory)
                val aiResponseText = response.text?.trim()
                    ?: "I apologize, but I couldn't generate a response. Please try again."

                val aiMessage = ChatMessage(
                    message = aiResponseText,
                    isUser = false,
                    canBeSaved = false
                )
                chatAdapter.addMessage(aiMessage)
                scrollChatToBottom()

            } catch (e: Exception) {
                Log.e("PlantActivity", "Error in getAIResponse", e)
                val errorMessage = when {
                    e.message?.contains("API_KEY_INVALID") == true ->
                        "⚠️ API Key is invalid. Please update your API key."
                    e.message?.contains("quota") == true ->
                        "⚠️ API quota exceeded. Please try again later."
                    e.message?.contains("network") == true ->
                        "⚠️ Network error. Please check your connection."
                    else ->
                        "⚠️ Error: ${e.message ?: "Unknown error occurred"}"
                }

                val errorMsg = ChatMessage(
                    message = "Sorry, I encountered an error: $errorMessage",
                    isUser = false,
                    canBeSaved = false
                )
                chatAdapter.addMessage(errorMsg)
                scrollChatToBottom()

                Toast.makeText(this@Plant, errorMessage, Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
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

    private fun showLoading(isLoading: Boolean) {
        layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSendChat.isEnabled = !isLoading
        etChatMessage.isEnabled = !isLoading
    }

    private fun scrollChatToBottom() {
        recyclerViewChat.postDelayed({
            if (chatMessages.isNotEmpty()) {
                recyclerViewChat.smoothScrollToPosition(chatMessages.size - 1)
            }
        }, 100)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// Data class for planting steps
data class PlantingStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val icon: String
)

// Adapter for planting steps
class PlantingStepsAdapter(
    private val steps: List<PlantingStep>
) : RecyclerView.Adapter<PlantingStepsAdapter.StepViewHolder>() {

    inner class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStepNumber: TextView = view.findViewById(R.id.tvStepNumber)
        val tvStepIcon: TextView = view.findViewById(R.id.tvStepIcon)
        val tvStepTitle: TextView = view.findViewById(R.id.tvStepTitle)
        val tvStepDescription: TextView = view.findViewById(R.id.tvStepDescription)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): StepViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_planting_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = steps[position]
        holder.tvStepNumber.text = "Step ${step.stepNumber}"
        holder.tvStepIcon.text = step.icon
        holder.tvStepTitle.text = step.title
        holder.tvStepDescription.text = step.description
    }

    override fun getItemCount() = steps.size
}