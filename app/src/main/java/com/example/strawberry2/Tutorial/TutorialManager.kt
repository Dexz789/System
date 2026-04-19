package com.example.strawberry2.Tutorial

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.RectF
import android.util.Log
import com.example.strawberry2.DiagnosisHistoryActivity
import com.example.strawberry2.MainActivity
import com.example.strawberry2.R

/**
 * Orchestrates the GrowMate first-time tutorial.
 *
 * First-time detection:
 *   SharedPreferences keyed by Firebase UID → each account sees the tutorial
 *   exactly once per device install (offline-safe, no network required).
 *   For true cross-device persistence swap these calls with a Firestore flag
 *   on the user's profile document (users/{uid}.tutorialComplete = true).
 *
 * Step flow:
 *   Welcome screen
 *     ↓ Start Tour
 *   Step 1a — highlight entire "Choose Image Source" card (non-interactive)
 *     ↓ Next
 *   Step 1b — highlight "Select from Gallery" card (non-interactive)
 *     ↓ Next
 *   Step 1c — highlight "Take Photo" card (non-interactive)
 *     ↓ Next  →  overlay dismissed, user may now tap either button freely
 *   [User selects or captures an image]
 *   Step 3  — highlight "Diagnosis Results" card while analysis runs
 *     ↓ analysis complete (MainActivity calls onAnalysisComplete())
 *   Slowly scroll to bottom of the page, then dismiss
 */
