package com.example.strawberry2.Tutorial

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.MotionEvent
import android.view.View

/**
 * Full-screen overlay that:
 * - Dims everything except a spotlight rectangle around the target element
 * - Shows a tooltip card (title + description) above or below the spotlight
 * - Draws a triangle arrow connecting the tooltip to the spotlight
 * - Optionally draws a tappable "Next →" button inside the tooltip
 * - Blocks all touches outside the spotlight
 * - When [spotlightInteractive] is false, tapping the spotlight does nothing
 */
class TutorialOverlayView(context: Context) : View(context) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    var spotlightRect: RectF? = null
        set(value) { field = value; invalidate() }

    var spotlightCornerRadius: Float = dpToPx(20f)
    var tooltipTitle: String = ""
    var tooltipDescription: String = ""

    /** When true the spotlight area can be tapped to trigger [onSpotlightClick]. */
    var spotlightInteractive: Boolean = false

    /** Show a "Next →" button drawn inside the tooltip card. */
    var showNextButton: Boolean = false
    var nextButtonText: String = "Next  →"

    var onSpotlightClick: (() -> Unit)? = null
    var onNextClick: (() -> Unit)? = null
    var onDimClick: (() -> Unit)? = null
    var allowScrollPassthrough: Boolean = false
    var hideNextButton: Boolean = false



    // ── Paints ────────────────────────────────────────────────────────────────

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }
    private val spotBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
        isAntiAlias = true
        alpha = 200
    }
    private val cardBgPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    private val headerBgPaint = Paint().apply {
        color = Color.parseColor("#C62828")
        isAntiAlias = true
    }
    private val titlePaint = TextPaint().apply {
        color = Color.WHITE
        textSize = spToPx(15f)
        isFakeBoldText = true
        isAntiAlias = true
    }
    private val descPaint = TextPaint().apply {
        color = Color.parseColor("#424242")
        textSize = spToPx(13f)
        isAntiAlias = true
    }
    private val nextBtnBgPaint = Paint().apply {
        color = Color.parseColor("#C62828")
        isAntiAlias = true
    }
    private val nextBtnTextPaint = TextPaint().apply {
        color = Color.WHITE
        textSize = spToPx(13f)
        isFakeBoldText = true
        isAntiAlias = true
    }
    private val arrowPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    private val shadowPaint = Paint().apply {
        color = Color.parseColor("#50000000")
        isAntiAlias = true
        maskFilter = BlurMaskFilter(dpToPx(10f), BlurMaskFilter.Blur.NORMAL)
    }

    // ── State set during onDraw ───────────────────────────────────────────────

    private var nextButtonRect: RectF? = null

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Dim layer + clear hole
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        spotlightRect?.let { r ->
            val p = dpToPx(14f)
            canvas.drawRoundRect(r.left - p, r.top - p, r.right + p, r.bottom + p,
                spotlightCornerRadius, spotlightCornerRadius, clearPaint)
        }
        canvas.restoreToCount(layer)

        // 2. Spotlight glow border
        spotlightRect?.let { r ->
            val p = dpToPx(14f)
            canvas.drawRoundRect(r.left - p, r.top - p, r.right + p, r.bottom + p,
                spotlightCornerRadius, spotlightCornerRadius, spotBorderPaint)
        }

        // 3. Tooltip
        spotlightRect?.let { drawTooltip(canvas, it, w, h) }
    }

    private fun drawTooltip(canvas: Canvas, spotRect: RectF, screenW: Float, screenH: Float) {
        val hMargin  = dpToPx(20f)
        val hPad     = dpToPx(16f)
        val vPad     = dpToPx(14f)
        val headerH  = dpToPx(52f)
        val arrowH   = dpToPx(18f)
        val arrowHW  = dpToPx(16f)
        val cornerR  = dpToPx(16f)
        val cardW    = screenW - hMargin * 2

        val descLayout = makeStaticLayout(tooltipDescription, descPaint, (cardW - hPad * 2).toInt())

        val btnH    = if (showNextButton) dpToPx(36f) + vPad else 0f
        val cardH   = headerH + vPad + descLayout.height + vPad + btnH

        val spotPad   = dpToPx(14f)
        val placeBelow = (screenH - (spotRect.bottom + spotPad)) >= cardH + arrowH + dpToPx(8f)

        val cardL = hMargin
        val cardR = hMargin + cardW
        val cardTop: Float
        val cardBottom: Float
        val arrowTipY: Float

        if (placeBelow) {
            cardTop    = spotRect.bottom + spotPad + arrowH + dpToPx(4f)
            cardBottom = cardTop + cardH
            arrowTipY  = spotRect.bottom + spotPad + dpToPx(4f)
        } else {
            cardBottom = spotRect.top - spotPad - arrowH - dpToPx(4f)
            cardTop    = cardBottom - cardH
            arrowTipY  = spotRect.top - spotPad - dpToPx(4f)
        }

        val cardRect = RectF(cardL, cardTop, cardR, cardBottom)

        // Shadow
        canvas.drawRoundRect(
            cardRect.left + dpToPx(3f), cardRect.top + dpToPx(4f),
            cardRect.right + dpToPx(3f), cardRect.bottom + dpToPx(4f),
            cornerR, cornerR, shadowPaint
        )

        // Card bg
        canvas.drawRoundRect(cardRect, cornerR, cornerR, cardBgPaint)

        // Header (red top half, square bottom so white body covers the rounded corner)
        val headerBottom = cardTop + headerH
        canvas.drawRoundRect(cardL, cardTop, cardR, headerBottom + cornerR,
            cornerR, cornerR, headerBgPaint)
        canvas.drawRect(cardL, headerBottom, cardR, headerBottom + cornerR, headerBgPaint)

        // Title
        val titleY = cardTop + (headerH + titlePaint.textSize) / 2f - dpToPx(1f)
        canvas.drawText(tooltipTitle, cardL + hPad, titleY, titlePaint)

        // Description
        canvas.save()
        canvas.translate(cardL + hPad, cardTop + headerH + vPad)
        descLayout.draw(canvas)
        canvas.restore()

        // "Next →" button
        if (showNextButton) {
            val btnW   = dpToPx(100f)
            val btnH2  = dpToPx(36f)
            val btnL   = cardR - hPad - btnW
            val btnT   = cardTop + headerH + vPad + descLayout.height + vPad
            val btnR   = cardR - hPad
            val btnB   = btnT + btnH2
            val btnRect = RectF(btnL, btnT, btnR, btnB)
            nextButtonRect = btnRect

            canvas.drawRoundRect(btnRect, dpToPx(18f), dpToPx(18f), nextBtnBgPaint)

            val tw     = nextBtnTextPaint.measureText(nextButtonText)
            val textX  = btnL + (btnW - tw) / 2f
            val textY  = btnT + (btnH2 + nextBtnTextPaint.textSize) / 2f - dpToPx(2f)
            canvas.drawText(nextButtonText, textX, textY, nextBtnTextPaint)
        } else {
            nextButtonRect = null
        }

        // Arrow triangle
        val arrowCX = spotRect.centerX().coerceIn(cardL + dpToPx(40f), cardR - dpToPx(40f))
        val path = Path()
        if (placeBelow) {
            path.moveTo(arrowCX - arrowHW, cardTop)
            path.lineTo(arrowCX + arrowHW, cardTop)
            path.lineTo(arrowCX, arrowTipY)
        } else {
            path.moveTo(arrowCX - arrowHW, cardBottom)
            path.lineTo(arrowCX + arrowHW, cardBottom)
            path.lineTo(arrowCX, arrowTipY)
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (event.action == MotionEvent.ACTION_UP) {
            Log.d("TutorialDebug", "onTouchEvent ACTION_UP x=$x y=$y")
            Log.d("TutorialDebug", "spotlightRect=$spotlightRect  spotlightInteractive=$spotlightInteractive")
            Log.d("TutorialDebug", "nextButtonRect=$nextButtonRect")
        }

        // ── "Got it" / Next button always intercepts ──────────────────────────
        nextButtonRect?.let { btn ->
            if (x >= btn.left && x <= btn.right && y >= btn.top && y <= btn.bottom) {
                if (event.action == MotionEvent.ACTION_UP) {
                    Log.d("TutorialDebug", "Next button tapped — invoking onNextClick")
                    onNextClick?.invoke()
                }
                return true
            }
        }

        // ── Scroll-passthrough mode (diagnosis step) ──────────────────────────
        if (allowScrollPassthrough) {
            return false
        }

        // ── Spotlight passthrough (Step 1 — interactive card buttons) ─────────
        val pad = dpToPx(14f)
        val inSpot = spotlightRect?.let { r ->
            x >= r.left - pad && x <= r.right + pad &&
                    y >= r.top - pad  && y <= r.bottom + pad
        } ?: false

        if (event.action == MotionEvent.ACTION_UP) {
            Log.d("TutorialDebug", "inSpot=$inSpot  spotlightInteractive=$spotlightInteractive")
        }

        if (inSpot && spotlightInteractive) {
            Log.d("TutorialDebug", "Touch passing THROUGH spotlight (interactive mode) — onSpotlightClick will NOT fire")
            return false  // pass through — onSpotlightClick is never called here
        }

        // ── Normal blocking mode ──────────────────────────────────────────────
        if (event.action == MotionEvent.ACTION_UP) {
            if (inSpot) {
                Log.d("TutorialDebug", "Tap inside spotlight (non-interactive) — invoking onSpotlightClick")
                onSpotlightClick?.invoke()
            } else {
                Log.d("TutorialDebug", "Tap outside spotlight — invoking onDimClick")
                onDimClick?.invoke()
            }
        }

        return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float) = sp * resources.displayMetrics.scaledDensity

    private fun makeStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width,
                android.text.Layout.Alignment.ALIGN_NORMAL, 1.3f, 0f, false)
        }
}