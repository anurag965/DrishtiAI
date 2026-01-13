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

import CustomNavTypes
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Spanned
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import com.example.drishtiai.R
import com.example.drishtiai.data.Chat
import com.example.drishtiai.data.Task
import com.example.drishtiai.ui.components.*
import com.example.drishtiai.ui.screens.chat.ChatScreenViewModel.ModelLoadingState
import com.example.drishtiai.ui.screens.chat.dialogs.ChangeFolderDialogUI
import com.example.drishtiai.ui.screens.chat.dialogs.ChatMessageOptionsDialog
import com.example.drishtiai.ui.screens.chat.dialogs.ChatMoreOptionsPopup
import com.example.drishtiai.ui.screens.chat.dialogs.createChatMessageOptionsDialog
import com.example.drishtiai.ui.screens.livevideo.FrameAnalyzer
import com.example.drishtiai.ui.screens.manage_tasks.ManageTasksActivity
import com.example.drishtiai.ui.theme.SmolLMAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import java.util.*
import java.util.concurrent.Executors
import kotlin.reflect.typeOf

private const val LOGTAG = "[ChatActivity-Kt]"

@Serializable
object ChatRoute

@Serializable
data class EditChatSettingsRoute(val chat: Chat, val modelContextSize: Int)

class ChatActivity : ComponentActivity() {
    private val viewModel: ChatScreenViewModel by inject()
    private var modelUnloaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()
            SmolLMAndroidTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NavHost(
                        navController = navController,
                        startDestination = ChatRoute,
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                    ) {
                        composable<EditChatSettingsRoute>(
                            typeMap = mapOf(
                                typeOf<Chat>() to CustomNavTypes.ChatNavType
                            )
                        ) { backStackEntry ->
                            val route: EditChatSettingsRoute = backStackEntry.toRoute()
                            val settings = EditableChatSettings.fromChat(route.chat)
                            EditChatSettingsScreen(
                                settings,
                                route.modelContextSize,
                                onUpdateChat = {
                                    viewModel.updateChatSettings(
                                        existingChat = route.chat,
                                        it
                                    )
                                },
                                onBackClicked = { navController.navigateUp() },
                            )
                        }
                        composable<ChatRoute> {
                            ChatActivityScreenUI(
                                viewModel,
                                onEditChatParamsClick = { chat, modelContextSize ->
                                    navController.navigate(
                                        EditChatSettingsRoute(
                                            chat,
                                            modelContextSize,
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (modelUnloaded) {
            viewModel.loadModel()
        }
    }

    override fun onStop() {
        super.onStop()
        modelUnloaded = viewModel.unloadModel()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatActivityScreenUI(
    viewModel: ChatScreenViewModel,
    onEditChatParamsClick: (Chat, Int) -> Unit
) {
    val currChat by viewModel.currChatState.collectAsStateWithLifecycle()
    
    LaunchedEffect(currChat) { viewModel.loadModel() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DrishtiAI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (currChat != null) {
                            val modelName = viewModel.modelsRepository.getModelFromId(currChat!!.llmModelId)?.name ?: ""
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (currChat != null) {
                        Box {
                            IconButton(onClick = { viewModel.onEvent(ChatScreenUIEvent.DialogEvents.ToggleMoreOptionsPopup(visible = true)) }) {
                                Icon(FeatherIcons.MoreVertical, contentDescription = "Options")
                            }
                            ChatMoreOptionsPopup(
                                viewModel,
                                {
                                    onEditChatParamsClick(
                                        currChat!!,
                                        viewModel.modelsRepository.getModelFromId(currChat!!.llmModelId).contextSize,
                                    )
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            MessageInput(viewModel)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (currChat != null) {
                ScreenContent(viewModel, currChat!!)
            }
        }
        
        SelectModelsListWrapper(viewModel)
        TasksListBottomSheet(viewModel)
        ChangeFolderDialog(viewModel)
        TextFieldDialog()
        ChatMessageOptionsDialog()
    }
}

@Composable
private fun ScreenContent(viewModel: ChatScreenViewModel, currChat: Chat) {
    var isLiveCameraEnabled by remember { mutableStateOf(false) }
    var isInferring by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }

    Column(modifier = Modifier.fillMaxSize()) {
        RAMUsageLabel(viewModel)

        // Media Section
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { 
                        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                        else isLiveCameraEnabled = !isLiveCameraEnabled 
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (isLiveCameraEnabled) FeatherIcons.CameraOff else FeatherIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isLiveCameraEnabled) "Close Camera" else "Live Camera")
                }
                
                if (isLiveCameraEnabled) {
                    Button(
                        onClick = { 
                            isInferring = !isInferring
                            if (!isInferring) viewModel.stopGeneration()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInferring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (isInferring) FeatherIcons.StopCircle else FeatherIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isInferring) "Stop" else "Inference")
                    }
                }
            }

            if (isLiveCameraEnabled && hasCameraPermission) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel,
                        isInferring = isInferring
                    )
                    if (isInferring) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(12.dp).background(Color.Red, CircleShape))
                    }
                }
            }

            val previews by viewModel.videoPreviewBitmaps.collectAsStateWithLifecycle()
            val isProcessing by viewModel.isProcessingMedia.collectAsStateWithLifecycle()
            
            if (previews.isNotEmpty() || isProcessing) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    MediaPreview(viewModel)
                }
            }
        }

        MessagesList(modifier = Modifier.weight(1f), viewModel = viewModel, chatId = currChat.id)
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: ChatScreenViewModel,
    isInferring: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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

