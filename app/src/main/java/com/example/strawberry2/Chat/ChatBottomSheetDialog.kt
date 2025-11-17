package com.example.strawberry2.Chat

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.strawberry2.R
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch


import android.widget.FrameLayout
import com.google.ai.client.generativeai.type.content

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

import kotlinx.coroutines.launch


class ChatBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var btnClose: ImageButton
    private lateinit var layoutLoading: LinearLayout

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private var initialMessage: String? = null
    private var diagnosisImage: Bitmap? = null
    private var onAiResponseSaved: ((String) -> Unit)? = null

    private val generativeModel by lazy {
        GenerativeModel(
            // ✅ FIX 1: Use correct stable model name
            modelName = "gemini-2.5-flash-lite", // Updated to stable version
            apiKey = GEMINI_API_KEY, // ⚠️ IMPORTANT: Replace with your NEW API key
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 500
            }
        )
    }

    companion object {
        // ⚠️ FIX 2: REPLACE THIS WITH YOUR NEW API KEY FROM GOOGLE AI STUDIO
        // Get a new key from: https://makersuite.google.com/app/apikey
        private const val GEMINI_API_KEY = "AIzaSyAP733BlqJCjVfeAuP0MjA5Y0qRH1cB8b0"
        private const val ARG_INITIAL_MESSAGE = "initial_message"

        private const val SYSTEM_PROMPT = """You are an expert agricultural AI assistant specializing in strawberry plants and their diseases. 

IMPORTANT: Always format your responses using bullet points for easy reading.
Your role is to:
1. Identify strawberry diseases based on symptoms described
2. Provide information about common strawberry diseases
3. Recommend treatment options and prevention strategies
4. Give advice on strawberry plant care and disease management

Always be:
- Helpful and informative
- Clear and concise (use bullet points)
- Practical with actionable advice
- Encouraging and supportive

Keep responses brief (2-3 sections maximum) and use bullet points for all key information.

If asked about topics outside of strawberries and plant diseases, politely redirect the conversation back to your area of expertise. Use 150 words only. Make it concise"""

        fun newInstance(
            initialMessage: String? = null,
            image: Bitmap? = null,
            onAiResponseSaved: ((String) -> Unit)? = null
        ): ChatBottomSheetDialog {
            val fragment = ChatBottomSheetDialog()
            val args = Bundle()
            initialMessage?.let {
                args.putString(ARG_INITIAL_MESSAGE, it)
            }
            fragment.arguments = args
            fragment.diagnosisImage = image
            fragment.onAiResponseSaved = onAiResponseSaved
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialMessage = it.getString(ARG_INITIAL_MESSAGE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()

        if (initialMessage != null) {
            sendWelcomeMessage()
            view.postDelayed({
                processDiagnosisWithImage(initialMessage!!, diagnosisImage)
            }, 500)
        } else {
            sendWelcomeMessage()
        }
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewChat)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSendMessage)
        btnClose = view.findViewById(R.id.btnCloseChat)
        layoutLoading = view.findViewById(R.id.layoutLoading)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages) { aiResponse ->
            saveAiResponse(aiResponse)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        btnSend.setOnClickListener {
            sendMessage()
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun sendWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            message = "Hello! 🍓 I'm your Strawberry Disease Expert AI.\n\n" +
                    "**I can help you with:**\n" +
                    "• Disease identification\n" +
                    "• Treatment recommendations\n" +
                    "• Prevention strategies\n" +
                    "• Plant care tips\n\n" +
                    "How can I help you today?",
            isUser = false,
            canBeSaved = false
        )
        chatAdapter.addMessage(welcomeMessage)
        scrollToBottom()
    }

    private fun processDiagnosisWithImage(message: String, image: Bitmap?) {
        val userMessage = ChatMessage(
            message = message,
            isUser = true,
            image = image
        )
        chatAdapter.addMessage(userMessage)
        scrollToBottom()

        showLoading(true)

        lifecycleScope.launch {
            try {
                // ✅ FIX 3: Improved error handling and response parsing
                val response = if (image != null) {
                    val inputContent = content {
                        image(image)
                        text("$SYSTEM_PROMPT\n\nUser: $message")
                    }
                    generativeModel.generateContent(inputContent)
                } else {
                    generativeModel.generateContent("$SYSTEM_PROMPT\n\nUser: $message")
                }

                // ✅ FIX 4: Better null safety
                val aiResponseText = response.text?.trim() ?: "I apologize, but I couldn't generate a response. Please try again."

                val canBeSaved = (diagnosisImage != null) ||
                        (initialMessage?.contains("diagnosis", ignoreCase = true) == true)

                val aiMessage = ChatMessage(
                    message = aiResponseText,
                    isUser = false,
                    canBeSaved = canBeSaved
                )
                chatAdapter.addMessage(aiMessage)
                scrollToBottom()

            } catch (e: Exception) {
                // ✅ FIX 5: Better error handling
                Log.e("ChatDialog", "Error generating response", e)
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
                scrollToBottom()

                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        val userMessage = ChatMessage(messageText, isUser = true)
        chatAdapter.addMessage(userMessage)
        scrollToBottom()

        etMessage.text?.clear()
        showLoading(true)

        getAIResponse(messageText)
    }

    private fun getAIResponse(userMessage: String) {
        lifecycleScope.launch {
            try {
                // ✅ FIX 6: Simplified conversation history building
                val conversationHistory = buildString {
                    append(SYSTEM_PROMPT)
                    append("\n\n")

                    // Include last 5 message pairs for context (to avoid token limits)
                    val recentMessages = messages
                        .filter { !it.message.contains("Hello! 🍓") } // Skip welcome
                        .takeLast(10) // Last 10 messages (5 pairs)

                    recentMessages.forEach { msg ->
                        if (msg.isUser) {
                            append("User: ${msg.message}\n\n")
                        } else if (msg.canBeSaved || !msg.message.contains("Sorry, I encountered")) {
                            append("Assistant: ${msg.message}\n\n")
                        }
                    }

                    append("User: $userMessage\n\nAssistant:")
                }

                val response = generativeModel.generateContent(conversationHistory)
                val aiResponseText = response.text?.trim() ?: "I apologize, but I couldn't generate a response. Please try again."

                val canBeSaved = (diagnosisImage != null) ||
                        (initialMessage?.contains("diagnosis", ignoreCase = true) == true)

                val aiMessage = ChatMessage(
                    message = aiResponseText,
                    isUser = false,
                    canBeSaved = canBeSaved
                )
                chatAdapter.addMessage(aiMessage)
                scrollToBottom()

            } catch (e: Exception) {
                Log.e("ChatDialog", "Error in getAIResponse", e)
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
                scrollToBottom()

                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveAiResponse(aiResponse: String) {
        onAiResponseSaved?.invoke(aiResponse)

        Toast.makeText(
            context,
            "AI insights added to diagnosis results",
            Toast.LENGTH_SHORT
        ).show()

        dismiss()
    }

    private fun showLoading(isLoading: Boolean) {
        layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !isLoading
    }

    private fun scrollToBottom() {
        recyclerView.postDelayed({
            if (messages.isNotEmpty()) {
                recyclerView.smoothScrollToPosition(messages.size - 1)
            }
        }, 100)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }
}