package com.example.strawberry2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class ObjectDetector(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels: List<String>
    private val inputSize = 320 // Match your training size

    data class Detection(
        val label: String,
        val score: Float,
        val boundingBox: RectF
    )

    init {
        try {
            // Load TFLite model
            val model = FileUtil.loadMappedFile(context, "model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                // Uncomment to use GPU (if available)
                // addDelegate(GpuDelegate())
            }
            interpreter = Interpreter(model, options)

            // Load labels
            labels = FileUtil.loadLabels(context, "labels.txt")

            // Print model input/output info for debugging
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            println("Model Input Shape: ${inputShape?.contentToString()}")
            println("Model Output Shape: ${outputShape?.contentToString()}")
            println("Number of classes: ${labels.size}")
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Error loading model: ${e.message}")
        }
    }

    fun detect(bitmap: Bitmap, confidenceThreshold: Float = 0.75f): List<Detection> {
        val detections = mutableListOf<Detection>()

        try {
            // Resize image to model input size
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

            // Convert bitmap to ByteBuffer
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

            // Get output shape dynamically
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            if (outputShape == null || outputShape.size < 3) {
                throw RuntimeException("Invalid output shape")
            }

            // The model output shape tells us the format:
            // [1, channels, predictions] (e.g. [1, 11, 2100]) or [1, predictions, channels] (e.g. [1, 2100, 11])
            // Always allocate to match the model's actual shape so TensorFlow Lite can copy the data.
            val outChannels = outputShape[1]
            val outPredictions = outputShape[2]
            val output = Array(1) { Array(outChannels) { FloatArray(outPredictions) } }

            // Run inference
            interpreter?.run(inputBuffer, output)

            // Determine actual semantics:
            // [1, channels, predictions] → outChannels=11, outPredictions=2100 (channels first)
            // [1, predictions, channels] → outChannels=2100, outPredictions=11 (predictions first)
            val predictionsFirst = outChannels > outPredictions
            val numChannels = if (predictionsFirst) outPredictions else outChannels
            val numPredictions = if (predictionsFirst) outChannels else outPredictions

            // Parse YOLOv8 output format
            // channels = [x, y, w, h, objectness, class1, class2, ..., classN]
            val raw = output[0]

            for (i in 0 until numPredictions) {
                // Get bounding box coordinates (center format)
                val x = if (predictionsFirst) raw[i][0] else raw[0][i]
                val y = if (predictionsFirst) raw[i][1] else raw[1][i]
                val w = if (predictionsFirst) raw[i][2] else raw[2][i]
                val h = if (predictionsFirst) raw[i][3] else raw[3][i]

                // Determine class start index
                val hasObjectness = numChannels == labels.size + 5
                val classStartIndex = if (hasObjectness) 5 else 4

                // Find max class score and index
                var maxScore = 0f
                var maxClassIndex = 0

                for (j in classStartIndex until numChannels) {
                    val classScore = if (predictionsFirst) raw[i][j] else raw[j][i]
                    if (classScore > maxScore) {
                        maxScore = classScore
                        maxClassIndex = j - classStartIndex
                    }
                }

                // Debug: log ALL class scores for non-trivial predictions
                if (maxScore > 0.1f && i < 20) {
                    val scores = (classStartIndex until numChannels).joinToString(", ") { j ->
                        val score = if (predictionsFirst) raw[i][j] else raw[j][i]
                        val idx = j - classStartIndex
                        "${labels.getOrNull(idx) ?: "c$idx"}=${"%.2f".format(score)}"
                    }
                    println("DEBUG RAW: pred=$i best=${labels.getOrNull(maxClassIndex)} score=${"%.3f".format(maxScore)} | $scores")
                }

                // Use the caller-supplied threshold for all classes uniformly.
                // The per-class cap (max 2 per class after NMS) prevents spam.
                // Missing a real disease is worse than an occasional false positive.
                val detectionLabel = labels.getOrNull(maxClassIndex) ?: ""
                val effectiveThreshold = confidenceThreshold

                if (maxScore >= effectiveThreshold) {
                    val topClass = detectionLabel.ifEmpty { "class_$maxClassIndex" }
                    println("DEBUG DETECT: class=$topClass score=${"%.3f".format(maxScore)} thresh=${"%.2f".format(effectiveThreshold)}")

                    val scaleX = bitmap.width.toFloat() / inputSize
                    val scaleY = bitmap.height.toFloat() / inputSize

                    val left   = (x - w / 2) * scaleX
                    val top    = (y - h / 2) * scaleY
                    val right  = (x + w / 2) * scaleX
                    val bottom = (y + h / 2) * scaleY

                    val boundingBox = RectF(
                        max(0f, left),
                        max(0f, top),
                        min(bitmap.width.toFloat(), right),
                        min(bitmap.height.toFloat(), bottom)
                    )

                    detections.add(Detection(topClass, maxScore, boundingBox))
                }
            }

            resizedBitmap.recycle()

            // Debug: log if no detections found
            if (detections.isEmpty()) {
                println("DEBUG DETECT: shape=[1,$outChannels,$outPredictions] channels=$numChannels predictions=$numPredictions predFirst=$predictionsFirst threshold=$confidenceThreshold")
                println("DEBUG DETECT: raw[0]=${raw[0].take(12).joinToString()}")
            }

            // Apply Non-Maximum Suppression (NMS) to remove duplicate detections
            val nmsDetections = applyNMS(detections, iouThreshold = 0.30f)

            // Cap at max 2 detections per class to prevent spam on repetitive images
            val filteredDetections = nmsDetections
                .groupBy { it.label }
                .flatMap { (_, group) -> group.take(2) }
                .sortedByDescending { it.score }

            return filteredDetections

        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Error during detection: ${e.message}")
        }
    }

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        // Sort by confidence score (descending)
        val sortedDetections = detections.sortedByDescending { it.score }.toMutableList()
        val selectedDetections = mutableListOf<Detection>()

        while (sortedDetections.isNotEmpty()) {
            val best = sortedDetections.removeAt(0)
            selectedDetections.add(best)

            // Remove detections with high IoU
            sortedDetections.removeAll { detection ->
                calculateIoU(best.boundingBox, detection.boundingBox) > iouThreshold &&
                        best.label == detection.label
            }
        }

        return selectedDetections
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        val intersectionWidth = max(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                // Normalize pixel values to [0, 1] — RGB order (standard for YOLOv8)
                // Android getPixels() returns ARGB: bits 16-23 = R, 8-15 = G, 0-7 = B
                byteBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f)) // Red
                byteBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))  // Green
                byteBuffer.putFloat(((value and 0xFF) / 255.0f))         // Blue
            }
        }

        return byteBuffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}