@Composable
private fun MediaPreview(viewModel: ChatScreenViewModel) {
    val previews by viewModel.videoPreviewBitmaps.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingMedia.collectAsStateWithLifecycle()
    val processingText by viewModel.mediaProcessingProgressText.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (previews.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(previews) { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Keyframe",
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            IconButton(
                onClick = { viewModel.clearVideoFrames() },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(FeatherIcons.X, contentDescription = "Clear", modifier = Modifier.size(16.dp))
            }
        }
        
        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(processingText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}


@Composable
private fun RAMUsageLabel(viewModel: ChatScreenViewModel) {
    val showRAMUsageLabel by viewModel.showRAMUsageLabel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var labelText by remember { mutableStateOf("") }
    LaunchedEffect(showRAMUsageLabel) {
        if (showRAMUsageLabel) {
            while (true) {
                val (used, total) = viewModel.getCurrentMemoryUsage()
                labelText = context.getString(R.string.label_device_ram).format(used, total)
                delay(3000L)
            }
        }
    }
    if (showRAMUsageLabel) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                labelText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MessagesList(
    modifier: Modifier = Modifier,
    viewModel: ChatScreenViewModel,
    chatId: Long,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val messages by viewModel.getChatMessages(chatId).collectAsState(emptyList())
    val lastUserMessageIndex by remember { derivedStateOf { messages.indexOfLast { it.isUserMessage } } }
    val partialResponse by viewModel.partialResponse.collectAsStateWithLifecycle()
    val isGeneratingResponse by viewModel.isGeneratingResponse.collectAsStateWithLifecycle()

    LaunchedEffect(messages.size, isGeneratingResponse) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { i, chatMessage ->
                MessageListItem(
                    viewModel.markwon.render(viewModel.markwon.parse(chatMessage.message)),
                    chatMessage.isUserMessage,
                    onCopyClicked = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Copied message", chatMessage.message))
                        Toast.makeText(context, context.getString(R.string.chat_message_copied), Toast.LENGTH_SHORT).show()
                    },
                    onShareClicked = {
                        context.startActivity(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, chatMessage.message)
                        })
                    },
                    onMessageEdited = { newMessage ->
                        viewModel.deleteMessage(chatMessage.id)
                        if (messages.isNotEmpty() && !messages.last().isUserMessage) {
                            viewModel.deleteMessage(messages.last().id)
                        }
                        viewModel.appDB.addUserMessage(chatId, newMessage)
                        viewModel.unloadModel()
                        viewModel.loadModel(onComplete = {
                            if (it == ModelLoadingState.SUCCESS) {
                                viewModel.sendUserQuery(newMessage, addMessageToDB = false)
                            }
                        })
                    },
                    allowEditing = (i == lastUserMessageIndex),
                )
            }
            if (isGeneratingResponse) {
                item {
                    if (partialResponse.isNotEmpty()) {
                        MessageListItem(
                            viewModel.markwon.render(viewModel.markwon.parse(partialResponse)),
                            isUserMessage = false,
                            onCopyClicked = {},
                            onShareClicked = {},
                            onMessageEdited = {},
                            allowEditing = false,
                            isPartial = true
                        )
                    } else {
                        ThinkingIndicator()
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        
        val showScrollToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
        val scope = rememberCoroutineScope()
        if (showScrollToBottom) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(messages.size) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(FeatherIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
@Composable
private fun ThinkingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Assistant is thinking",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(modifier = Modifier.width(20.dp).height(2.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageListItem(
    messageStr: Spanned,
    isUserMessage: Boolean,
    onCopyClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onMessageEdited: (String) -> Unit,
    allowEditing: Boolean,
    isPartial: Boolean = false
) {
    var isEditing by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Surface(
                color = if (isUserMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUserMessage) 18.dp else 4.dp,
                    bottomEnd = if (isUserMessage) 4.dp else 18.dp
                ),
                tonalElevation = if (isUserMessage) 0.dp else 1.dp,
                shadowElevation = if (isUserMessage) 1.dp else 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (isEditing) {
                        var editedText by remember { mutableStateOf(messageStr.toString()) }
                        TextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                onMessageEdited(editedText)
                                isEditing = false
                            })
                        )
                    } else {
                        ChatMessageText(
                            message = messageStr,
                            textSize = 16f,
                            textColor = (if (isUserMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface).toArgb(),
                            onLongClick = {
                                createChatMessageOptionsDialog(
                                    showEditOption = allowEditing,
                                    onEditClick = { isEditing = true },
                                    onCopyClick = onCopyClicked,
                                    onShareClick = onShareClicked,
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageInput(viewModel: ChatScreenViewModel) {
    val isGeneratingResponse by viewModel.isGeneratingResponse.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mediaPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType?.startsWith("video/") == true) {
                    viewModel.processVideoFile(uri)
                } else {
                    viewModel.processImageFile(uri)
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        color = Color.Transparent
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        var questionText by remember { mutableStateOf(viewModel.questionTextDefaultVal ?: "") }
        
        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                    }
                    mediaPickerLauncher.launch(intent)
                }) {
                    Icon(FeatherIcons.Plus, contentDescription = "Add Media", tint = MaterialTheme.colorScheme.primary)
                }
                
                TextField(
                    value = questionText,
                    onValueChange = { 
                        questionText = it 
                        viewModel.setLiveQuery(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask anything...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (questionText.isNotBlank()) {
                            viewModel.sendUserQuery(questionText)
                            questionText = ""
                            keyboardController?.hide()
                        }
                    })
                )

                AnimatedContent(targetState = isGeneratingResponse) { generating ->
                    if (generating) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        ) {
                            Icon(FeatherIcons.Square, contentDescription = "Stop", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (questionText.isNotBlank()) {
                                    viewModel.sendUserQuery(questionText)
                                    questionText = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = questionText.isNotBlank(),
                            modifier = Modifier.background(
                                if (questionText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                        ) {
                            Icon(FeatherIcons.ArrowUp, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksListBottomSheet(viewModel: ChatScreenViewModel) {
    val context = LocalContext.current
    val showTaskListBottomList by
    viewModel.showTaskListBottomListState.collectAsStateWithLifecycle()
    if (showTaskListBottomList) {
        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = {
                viewModel.onEvent(
                    ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList(visible = false)
                )
            },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tasks by viewModel.appDB.getTasks().collectAsState(emptyList())
                if (tasks.isEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.chat_no_task_created),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.onEvent(
                                ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList(
                                    visible = true
                                )
                            )
                            Intent(context, ManageTasksActivity::class.java).also {
                                context.startActivity(it)
                            }
                        }
                    ) {
                        MediumLabelText(stringResource(R.string.chat_create_task))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    AppBarTitleText(stringResource(R.string.chat_select_task))
                    TasksList(
                        tasks.map {
                            val modelName =
                                viewModel.modelsRepository.getModelFromId(it.modelId)?.name
                                    ?: return@map it
                            it.copy(modelName = modelName)
                        },
                        onTaskSelected = { task -> createChatFromTask(viewModel, task) },
                        onUpdateTaskClick = { 
                        },
                        onEditTaskClick = { 
                        },
                        onDeleteTaskClick = { 
                        },
                        enableTaskClick = true,
                        showTaskOptions = false,
                    )
                }
            }
        }
    }
}



@Composable
private fun ChangeFolderDialog(viewModel: ChatScreenViewModel) {
    val showChangeFolderDialogState by
    viewModel.showChangeFolderDialogState.collectAsStateWithLifecycle()
    val currentChat by viewModel.currChatState.collectAsStateWithLifecycle()
    if (showChangeFolderDialogState && currentChat != null) {
        val folders by viewModel.appDB.getFolders().collectAsState(emptyList())
        ChangeFolderDialogUI(
            onDismissRequest = {
                viewModel.onEvent(
                    ChatScreenUIEvent.DialogEvents.ToggleChangeFolderDialog(visible = false)
                )
            },
            currentChat!!.folderId,
            folders,
            onUpdateFolderId = { folderId -> viewModel.updateChatFolder(folderId) },
        )
    }
}

private fun createChatFromTask(viewModel: ChatScreenViewModel, task: Task) {
    viewModel.modelsRepository.getModelFromId(task.modelId)?.let { model ->
        val newTask =
            viewModel.appDB.addChat(
                chatName = task.name,
                chatTemplate = model.chatTemplate,
                systemPrompt = task.systemPrompt,
                llmModelId = task.modelId,
                isTask = true,
            )
        viewModel.switchChat(newTask)
        viewModel.onEvent(ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList(visible = false))
    }
}
