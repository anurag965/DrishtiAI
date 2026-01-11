package com.example.drishtiai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.drishtiai.llm.ModelsRepository
import com.example.drishtiai.ui.screens.chat.ChatActivity
import com.example.drishtiai.ui.screens.model_download.DownloadModelActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val modelsRepository by inject<ModelsRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoadingScreen {
                // Redirect user to the DownloadModelActivity if no models are available
                // as the app requires at least one model to function
                if (modelsRepository.getAvailableModelsList().isEmpty()) {
                    Intent(this, DownloadModelActivity::class.java).apply {
                        startActivity(this)
                        finish()
                    }
                } else {
                    Intent(this, ChatActivity::class.java).apply {
                        startActivity(this)
                        finish()
                    }
                }
            }
        }
    }

    @Composable
    fun LoadingScreen(onLoadComplete: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    modelsRepository.discoverAndRegisterModels()
                }
                onLoadComplete()
            }
        }
    }
}
