package com.example.strawberry2

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class PlantNetService {
    private val client = OkHttpClient()

    // Get your API key from https://my.plantnet.org/
    private val API_KEY = "2b10uM74OcWSAACaMpA0nz78O"
    private val BASE_URL = "https://my-api.plantnet.org/v2/identify/all"

    data class PlantIdentification(
        val isStrawberry: Boolean,
        val scientificName: String?,
        val commonName: String?,
        val confidence: Float
    )

    /**
     * Identifies if the image contains a strawberry plant
     * @param bitmap The image to analyze
     * @return PlantIdentification result
     */
    suspend fun identifyPlant(bitmap: Bitmap): Result<PlantIdentification> {
        return withContext(Dispatchers.IO) {
            try {
                // Convert bitmap to JPEG bytes
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val imageBytes = stream.toByteArray()

                // Create multipart request body
                // Send multiple organ types so PlantNet can match leaves, fruit, or flowers
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "images",
                        "image.jpg",
                        imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                    .addFormDataPart("organs", "auto") // let PlantNet decide the organ type
                    .build()

                // Build request
                val request = Request.Builder()
                    .url("$BASE_URL?api-key=$API_KEY")
                    .post(requestBody)
                    .build()

                // Execute request
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("PlantNet API error: ${response.code} - ${response.message}")
                    )
                }

                // Parse response
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    return@withContext Result.failure(
                        IOException("Empty response from PlantNet API")
                    )
                }

                println("DEBUG PlantNet: Raw response (first 500 chars): ${responseBody.take(500)}")
                println("DEBUG PlantNet: Response received, parsing...")
                val identification = parsePlantNetResponse(responseBody)
                println("DEBUG PlantNet: Parsed - isStrawberry=${identification.isStrawberry}, confidence=${identification.confidence}, name=${identification.scientificName}, common=${identification.commonName}")

                Result.success(identification)

            } catch (e: Exception) {
                println("ERROR PlantNet: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Parse PlantNet API response and determine if it's a strawberry plant
     */
    private fun parsePlantNetResponse(jsonResponse: String): PlantIdentification {
        try {
            val json = JSONObject(jsonResponse)
            val results = json.optJSONArray("results")

            // No results or empty results = no plant detected
            if (results == null || results.length() == 0) {
                println("DEBUG PlantNet: No results found in response")
                return PlantIdentification(
                    isStrawberry = false,
                    scientificName = null,
                    commonName = null,
                    confidence = 0f
                )
            }

            // Get the top result (highest confidence)
            val topResult = results.getJSONObject(0)
            val score = topResult.optDouble("score", 0.0).toFloat()
            val species = topResult.optJSONObject("species")

            val scientificName = species?.optString("scientificNameWithoutAuthor") ?: ""
            val commonNames = species?.optJSONArray("commonNames")
            val firstCommonName = if (commonNames != null && commonNames.length() > 0) {
                commonNames.getString(0)
            } else {
                null
            }

            println("DEBUG PlantNet: Top result - $scientificName ($firstCommonName) - score: $score")

            // If confidence is extremely low, treat as "nothing detected"
            if (score < 0.01f) {
                println("DEBUG PlantNet: Confidence too low ($score), treating as no plant detected")
                return PlantIdentification(
                    isStrawberry = false,
                    scientificName = scientificName.takeIf { it.isNotEmpty() },
                    commonName = firstCommonName,
                    confidence = score
                )
            }

            // Check if it's a strawberry plant
            // Strawberry scientific names: Fragaria × ananassa (garden strawberry),
            // Fragaria vesca (wild strawberry), etc.
            val isStrawberry = isStrawberryPlant(scientificName, firstCommonName)

            println("DEBUG PlantNet: Is strawberry? $isStrawberry")

            return PlantIdentification(
                isStrawberry = isStrawberry,
                scientificName = scientificName.takeIf { it.isNotEmpty() },
                commonName = firstCommonName,
                confidence = score
            )

        } catch (e: Exception) {
            println("ERROR PlantNet: Failed to parse response - ${e.message}")
            e.printStackTrace()
            return PlantIdentification(
                isStrawberry = false,
                scientificName = null,
                commonName = null,
                confidence = 0f
            )
        }
    }

    /**
     * Determine if the identified plant is a strawberry based on scientific and common names
     */
    private fun isStrawberryPlant(scientificName: String, commonName: String?): Boolean {
        val scientificLower = scientificName.lowercase()
        val commonLower = commonName?.lowercase() ?: ""

        // Check scientific name for Fragaria genus (strawberries)
        if (scientificLower.contains("fragaria")) {
            println("DEBUG PlantNet: Matched 'fragaria' in scientific name")
            return true
        }

        // Check common name for "strawberry" in various languages
        val strawberryKeywords = listOf(
            "strawberry",
            "fraise",       // French
            "fresa",        // Spanish
            "erdbeere",     // German
            "fragola",      // Italian
            "morango",      // Portuguese
        )

        for (keyword in strawberryKeywords) {
            if (commonLower.contains(keyword)) {
                println("DEBUG PlantNet: Matched '$keyword' in common name")
                return true
            }
        }

        println("DEBUG PlantNet: No strawberry match found")
        return false
    }
}