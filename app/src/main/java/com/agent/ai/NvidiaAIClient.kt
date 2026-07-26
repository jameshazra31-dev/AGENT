package com.agent.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NvidiaAIClient(
    private val apiKey: String,
    private val baseUrl: String = "https://integrate.api.nvidia.com/v1",
    private val model: String = "meta/llama-3.1-405b-instruct"
) {
    companion object {
        private const val TAG = "NvidiaAI"
        val FALLBACK_MODELS = listOf(
            "meta/llama-3.1-405b-instruct",
            "meta/llama-3.1-70b-instruct",
            "meta/llama-3.1-8b-instruct",
            "mistralai/mistral-large",
            "mistralai/mixtral-8x22b-instruct-v0.1",
            "google/gemma-2-27b-it",
            "microsoft/phi-3-mini-128k-instruct"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    data class ChatMessage(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: String
    )

    data class ChatRequest(
        @SerializedName("model") val model: String,
        @SerializedName("messages") val messages: List<ChatMessage>,
        @SerializedName("temperature") val temperature: Double = 0.3,
        @SerializedName("max_tokens") val maxTokens: Int = 2000,
        @SerializedName("top_p") val topP: Double = 0.9,
        @SerializedName("tools") val tools: List<ToolDef>? = null,
        @SerializedName("tool_choice") val toolChoice: String? = null
    )

    data class ToolDef(
        @SerializedName("type") val type: String = "function",
        @SerializedName("function") val function: FunctionDef
    )

    data class FunctionDef(
        @SerializedName("name") val name: String,
        @SerializedName("description") val description: String,
        @SerializedName("parameters") val parameters: Map<String, Any>
    )

    data class ChatResponse(
        @SerializedName("id") val id: String? = null,
        @SerializedName("choices") val choices: List<Choice>? = null,
        @SerializedName("error") val error: ErrorInfo? = null
    )

    data class Choice(
        @SerializedName("message") val message: ResponseMessage? = null,
        @SerializedName("finish_reason") val finishReason: String? = null,
        @SerializedName("index") val index: Int? = null
    )

    data class ResponseMessage(
        @SerializedName("role") val role: String? = null,
        @SerializedName("content") val content: String? = null,
        @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null
    )

    data class ToolCall(
        @SerializedName("id") val id: String? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("function") val function: ToolCallFunction? = null
    )

    data class ToolCallFunction(
        @SerializedName("name") val name: String? = null,
        @SerializedName("arguments") val arguments: String? = null
    )

    data class ErrorDetail(
        @SerializedName("message") val message: String? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("code") val code: String? = null
    )

    data class ErrorInfo(
        @SerializedName("message") val message: String? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("code") val code: String? = null
    )

    data class ModelsResponse(
        @SerializedName("data") val data: List<ModelInfo>? = null,
        @SerializedName("object") val objectType: String? = null
    )

    data class ModelInfo(
        @SerializedName("id") val id: String? = null,
        @SerializedName("object") val objectType: String? = null,
        @SerializedName("created") val created: Long? = null,
        @SerializedName("owned_by") val ownedBy: String? = null
    )

    suspend fun fetchModels(): Result<List<ModelInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "AGENT-Android/1.0")
                    .build()
                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string()
                Log.d(TAG, "Models response code: ${response.code}, body: ${bodyStr?.take(500)}")

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${bodyStr?.take(200)}")
                    )
                }

                val parsed = gson.fromJson(bodyStr, ModelsResponse::class.java)
                if (parsed?.data != null && parsed.data.isNotEmpty()) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(Exception("No models returned from API"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchModels error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDef>? = null
    ): Result<ChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = ChatRequest(
                    model = model,
                    messages = messages,
                    tools = tools,
                    toolChoice = if (tools != null) "auto" else null
                )
                val json = gson.toJson(requestBody)
                Log.d(TAG, "Chat request: $json")

                val body = json.toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "AGENT-Android/1.0")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d(TAG, "Chat response code: ${response.code}, body: ${responseBody?.take(500)}")

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val err = gson.fromJson(responseBody, ErrorDetail::class.java)
                        err?.message ?: responseBody?.take(200)
                    } catch (e: Exception) {
                        responseBody?.take(200)
                    }
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${errMsg ?: "Unknown error"}")
                    )
                }

                val parsed = gson.fromJson(responseBody, ChatResponse::class.java)

                if (parsed?.error != null) {
                    Result.failure(Exception(parsed.error.message ?: "API Error"))
                } else if (parsed?.choices == null || parsed.choices.isEmpty()) {
                    Result.failure(Exception("No response choices returned"))
                } else {
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "chat error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}
