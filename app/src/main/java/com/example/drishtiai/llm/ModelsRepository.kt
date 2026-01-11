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

package com.example.drishtiai.llm

import android.content.Context
import com.example.drishtiai.data.AppDB
import com.example.drishtiai.data.LLMModel
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import java.io.File

import com.example.drishtiai.smollm.GGUFReader
import com.example.drishtiai.smollm.SmolLM
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

@Single
class ModelsRepository(private val context: Context, private val appDB: AppDB) {
    init {
        for (model in appDB.getModelsList()) {
            if (!File(model.path).exists()) {
                deleteModel(model.id)
            }
        }
    }

    suspend fun discoverAndRegisterModels() {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dbFilesByPath = appDB.getModelsList().associateBy { it.path }

        downloadDir?.listFiles { file ->
            file.isFile && file.name.endsWith(".gguf") && !file.name.startsWith("mmproj-")
        }?.forEach { ggufFile ->
            if (!dbFilesByPath.containsKey(ggufFile.absolutePath)) {
                // This is a new GGUF file not in the database. Add it.
                val mmprojFileName = "mmproj-${ggufFile.name}"
                val mmprojFile = File(downloadDir, mmprojFileName)

                val mmprojPath = if (mmprojFile.exists()) mmprojFile.absolutePath else ""

                val ggufReader = GGUFReader()
                ggufReader.load(ggufFile.absolutePath)
                val contextSize =
                    ggufReader.getContextSize() ?: SmolLM.DefaultInferenceParams.contextSize
                val chatTemplate =
                    ggufReader.getChatTemplate() ?: SmolLM.DefaultInferenceParams.chatTemplate
                appDB.addModel(
                    ggufFile.name,
                    "",
                    ggufFile.absolutePath,
                    mmprojPath,
                    contextSize.toInt(),
                    chatTemplate,
                )
            }
        }
    }

    companion object {
        fun checkIfModelsDownloaded(context: Context): Boolean {
            context.filesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gguf")) {
                    return true
                }
            }
            return false
        }
    }

    fun getModelFromId(id: Long): LLMModel = appDB.getModel(id)

    fun getAvailableModels(): Flow<List<LLMModel>> = appDB.getModels()

    fun getAvailableModelsList(): List<LLMModel> = appDB.getModelsList()

    fun deleteModel(id: Long) {
        appDB.getModel(id).also {
            File(it.path).delete()
            appDB.deleteModel(it.id)
        }
    }
}
