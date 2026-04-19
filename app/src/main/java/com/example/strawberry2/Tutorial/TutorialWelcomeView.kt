package com.example.strawberry2.Tutorial

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.cardview.widget.CardView

/**
 * Full-screen dim overlay with a centered welcome card.
 * Shown once on first launch: greets the user by name and offers
 * to start or skip the tutorial.
 */
class TutorialWelcomeView(
    context: Context,
    userName: String,
    private val onSkip: () -> Unit,
    private val onStart: () -> Unit
) : FrameLayout(context) {

    init {
        setBackgroundColor(Color.parseColor("#CC000000"))
        isClickable = true // prevents click-through

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun spSz(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

        // ── Card shell ───────────────────────────────────────────────────────
        val card = CardView(context).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(16).toFloat()
            setCardBackgroundColor(Color.WHITE)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(32), dp(28), dp(24))
        }

        // ── Strawberry emoji ─────────────────────────────────────────────────
        val emoji = TextView(context).apply {
            text = "🍓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            gravity = Gravity.CENTER
        }

        // ── Welcome headline ─────────────────────────────────────────────────
        val firstName = userName.split(" ").firstOrNull()?.ifBlank { null } ?: "there"
        val headline = TextView(context).apply {
            text = "Welcome, $firstName! 👋"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#B71C1C"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(2))
        }

        // ── App subtitle ─────────────────────────────────────────────────────
        val subtitle = TextView(context).apply {
            text = "GrowMate"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }

        // ── Divider ──────────────────────────────────────────────────────────
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        val divLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).also {
            it.topMargin = dp(18); it.bottomMargin = dp(18)
        }

        // ── Description ──────────────────────────────────────────────────────
        val desc = TextView(context).apply {
            text = "Let's take a quick tour so you know exactly how to capture and analyze your strawberry plants."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#555555"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.45f)
        }

        // ── Buttons ──────────────────────────────────────────────────────────
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 2f
            setPadding(0, dp(22), 0, 0)
        }

        val skipBtn = Button(context).apply {
            text = "Skip"
            setTextColor(Color.parseColor("#9E9E9E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onSkip() }
        }

        val startBtn = Button(context).apply {
            text = "Start Tour  →"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#C62828"))
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { onStart() }
        }

        val btnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
            it.marginEnd = dp(6); it.marginStart = dp(6)
        }
        btnRow.addView(skipBtn, btnLp)
        btnRow.addView(startBtn, btnLp)

        // ── Assemble ─────────────────────────────────────────────────────────
        content.addView(emoji)
        content.addView(headline)
        content.addView(subtitle)
        content.addView(divider, divLp)
        content.addView(desc)
        content.addView(btnRow)
        card.addView(content)

        val cardLp = LayoutParams(dp(300), LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(card, cardLp)
    }
}