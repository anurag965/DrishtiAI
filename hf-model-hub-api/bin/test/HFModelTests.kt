

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.example.drishtiai.hf_model_hub_api.HFModelInfo
import com.example.drishtiai.hf_model_hub_api.HFModelSearch
import com.example.drishtiai.hf_model_hub_api.HFModelTree
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class HFModelTests {
    private val huggingFaceModelId = "QuantFactory/BharatGPT-3B-Indic-GGUF"
    private val huggingFaceModelOrg = "QuantFactory"
    private val invalidHuggingFaceModelId = "shubham0204/BharatGPT-3B-Indic-GGUF"
    private val client: HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        // ignore unknown keys from the response
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    @Test
    fun testModelInfo_works() =
        runTest {
            val modelInfo = HFModelInfo(client).getModelInfo(huggingFaceModelId)
            assertEquals(huggingFaceModelId, modelInfo.modelId)
            assertEquals(huggingFaceModelOrg, modelInfo.author)
            assert(modelInfo.tags.isNotEmpty())
            assert(modelInfo.numDownloads > 0)
            assert(modelInfo.numLikes > 0)
        }

    @Test
    fun testModelInfoInvalidID_throws() =
        runTest {
            val exception =
                assertThrows<Exception> {
                    HFModelInfo(client).getModelInfo(invalidHuggingFaceModelId)
                }
            assertEquals("Invalid model ID", exception.message)
        }

    @Test
    fun testModelTree_works() =
        runTest {
            val modelTree = HFModelTree(client).getModelFileTree(huggingFaceModelId)
            assert(modelTree.isNotEmpty())
        }

    @Test
    fun testModelTreeInvalidID_throws() =
        runTest {
            val exception =
                assertThrows<Exception> {
                    HFModelTree(client).getModelFileTree(invalidHuggingFaceModelId)
                }
            assertEquals("Invalid model ID", exception.message)
        }

    @Test
    fun testModelSearch_works() =
        runTest {
            val results =
                HFModelSearch(client).searchModels(
                    query = "gguf",
                    author = "QuantFactory",
                    filter = "conversational",
                    limit = 5,
                )
            assert(results.isNotEmpty())
            assert(results.size <= 5)
        }
}
