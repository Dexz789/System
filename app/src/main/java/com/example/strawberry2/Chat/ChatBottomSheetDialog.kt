package com.example.strawberry2.Chat

import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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
    private var diagnosisContext: String? = null  // ← injected context from saved diagnosis
    private var onAiResponseSaved: ((String) -> Unit)? = null

    /**
     * Called when the user closes the chat if there are any meaningful messages in the
     * conversation (i.e. at least one user message and one AI reply).
     * Receives a formatted markdown transcript that can be appended to aiInsights.
     */
    private var onConversationEnded: ((String) -> Unit)? = null

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 500
            }
        )
    }

    companion object {
        private const val GEMINI_API_KEY = "AIzaSyAP733BlqJCjVfeAuP0MjA5Y0qRH1cB8b0"
        private const val ARG_INITIAL_MESSAGE = "initial_message"
        private const val ARG_DIAGNOSIS_CONTEXT = "diagnosis_context"

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

If asked about topics outside of strawberries and plant diseases, politely redirect the conversation back to your area of expertise. Use 150 words only. Make it concise
IMPORTANT FORMATTING RULES:
- Use bold bullet points (•) for main sections
- Use numbered lists (1., 2., 3.) for sub-points under each section
- Do NOT use nested bullet points (* or - inside another bullet)
- Keep formatting clean and consistent

Example format:

• What it means:
1. This disease appears as white powder
2. It spreads in humid conditions

• Treatment:
1. Remove affected leaves
2. Apply fungicide

• Prevention:
1. Improve air circulation
2. Avoid overwatering


"""

        fun newInstance(
            initialMessage: String? = null,
            image: Bitmap? = null,
            diagnosisContext: String? = null,
            onAiResponseSaved: ((String) -> Unit)? = null,
            onConversationEnded: ((String) -> Unit)? = null
        ): ChatBottomSheetDialog {
            val fragment = ChatBottomSheetDialog()
            val args = Bundle()
            initialMessage?.let { args.putString(ARG_INITIAL_MESSAGE, it) }
            diagnosisContext?.let { args.putString(ARG_DIAGNOSIS_CONTEXT, it) }
            fragment.arguments = args
            fragment.diagnosisImage = image
            fragment.onAiResponseSaved = onAiResponseSaved
            fragment.onConversationEnded = onConversationEnded
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialMessage = it.getString(ARG_INITIAL_MESSAGE)
            diagnosisContext = it.getString(ARG_DIAGNOSIS_CONTEXT)
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
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()

        if (diagnosisContext != null) {
            // Opened from DiagnosisHistory — show context-aware welcome, no initial user message
            sendContextAwareWelcomeMessage(diagnosisContext!!)
        } else if (initialMessage != null) {
            sendWelcomeMessage()
            view.postDelayed({
                processDiagnosisWithImage(initialMessage!!, diagnosisImage)
            }, 500)
        } else {
            sendWelcomeMessage()
        }
    }

    /**
     * When the dialog is dismissed (user presses close or taps outside), check if there
     * are any real user↔AI exchanges and, if so, build a transcript and fire the callback
     * so the caller can persist it.
     */
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        fireConversationEndedIfNeeded()
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun fireConversationEndedIfNeeded() {
        val callback = onConversationEnded ?: return

        // Collect only genuine user/AI exchanges (skip system welcome banners)
        val exchangeMessages = messages.filter { msg ->
            msg.isUser ||
                    (msg.canBeSaved && !msg.message.contains("Hello! 🍓") &&
                            !msg.message.contains("I've loaded your saved"))
        }

        // Need at least one user message AND one AI reply to be worth saving
        val hasUserMessage = exchangeMessages.any { it.isUser }
        val hasAiReply    = exchangeMessages.any { !it.isUser }
        if (!hasUserMessage || !hasAiReply) return

        val timestamp = SimpleDateFormat(
            "MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()
        ).format(Date())

        val transcript = buildString {
            appendLine("---")
            appendLine("**Follow-up Chat — $timestamp**")
            appendLine()
            exchangeMessages.forEach { msg ->
                if (msg.isUser) {
                    appendLine("**You:** ${msg.message}")
                } else {
                    appendLine("**AI Expert:** ${msg.message}")
                }
                appendLine()
            }
            appendLine("---")
        }

        callback(transcript)
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

    private fun sendContextAwareWelcomeMessage(context: String) {
        // Parse detections from context for a personalized greeting
        val hasDetections = context.contains("•") && !context.contains("No diseases detected")
        val greeting = if (hasDetections) {
            "Hello! 🍓 I've loaded your saved diagnosis report.\n\n" +
                    "I can see the **detections and AI insights** from this scan. " +
                    "Ask me anything — follow-up questions, treatment options, prevention tips, or anything else about these findings!"
        } else {
            "Hello! 🍓 I've loaded your saved diagnosis report.\n\n" +
                    "It looks like your plant was **healthy** in this scan! " +
                    "Feel free to ask me any questions about strawberry plant care or disease prevention."
        }
        val welcomeMessage = ChatMessage(
            message = greeting,
            isUser = false,
            canBeSaved = false,
            image = diagnosisImage          // ← attach the strawberry image to the greeting
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
                val response = if (image != null) {
                    val inputContent = content {
                        image(image)
                        text("$SYSTEM_PROMPT\n\nUser: $message")
                    }
                    generativeModel.generateContent(inputContent)
                } else {
                    generativeModel.generateContent("$SYSTEM_PROMPT\n\nUser: $message")
                }

                val aiResponseText = response.text?.trim()
                    ?: "I apologize, but I couldn't generate a response. Please try again."

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
                Log.e("ChatDialog", "Error generating response", e)
                val errorMessage = buildErrorMessage(e)
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
                val conversationHistory = buildString {
                    append(SYSTEM_PROMPT)
                    append("\n\n")

                    // ── Inject saved diagnosis context when coming from history ──
                    if (!diagnosisContext.isNullOrEmpty()) {
                        append("IMPORTANT CONTEXT — The user is asking follow-up questions about a previously saved diagnosis. ")
                        append("Use the details below as the reference for this entire conversation:\n\n")
                        append(diagnosisContext)
                        append("\n\n")
                    }

                    // Include last 4 messages for conversational context (avoid token limits)
                    val recentMessages = messages
                        .filter {
                            !it.message.contains("Hello! 🍓") &&
                                    !it.message.contains("I've loaded your saved") &&
                                    !it.message.contains("What it means:", ignoreCase = true)
                        }
                        .takeLast(4)

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
                val aiResponseText = response.text?.trim()
                    ?: "I apologize, but I couldn't generate a response. Please try again."

                val canBeSaved = (diagnosisImage != null) ||
                        (initialMessage?.contains("diagnosis", ignoreCase = true) == true)
                // Note: diagnosisContext flow is auto-saved via onConversationEnded callback

                val aiMessage = ChatMessage(
                    message = aiResponseText,
                    isUser = false,
                    canBeSaved = canBeSaved
                )
                chatAdapter.addMessage(aiMessage)
                scrollToBottom()

            } catch (e: Exception) {
                Log.e("ChatDialog", "Error in getAIResponse", e)
                val errorMessage = buildErrorMessage(e)
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

    private fun buildErrorMessage(e: Exception): String = when {
        e.message?.contains("API_KEY_INVALID") == true ->
            "⚠️ API Key is invalid. Please update your API key."
        e.message?.contains("quota") == true ->
            "⚠️ API quota exceeded. Please try again later."
        e.message?.contains("network") == true ->
            "⚠️ Network error. Please check your connection."
        else ->
            "⚠️ Error: ${e.message ?: "Unknown error occurred"}"
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