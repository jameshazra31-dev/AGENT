package com.agent.telegram

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TelegramBotManager(private val botToken: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private var lastUpdateId = 0

    private val baseUrl get() = "https://api.telegram.org/bot$botToken"

    data class SendMessageRequest(
        @SerializedName("chat_id") val chatId: String,
        @SerializedName("text") val text: String,
        @SerializedName("parse_mode") val parseMode: String = "HTML"
    )

    data class GetUpdatesResponse(
        @SerializedName("ok") val ok: Boolean,
        @SerializedName("result") val result: List<Update> = emptyList()
    )

    data class Update(
        @SerializedName("update_id") val updateId: Int,
        @SerializedName("message") val message: Message? = null
    )

    data class Message(
        @SerializedName("message_id") val messageId: Int,
        @SerializedName("chat") val chat: Chat,
        @SerializedName("text") val text: String? = null
    )

    data class Chat(@SerializedName("id") val id: Long)

    data class SendMessageResponse(
        @SerializedName("ok") val ok: Boolean,
        @SerializedName("description") val description: String? = null
    )

    suspend fun sendMessage(text: String, chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(SendMessageRequest(chatId = chatId, text = text))
                    .toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$baseUrl/sendMessage")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val parsed = gson.fromJson(response.body?.string(), SendMessageResponse::class.java)
                parsed?.ok == true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getUpdates(): List<Update> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/getUpdates?offset=$lastUpdateId&timeout=10"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = gson.fromJson(response.body?.string(), GetUpdatesResponse::class.java)
                if (body?.ok == true) {
                    body.result.forEach { if (it.updateId >= lastUpdateId) lastUpdateId = it.updateId + 1 }
                    body.result
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
