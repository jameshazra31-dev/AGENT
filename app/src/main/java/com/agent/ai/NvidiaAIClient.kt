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

        val KNOWN_MODELS = listOf(
            "meta/llama-3.1-405b-instruct",
            "meta/llama-3.1-70b-instruct",
            "meta/llama-3.1-8b-instruct",
            "mistralai/mistral-large",
            "mistralai/mixtral-8x22b-instruct-v0.1",
            "google/gemma-2-27b-it",
            "microsoft/phi-3-mini-128k-instruct",
            "nvidia/llama-3.1-nemotron-70b-instruct",
            "nvidia/nemotron-4-340b-instruct"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
        @SerializedName("temperature") val temperature: Double = 0.7,
        @SerializedName("max_tokens") val maxTokens: Int = 1024,
        @SerializedName("top_p") val topP: Double = 0.95,
        @SerializedName("tools") val tools: List<ToolDef>? = null,
        @SerializedName("tool_choice") val toolChoice: String? = null
    )

    data class ChatResponse(
        @SerializedName("id") val id: String? = null,
        @SerializedName("choices") val choices: List<Choice>? = null,
        @SerializedName("error") val error: ErrorInfo? = null
    )

    data class Choice(
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

    data class ToolDef(
        @SerializedName("type") val type: String = "function",
        @SerializedName("function") val function: FunctionDef
    )

    data class FunctionDef(
        @SerializedName("name") val name: String,
        @SerializedName("description") val description: String,
        @SerializedName("parameters") val parameters: Map<String, Any>
    )

    data class ErrorInfo(
        @SerializedName("message") val message: String? = null,
        @SerializedName("code") val code: String? = null
    )

    fun getSystemPrompt(): String = buildString {
        appendLine("You are AGENT, an AI assistant that controls an Android phone via accessibility services.")
        appendLine("You can perform the following actions on the phone:")
        appendLine("1. Click on UI elements by text")
        appendLine("2. Click at specific coordinates (x, y)")
        appendLine("3. Type text into input fields")
        appendLine("4. Scroll up/down/left/right")
        appendLine("5. Go back, go home, open recent apps")
        appendLine("6. Swipe between coordinates")
        appendLine("7. Get current screen content")
        appendLine("8. Wait/delay")
        appendLine("")
        appendLine("Analyze what the user asks and determine which phone actions to take.")
        appendLine("Be precise with coordinates and text matching. Use the screen content to understand what's visible.")
        appendLine("When you need to perform an action, use the available functions.")
        appendLine("Respond in the user's language.")
    }

    suspend fun testConnection(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val testMessages = listOf(
                    ChatMessage("system", "You are a helpful assistant. Reply with just the word OK."),
                    ChatMessage("user", "Say OK")
                )
                val requestBody = ChatRequest(
                    model = model,
                    messages = testMessages,
                    maxTokens = 10
                )
                val body = gson.toJson(requestBody).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val err = gson.fromJson(responseBodyStr, ErrorInfo::class.java)
                        err?.message ?: responseBodyStr.take(200)
                    } catch (e: Exception) {
                        responseBodyStr.take(200)
                    }
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: $errMsg")
                    )
                }

                val parsed = gson.fromJson(responseBodyStr, ChatResponse::class.java)
                val reply = parsed?.choices?.firstOrNull()?.message?.content
                if (reply != null) Result.success(reply)
                else Result.failure(Exception("Empty response from API"))
            } catch (e: Exception) {
                Log.e(TAG, "testConnection error: ${e.message}", e)
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
                val body = gson.toJson(requestBody).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val err = gson.fromJson(responseBodyStr, ErrorInfo::class.java)
                        err?.message ?: responseBodyStr.take(200)
                    } catch (e: Exception) {
                        responseBodyStr.take(200)
                    }
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: $errMsg")
                    )
                }

                val parsed = gson.fromJson(responseBodyStr, ChatResponse::class.java)
                if (parsed?.error != null) {
                    Result.failure(Exception(parsed.error.message ?: "API Error"))
                } else if (parsed?.choices == null || parsed.choices.isEmpty()) {
                    Result.failure(Exception("No response from API"))
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
