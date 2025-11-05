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

    fun detect(bitmap: Bitmap, confidenceThreshold: Float = 0.5f): List<Detection> {
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

            val numChannels = outputShape[1] // Should be 11 (4 bbox + 1 obj + 6 classes)
            val numPredictions = outputShape[2] // Should be 2100

            // Prepare output array with correct shape
            val output = Array(1) { Array(numChannels) { FloatArray(numPredictions) } }

            // Run inference
            interpreter?.run(inputBuffer, output)

            // Parse YOLOv8 output format
            // Output format: [batch, channels, predictions]
            // channels = [x, y, w, h, objectness, class1, class2, ..., classN]
            val predictions = output[0]

            for (i in 0 until numPredictions) {
                // Get bounding box coordinates (center format)
                val x = predictions[0][i]
                val y = predictions[1][i]
                val w = predictions[2][i]
                val h = predictions[3][i]

                // Get objectness score (if your model has it at index 4)
                // Some YOLOv8 models don't have objectness, skip to class scores
                val hasObjectness = numChannels == labels.size + 5
                val classStartIndex = if (hasObjectness) 5 else 4

                // Find max class score and index
                var maxScore = 0f
                var maxClassIndex = 0

                for (j in classStartIndex until numChannels) {
                    val classScore = predictions[j][i]
                    if (classScore > maxScore) {
                        maxScore = classScore
                        maxClassIndex = j - classStartIndex
                    }
                }

                // Apply confidence threshold
                if (maxScore >= confidenceThreshold) {
                    // Convert from center format to corner format
                    // Scale back to original image size
                    val scaleX = bitmap.width.toFloat() / inputSize
                    val scaleY = bitmap.height.toFloat() / inputSize

                    val left = (x - w / 2) * scaleX
                    val top = (y - h / 2) * scaleY
                    val right = (x + w / 2) * scaleX
                    val bottom = (y + h / 2) * scaleY

                    // Clamp to image boundaries
                    val boundingBox = RectF(
                        max(0f, left),
                        max(0f, top),
                        min(bitmap.width.toFloat(), right),
                        min(bitmap.height.toFloat(), bottom)
                    )

                    // Get label
                    val label = if (maxClassIndex < labels.size) {
                        labels[maxClassIndex]
                    } else {
                        "Class $maxClassIndex"
                    }

                    detections.add(Detection(label, maxScore, boundingBox))
                }
            }

            resizedBitmap.recycle()

            // Apply Non-Maximum Suppression (NMS) to remove duplicate detections
            val filteredDetections = applyNMS(detections, iouThreshold = 0.45f)

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

                // Normalize pixel values to [0, 1]
                byteBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((value and 0xFF) / 255.0f))
            }
        }

        return byteBuffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}