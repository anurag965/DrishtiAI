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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.example.drishtiai.llm.SmolLMManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private const val LOGTAG = "[VideoCaptionActivity-Kt]"

class VideoCaptionActivity : AppCompatActivity() {

    private lateinit var selectMediaButton: Button
    private lateinit var generateButton: Button
    private lateinit var queryEditText: EditText
    private lateinit var imageView: ImageView
    private lateinit var captionTextView: TextView
    private lateinit var progressBar: ProgressBar

    private val smolLMManager by inject<SmolLMManager>()
    private val videoFrameExtractor by lazy { VideoFrameExtractor(this) }

    private var selectedMediaUri: Uri? = null
    private val numFrames = 8
    private val frameSize = 384

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_caption)

        selectMediaButton = findViewById(R.id.select_video_button)
        generateButton = findViewById(R.id.generate_button)
        queryEditText = findViewById(R.id.query_edit_text)
        imageView = findViewById(R.id.image_view)
        captionTextView = findViewById(R.id.caption_text_view)
        progressBar = findViewById(R.id.progress_bar)

        selectMediaButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
            startActivityForResult(intent, 101)
        }

        generateButton.setOnClickListener {
            val uri = selectedMediaUri
            val query = queryEditText.text.toString()
            
            if (uri == null) {
                Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (query.isEmpty()) {
                Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            processMedia(uri, query)
        }

        loadModel()
    }

    private fun loadModel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelPath = intent.getStringExtra("model_path")
                val mmProjPath = intent.getStringExtra("mmproj_path")
                if (modelPath != null && mmProjPath != null) {
                    smolLMManager.loadVideoModel(modelPath, mmProjPath)
                }
            } catch (e: Exception) {
                Log.e(LOGTAG, "Error loading model", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            selectedMediaUri = data?.data
            captionTextView.text = "Video selected. Now enter a question and click Generate."
            
            // Show a preview frame if it's a video
            selectedMediaUri?.let { uri ->
                val mimeType = contentResolver.getType(uri)
                if (mimeType?.startsWith("video/") == true) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val frames = videoFrameExtractor.extractFrames(uri, 1, frameSize)
                        if (frames.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                imageView.setImageBitmap(rgbToBitmap(frames[0], frameSize, frameSize))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processMedia(uri: Uri, query: String) {
        captionTextView.text = "Processing..."
        generateButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                smolLMManager.clearFrames()
                val mimeType = contentResolver.getType(uri)
                val isVideo = mimeType?.startsWith("video/") == true

                if (isVideo) {
                    val frames = videoFrameExtractor.extractFrames(uri, numFrames, frameSize)
                    for (frame in frames) {
                        smolLMManager.addVideoFrameRGB(frame, frameSize, frameSize)
                    }
                } else {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    val scaled = Bitmap.createScaledBitmap(bitmap, frameSize, frameSize, false)
                    smolLMManager.addVideoFrameRGB(bitmapToRgb(scaled), frameSize, frameSize)
                }

                smolLMManager.getResponseMultimodal(
                    query,
                    onPartialResponseGenerated = { partial ->
                        captionTextView.text = partial
                    },
                    onSuccess = { full ->
                        captionTextView.text = full
                    },
                    onCancelled = { final -> captionTextView.text = final },
                    onError = { e -> captionTextView.text = "Error: ${e.message}" }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { captionTextView.text = "Error: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) {
                    generateButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun bitmapToRgb(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bytes = ByteArray(bitmap.width * bitmap.height * 3)
        for (i in pixels.indices) {
            bytes[i * 3] = ((pixels[i] shr 16) and 0xFF).toByte()
            bytes[i * 3 + 1] = ((pixels[i] shr 8) and 0xFF).toByte()
            bytes[i * 3 + 2] = (pixels[i] and 0xFF).toByte()
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
}
