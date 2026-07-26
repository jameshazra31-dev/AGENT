package com.agent.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
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
    var model: String = "meta/llama-3.1-405b-instruct"
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        val FALLBACK_MODELS = listOf(
            "meta/llama-3.1-405b-instruct",
            "meta/llama-3.1-70b-instruct",
            "meta/llama-3.1-8b-instruct",
            "meta/llama-3.3-70b-instruct",
            "mistralai/mistral-large-2-instruct",
            "mistralai/mixtral-8x22b-instruct-v0.1",
            "google/gemma-2-27b-it",
            "google/gemma-2-9b-it",
            "microsoft/phi-3-mini-128k-instruct",
            "nvidia/nemotron-4-340b-instruct",
            "deepseek-ai/deepseek-r1",
            "qwen/qwen2.5-7b-instruct"
        )
    }

    data class ChatMessage(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: String
    )

    data class ChatRequest(
        @SerializedName("model") val model: String,
        @SerializedName("messages") val messages: List<ChatMessage>,
        @SerializedName("temperature") val temperature: Double = 0.7,
        @SerializedName("max_tokens") val maxTokens: Int = 1024,
        @SerializedName("top_p") val topP: Double = 0.95,
        @SerializedName("stream") val stream: Boolean = false,
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
        @SerializedName("index") val index: Int? = null,
        @SerializedName("message") val message: ResponseMessage? = null,
        @SerializedName("finish_reason") val finishReason: String? = null
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

    data class ErrorInfo(
        @SerializedName("message") val message: String? = null,
        @SerializedName("code") val code: String? = null,
        @SerializedName("type") val type: String? = null
    )

    suspend fun fetchModels(): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("API key is empty"))
                }
                val request = Request.Builder()
                    .url("$baseUrl/models")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val msg = try {
                        val obj = JsonParser.parseString(bodyStr).asJsonObject
                        val err = obj.getAsJsonObject("error")
                        err?.get("message")?.asString ?: bodyStr.take(200)
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val models = mutableListOf<String>()
                try {
                    val root = JsonParser.parseString(bodyStr).asJsonObject
                    val data = root.getAsJsonArray("data")
                    if (data != null) {
                        for (i in 0 until data.size()) {
                            val id = data[i].asJsonObject?.get("id")?.asString
                            if (!id.isNullOrBlank()) models.add(id)
                        }
                    }
                } catch (_: Exception) {}

                if (models.isEmpty()) {
                    return@withContext Result.success(FALLBACK_MODELS)
                }
                Result.success(models)
            } catch (e: Exception) {
                Log.e("NvidiaAIClient", "fetchModels error", e)
                Result.success(FALLBACK_MODELS)
            }
        }
    }

    suspend fun testConnection(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("API key is empty"))
                }
                val body = gson.toJson(
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage("user", "Reply with just: OK")
                        ),
                        maxTokens = 5,
                        temperature = 0.1,
                        tools = null,
                        toolChoice = null
                    )
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    var msg = "HTTP ${response.code}"
                    try {
                        val root = JsonParser.parseString(bodyStr).asJsonObject
                        val err = root.getAsJsonObject("error")
                        if (err != null) {
                            val m = err.get("message")?.asString
                            if (!m.isNullOrBlank()) msg = m
                        } else {
                            val detail = root.get("detail")?.asString
                            if (!detail.isNullOrBlank()) msg = detail
                        }
                    } catch (_: Exception) {
                        if (bodyStr.isNotBlank()) msg = bodyStr.take(200)
                    }
                    Log.e("NvidiaAIClient", "testConnection failed: $msg")
                    return@withContext Result.failure(Exception(msg))
                }

                val parsed = try {
                    gson.fromJson(bodyStr, ChatResponse::class.java)
                } catch (_: Exception) { null }

                val reply = parsed?.choices?.firstOrNull()?.message?.content
                    ?: parsed?.choices?.firstOrNull()?.message?.toolCalls?.let { "[Tool call]" }

                if (reply != null) {
                    Result.success("Connected: $model\nReply: $reply")
                } else {
                    Result.failure(Exception("Empty reply from API"))
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e("NvidiaAIClient", "DNS error", e)
                Result.failure(Exception("No internet / wrong URL"))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("NvidiaAIClient", "Timeout", e)
                Result.failure(Exception("Request timed out"))
            } catch (e: Exception) {
                Log.e("NvidiaAIClient", "testConnection error", e)
                Result.failure(Exception("Error: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDef>? = null
    ): Result<ChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(
                    ChatRequest(
                        model = model,
                        messages = messages,
                        tools = tools,
                        toolChoice = if (tools != null) "auto" else null,
                        maxTokens = 2048
                    )
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    var msg = "HTTP ${response.code}"
                    try {
                        val root = JsonParser.parseString(bodyStr).asJsonObject
                        val err = root.getAsJsonObject("error")
                        val m = err?.get("message")?.asString
                        if (!m.isNullOrBlank()) msg = m
                    } catch (_: Exception) {
                        if (bodyStr.isNotBlank()) msg = bodyStr.take(200)
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val parsed = gson.fromJson(bodyStr, ChatResponse::class.java)
                if (parsed?.error != null) {
                    Result.failure(Exception(parsed.error.message ?: "API error"))
                } else if (parsed?.choices == null || parsed.choices.isEmpty()) {
                    Result.failure(Exception("No choices returned"))
                } else {
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Log.e("NvidiaAIClient", "chat error", e)
                Result.failure(Exception("Error: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }
}
