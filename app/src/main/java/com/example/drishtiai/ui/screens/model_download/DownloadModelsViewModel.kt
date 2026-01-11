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

package com.example.drishtiai.ui.screens.model_download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.example.drishtiai.hf_model_hub_api.HFModelInfo
import com.example.drishtiai.hf_model_hub_api.HFModelSearch
import com.example.drishtiai.hf_model_hub_api.HFModelTree
import com.example.drishtiai.smollm.GGUFReader
import com.example.drishtiai.smollm.SmolLM
import com.example.drishtiai.R
import com.example.drishtiai.data.AppDB
import com.example.drishtiai.data.HFModelsAPI
import com.example.drishtiai.ui.components.hideProgressDialog
import com.example.drishtiai.ui.components.setProgressDialogText
import com.example.drishtiai.ui.components.setProgressDialogTitle
import com.example.drishtiai.ui.components.showProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths

@Single
class DownloadModelsViewModel(
    val context: Context,
    val appDB: AppDB,
    val hfModelsAPI: HFModelsAPI,
) : ViewModel() {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadModelFromIndex(selectedPopularModelIndex: Int) {
        val model = getPopularModel(selectedPopularModelIndex)!!
        downloadModelFromUrl(model.url)
        if (model.mmProjPath.isNotEmpty()) {
            downloadModelFromUrl(model.mmProjPath)
        }
    }

    fun downloadModelFromUrl(modelUrl: String) {
        val fileName = modelUrl.substring(modelUrl.lastIndexOf('/') + 1)
        val request =
            DownloadManager.Request(modelUrl.toUri())
                .setTitle(fileName)
                .setDescription(
                    "The GGUF model will be downloaded to your Downloads folder for use with DrishtiAI."
                )
                .setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        downloadManager.enqueue(request)
        Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
    }

    fun downloadModelAndMmprj(modelId: String, precision: String) {
        val modelName = modelId.substring(modelId.lastIndexOf('/') + 1)
        val ggufFileName = "$modelName-$precision.gguf"
        val mmprojFileName = "mmproj-$modelName-$precision.gguf"

        val ggufUrl = "https://huggingface.co/$modelId/resolve/main/$ggufFileName"
        val mmprojUrl = "https://huggingface.co/$modelId/resolve/main/$mmprojFileName"

        downloadModelFromUrl(ggufUrl)
        downloadModelFromUrl(mmprojUrl)
    }


    fun getModels(query: String): Flow<PagingData<HFModelSearch.ModelSearchResult>> =
        hfModelsAPI.getModelsList(query)

    /**
     * Given the model file URI, copy the model file to the app's internal directory. Once copied,
     * add a new LLMModel entity with modelName=fileName where fileName is the name of the model
     * file.
     */
    fun copyModelFile(uri: Uri, mmprojUri: Uri? = null, onComplete: () -> Unit) {
        var fileName = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        if (fileName.isNotEmpty()) {
            setProgressDialogTitle(context.getString(R.string.dialog_progress_copy_model_title))
            setProgressDialogText(
                context.getString(R.string.dialog_progress_copy_model_text, fileName)
            )
            showProgressDialog()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    context.contentResolver.openInputStream(uri).use { inputStream ->
                        FileOutputStream(File(context.filesDir, fileName)).use { outputStream ->
                            inputStream?.copyTo(outputStream)
                        }
                    }

                    var mmprojPath = ""
                    if (mmprojUri != null && mmprojUri.toString().isNotEmpty()) {
                        var mmprojFileName = ""
                        context.contentResolver.query(mmprojUri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            mmprojFileName = cursor.getString(nameIndex)
                        }
                        if (mmprojFileName.isNotEmpty()) {
                            val mmprojFile = File(context.filesDir, mmprojFileName)
                            context.contentResolver.openInputStream(mmprojUri).use { inputStream ->
                                FileOutputStream(mmprojFile).use { outputStream ->
                                    inputStream?.copyTo(outputStream)
                                }
                            }
                            mmprojPath = mmprojFile.absolutePath
                        }
                    }
                    
                    val ggufReader = GGUFReader()
                    ggufReader.load(File(context.filesDir, fileName).absolutePath)
                    val contextSize =
                        ggufReader.getContextSize() ?: SmolLM.DefaultInferenceParams.contextSize
                    val chatTemplate =
                        ggufReader.getChatTemplate() ?: SmolLM.DefaultInferenceParams.chatTemplate
                    appDB.addModel(
                        fileName,
                        "",
                        Paths.get(context.filesDir.absolutePath, fileName).toString(),
                        mmprojPath,
                        contextSize.toInt(),
                        chatTemplate,
                    )
                    withContext(Dispatchers.Main) {
                        hideProgressDialog()
                        onComplete()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hideProgressDialog()
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(
                    context,
                    context.getString(R.string.toast_invalid_file),
                    Toast.LENGTH_SHORT,
            )
                .show()
        }
    }

    fun fetchModelInfoAndTree(
        modelId: String,
        onResult: (HFModelInfo.ModelInfo, List<HFModelTree.HFModelFile>) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val modelInfo = hfModelsAPI.getModelInfo(modelId)
            var modelTree = hfModelsAPI.getModelTree(modelId)
            modelTree = modelTree.filter { modelFile -> modelFile.path.endsWith("gguf") }
            withContext(Dispatchers.Main) { onResult(modelInfo, modelTree) }
        }
    }
}
