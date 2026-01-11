package com.example.drishtiai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type

/**
 * Helper class used to convert a YUV image to RGB
 *
 * This class is a convenience wrapper for RenderScript intrinsic YuvToRGB.
 * It is designed to be used with CameraX ImageAnalysis use case.
 *
 * This class has been copied from Google's CameraX samples
 * https://github.com/android/camera-samples/blob/main/CameraX-ML-Kit/app/src/main/java/com/android/example/camerax/mlkit/YuvToRgbConverter.kt
 */
class YuvToRgbConverter(
    context: Context,
) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    // Do not add private to this variable, it is used in the FrameAnalyzer
    // to check if the class has been initialized
    private lateinit var yuvType: Type
    private lateinit var yuvAllocation: Allocation
    private lateinit var rgbAllocation: Allocation
    private var yuvLen = 0

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {
        if (!::yuvType.isInitialized) {
            yuvLen = image.planes[0].buffer.capacity()
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvLen).create()
            yuvAllocation = Allocation.createTyped(rs, yuvType, Allocation.USAGE_SCRIPT)
            val rgbType =
                Type.Builder(rs, Element.RGBA_8888(rs))
                    .setX(image.width)
                    .setY(image.height)
                    .create()
            rgbAllocation = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT)
        }

        val yuvBuffer = image.planes[0].buffer
        yuvBuffer.rewind()
        val yuvData = ByteArray(yuvLen)
        yuvBuffer.get(yuvData)
        yuvAllocation.copyFrom(yuvData)

        scriptYuvToRgb.setInput(yuvAllocation)
        scriptYuvToRgb.forEach(rgbAllocation)

        rgbAllocation.copyTo(output)
    }
}
