package com.example.strawberry2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object EmbeddingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun generateEmbedding(text: String): List<Double>? = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", AppConfig.EMBEDDING_MODEL)
                put("input", text)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/embeddings")
                .addHeader("Authorization", "Bearer ${AppConfig.OPENROUTER_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/GrowMate-Inc")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val data = json.getJSONArray("data")
                if (data.length() > 0) {
                    val embeddingArray = data.getJSONObject(0).getJSONArray("embedding")
                    val result = mutableListOf<Double>()
                    for (i in 0 until embeddingArray.length()) {
                        result.add(embeddingArray.getDouble(i))
                    }
                    result
                } else null
            } else {
                android.util.Log.e("EmbeddingService", "Error ${response.code}: $responseBody")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("EmbeddingService", "Failed to generate embedding", e)
            null
        }
    }

    fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size) return 0.0
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val magnitude = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (magnitude == 0.0) 0.0 else dotProduct / magnitude
    }

    fun buildSearchText(diagnosis: DiagnosisData): String {
        val detections = diagnosis.detections.joinToString(", ") { "${it.label} (${(it.confidence * 100).toInt()}%)" }
        return buildString {
            append("Detections: $detections. ")
            diagnosis.aiInsights?.let { append("Analysis: $it. ") }
        }
    }
}
