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
import com.example.strawberry2.AppConfig
import com.example.strawberry2.DiagnosisRepository
import com.example.strawberry2.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

import android.widget.FrameLayout

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
    private var diagnosisContext: String? = null
    private var onAiResponseSaved: ((String) -> Unit)? = null
    private var lastMessageTime = 0L

    /**
     * Called when the user closes the chat if there are any meaningful messages in the
     * conversation (i.e. at least one user message and one AI reply).
     * Receives a formatted markdown transcript that can be appended to aiInsights.
     */
    private var onConversationEnded: ((String) -> Unit)? = null

    companion object {
        private const val RATE_LIMIT_MS = 2000L
        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        private const val ARG_INITIAL_MESSAGE = "initial_message"
        private const val ARG_DIAGNOSIS_CONTEXT = "diagnosis_context"

        private const val SYSTEM_PROMPT = """You are an expert agricultural AI assistant that knows about strawberry plants and diseases.

Always answer in simple, easy-to-understand English. This is for beginners who have no idea how to plant strawberries.

Use bullet points (•) for lists and numbered lists (1. 2. 3.) for steps.

Your role:
1. Identify strawberry diseases based on symptoms
2. Give treatment options and prevention tips
3. Advise on strawberry plant care

In every answer, always include:
- Immediate Treatment — specific product name, how to mix, how often (e.g., every 7-14 days)
- Prevention — specific steps, spacing, watering techniques, mulch type/thickness

Keep responses brief (2-3 sections max) and use 150 words only.

REFERENCES: At the end, include 2-3 reference links from Philippine websites (like da.gov.ph, bpi.da.gov.ph, ati.da.gov.ph, pcaarrd.dost.gov.ph, or other .ph domains). Format: "- [Title](https://...)".
STRICT GUARDRAIL: Only answer about strawberries. If asked something else, say: "I only know about strawberry plants. Please ask me about strawberries!" Do not answer non-strawberry questions.

Example format:

• What's the problem:
1. White powder appears on strawberry leaves
2. Gets worse when the air is humid

• Immediate Treatment:
1. Cut and throw away infected leaves
2. Spray sulfur fungicide (20g per 16L water) every 7-10 days
3. Spray in the morning or late afternoon to avoid leaf burn

• Prevention:
1. Plant 30-45cm apart for good air flow
2. Water the soil only, in the morning — avoid wetting the leaves
3. Spray neem oil (5ml per 1L water) weekly
4. Use plastic mulch to prevent soil from splashing onto leaves
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
        val now = System.currentTimeMillis()
        if (now - lastMessageTime < RATE_LIMIT_MS) {
            Toast.makeText(context, "Please wait before sending another message", Toast.LENGTH_SHORT).show()
            return
        }
        lastMessageTime = now

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
                val messagesArray = JSONArray()

                // System message
                messagesArray.put(JSONObject(mapOf("role" to "system", "content" to SYSTEM_PROMPT)))

                try {
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    val ragContext = DiagnosisRepository.buildRagContext(uid, message)
                    if (ragContext != null) {
                        messagesArray.put(JSONObject(mapOf("role" to "system", "content" to "Relevant past diagnoses for reference:\n\n$ragContext")))
                    }
                } catch (_: Exception) { }

                // User message (with optional image)
                val userContent: Any = if (image != null) {
                    val base64 = bitmapToBase64(image)
                    JSONArray().apply {
                        put(JSONObject(mapOf("type" to "text", "text" to message)))
                        put(JSONObject(mapOf(
                            "type" to "image_url",
                            "image_url" to JSONObject(mapOf("url" to "data:image/jpeg;base64,$base64"))
                        )))
                    }
                } else {
                    message
                }
                messagesArray.put(JSONObject(mapOf("role" to "user", "content" to userContent)))

                val requestBody = JSONObject().apply {
                    put("model", AppConfig.OPENROUTER_MODEL)
                    put("messages", messagesArray)
                    put("max_tokens", 500)
                    put("temperature", 0.7)
                    put("top_p", 0.95)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${AppConfig.OPENROUTER_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/GrowMate-Inc")
                    .post(body)
                    .build()

                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                val responseBody = response.body?.string()
                val aiResponseText = if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        jsonResponse.optJSONObject("error")?.let {
                            val msg = it.optString("message", "Unknown error")
                            Log.e("ChatDialog", "OpenRouter returned error in 200: $msg")
                            "⚠️ AI Error: $msg"
                        } ?: jsonResponse.getJSONArray("choices")
                            .optJSONObject(0)
                            ?.getJSONObject("message")
                            ?.getString("content")
                            ?.trim()
                            ?.ifEmpty { null }
                    } catch (e: Exception) {
                        Log.e("ChatDialog", "Failed to parse OpenRouter response: $responseBody", e)
                        null
                    }
                } else {
                    val errorDetail = try {
                        JSONObject(responseBody ?: "").optJSONObject("error")?.optString("message") ?: ""
                    } catch (_: Exception) { "" }
                    Log.e("ChatDialog", "OpenRouter error ${response.code}: $responseBody")
                    "⚠️ AI Error (${response.code}): ${errorDetail.ifEmpty { response.message }}"
                } ?: "I apologize, but I couldn't generate a response. Please try again."

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
        val now = System.currentTimeMillis()
        if (now - lastMessageTime < RATE_LIMIT_MS) {
            Toast.makeText(context, "Please wait before sending another message", Toast.LENGTH_SHORT).show()
            return
        }
        lastMessageTime = now

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
                val messagesArray = JSONArray()

                // System prompt
                messagesArray.put(JSONObject(mapOf("role" to "system", "content" to SYSTEM_PROMPT)))

                try {
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    val ragContext = DiagnosisRepository.buildRagContext(uid, userMessage)
                    if (ragContext != null) {
                        messagesArray.put(JSONObject(mapOf("role" to "system", "content" to "Relevant past diagnoses for reference:\n\n$ragContext")))
                    }
                } catch (_: Exception) { }

                // ── Inject saved diagnosis context when coming from history ──
                if (!diagnosisContext.isNullOrEmpty()) {
                    messagesArray.put(JSONObject(mapOf(
                        "role" to "system",
                        "content" to "IMPORTANT CONTEXT — The user is asking follow-up questions about a previously saved diagnosis. Use the details below as the reference for this entire conversation:\n\n$diagnosisContext"
                    )))
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
                    val role = if (msg.isUser) "user" else "assistant"
                    messagesArray.put(JSONObject(mapOf("role" to role, "content" to msg.message)))
                }

                // Current user message
                messagesArray.put(JSONObject(mapOf("role" to "user", "content" to userMessage)))

                val requestBody = JSONObject().apply {
                    put("model", AppConfig.OPENROUTER_MODEL)
                    put("messages", messagesArray)
                    put("max_tokens", 500)
                    put("temperature", 0.7)
                    put("top_p", 0.95)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${AppConfig.OPENROUTER_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/GrowMate-Inc")
                    .post(body)
                    .build()

                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                val responseBody = response.body?.string()
                val aiResponseText = if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        jsonResponse.optJSONObject("error")?.let {
                            val msg = it.optString("message", "Unknown error")
                            Log.e("ChatDialog", "OpenRouter returned error in 200: $msg")
                            "⚠️ AI Error: $msg"
                        } ?: jsonResponse.getJSONArray("choices")
                            .optJSONObject(0)
                            ?.getJSONObject("message")
                            ?.getString("content")
                            ?.trim()
                            ?.ifEmpty { null }
                    } catch (e: Exception) {
                        Log.e("ChatDialog", "Failed to parse OpenRouter response: $responseBody", e)
                        null
                    }
                } else {
                    val errorDetail = try {
                        JSONObject(responseBody ?: "").optJSONObject("error")?.optString("message") ?: ""
                    } catch (_: Exception) { "" }
                    Log.e("ChatDialog", "OpenRouter error ${response.code}: $responseBody")
                    "⚠️ AI Error (${response.code}): ${errorDetail.ifEmpty { response.message }}"
                } ?: "I apologize, but I couldn't generate a response. Please try again."

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
        e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true ->
            "⚠️ API Key is invalid. Please update your API key."
        e.message?.contains("402") == true || e.message?.contains("Insufficient credits") == true ->
            "⚠️ Insufficient credits. Please check your OpenRouter account."
        e.message?.contains("timeout") == true || e.message?.contains("network") == true ->
            "⚠️ Network error. Please check your connection."
        else ->
            "⚠️ Error: ${e.message ?: "Unknown error occurred"}"
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
        val bytes = stream.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
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