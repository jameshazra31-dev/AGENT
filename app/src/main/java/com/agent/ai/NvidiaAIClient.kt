package com.agent.ai

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
        @SerializedName("type") val type: String? = null
    )

    data class ToolCallResult(
        val name: String,
        val args: Map<String, Any?>
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
                val body = json.toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val parsed = gson.fromJson(responseBody, ChatResponse::class.java)

                if (parsed?.error != null) {
                    Result.failure(Exception(parsed.error.message ?: "API Error"))
                } else {
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
