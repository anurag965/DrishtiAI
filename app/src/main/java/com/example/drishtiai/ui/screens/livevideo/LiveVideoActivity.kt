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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.drishtiai.ui.screens.chat.ChatScreenViewModel
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors

class LiveVideoActivity : ComponentActivity() {

    private val isPermissionGranted = mutableStateOf(false)
    private val viewModel: ChatScreenViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isPermissionGranted.value = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            LiveVideoScreen(
                isPermissionGranted = isPermissionGranted.value,
                viewModel = viewModel
            )
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        isPermissionGranted.value = isGranted
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVideoScreen(
    isPermissionGranted: Boolean,
    viewModel: ChatScreenViewModel
) {
    var query by remember { mutableStateOf("") }
    var isInferring by remember { mutableStateOf(false) }
    val partialResponse by viewModel.partialResponse.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Live Video Inference") })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isPermissionGranted) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    isInferring = isInferring
                )
            } else {
                Text("Camera permission denied", modifier = Modifier.align(Alignment.Center))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (partialResponse.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = partialResponse,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Enter your query") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isInferring = !isInferring
                        if (isInferring) {
                            viewModel.setLiveQuery(query)
                        } else {
                            viewModel.stopGeneration()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isInferring) "Stop Inference" else "Start Inference")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: ChatScreenViewModel,
    isInferring: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(cameraProviderFuture, isInferring) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FrameAnalyzer(context) { bitmap ->
                    if (isInferring) {
                        viewModel.onLiveFrame(bitmap)
                    }
                })
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Use case binding failed", e)
        }
    }

    AndroidView({ previewView }, modifier = modifier)
}
