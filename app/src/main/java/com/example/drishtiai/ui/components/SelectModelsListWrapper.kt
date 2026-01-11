package com.example.drishtiai.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drishtiai.R
import com.example.drishtiai.ui.screens.chat.ChatScreenUIEvent
import com.example.drishtiai.ui.screens.chat.ChatScreenViewModel

@Composable
fun SelectModelsListWrapper(viewModel: ChatScreenViewModel) {
    val showSelectModelsListDialog by
    viewModel.showSelectModelListDialogState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (showSelectModelsListDialog) {
        val modelsList by
        viewModel.modelsRepository.getAvailableModels().collectAsState(emptyList())
        SelectModelsList(
            onDismissRequest = {
                viewModel.onEvent(
                    ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(visible = false)
                )
            },
            modelsList,
            onModelListItemClick = { model ->
                viewModel.updateChatLLMParams(model.id, model.chatTemplate)
                viewModel.onEvent(
                    ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(visible = false)
                )
            },
            onModelDeleteClick = { model ->
                viewModel.deleteModel(model.id)
                Toast.makeText(
                    viewModel.context,
                    context.getString(R.string.chat_model_deleted, model.name),
                    Toast.LENGTH_LONG,
                )
                    .show()
            },
        )
    }
}
