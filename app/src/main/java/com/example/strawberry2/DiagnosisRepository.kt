package com.example.strawberry2

import android.graphics.Bitmap

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import android.util.Base64
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DiagnosisRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val diagnosesCollection = db.collection("diagnoses")
    private val imagesRef = storage.reference.child("diagnosis_images")

    // Upload image to Firebase Storage and get download URL
    private suspend fun uploadImage(bitmap: Bitmap, diagnosisId: String): String {
        return withContext(Dispatchers.IO) {
            // Compress bitmap to JPEG
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val data = baos.toByteArray()

            // Create unique filename
            val filename = "${diagnosisId}_${System.currentTimeMillis()}.jpg"
            val imageRef = imagesRef.child(filename)

            println("DEBUG: Starting image upload, size: ${data.size} bytes, filename: $filename")

            // Upload image and wait for completion
            imageRef.putBytes(data).await()
            println("DEBUG: Image uploaded successfully")

            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await().toString()
            println("DEBUG: Got download URL: $downloadUrl")

            downloadUrl
        }
    }

    // Save a new diagnosis with image
    suspend fun saveDiagnosis(diagnosis: DiagnosisData, imageBitmap: Bitmap? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val docRef = diagnosesCollection.document()
                val diagnosisId = docRef.id
                println("DEBUG: Created diagnosis ID: $diagnosisId")

                // Upload image if provided and get URL
                var imageUrl: String? = null
                if (imageBitmap != null) {
                    println("DEBUG: Image bitmap provided (${imageBitmap.width}x${imageBitmap.height}), uploading...")
                    try {
                        // Wait for upload to complete and get URL
                        imageUrl = uploadImage(imageBitmap, diagnosisId)
                        println("DEBUG: Image uploaded successfully, URL: $imageUrl")
                    } catch (e: Exception) {
                        println("ERROR: Failed to upload image: ${e.message}")
                        e.printStackTrace()
                        // Don't throw - continue saving diagnosis without image
                    }
                } else {
                    println("DEBUG: No image bitmap provided")
                }

                // Create diagnosis with image URL and embedding
                var diagnosisWithEmbedding = diagnosis.copy(
                    id = diagnosisId,
                    imageUrl = imageUrl
                )

                try {
                    val searchText = EmbeddingService.buildSearchText(diagnosisWithEmbedding)
                    val embedding = EmbeddingService.generateEmbedding(searchText)
                    if (embedding != null) {
                        diagnosisWithEmbedding = diagnosisWithEmbedding.copy(embedding = embedding)
                    }
                } catch (_: Exception) { }

                // Save to Firestore
                docRef.set(diagnosisWithEmbedding.toMap()).await()
                println("DEBUG: Diagnosis saved to Firestore successfully")

                Result.success(docRef.id)
            } catch (e: Exception) {
                println("ERROR: Save failed: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Appends a follow-up chat transcript to an existing diagnosis's aiInsights field.
     * The new [chatTranscript] is appended after a divider so the original insights
     * are always preserved.
     */
    suspend fun updateAiInsights(diagnosisId: String, chatTranscript: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch the current insights so we can append to them
                val snapshot = diagnosesCollection.document(diagnosisId).get().await()
                val currentInsights = snapshot.getString("aiInsights") ?: ""

                val updatedInsights = if (currentInsights.isNotBlank()) {
                    "$currentInsights\n\n$chatTranscript"
                } else {
                    chatTranscript
                }

                diagnosesCollection.document(diagnosisId)
                    .update("aiInsights", updatedInsights)
                    .await()

                println("DEBUG: Updated aiInsights for diagnosis: $diagnosisId")
                Result.success(Unit)
            } catch (e: Exception) {
                println("ERROR: Failed to update aiInsights: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // Get all diagnoses for a specific user
    suspend fun getUserDiagnoses(userId: String): Result<List<DiagnosisData>> {
        return try {
            val snapshot = diagnosesCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val diagnoses = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = DiagnosisData.fromMap(doc.data as Map<String, Any>)
                    println("DEBUG: Loaded diagnosis ID: ${data.id}, imageUrl: ${data.imageUrl}")
                    data
                } catch (e: Exception) {
                    println("ERROR: Failed to parse diagnosis: ${e.message}")
                    e.printStackTrace()
                    null
                }
            }
            println("DEBUG: Loaded ${diagnoses.size} diagnoses")
            Result.success(diagnoses)
        } catch (e: Exception) {
            println("ERROR: Failed to get diagnoses: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Get a single diagnosis by ID
    suspend fun getDiagnosisById(diagnosisId: String): Result<DiagnosisData?> {
        return try {
            val snapshot = diagnosesCollection.document(diagnosisId).get().await()
            val diagnosis = snapshot.data?.let { DiagnosisData.fromMap(it) }
            Result.success(diagnosis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Delete a diagnosis (including its image)
    suspend fun deleteDiagnosis(diagnosisId: String): Result<Unit> {
        return try {
            // Get diagnosis to find image URL
            val diagnosisSnapshot = diagnosesCollection.document(diagnosisId).get().await()
            val diagnosis = diagnosisSnapshot.data?.let { DiagnosisData.fromMap(it) }

            // Delete image from Storage if exists
            diagnosis?.imageUrl?.let { imageUrl ->
                if (imageUrl.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(imageUrl)
                        imageRef.delete().await()
                        println("DEBUG: Deleted image from storage: $imageUrl")
                    } catch (e: Exception) {
                        // Log error but continue with diagnosis deletion
                        println("ERROR: Failed to delete image: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // Delete diagnosis document
            diagnosesCollection.document(diagnosisId).delete().await()
            println("DEBUG: Deleted diagnosis document: $diagnosisId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("ERROR: Failed to delete diagnosis: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun searchSimilarDiagnoses(
        userId: String,
        queryEmbedding: List<Double>,
        maxResults: Int = 3,
        minSimilarity: Double = 0.3
    ): List<DiagnosisData> {
        return try {
            val snapshot = diagnosesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val scored = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = DiagnosisData.fromMap(doc.data as Map<String, Any>)
                    val emb = data.embedding
                    if (emb != null && emb.isNotEmpty()) {
                        val similarity = EmbeddingService.cosineSimilarity(queryEmbedding, emb)
                        if (similarity >= minSimilarity) {
                            SimilarDiagnosis(data, similarity)
                        } else null
                    } else null
                } catch (_: Exception) { null }
            }.sortedByDescending { it.similarity }.take(maxResults)

            scored.map { it.diagnosis }
        } catch (_: Exception) { emptyList() }
    }

    companion object {
        suspend fun buildRagContext(userId: String?, query: String): String? {
            if (userId == null) return null
            return try {
                val embedding = EmbeddingService.generateEmbedding(query) ?: return null
                val similar = DiagnosisRepository().searchSimilarDiagnoses(userId, embedding)
                if (similar.isEmpty()) return null
                similar.joinToString("\n\n---\n\n") { diag ->
                    val detections = diag.detections.joinToString(", ") {
                        "${it.label} (${(it.confidence * 100).toInt()}%)"
                    }
                    buildString {
                        appendLine("Date: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(diag.timestamp))}")
                        appendLine("Detections: $detections")
                        diag.aiInsights?.let { appendLine("Analysis: ${it.take(500)}") }
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    private data class SimilarDiagnosis(
        val diagnosis: DiagnosisData,
        val similarity: Double
    )

    suspend fun getDiagnosisCount(userId: String): Result<Int> {
        return try {
            val snapshot = diagnosesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            Result.success(snapshot.size())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}