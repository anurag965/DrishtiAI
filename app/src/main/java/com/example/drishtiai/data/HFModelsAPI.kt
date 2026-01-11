
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
package com.example.drishtiai.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.drishtiai.hf_model_hub_api.HFModelInfo
import com.example.drishtiai.hf_model_hub_api.HFModelSearch
import com.example.drishtiai.hf_model_hub_api.HFModelTree
import com.example.drishtiai.hf_model_hub_api.HFModels
import org.koin.core.annotation.Single
import java.time.LocalDateTime

@Single
class HFModelsAPI {
    suspend fun getModelInfo(modelId: String): HFModelInfo.ModelInfo =
        HFModels.getInfo().getModelInfo(modelId)

    suspend fun getModelTree(modelId: String): List<HFModelTree.HFModelFile> {
        return HFModels.getTree().getModelFileTree(modelId)
    }


    fun getModelsList(query: String) =
        Pager(
            config = PagingConfig(pageSize = 10),
            pagingSourceFactory = { HFModelSearchPagedDataSource(query) },
        )
            .flow

    class HFModelSearchPagedDataSource(private val query: String) :
        PagingSource<Int, HFModelSearch.ModelSearchResult>() {

        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, HFModelSearch.ModelSearchResult> {
            val models = mutableListOf<HFModelSearch.ModelSearchResult>()
            models.add(
                HFModelSearch.ModelSearchResult(
                _id = "1",
                id = "HuggingFaceTB/SmolVLM2-2.2B-Instruct",
                numLikes = 0,
                numDownloads = 0,
                isPrivate = false,
                tags = emptyList(),
                createdAt = LocalDateTime.now(),
                modelId = "HuggingFaceTB/SmolVLM2-2.2B-Instruct"
            )
            )
            models.add(
                HFModelSearch.ModelSearchResult(
                _id = "2",
                id = "HuggingFaceTB/SmolVLM2-500M-Video-Instruct",
                numLikes = 0,
                numDownloads = 0,
                isPrivate = false,
                tags = emptyList(),
                createdAt = LocalDateTime.now(),
                modelId = "HuggingFaceTB/SmolVLM2-500M-Video-Instruct"
            )
            )
            return LoadResult.Page(
                data = models,
                prevKey = null,
                nextKey = null,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, HFModelSearch.ModelSearchResult>): Int? =
            state.anchorPosition?.let { anchorPosition ->
                state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                    ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
            }
    }
}
