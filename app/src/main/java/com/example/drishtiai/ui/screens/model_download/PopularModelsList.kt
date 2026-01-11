package com.example.drishtiai.ui.screens.model_download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import com.example.drishtiai.data.LLMModel

@Preview
@Composable
fun PreviewPopularModelsList() {
    PopularModelsList(selectedModelIndex = 0, onModelSelected = {})
}

@Composable
fun PopularModelsList(selectedModelIndex: Int?, onModelSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.Center) {
        popularModelsList.forEachIndexed { idx, model ->
            Row(
                Modifier
                    .clickable { onModelSelected(idx) }
                    .fillMaxWidth()
                    .background(
                        if (idx == selectedModelIndex) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (idx == selectedModelIndex) {
                    Icon(
                        FeatherIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    color =
                        if (idx == selectedModelIndex) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    text = model.name,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

fun getPopularModel(index: Int?): LLMModel? = if (index != null) popularModelsList[index] else null

/**
 * A list of models that are shown in the DownloadModelActivity for the user to quickly get started
 * by downloading a model.
 */
private val popularModelsList =
    listOf(
        LLMModel(
            name = "SmolVLM2-2.2B-Instruct-f16",
            url = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-f16.gguf",
            path = "",
            mmProjPath = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-f16.gguf",
            contextSize = 4096,
            chatTemplate = "auto"
        ),
        LLMModel(
            name = "SmolVLM2-2.2B-Instruct-Q8_0",
            url = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q8_0.gguf",
            path = "",
            mmProjPath = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
            contextSize = 4096,
            chatTemplate = "auto"
        ),
        LLMModel(
            name = "SmolVLM2-500M-Video-Instruct-f16",
            url = "https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/SmolVLM2-500M-Video-Instruct-f16.gguf",
            path = "",
            mmProjPath = "https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-500M-Video-Instruct-f16.gguf",
            contextSize = 4096,
            chatTemplate = "auto"
        ),
        LLMModel(
            name = "SmolVLM2-500M-Video-Instruct-Q8_0",
            url = "https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/SmolVLM2-500M-Video-Instruct-Q8_0.gguf",
            path = "",
            mmProjPath = "https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf",
            contextSize = 4096,
            chatTemplate = "auto"
        ),
    )
