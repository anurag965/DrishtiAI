package com.example.drishtiai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

class VideoFrameExtractor(private val context: Context) {

    fun extractFrames(
        videoPath: String,
        numFrames: Int = 8,
        targetSize: Int = 384,
    ): List<ByteArray> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoPath)

        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

        val frames = mutableListOf<ByteArray>()
        if (durationMs <= 0L) {
            retriever.release()
            return frames
        }

        val step = durationMs / numFrames.toLong()

        for (i in 0 until numFrames) {
            val timeUs = (i * step) * 1000L
            val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: continue

            val resized = Bitmap.createScaledBitmap(bmp, targetSize, targetSize, true)
            frames.add(bitmapToRGB(resized))

            bmp.recycle()
            resized.recycle()
        }

        retriever.release()
        return frames
    }

    private fun bitmapToRGB(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val rgb = ByteArray(w * h * 3)
        var idx = 0
        for (p in pixels) {
            rgb[idx++] = ((p shr 16) and 0xFF).toByte()
            rgb[idx++] = ((p shr 8) and 0xFF).toByte()
            rgb[idx++] = (p and 0xFF).toByte()
        }
        return rgb
    }
}
