package com.example.strawberry2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.strawberry2.Chat.ChatBottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosisDetailsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var diagnosis: DiagnosisData

    // Holds the cumulative aiInsights text (original + any follow-up chats added this session)
    private var currentAiInsights: String = ""

    // Cached view references so the onConversationEnded callback can update the UI
    private var tvAiInsightsRef: TextView? = null
    private var btnContinueChatRef: com.google.android.material.button.MaterialButton? = null
    private var markwon: Markwon? = null

    // Holds the loaded strawberry image so it can be passed into the chat
    private var loadedImageBitmap: Bitmap? = null

    companion object {
        private const val ARG_DIAGNOSIS   = "diagnosis"
        private const val ARG_SHOW_TUTORIAL = "show_tutorial"

        fun newInstance(diagnosis: DiagnosisData, showTutorial: Boolean = false): DiagnosisDetailsBottomSheet {
            val fragment = DiagnosisDetailsBottomSheet()
            fragment.arguments = Bundle().apply {
                putSerializable(ARG_DIAGNOSIS, diagnosis)
                putBoolean(ARG_SHOW_TUTORIAL, showTutorial)
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosis = arguments?.getSerializable("diagnosis") as DiagnosisData
        currentAiInsights = diagnosis.aiInsights ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_diagnosis_details_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val showTutorial = arguments?.getBoolean(ARG_SHOW_TUTORIAL, false) ?: false
        val tutorialBanner = view.findViewById<View>(R.id.tutorialBanner)
        val btnFinishTour  = view.findViewById<Button>(R.id.btnFinishTour)
        val ivImage        = view.findViewById<ImageView>(R.id.ivDiagnosisImage)
        val tvSummary      = view.findViewById<TextView>(R.id.tvDiagnosisSummary)
        val tvDetections   = view.findViewById<TextView>(R.id.tvDetections)
        val tvAiInsights   = view.findViewById<TextView>(R.id.tvAiInsights)
        val btnClose       = view.findViewById<ImageButton>(R.id.btnClose)
        val btnContinueChat = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnContinueChatWithAI)
        val progressBar    = view.findViewById<ProgressBar>(R.id.progressBarImage)

        // Cache references for use in the chat callback
        tvAiInsightsRef   = tvAiInsights
        btnContinueChatRef = btnContinueChat
        markwon = Markwon.create(requireContext())

        if (showTutorial) {
            tutorialBanner.visibility = View.VISIBLE
            btnFinishTour.setOnClickListener { tutorialBanner.visibility = View.GONE }
        } else {
            tutorialBanner.visibility = View.GONE
        }

        // Image loading
        ivImage.visibility = View.INVISIBLE
        progressBar.visibility = View.VISIBLE

        if (!diagnosis.imageUrl.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val input = URL(diagnosis.imageUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(input)
                    withContext(Dispatchers.Main) {
                        loadedImageBitmap = bitmap
                        ivImage.setImageBitmap(bitmap)
                        ivImage.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        ivImage.setImageResource(R.drawable.sdaad)
                        ivImage.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                    }
                }
            }
        } else {
            ivImage.visibility = View.GONE
            progressBar.visibility = View.GONE
        }

        // Summary
        tvSummary.text = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            .format(Date(diagnosis.timestamp))

        // Detections
        if (diagnosis.detections.isEmpty()) {
            tvDetections.text = "No diseases detected. Plant appears healthy!"
        } else {
            tvDetections.text = buildString {
                diagnosis.detections.forEachIndexed { index, detection ->
                    append("${index + 1}. ${detection.label} (${String.format("%.1f%%", detection.confidence * 100)})\n")
                }
            }
        }

        // AI Insights (rendered with current value which may grow across sessions)
        refreshInsightsView()

        // Build context string from detections + AI insights to seed the chat
        btnContinueChat.setOnClickListener {
            launchChat()
        }

        btnClose.setOnClickListener { dismiss() }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Re-renders the AI Insights TextView with [currentAiInsights] and toggles the
     * "Continue to chat" button visibility.
     */
    private fun refreshInsightsView() {
        val tvAiInsights   = tvAiInsightsRef ?: return
        val btnContinueChat = btnContinueChatRef ?: return
        val mk             = markwon ?: return

        if (currentAiInsights.isNotBlank()) {
            mk.setMarkdown(tvAiInsights, currentAiInsights)
            tvAiInsights.visibility = View.VISIBLE
            btnContinueChat.visibility = View.VISIBLE
        } else {
            tvAiInsights.visibility = View.GONE
            btnContinueChat.visibility = View.GONE
        }
    }

    private fun buildDiagnosisContext(): String {
        val detectionsText = if (diagnosis.detections.isEmpty()) {
            "No diseases detected. Plant appears healthy."
        } else {
            diagnosis.detections.joinToString("\n") { detection ->
                "• ${detection.label} (${String.format("%.1f%%", detection.confidence * 100)} confidence)"
            }
        }

        return buildString {
            appendLine("=== SAVED DIAGNOSIS CONTEXT ===")
            appendLine()
            appendLine("Scan Date: ${SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(diagnosis.timestamp))}")
            appendLine()
            appendLine("--- Detections ---")
            appendLine(detectionsText)
            appendLine()
            appendLine("--- AI Expert Insight ---")
            appendLine(currentAiInsights.ifBlank { "No insights available." })
            appendLine()
            appendLine("=== END OF CONTEXT ===")
        }
    }

    /**
     * Opens the chat bottom sheet. When the user closes the chat, any genuine
     * user↔AI exchange is received here as a markdown transcript, appended to
     * [currentAiInsights], shown immediately in the UI, and persisted to Firestore.
     */
    private fun launchChat() {
        val chatDialog = ChatBottomSheetDialog.newInstance(
            diagnosisContext = buildDiagnosisContext(),
            image = loadedImageBitmap,
            onConversationEnded = { transcript ->
                // Append the new transcript to the running insights
                currentAiInsights = if (currentAiInsights.isNotBlank()) {
                    "$currentAiInsights\n\n$transcript"
                } else {
                    transcript
                }

                // Update the UI immediately so the user sees their conversation
                refreshInsightsView()

                // Persist the updated insights to Firestore
                if (diagnosis.id.isNotBlank()) {
                    lifecycleScope.launch {
                        val result = DiagnosisRepository().updateAiInsights(
                            diagnosisId = diagnosis.id,
                            chatTranscript = transcript
                        )
                        if (result.isFailure) {
                            Toast.makeText(
                                requireContext(),
                                "Could not save chat — please check your connection.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
        chatDialog.show(parentFragmentManager, "DiagnosisChatDialog")
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear view references to prevent leaks
        tvAiInsightsRef   = null
        btnContinueChatRef = null
        markwon = null
    }
}