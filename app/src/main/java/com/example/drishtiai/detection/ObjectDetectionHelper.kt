package com.example.drishtiai.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class ObjectDetectionHelper(
    val context: Context,
    val modelName: String = "yolov8n_float16.tflite", // Use your YOLO model name here
    val threshold: Float = 0.4f,
    val maxResults: Int = 5
) {
    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)

        val baseOptionsBuilder = BaseOptions.builder()
        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
            baseOptionsBuilder.useGpu()
        }
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detect(bitmap: Bitmap, rotation: Int = 0): List<DetectionResult> {
        if (objectDetector == null) setupObjectDetector()

        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotation / 90))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        val results = objectDetector?.detect(tensorImage)

        return results?.map { detection ->
            DetectionResult(
                boundingBox = detection.boundingBox,
                label = detection.categories.firstOrNull()?.label ?: "Unknown",
                score = detection.categories.firstOrNull()?.score ?: 0f
            )
        } ?: emptyList()
    }

    data class DetectionResult(
        val boundingBox: RectF,
        val label: String,
        val score: Float
    )
}
