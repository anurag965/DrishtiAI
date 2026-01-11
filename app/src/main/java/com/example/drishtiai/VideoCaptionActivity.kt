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

package com.example.drishtiai

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.example.drishtiai.smollm.SmolLM
import com.example.drishtiai.llm.SmolLMManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.nio.ByteBuffer

private const val LOGTAG = "[VideoCaptionActivity-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

class VideoCaptionActivity : AppCompatActivity() {

    private lateinit var selectMediaButton: Button
    private lateinit var imageView: ImageView
    private lateinit var captionTextView: TextView
    private lateinit var progressBar: ProgressBar

    private val smolLMManager by inject<SmolLMManager>()
    private val videoFrameExtractor by lazy { VideoFrameExtractor(this) }

    private val numFrames = 8
    private val frameSize = 384

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_caption)

        selectMediaButton = findViewById(R.id.select_video_button)
        imageView = findViewById(R.id.image_view)
        captionTextView = findViewById(R.id.caption_text_view)
        progressBar = findViewById(R.id.progress_bar)

        selectMediaButton.text = "Select Image or Video"
        selectMediaButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
            startActivityForResult(intent, 101)
        }

        loadModel()
    }

    private fun loadModel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelPath = intent.getStringExtra("model_path")
                val mmProjPath = intent.getStringExtra("mmproj_path")
                LOGD("Received modelPath: $modelPath, mmProjPath: $mmProjPath")

                if (modelPath != null && mmProjPath != null && mmProjPath.isNotEmpty()) {
                    smolLMManager.loadVideoModel(modelPath, mmProjPath)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VideoCaptionActivity,
                            "VLM Model loaded successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VideoCaptionActivity,
                            "Model paths not provided or invalid.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VideoCaptionActivity,
                        "Model load error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                val mimeType = contentResolver.getType(uri)
                if (mimeType?.startsWith("video/") == true) {
                    generateCaptionForVideo(uri)
                } else if (mimeType?.startsWith("image/") == true) {
                    generateCaptionForImage(uri)
                } else {
                    Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateCaptionForImage(uri: Uri) {
        captionTextView.text = "Processing image..."
        selectMediaButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, frameSize, frameSize, false)

                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(scaledBitmap)
                }

                val byteArray = bitmapToRgb(scaledBitmap)

                smolLMManager.clearFrames()
                smolLMManager.addVideoFrameRGB(byteArray, frameSize, frameSize)

                val prompt = "Describe this image in detail."
                withContext(Dispatchers.Main) {
                    captionTextView.text = "Generating description..."
                }

                smolLMManager.getResponseMultimodal(
                    prompt,
                    onPartialResponseGenerated = { partial ->
                        captionTextView.text = partial
                    },
                    onSuccess = { full ->
                        captionTextView.text = full
                    },
                    onCancelled = { final ->
                        captionTextView.text = final
                    },
                    onError = { e ->
                        captionTextView.text = "Error: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    captionTextView.text = "Error: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    selectMediaButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun generateCaptionForVideo(uri: Uri) {
        captionTextView.text = "Processing video..."
        selectMediaButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val videoPath = getRealPathFromURI(uri)
                if (videoPath == null) {
                    withContext(Dispatchers.Main) {
                        captionTextView.text = "Error: Could not access video file"
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    captionTextView.text = "Extracting frames..."
                    imageView.setImageBitmap(null)
                }

                val frames = videoFrameExtractor.extractFrames(videoPath, numFrames, frameSize)

                if (frames.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        captionTextView.text = "Error: Could not extract frames from video"
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    captionTextView.text = "Building multimodal chat..."
                    imageView.setImageBitmap(rgbToBitmap(frames[0], frameSize, frameSize))
                }

                smolLMManager.clearFrames()

                for (frameRgb in frames) {
                    smolLMManager.addVideoFrameRGB(frameRgb, frameSize, frameSize)
                }

                val prompt = "Describe what is happening in this video in detail."
                withContext(Dispatchers.Main) {
                    captionTextView.text = "Generating description..."
                }

                smolLMManager.getResponseMultimodal(
                    prompt,
                    onPartialResponseGenerated = { partial ->
                        captionTextView.text = partial
                    },
                    onSuccess = { full ->
                        captionTextView.text = full
                    },
                    onCancelled = { final ->
                        captionTextView.text = final
                    },
                    onError = { e ->
                        captionTextView.text = "Error: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    captionTextView.text = "Error: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    selectMediaButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun bitmapToRgb(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bytes = ByteArray(width * height * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            bytes[i * 3] = ((pixel shr 16) and 0xFF).toByte() // Red
            bytes[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte() // Green
            bytes[i * 3 + 2] = (pixel and 0xFF).toByte() // Blue
        }
        return bytes
    }

    private fun rgbToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = bytes[i * 3].toInt() and 0xFF
            val g = bytes[i * 3 + 1].toInt() and 0xFF
            val b = bytes[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            uri.path
        }
    }
}