class TutorialManager(
    private val activity: AppCompatActivity,
    private val userId: String
) {

    companion object {
        private const val PREFS_NAME = "growmate_tutorial_prefs"
        private fun prefKey(uid: String) = "tutorial_done_$uid"
    }

    private var diagnosisScrollListener: NestedScrollView.OnScrollChangeListener? = null

    private val decorView get() = activity.window.decorView as ViewGroup
    private var activeOverlay: View? = null


    /** Set to true once step 1c is dismissed; MainActivity checks this
     *  before forwarding image-selected events. */
    var isWaitingForImage: Boolean = false
        private set
    var pendingHistorySpotlight: Boolean = false
        private set
    // References held for step 3
    private var diagnosisCardRef: View? = null
    private var nestedScrollRef: NestedScrollView? = null
    private var imagePreviewRef: View? = null
    private var saveDiagnosisButtonRef: View? = null
    private var drawerLayoutRef: androidx.drawerlayout.widget.DrawerLayout? = null
    private var pastReportsMenuItemRef: View? = null
    private var navigationViewRef: com.google.android.material.navigation.NavigationView? = null
    private var imagePreviewOverlay: TutorialOverlayView? = null
    private var analysisComplete: Boolean = false
    private enum class AnalysisState { IDLE, WAITING_FOR_USER, USER_WAITING_FOR_ANALYSIS }

    private var analysisState: AnalysisState = AnalysisState.IDLE




    // ── Public API ────────────────────────────────────────────────────────────

    fun shouldShowTutorial(): Boolean =
        !activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(prefKey(userId), false)

    fun markComplete() {
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(prefKey(userId), true).apply()
    }

    /**
     * Entry point. Call from MainActivity.showTutorialIfNeeded() after all views are ready.
     *
     * @param userName          Display name for the welcome greeting.
     * @param imageSourceCard   The whole "Choose Image Source" MaterialCardView (id: cardImageSource).

     */
    fun startTutorial(
        userName: String,
        imageSourceCard: View,
        imagePreview: View,
        diagnosisCard: View,
        saveDiagnosisButton: View,
        drawerLayout: androidx.drawerlayout.widget.DrawerLayout,   // ← ADD
        navigationView: com.google.android.material.navigation.NavigationView  // ← ADD
    ) {
        this.saveDiagnosisButtonRef = saveDiagnosisButton
        this.drawerLayoutRef        = drawerLayout
        this.navigationViewRef      = navigationView              // ← store it (add field below)
        showWelcome(
            userName = userName,
            onSkip   = { dismissActive(); markComplete() },
            onStart  = { dismissActive(); showStep1ImageSource(imageSourceCard, imagePreview, diagnosisCard) }
        )
    }

    /**
     * Call this from MainActivity when an image has been loaded (gallery or camera).
     * Only acts while the tutorial is in the "waiting for image" phase.
     *
     * @param diagnosisCard  The "Diagnosis Results" MaterialCardView (id: cardDiagnosisResults).
     * @param nestedScroll   The root NestedScrollView (id: nestedScrollView).
     */
    fun onImageSelected(diagnosisCard: View, nestedScroll: NestedScrollView) {
        if (!isWaitingForImage) return
        isWaitingForImage = false
        dismissActive()
        diagnosisCardRef = diagnosisCard
        nestedScrollRef  = nestedScroll

        val preview = imagePreviewRef
        if (preview != null) {
            showStepImagePreview(preview, diagnosisCard)
        }
        // showStep2 is no longer called here — onAnalysisComplete handles it
    }

    /**
     * Call this from MainActivity once the AI analysis result has been written to tvResults
     * and all views have had a chance to re-measure. Triggers the scroll-to-bottom animation
     * and then dismisses the tutorial overlay.
     */
    fun onAnalysisComplete() {
        when (analysisState) {
            AnalysisState.USER_WAITING_FOR_ANALYSIS -> {
                // User already pressed Next and is on the waiting screen — advance now
                analysisState = AnalysisState.IDLE
                val diagCard = diagnosisCardRef ?: run { dismissAndFinish(); return }
                val scroll   = nestedScrollRef  ?: run { dismissAndFinish(); return }
                dismissActive()
                diagCard.post {
                    // Estimate tooltip height (header ~52dp + desc ~80dp + padding + arrow ~18dp ≈ 220dp)
                    val tooltipOffset = (200 * activity.resources.displayMetrics.density).toInt()
                    val targetScroll = (diagCard.top - tooltipOffset).coerceAtLeast(0)
                    scroll.smoothScrollTo(0, targetScroll)
                    scroll.postDelayed({ showStep2(diagCard, scroll) }, 600L)
                }
            }
            AnalysisState.WAITING_FOR_USER -> {
                // User hasn't pressed Next yet — just mark analysis as done and do nothing
                analysisState = AnalysisState.IDLE
            }
            AnalysisState.IDLE -> { /* nothing to do */ }
        }
    }

    fun dismiss() = dismissActive()

    // ── Welcome screen ────────────────────────────────────────────────────────

    private fun showWelcome(userName: String, onSkip: () -> Unit, onStart: () -> Unit) {
        val view = TutorialWelcomeView(
            context  = activity,
            userName = userName,
            onSkip   = onSkip,
            onStart  = onStart
        ).also {
            it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        decorView.addView(view)
        activeOverlay = view
    }

    // ── Step 1: Whole "Choose Image Source" card ─────────────────────────────

    private fun showStep1ImageSource(imageSourceCard: View, imagePreview: View, diagnosisCard: View) {
        val overlay = buildOverlay(
            title       = "📷  Choose Image Source",
            description = "Tap one of the options below to get started — choose an image " +
                    "from your gallery or take a new photo. You must select an image to continue.",
            showNext    = false,
            interactive = true,
            onNext      = {}
        )
        isWaitingForImage = true
        decorView.addView(overlay)
        activeOverlay = overlay
        locateSpotlight(overlay, imageSourceCard)

        // Store imagePreview so onImageSelected can pass it along
        this.imagePreviewRef = imagePreview       // ← store reference
    }

    private fun showStepImagePreview(imagePreview: View, diagnosisCard: View) {
        analysisState = AnalysisState.WAITING_FOR_USER  // ← user hasn't pressed Next yet

        val overlay = buildOverlay(
            title       = "🖼️  Your Selected Image",
            description = "This is the image you chose. Make sure your strawberry plant is " +
                    "clearly visible and well-lit for the best diagnosis results. " +
                    "Tap Next when you're ready.",
            showNext    = true,
            nextText    = "Next  →",
            interactive = false,
            onNext      = {
                val scroll   = nestedScrollRef  ?: run { dismissAndFinish(); return@buildOverlay }
                val diagCard = diagnosisCardRef ?: run { dismissAndFinish(); return@buildOverlay }
                dismissActive()

                if (analysisState == AnalysisState.IDLE) {
                    // Analysis already finished before user pressed Next
                    diagCard.post {
                        scroll.smoothScrollTo(0, diagCard.top)
                        scroll.postDelayed({ showStep2(diagCard, scroll) }, 600L)
                    }
                } else {
                    // Analysis still running — show waiting screen
                    analysisState = AnalysisState.USER_WAITING_FOR_ANALYSIS
                    showStepWaitingForAnalysis(diagCard, scroll)
                }
            }
        )
        decorView.addView(overlay)
        activeOverlay = overlay
        locateSpotlight(overlay, imagePreview)
    }
    private fun showStepWaitingForAnalysis(diagnosisCard: View, scrollView: NestedScrollView) {
        val overlay = buildOverlay(
            title       = "⏳  Analyzing Your Image",
            description = "Please wait while we analyze your plant. The next step will appear automatically once the diagnosis is ready.",
            showNext    = false,
            interactive = false,
            onNext      = {}
        )
        decorView.addView(overlay)
        activeOverlay = overlay
        // No spotlight target — just a dimmed waiting screen

        // onAnalysisComplete() will now need to check for this waiting state
        diagnosisCardRef = diagnosisCard
        nestedScrollRef  = scrollView
    }


    private fun showStep2(diagnosisCard: View, scrollView: NestedScrollView) {
        saveDiagnosisButtonRef?.isEnabled = false  // ← disable on entry

        val overlay = buildOverlay(
            title       = "🔍  Diagnosis Results",
            description = "Here are your AI diagnosis results — disease detections, confidence " +
                    "scores, and your plant's health status. Scroll down to see the whole Diagnosis. Tap 'Got it' when you're done.",
            showNext    = true,
            nextText    = "Got it  ✓",
            interactive = false,
            onNext      = {
                saveDiagnosisButtonRef?.isEnabled = true  // ← re-enable on dismiss
                diagnosisScrollListener?.let {
                    scrollView.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
                }
                diagnosisScrollListener = null
                dismissActive()
                val saveBtn = saveDiagnosisButtonRef
                if (saveBtn != null) showStep3SaveDiagnosis(saveBtn, scrollView)
                else dismissAndFinish()
            }
        )
        overlay.allowScrollPassthrough = true
        decorView.addView(overlay)
        activeOverlay = overlay
        locateSpotlight(overlay, diagnosisCard)

        val listener = NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
            val loc = IntArray(2)
            diagnosisCard.getLocationOnScreen(loc)
            overlay.spotlightRect = android.graphics.RectF(
                loc[0].toFloat(),
                loc[1].toFloat(),
                (loc[0] + diagnosisCard.width).toFloat(),
                (loc[1] + diagnosisCard.height).toFloat()
            )
        }
        diagnosisScrollListener = listener
        scrollView.setOnScrollChangeListener(listener)
    }
    private fun showStep3SaveDiagnosis(saveButton: View, scrollView: NestedScrollView) {
        // Calculate the button's Y position relative to the NestedScrollView's content
        var offset = 0
        var current: View = saveButton
        while (current != scrollView) {
            offset += current.top
            current = current.parent as View
        }
        val targetScroll = offset - 40  // 40px padding above the button

        scrollView.smoothScrollTo(0, targetScroll)

        scrollView.postDelayed({
            val overlay = buildOverlay(
                title       = "💾  Save Your Diagnosis",
                description = "Tap the Save Diagnosis button to store your results. " +
                        "Your diagnosis will be saved to your history for future reference.",
                showNext    = false,
                interactive = true,
                onNext      = {}
            )
            overlay.onSpotlightClick = {
                Log.d("TutorialDebug", "Save button spotlight tapped")
                dismissActive()
                Log.d("TutorialDebug", "Calling saveButton.performClick()")
                saveButton.performClick()
            }

            val listener = NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
                val l = IntArray(2)
                saveButton.getLocationOnScreen(l)
                overlay.spotlightRect = android.graphics.RectF(
                    l[0].toFloat(),
                    l[1].toFloat(),
                    (l[0] + saveButton.width).toFloat(),
                    (l[1] + saveButton.height).toFloat()
                )
            }
            diagnosisScrollListener = listener
            scrollView.setOnScrollChangeListener(listener)

            decorView.addView(overlay)
            activeOverlay = overlay
            locateSpotlight(overlay, saveButton)
        }, 700L)  // 700ms gives smoothScrollTo enough time to finish
    }
    private fun showStep4PastReports(
        pastReportsItemView: View,
        drawer: androidx.drawerlayout.widget.DrawerLayout
    ) {
        Log.d("TutorialDebug", "showStep4PastReports() called")
        Log.d("TutorialDebug", "pastReportsItemView=$pastReportsItemView  width=${pastReportsItemView.width}  height=${pastReportsItemView.height}")

        val overlay = buildOverlay(
            title       = "📋  Past Reports",
            description = "All your saved diagnoses are stored here. Tap 'Past Reports' anytime " +
                    "to review your history, track your plant's health over time, and revisit " +
                    "AI insights from previous scans.",
            showNext    = false,
            interactive = false,   // ← KEY CHANGE: must be false so onSpotlightClick fires
            onNext      = {}
        )

        overlay.onSpotlightClick = {
            Log.d("TutorialDebug", "onSpotlightClick FIRED — proceeding to DiagnosisHistoryActivity")
            dismissActive()
            markComplete()
            (activity as? MainActivity)?.isTutorialActive = false
            drawer.closeDrawers()
            val intent = android.content.Intent(activity, DiagnosisHistoryActivity::class.java)
            intent.putExtra("SHOW_TUTORIAL_SPOTLIGHT", true)
            activity.startActivity(intent)
        }

        Log.d("TutorialDebug", "Adding overlay to decorView")
        decorView.addView(overlay)
        activeOverlay = overlay
        locateSpotlight(overlay, pastReportsItemView)
        Log.d("TutorialDebug", "locateSpotlight() called — spotlightRect will be set shortly")
    }

    /**
     * Call this from MainActivity's saveDiagnosis() result.onSuccess block,
     * after the button has been set to "Saved ✓" and disabled.
     */
    fun onSaveComplete() {
        Log.d("TutorialDebug", "onSaveComplete() entered")
        dismissActive()
        val drawer = drawerLayoutRef
        val navView = navigationViewRef

        Log.d("TutorialDebug", "drawerLayoutRef=$drawer")
        Log.d("TutorialDebug", "navigationViewRef=$navView")

        if (drawer == null) {
            Log.d("TutorialDebug", "drawer is null — calling dismissAndFinish()")
            dismissAndFinish()
            return
        }
        if (navView == null) {
            Log.d("TutorialDebug", "navView is null — calling dismissAndFinish()")
            dismissAndFinish()
            return
        }

        Log.d("TutorialDebug", "Posting 600ms delay to open drawer")
        activity.window.decorView.postDelayed({
            Log.d("TutorialDebug", "Opening drawer now")
            drawer.openDrawer(androidx.core.view.GravityCompat.START)

            activity.window.decorView.postDelayed({
                Log.d("TutorialDebug", "Looking for nav_history item view")
                val itemView = navView.findViewById<View>(R.id.nav_history)
                Log.d("TutorialDebug", "nav_history itemView=$itemView")

                if (itemView != null) {
                    Log.d("TutorialDebug", "Found itemView — calling showStep4PastReports")
                    showStep4PastReports(itemView, drawer)
                } else {
                    navView.post {
                        val v = navView.findViewById<View>(R.id.nav_history)
                        Log.d("TutorialDebug", "Retried — nav_history view=$v")
                        if (v != null) showStep4PastReports(v, drawer)
                        else {
                            Log.d("TutorialDebug", "Still null — dismissAndFinish()")
                            dismissAndFinish()
                        }
                    }
                }
            }, 400L)
        }, 600L)
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a configured [TutorialOverlayView] without attaching it to the window.
     */
    private fun buildOverlay(
        title: String,
        description: String,
        showNext: Boolean,
        nextText: String = "Next  →",
        interactive: Boolean,
        onNext: () -> Unit
    ): TutorialOverlayView {
        return TutorialOverlayView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tooltipTitle          = title
            tooltipDescription    = description
            showNextButton        = showNext
            nextButtonText        = nextText
            spotlightInteractive  = interactive
            onNextClick           = onNext
            onDimClick            = { /* block all background taps */ }
            onSpotlightClick      = { if (interactive) onNext() }
        }
    }

    /**
     * Sets [overlay]'s spotlight rect once [targetView] is laid out on screen.
     */
    private fun locateSpotlight(overlay: TutorialOverlayView, targetView: View) {
        fun measure() {
            val loc = IntArray(2)
            targetView.getLocationOnScreen(loc)
            overlay.spotlightRect = RectF(
                loc[0].toFloat(),
                loc[1].toFloat(),
                (loc[0] + targetView.width).toFloat(),
                (loc[1] + targetView.height).toFloat()
            )
        }

        if (targetView.isLaidOut && targetView.width > 0) {
            measure()
        } else {
            targetView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        targetView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        measure()
                    }
                }
            )
        }
    }

    private fun dismissAndFinish() {
        dismissActive()
        markComplete()
        (activity as? MainActivity)?.isTutorialActive = false
    }

    private fun dismissActive() {
        activeOverlay?.let { decorView.removeView(it) }
        activeOverlay = null
        // Clean up scroll listener if it was set
        diagnosisScrollListener?.let {
            nestedScrollRef?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        }
        diagnosisScrollListener = null
    }
}