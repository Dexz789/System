package com.example.strawberry2

import java.io.Serializable

data class DiagnosisData(
    val id: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val detections: List<DetectionResult> = emptyList(),
    val imageUrl: String? = null,
    val totalIssuesFound: Int = 0,
    val aiInsights: String? = null,
    val embedding: List<Double>? = null,
    val chatHistory: List<Map<String, String>>? = null

) : Serializable {
    fun toMap(): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "id" to id,
            "userId" to userId,
            "timestamp" to timestamp,
            "detections" to detections.map { it.toMap() },
            "imageUrl" to (imageUrl ?: ""),
            "totalIssuesFound" to totalIssuesFound,
            "aiInsights" to (aiInsights ?: "")
        )
        embedding?.let { map["embedding"] = it }
        chatHistory?.let { map["chatHistory"] = it }
        return map as Map<String, Any>
    }

    companion object {
        fun fromMap(map: Map<String, Any>): DiagnosisData {
            return DiagnosisData(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                timestamp = map["timestamp"] as? Long ?: 0L,
                detections = (map["detections"] as? List<Map<String, Any>>)?.map {
                    DetectionResult.fromMap(it)
                } ?: emptyList(),
                imageUrl = map["imageUrl"] as? String,
                totalIssuesFound = (map["totalIssuesFound"] as? Long)?.toInt() ?: 0,
                aiInsights = map["aiInsights"] as? String,
                embedding = (map["embedding"] as? List<Double>),
                chatHistory = (map["chatHistory"] as? List<Map<String, String>>)
            )
        }
    }
}

data class DetectionResult(
    val label: String = "",
    val confidence: Float = 0f,
    val boundingBox: BoundingBoxData = BoundingBoxData()
) {
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "label" to label,
            "confidence" to confidence,
            "boundingBox" to boundingBox.toMap()
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): DetectionResult {
            return DetectionResult(
                label = map["label"] as? String ?: "",
                confidence = (map["confidence"] as? Double)?.toFloat() ?: 0f,
                boundingBox = (map["boundingBox"] as? Map<String, Any>)?.let {
                    BoundingBoxData.fromMap(it)
                } ?: BoundingBoxData()
            )
        }
    }
}

data class BoundingBoxData(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "left" to left,
            "top" to top,
            "right" to right,
            "bottom" to bottom
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): BoundingBoxData {
            return BoundingBoxData(
                left = (map["left"] as? Double)?.toFloat() ?: 0f,
                top = (map["top"] as? Double)?.toFloat() ?: 0f,
                right = (map["right"] as? Double)?.toFloat() ?: 0f,
                bottom = (map["bottom"] as? Double)?.toFloat() ?: 0f
            )
        }
    }
}
