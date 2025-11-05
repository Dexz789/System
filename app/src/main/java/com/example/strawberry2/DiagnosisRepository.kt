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

                // Create diagnosis with image URL
                val diagnosisWithId = diagnosis.copy(
                    id = diagnosisId,
                    imageUrl = imageUrl
                )

                println("DEBUG: Saving diagnosis with imageUrl: ${diagnosisWithId.imageUrl}")
                println("DEBUG: Full diagnosis data: $diagnosisWithId")

                // Save to Firestore
                docRef.set(diagnosisWithId.toMap()).await()
                println("DEBUG: Diagnosis saved to Firestore successfully")

                Result.success(docRef.id)
            } catch (e: Exception) {
                println("ERROR: Save failed: ${e.message}")
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

    // Get diagnosis count for a user
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