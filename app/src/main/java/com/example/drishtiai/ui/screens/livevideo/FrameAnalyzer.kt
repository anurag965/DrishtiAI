/*
 * Copyright (C) 2025 Shubham Panchal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.drishtiai.ui.screens.livevideo

import android.content.Context
import android.graphics.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class FrameAnalyzer(
    private val context: Context,
    private val onFrameAnalyzed: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    private var previousFrame: Bitmap? = null
    private var lastFrameTime: Long = 0
    private val frameTimeDelay = 2000 // 2 seconds
    private val differenceThreshold = 25 // Lower this for more sensitivity

    override fun analyze(image: ImageProxy) {
        val currentFrame = image.toBitmap()
        val rotatedFrame = rotateBitmap(currentFrame, image.imageInfo.rotationDegrees.toFloat())

        if (shouldAnalyze(rotatedFrame)) {
            onFrameAnalyzed(rotatedFrame)
            previousFrame = rotatedFrame
            lastFrameTime = System.currentTimeMillis()
        }

        image.close()
    }

    private fun shouldAnalyze(currentFrame: Bitmap): Boolean {
        val currentTime = System.currentTimeMillis()
        if (previousFrame == null || currentTime - lastFrameTime > frameTimeDelay) {
            return true
        }

        val scaledPrevious = previousFrame?.let {
            Bitmap.createScaledBitmap(it, it.width / 4, it.height / 4, true)
        }
        val scaledCurrent = Bitmap.createScaledBitmap(currentFrame, currentFrame.width / 4, currentFrame.height / 4, true)

        val difference = calculateImageDifference(scaledPrevious, scaledCurrent)
        return difference > differenceThreshold
    }

    private fun calculateImageDifference(bitmap1: Bitmap?, bitmap2: Bitmap): Double {
        if (bitmap1 == null) return 255.0
        var diff = 0.0
        for (y in 0 until bitmap1.height) {
            for (x in 0 until bitmap1.width) {
                val pixel1 = bitmap1.getPixel(x, y)
                val pixel2 = bitmap2.getPixel(x, y)
                diff += abs(Color.red(pixel1) - Color.red(pixel2))
                diff += abs(Color.green(pixel1) - Color.green(pixel2))
                diff += abs(Color.blue(pixel1) - Color.blue(pixel2))
            }
        }
        return diff / (bitmap1.width * bitmap1.height * 3)
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
