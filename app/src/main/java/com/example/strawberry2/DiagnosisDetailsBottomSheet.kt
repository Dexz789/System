package com.example.strawberry2

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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

    companion object {
        fun newInstance(diagnosis: DiagnosisData): DiagnosisDetailsBottomSheet {
            val fragment = DiagnosisDetailsBottomSheet()
            val args = Bundle()
            args.putSerializable("diagnosis", diagnosis)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosis = arguments?.getSerializable("diagnosis") as DiagnosisData
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_diagnosis_details_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ivImage = view.findViewById<ImageView>(R.id.ivDiagnosisImage)
        val tvSummary = view.findViewById<TextView>(R.id.tvDiagnosisSummary)
        val tvDetections = view.findViewById<TextView>(R.id.tvDetections)
        val tvAiInsights = view.findViewById<TextView>(R.id.tvAiInsights)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarImage) // ✅ new line

        // Initially hide image and show loader
        ivImage.visibility = View.INVISIBLE
        progressBar.visibility = View.VISIBLE

        // Image loading logic
        if (!diagnosis.imageUrl.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val input = URL(diagnosis.imageUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(input)
                    withContext(Dispatchers.Main) {
                        ivImage.setImageBitmap(bitmap)
                        ivImage.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE // ✅ hide when done
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        ivImage.setImageResource(R.drawable.sdaad)
                        ivImage.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE // ✅ hide even if failed
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
            val details = buildString {
                diagnosis.detections.forEachIndexed { index, detection ->
                    append("${index + 1}. ${detection.label} (${String.format("%.1f%%", detection.confidence * 100)})\n")
                }
            }
            tvDetections.text = details
        }

        // AI Insights
        if (!diagnosis.aiInsights.isNullOrEmpty()) {
            val markwon = io.noties.markwon.Markwon.create(requireContext())
            markwon.setMarkdown(tvAiInsights, diagnosis.aiInsights ?: "")
            tvAiInsights.visibility = View.VISIBLE
        } else {
            tvAiInsights.visibility = View.GONE
        }

        btnClose.setOnClickListener { dismiss() }
    }


    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }
}