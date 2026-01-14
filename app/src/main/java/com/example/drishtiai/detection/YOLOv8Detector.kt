package com.example.drishtiai.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

class YOLOv8Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String? = null,
    private val confidenceThreshold: Float = 0.35f,
    private val iouThreshold: Float = 0.45f
) {
    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()

    private val inputWidth: Int
    private val inputHeight: Int
    private var numChannel: Int = 0
    private var numElements: Int = 0

    init {
        val options = Interpreter.Options().apply { setNumThreads(4) }
        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        if (inputShape[1] > 3) {
            inputHeight = inputShape[1]
            inputWidth = inputShape[2]
        } else {
            inputHeight = inputShape[2]
            inputWidth = inputShape[3]
        }

        val outputShape = interpreter.getOutputTensor(0).shape()
        if (outputShape[1] < outputShape[2]) {
            numChannel = outputShape[1]
            numElements = outputShape[2]
        } else {
            numChannel = outputShape[2]
            numElements = outputShape[1]
        }

        if (labelPath != null) {
            loadLabels(labelPath)
        } else {
            labels.addAll(listOf("person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"))
        }
    }

    private fun loadLabels(path: String) {
        val inputStream = context.assets.open(path)
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()
        while (line != null) {
            labels.add(line)
            line = reader.readLine()
        }
        reader.close()
        inputStream.close()
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        interpreter.run(tensorImage.buffer, outputBuffer.buffer)

        return postProcess(outputBuffer.floatArray, outputShape)
    }

    private fun postProcess(output: FloatArray, shape: IntArray): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numClasses = numChannel - 4
        val isTransposed = shape[1] < shape[2]

        for (i in 0 until numElements) {
            var maxConf = -1f
            var maxIdx = -1

            for (j in 0 until numClasses) {
                val conf = if (isTransposed) output[(j + 4) * numElements + i] else output[i * numChannel + (j + 4)]
                if (conf > maxConf) {
                    maxConf = conf
                    maxIdx = j
                }
            }

            if (maxConf > confidenceThreshold) {
                val cx = if (isTransposed) output[0 * numElements + i] else output[i * numChannel + 0]
                val cy = if (isTransposed) output[1 * numElements + i] else output[i * numChannel + 1]
                val w = if (isTransposed) output[2 * numElements + i] else output[i * numChannel + 2]
                val h = if (isTransposed) output[3 * numElements + i] else output[i * numChannel + 3]

                // YOLOv8 coordinates are usually absolute pixels relative to input (e.g. 640)
                // If they are > 1, divide by input size. If < 1, they might be already normalized.
                val x1Raw = (cx - (w / 2f))
                val y1Raw = (cy - (h / 2f))
                val x2Raw = (cx + (w / 2f))
                val y2Raw = (cy + (h / 2f))

                val x1 = if (cx > 1.1f) x1Raw / inputWidth else x1Raw
                val y1 = if (cy > 1.1f) y1Raw / inputHeight else y1Raw
                val x2 = if (cx > 1.1f) x2Raw / inputWidth else x2Raw
                val y2 = if (cy > 1.1f) y2Raw / inputHeight else y2Raw

                detections.add(
                    Detection(
                        x1 = x1.coerceIn(0f, 1f),
                        y1 = y1.coerceIn(0f, 1f),
                        x2 = x2.coerceIn(0f, 1f),
                        y2 = y2.coerceIn(0f, 1f),
                        label = labels.getOrElse(maxIdx) { "Unknown" },
                        confidence = maxConf
                    )
                )
            }
        }

        return nms(detections)
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()
        val selectedDetections = mutableListOf<Detection>()

        while (sortedDetections.isNotEmpty()) {
            val first = sortedDetections.first()
            selectedDetections.add(first)
            sortedDetections.removeAt(0)

            val iterator = sortedDetections.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(first, next) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedDetections
    }

    private fun calculateIoU(box1: Detection, box2: Detection): Float {
        val x1 = max(box1.x1, box2.x1)
        val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2)
        val y2 = min(box1.y2, box2.y2)

        val intersectionArea = max(0f, x2 - x1) * max(0f, y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    data class Detection(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val label: String,
        val confidence: Float
    )
}
