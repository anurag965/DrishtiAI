package com.example.drishtiai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri

class VideoFrameExtractor(private val context: Context) {

    fun extractFrames(
        videoUri: Uri,
        numFrames: Int = 4,
        targetSize: Int = 384,
    ): List<ByteArray> {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        val frames = mutableListOf<ByteArray>()

        try {
            retriever.setDataSource(context, videoUri)
            extractor.setDataSource(context, videoUri, null)

            // Find video track
            var videoTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    break
                }
            }

            if (videoTrackIndex == -1) return emptyList()
            extractor.selectTrack(videoTrackIndex)

            val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.times(1000) ?: 0L

            if (durationUs <= 0) return emptyList()

            // Find actual keyframe positions (Sync Frames)
            val keyframeTimes = mutableListOf<Long>()
            val interval = durationUs / numFrames
            
            for (i in 0 until numFrames) {
                val targetTime = i * interval
                extractor.seekTo(targetTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val actualKeyframeTime = extractor.sampleTime
                if (actualKeyframeTime != -1L && !keyframeTimes.contains(actualKeyframeTime)) {
                    keyframeTimes.add(actualKeyframeTime)
                }
            }

            // Fallback if no keyframes found via extractor
            if (keyframeTimes.isEmpty()) {
                for (i in 0 until numFrames) {
                    keyframeTimes.add(i * (durationUs / numFrames))
                }
            }

            // Extract the bitmaps at those specific keyframe times
            for (timeUs in keyframeTimes) {
                val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue

                val resized = Bitmap.createScaledBitmap(bmp, targetSize, targetSize, true)
                frames.add(bitmapToRGB(resized))
                bmp.recycle()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
                extractor.release()
            } catch (e: Exception) {}
        }
        
        return frames.distinctBy { it.contentHashCode() }.take(numFrames)
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
