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
    val model: String = "meta/llama-3.1-405b-instruct"
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        val KNOWN_MODELS = listOf(
            "meta/llama-3.1-405b-instruct",
            "meta/llama-3.1-70b-instruct",
            "meta/llama-3.1-8b-instruct",
            "mistralai/mistral-large-2-instruct",
            "mistralai/mistral-7b-instruct-v0.3",
            "google/gemma-2-27b-it",
            "google/gemma-2-9b-it",
            "microsoft/phi-3-mini-128k-instruct",
            "nvidia/nemotron-4-340b-instruct"
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
        @SerializedName("code") val code: String? = null
    )

    fun getSystemPrompt(): String = buildString {
        appendLine("You are AGENT, an AI assistant on an Android phone.")
        appendLine("You can perform these actions:")
        appendLine("- Click text: click_text(\"label\")")
        appendLine("- Click coordinates: click_coord(x, y)")
        appendLine("- Type text: type_text(\"text\")")
        appendLine("- Scroll: scroll(direction) — up/down/left/right")
        appendLine("- Navigate: go_back(), go_home(), recent_apps()")
        appendLine("- Swipe: swipe(x1, y1, x2, y2, duration_ms)")
        appendLine("- Wait: wait(ms)")
        appendLine("- Read screen: read_screen() — gets current screen content")
        appendLine("")
        appendLine("Analyze the request and decide which action(s) to take.")
        appendLine("Use function calls to execute actions. Respond conversationally to the user.")
    }

    suspend fun testConnection(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage("system", "Reply with just the word OK."),
                            ChatMessage("user", "Say OK")
                        ),
                        maxTokens = 5,
                        temperature = 0.1
                    )
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val msg = try {
                        gson.fromJson(bodyStr, ErrorInfo::class.java)?.message
                            ?: bodyStr.take(200)
                    } catch (_: Exception) {
                        "HTTP ${response.code}: ${bodyStr.take(100)}"
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val parsed = gson.fromJson(bodyStr, ChatResponse::class.java)
                val reply = parsed?.choices?.firstOrNull()?.message?.content

                if (reply != null) {
                    Result.success("Connected to $model")
                } else {
                    Result.failure(Exception("Empty response — check base URL"))
                }
            } catch (e: Exception) {
                Log.e("NvidiaAIClient", "testConnection error", e)
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
                val body = gson.toJson(
                    ChatRequest(
                        model = model,
                        messages = messages,
                        tools = tools,
                        toolChoice = if (tools != null) "auto" else null
                    )
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val msg = try {
                        gson.fromJson(bodyStr, ErrorInfo::class.java)?.message
                            ?: bodyStr.take(200)
                    } catch (_: Exception) {
                        "HTTP ${response.code}: ${bodyStr.take(100)}"
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
                Result.failure(e)
            }
        }
    }
}
