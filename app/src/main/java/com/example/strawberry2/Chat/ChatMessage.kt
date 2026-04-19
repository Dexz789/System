package com.example.strawberry2.Chat

import android.graphics.Bitmap

data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val image: Bitmap? = null,  // Add image support
    val canBeSaved: Boolean = false,  // Flag to show save button
    val excludeFromHistory: Boolean = false,
    val isInitialPrompt: Boolean = false
)

