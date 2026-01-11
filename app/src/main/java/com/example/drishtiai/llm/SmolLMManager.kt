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

package com.example.drishtiai.llm

import android.util.Log
import com.example.drishtiai.smollm.SmolLM
import com.example.drishtiai.data.AppDB
import com.example.drishtiai.data.Chat
import kotlinx.coroutines.*
import org.koin.core.annotation.Single
import kotlin.time.measureTime

private const val LOGTAG = "[SmolLMManager-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

@Single
class SmolLMManager(private val appDB: AppDB) {
    val smolLM = SmolLM()
    private var responseGenerationJob: Job? = null
    private var modelInitJob: Job? = null
    private var chat: Chat? = null
    private var isInstanceLoaded = false
    var isInferenceOn = false

    data class SmolLMResponse(
        val response: String,
        val generationSpeed: Float,
        val generationTimeSecs: Int,
        val contextLengthUsed: Int,
    )

    fun load(
        chat: Chat,
        modelPath: String,
        params: SmolLM.InferenceParams = SmolLM.InferenceParams(),
        onError: (Exception) -> Unit,
        onSuccess: () -> Unit,
    ) {
        this.chat = chat
        modelInitJob?.cancel()
        modelInitJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                if (isInstanceLoaded) {
                    smolLM.close()
                }
                smolLM.load(modelPath, params)
                LOGD("Model loaded")
                if (chat.systemPrompt.isNotEmpty()) {
                    smolLM.addSystemPrompt(chat.systemPrompt)
                }
                if (!chat.isTask) {
                    appDB.getMessagesForModel(chat.id).forEach { message ->
                        if (message.isUserMessage) {
                            smolLM.addUserMessage(message.message)
                        } else {
                            smolLM.addAssistantMessage(message.message)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    isInstanceLoaded = true
                    onSuccess()
                }
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun getResponse(
        query: String,
        responseTransform: (String) -> String,
        onPartialResponseGenerated: (String) -> Unit,
        onSuccess: (SmolLMResponse) -> Unit,
        onCancelled: (String) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        stopResponseGeneration()
        responseGenerationJob = CoroutineScope(Dispatchers.Default).launch {
            var response = ""
            try {
                isInferenceOn = true
                val duration = measureTime {
                    smolLM.getResponseAsFlow(query).collect { piece ->
                        response += piece
                        withContext(Dispatchers.Main) { onPartialResponseGenerated(response) }
                    }
                }
                val transformedResponse = responseTransform(response)
                withContext(Dispatchers.Main) {
                    isInferenceOn = false
                    onSuccess(
                        SmolLMResponse(
                            response = transformedResponse,
                            generationSpeed = smolLM.getResponseGenerationSpeed(),
                            generationTimeSecs = duration.inWholeSeconds.toInt(),
                            contextLengthUsed = smolLM.getContextLengthUsed(),
                        )
                    )
                }
            } catch (e: CancellationException) {
                isInferenceOn = false
                val transformedResponse = responseTransform(response)
                withContext(Dispatchers.Main) { onCancelled(transformedResponse) }
            } catch (e: Exception) {
                isInferenceOn = false
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun getResponseMultimodal(
        query: String,
        onPartialResponseGenerated: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onCancelled: (String) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        stopResponseGeneration()
        responseGenerationJob = CoroutineScope(Dispatchers.Default).launch {
            var fullResponse = ""
            try {
                isInferenceOn = true
                if (smolLM.frameCount() == 0) {
                    throw Exception("No video frames available. Please wait for camera to capture.")
                }
                val chatBuilt = smolLM.buildVideoChat(query)
                if (!chatBuilt) {
                    throw Exception("Failed to build multimodal chat")
                }
                smolLM.getMultimodalResponseAsFlow().collect { piece ->
                    fullResponse += piece
                    withContext(Dispatchers.Main) { onPartialResponseGenerated(fullResponse) }
                }
                withContext(Dispatchers.Main) {
                    isInferenceOn = false
                    onSuccess(fullResponse)
                }
            } catch (e: CancellationException) {
                isInferenceOn = false
                withContext(Dispatchers.Main) { onCancelled(fullResponse) }
            } catch (e: Exception) {
                isInferenceOn = false
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    suspend fun loadVideoModel(modelPath: String, mmprojPath: String) = withContext(Dispatchers.IO) {
        modelInitJob?.cancel()
        if (isInstanceLoaded) {
            smolLM.close()
        }
        smolLM.loadVideoModel(modelPath, mmprojPath)
        isInstanceLoaded = true
    }

    fun addVideoFrameRGB(rgbData: ByteArray, width: Int, height: Int) {
        smolLM.addVideoFrameRGB(rgbData, width, height)
    }

    fun clearFrames() {
        smolLM.clearFrames()
    }
    
    fun getFrameCount(): Int = smolLM.frameCount()

    fun stopResponseGeneration() {
        responseGenerationJob?.cancel()
        isInferenceOn = false
    }

    fun close() {
        stopResponseGeneration()
        modelInitJob?.cancel()
        smolLM.close()
        isInstanceLoaded = false
    }
}
