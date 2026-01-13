/*
 * Copyright (C) 2024 Shubham Panchal
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

package com.example.drishtiai.ui.screens.chat

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import com.example.drishtiai.smollm.SmolLM
import com.example.drishtiai.R
import com.example.drishtiai.VideoFrameExtractor
import com.example.drishtiai.data.AppDB
import com.example.drishtiai.data.Chat
import com.example.drishtiai.data.ChatMessage
import com.example.drishtiai.data.Folder
import com.example.drishtiai.llm.ModelsRepository
import com.example.drishtiai.llm.SmolLMManager
import com.example.drishtiai.prism4j.PrismGrammarLocator
import com.example.drishtiai.ui.components.createAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.pow

data class DetectedCrisis(
    val className: String,
    val boundingBox: List<Float> // [x1, y1, x2, y2] normalized 0-1000
)

private const val LOGTAG = "[SmolLMAndroid-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

sealed class ChatScreenUIEvent {
    data object Idle : ChatScreenUIEvent()

    sealed class DialogEvents {
        data class ToggleChangeFolderDialog(val visible: Boolean) : ChatScreenUIEvent()

        data class ToggleSelectModelListDialog(val visible: Boolean) : ChatScreenUIEvent()

        data class ToggleMoreOptionsPopup(val visible: Boolean) : ChatScreenUIEvent()

        data class ToggleTaskListBottomList(val visible: Boolean) : ChatScreenUIEvent()
    }
}

@KoinViewModel
class ChatScreenViewModel(
    val context: Context,
    val appDB: AppDB,
    val modelsRepository: ModelsRepository,
    val smolLMManager: SmolLMManager,
) : ViewModel() {
    enum class ModelLoadingState {
        NOT_LOADED, // model loading not started
        IN_PROGRESS, // model loading in-progress
        SUCCESS, // model loading finished successfully
        FAILURE, // model loading failed
    }

    // UI state variables
    private val _currChatState = MutableStateFlow<Chat?>(null)
    val currChatState: StateFlow<Chat?> = _currChatState

    private val _isGeneratingResponse = MutableStateFlow(false)
    val isGeneratingResponse: StateFlow<Boolean> = _isGeneratingResponse

    private val _modelLoadState = MutableStateFlow(ModelLoadingState.NOT_LOADED)
    val modelLoadState: StateFlow<ModelLoadingState> = _modelLoadState

    private val _partialResponse = MutableStateFlow("")
    val partialResponse: StateFlow<String> = _partialResponse

    private val _uiEvent = MutableStateFlow(ChatScreenUIEvent.Idle)
    val uiEvent: StateFlow<ChatScreenUIEvent> = _uiEvent

    private val _showChangeFolderDialogState = MutableStateFlow(false)
    val showChangeFolderDialogState: StateFlow<Boolean> = _showChangeFolderDialogState

    private val _showSelectModelListDialogState = MutableStateFlow(false)
    val showSelectModelListDialogState: StateFlow<Boolean> = _showSelectModelListDialogState

    private val _showMoreOptionsPopupState = MutableStateFlow(false)
    val showMoreOptionsPopupState: StateFlow<Boolean> = _showMoreOptionsPopupState

    private val _showTaskListBottomListState = MutableStateFlow(false)
    val showTaskListBottomListState: StateFlow<Boolean> = _showTaskListBottomListState

    private val _showRAMUsageLabel = MutableStateFlow(false)
    val showRAMUsageLabel: StateFlow<Boolean> = _showRAMUsageLabel

    private val _videoPreviewBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val videoPreviewBitmaps: StateFlow<List<Bitmap>> = _videoPreviewBitmaps

    private val _detectedCrises = MutableStateFlow<List<DetectedCrisis>>(emptyList())
    val detectedCrises: StateFlow<List<DetectedCrisis>> = _detectedCrises

    private val _isProcessingMedia = MutableStateFlow(false)
    val isProcessingMedia: StateFlow<Boolean> = _isProcessingMedia

    private val _mediaProcessingProgressText = MutableStateFlow("")
    val mediaProcessingProgressText: StateFlow<String> = _mediaProcessingProgressText

    // Used to pre-set a value in the query text-field of the chat screen
    var questionTextDefaultVal: String? = null

    private val findThinkTagRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    
    // Support formats: [Fight] [100, 200, 300, 400] OR [100, 200, 300, 400]
    private val boundingBoxRegex = Regex("\\[(.*?)\\]\\s*[\\[\\(]?(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)[\\)\\]]?|\\[(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\]")
    
    var responseGenerationsSpeed: Float? = null
    var responseGenerationTimeSecs: Int? = null
    val markwon: Markwon

    private var activityManager: ActivityManager
    private val videoFrameExtractor by lazy { VideoFrameExtractor(context) }
    private val INFERENCE_IMAGE_SIZE = 224 

    init {
        _currChatState.value = appDB.loadDefaultChat()
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val prism4j = Prism4j(PrismGrammarLocator())
        markwon =
            Markwon.builder(context)
                .usePlugin(CorePlugin.create())
                .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(
                    JLatexMathPlugin.create(
                        12f,
                        JLatexMathPlugin.BuilderConfigure {
                            it.inlinesEnabled(true)
                            it.blocksEnabled(true)
                        },
                    )
                )
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            val jetbrainsMonoFont =
                                ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!
                            builder
                                .codeBlockTypeface(
                                    ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!
                                )
                                .codeBlockTextColor(Color.WHITE)
                                .codeBlockTextSize(spToPx(10f))
                                .codeBlockBackgroundColor(Color.BLACK)
                                .codeTypeface(jetbrainsMonoFont)
                                .codeTextSize(spToPx(10f))
                                .codeTextColor(Color.WHITE)
                                .codeBackgroundColor(Color.BLACK)
                                .isLinkUnderlined(true)
                        }
                    }
                )
                .build()
    }

    private fun spToPx(sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
            .toInt()

    fun getChats(): Flow<List<Chat>> = appDB.getChats()
    fun getChatMessages(chatId: Long): Flow<List<ChatMessage>> = appDB.getMessages(chatId)
    fun getFolders(): Flow<List<Folder>> = appDB.getFolders()
    fun getChatsForFolder(folderId: Long): Flow<List<Chat>> = appDB.getChatsForFolder(folderId)

    fun updateChatLLMParams(modelId: Long, chatTemplate: String) {
        _currChatState.value =
            _currChatState.value?.copy(llmModelId = modelId, chatTemplate = chatTemplate)
        appDB.updateChat(_currChatState.value!!)
        loadModel()
    }

    fun updateChatFolder(folderId: Long) {
        appDB.updateChat(_currChatState.value!!.copy(folderId = folderId))
    }

    fun updateChatSettings(
        existingChat: Chat,
        settings: EditableChatSettings
    ) {
        val newChat = settings.toChat(existingChat)
        _currChatState.value = newChat
        appDB.updateChat(newChat)
        loadModel()
    }

    fun deleteMessage(messageId: Long) {
        appDB.deleteMessage(messageId)
    }

    fun deleteChat(chat: Chat) {
        stopGeneration()
        appDB.deleteChat(chat)
        appDB.deleteMessages(chat.id)
        _currChatState.value = null
    }

    fun deleteChatMessages(chat: Chat) {
        stopGeneration()
        appDB.deleteMessages(chat.id)
    }

    fun deleteModel(modelId: Long) {
        modelsRepository.deleteModel(modelId)
        if (_currChatState.value?.llmModelId == modelId) {
            _currChatState.value = _currChatState.value?.copy(llmModelId = -1)
        }
    }

    fun sendUserQuery(query: String, addMessageToDB: Boolean = true, clearResponse: Boolean = true) {
        _currChatState.value?.let { chat ->
            chat.dateUsed = Date()
            appDB.updateChat(chat)

            if (chat.isTask) appDB.deleteMessages(chat.id)
            if (addMessageToDB) appDB.addUserMessage(chat.id, query)
            
            _isGeneratingResponse.value = true
            if (clearResponse) _partialResponse.value = ""
            _detectedCrises.value = emptyList()

            val model = modelsRepository.getModelFromId(chat.llmModelId)
            if (model != null && model.mmProjPath.isNotEmpty()) {
                smolLMManager.getResponseMultimodal(
                    query,
                    onPartialResponseGenerated = {
                        CoroutineScope(Dispatchers.Main).launch {
                            _partialResponse.value = it
                            parseDetectedCrises(it)
                        }
                    },
                    onSuccess = { response ->
                        _isGeneratingResponse.value = false
                        if (addMessageToDB) appDB.addAssistantMessage(chat.id, response)
                        if (clearResponse) _partialResponse.value = ""
                    },
                    onCancelled = { final ->
                        _isGeneratingResponse.value = false
                        if (addMessageToDB && final.isNotEmpty()) appDB.addAssistantMessage(chat.id, final)
                    },
                    onError = { exception ->
                        _isGeneratingResponse.value = false
                        if (addMessageToDB) Toast.makeText(context, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                var accumulatedResponse = ""
                smolLMManager.getResponse(
                    query,
                    responseTransform = { it },
                    onPartialResponseGenerated = {
                        CoroutineScope(Dispatchers.Main).launch {
                            accumulatedResponse = it
                            _partialResponse.value = it
                        }
                    },
                    onSuccess = { response ->
                        _isGeneratingResponse.value = false
                        responseGenerationsSpeed = response.generationSpeed
                        responseGenerationTimeSecs = response.generationTimeSecs
                        appDB.updateChat(chat.copy(contextSizeConsumed = response.contextLengthUsed))
                    },
                    onCancelled = { _isGeneratingResponse.value = false },
                    onError = { _isGeneratingResponse.value = false },
                )
            }
        }
    }

    private fun parseDetectedCrises(text: String) {
        val crises = mutableListOf<DetectedCrisis>()
        boundingBoxRegex.findAll(text).forEach { match ->
            if (match.groupValues[2].isNotEmpty()) {
                // Format: [Class] [x1, y1, x2, y2]
                val className = match.groupValues[1]
                val x1 = match.groupValues[2].toFloat()
                val y1 = match.groupValues[3].toFloat()
                val x2 = match.groupValues[4].toFloat()
                val y2 = match.groupValues[5].toFloat()
                crises.add(DetectedCrisis(className, listOf(x1, y1, x2, y2)))
            } else if (match.groupValues[6].isNotEmpty()) {
                // Format: [x1, y1, x2, y2]
                val x1 = match.groupValues[6].toFloat()
                val y1 = match.groupValues[7].toFloat()
                val x2 = match.groupValues[8].toFloat()
                val y2 = match.groupValues[9].toFloat()
                crises.add(DetectedCrisis("Detection", listOf(x1, y1, x2, y2)))
            }
        }
        _detectedCrises.value = crises
    }

    fun stopGeneration() {
        smolLMManager.stopResponseGeneration()
    }

    fun switchChat(chat: Chat) {
        stopGeneration()
        _currChatState.value = chat
        loadModel()
    }

    fun loadModel(onComplete: (ModelLoadingState) -> Unit = {}) {
        _currChatState.value?.let { chat ->
            val model = modelsRepository.getModelFromId(chat.llmModelId)
            if (chat.llmModelId == -1L || model == null) {
                _showSelectModelListDialogState.value = true
            } else {
                _modelLoadState.value = ModelLoadingState.IN_PROGRESS
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (model.mmProjPath.isNotEmpty()) {
                            smolLMManager.loadVideoModel(model.path, model.mmProjPath)
                            withContext(Dispatchers.Main) {
                                _modelLoadState.value = ModelLoadingState.SUCCESS
                                onComplete(ModelLoadingState.SUCCESS)
                            }
                        } else {
                            smolLMManager.load(
                                chat,
                                model.path,
                                SmolLM.InferenceParams(
                                    minP = chat.minP,
                                    temperature = chat.temperature,
                                    contextSize = chat.contextSize.toLong(),
                                    chatTemplate = chat.chatTemplate,
                                    numThreads = chat.nThreads,
                                    useMmap = chat.useMmap,
                                    useMlock = chat.useMlock,
                                ),
                                onError = { e ->
                                    _modelLoadState.value = ModelLoadingState.FAILURE
                                    onComplete(ModelLoadingState.FAILURE)
                                },
                                onSuccess = {
                                    _modelLoadState.value = ModelLoadingState.SUCCESS
                                    onComplete(ModelLoadingState.SUCCESS)
                                },
                            )
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            _modelLoadState.value = ModelLoadingState.FAILURE
                            onComplete(ModelLoadingState.FAILURE)
                        }
                    }
                }
            }
        }
    }

    fun unloadModel(): Boolean =
        if (!smolLMManager.isInferenceOn) {
            smolLMManager.close()
            _modelLoadState.value = ModelLoadingState.NOT_LOADED
            true
        } else {
            false
        }

    fun getCurrentMemoryUsage(): Pair<Float, Float> {
        val memoryInfo = MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = (memoryInfo.totalMem) / 1024.0.pow(3.0)
        val usedMemory = (memoryInfo.availMem) / 1024.0.pow(3.0)
        return Pair(usedMemory.toFloat(), totalMemory.toFloat())
    }

    @SuppressLint("StringFormatMatches")
    fun showContextLengthUsageDialog() {
        _currChatState.value?.let { chat ->
            createAlertDialog(
                dialogTitle = context.getString(R.string.dialog_ctx_usage_title),
                dialogText =
                context.getString(
                    R.string.dialog_ctx_usage_text,
                    chat.contextSizeConsumed,
                    chat.contextSize,
                ),
                dialogPositiveButtonText = context.getString(R.string.dialog_ctx_usage_close),
                onPositiveButtonClick = {},
                dialogNegativeButtonText = null,
                onNegativeButtonClick = null,
            )
        }
    }

    fun onEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog -> _showSelectModelListDialogState.value = event.visible
            is ChatScreenUIEvent.DialogEvents.ToggleMoreOptionsPopup -> _showMoreOptionsPopupState.value = event.visible
            is ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList -> _showTaskListBottomListState.value = event.visible
            is ChatScreenUIEvent.DialogEvents.ToggleChangeFolderDialog -> _showChangeFolderDialogState.value = event.visible
            else -> {}
        }
    }

    fun toggleRAMUsageLabelVisibility() = run { _showRAMUsageLabel.value = !_showRAMUsageLabel.value }
    fun setVideoPreviewBitmaps(bitmaps: List<Bitmap>) { _videoPreviewBitmaps.value = bitmaps }
    fun setProcessingMedia(processing: Boolean, text: String = "") {
        _isProcessingMedia.value = processing
        _mediaProcessingProgressText.value = text
    }

    fun addVideoFrame(rgbData: ByteArray, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.IO) { smolLMManager.addVideoFrameRGB(rgbData, width, height) }
    }

    fun clearVideoFrames() {
        viewModelScope.launch(Dispatchers.IO) {
            smolLMManager.clearFrames()
            _videoPreviewBitmaps.value = emptyList()
            _detectedCrises.value = emptyList()
            lastFrameThumbnail = null
            lastStaticFrameThumbnail = null
        }
    }

    private val _liveQuery = mutableStateOf("")
    private var lastFrameTime = 0L
    private var lastFrameThumbnail: ByteArray? = null
    private var lastStaticFrameThumbnail: ByteArray? = null
    private var staticFrameStartTime = 0L
    private val isProcessingFrame = AtomicBoolean(false)

    fun setLiveQuery(query: String) { _liveQuery.value = query }

    fun onLiveFrame(bitmap: Bitmap) {
        if (_isGeneratingResponse.value) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime < 150) return 
        lastFrameTime = currentTime

        if (isProcessingFrame.compareAndSet(false, true)) {
            viewModelScope.launch {
                try {
                    val currentThumbnail = withContext(Dispatchers.Default) { getThumbnail(bitmap) }
                    val isMoving = lastStaticFrameThumbnail != null && !isSimilar(currentThumbnail, lastStaticFrameThumbnail!!, threshold = 0.15)
                    
                    if (isMoving) {
                        staticFrameStartTime = currentTime
                        lastStaticFrameThumbnail = currentThumbnail
                        return@launch
                    }

                    val stillnessDuration = currentTime - staticFrameStartTime
                    if (stillnessDuration > 150) {
                        val isNewScene = lastFrameThumbnail == null || !isSimilar(currentThumbnail, lastFrameThumbnail!!, threshold = 0.20)
                        if (isNewScene) {
                            val scaledBitmap = withContext(Dispatchers.Default) {
                                Bitmap.createScaledBitmap(bitmap, INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE, false)
                            }
                            val result = withContext(Dispatchers.Default) { bitmapToRgb(scaledBitmap) }

                            withContext(Dispatchers.IO) {
                                lastFrameThumbnail = currentThumbnail
                                staticFrameStartTime = currentTime 
                                val currentFrames = _videoPreviewBitmaps.value.toMutableList()
                                if (currentFrames.size >= 4) {
                                    currentFrames.removeAt(0)
                                    smolLMManager.clearFrames()
                                    currentFrames.forEach { bmp ->
                                        smolLMManager.addVideoFrameRGB(bitmapToRgb(bmp), INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE)
                                    }
                                }
                                currentFrames.add(scaledBitmap)
                                _videoPreviewBitmaps.value = currentFrames
                                smolLMManager.addVideoFrameRGB(result, INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE)
                            }
                        }
                    }
                    lastStaticFrameThumbnail = currentThumbnail
                } finally {
                    isProcessingFrame.set(false)
                }
            }
        }
    }

    fun processVideoFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { setProcessingMedia(true, "Extracting keyframes...") }
                smolLMManager.clearFrames()
                val frames = videoFrameExtractor.extractFrames(uri, numFrames = 4, targetSize = INFERENCE_IMAGE_SIZE)
                if (frames.isNotEmpty()) {
                    val bitmaps = frames.map { rgbToBitmap(it, INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE) }
                    withContext(Dispatchers.Main) { setVideoPreviewBitmaps(bitmaps) }
                    for (frame in frames) smolLMManager.addVideoFrameRGB(frame, INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE)
                    withContext(Dispatchers.Main) { setProcessingMedia(false, "Video loaded") }
                } else {
                    withContext(Dispatchers.Main) { setProcessingMedia(false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setProcessingMedia(false) }
            }
        }
    }

    fun processImageFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { setProcessingMedia(true, "Processing image...") }
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                val scaled = Bitmap.createScaledBitmap(bitmap, INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE, false)
                smolLMManager.clearFrames()
                smolLMManager.addVideoFrameRGB(bitmapToRgb(scaled), INFERENCE_IMAGE_SIZE, INFERENCE_IMAGE_SIZE)
                withContext(Dispatchers.Main) {
                    setVideoPreviewBitmaps(listOf(scaled))
                    setProcessingMedia(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setProcessingMedia(false) }
            }
        }
    }

    private fun getThumbnail(bitmap: Bitmap, size: Int = 32): ByteArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        return bitmapToRgb(scaled)
    }

    private fun isSimilar(f1: ByteArray, f2: ByteArray, threshold: Double = 0.20): Boolean {
        var diff = 0.0
        for (i in f1.indices) diff += abs((f1[i].toInt() and 0xFF) - (f2[i].toInt() and 0xFF))
        return (diff / (f1.size * 255.0)) < threshold
    }

    private fun bitmapToRgb(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bytes = ByteArray(width * height * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 3] = ((p shr 16) and 0xFF).toByte()
            bytes[i * 3 + 1] = ((p shr 8) and 0xFF).toByte()
            bytes[i * 3 + 2] = (p and 0xFF).toByte()
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
