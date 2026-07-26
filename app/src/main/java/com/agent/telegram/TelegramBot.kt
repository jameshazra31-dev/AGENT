package com.agent.telegram

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TelegramBot {

    companion object {
        private const val TAG = "TelegramBot"
        private const val POLL_TIMEOUT = 30
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var botToken: String = ""
    private var offset = 0
    private var job: Job? = null

    private val _incomingMessages = MutableStateFlow<TelegramMessage?>(null)
    val incomingMessages: StateFlow<TelegramMessage?> = _incomingMessages

    private val _status = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = _status

    data class TelegramMessage(
        val chatId: Long,
        val text: String,
        val messageId: Int
    )

    private data class Update(
        @SerializedName("update_id") val updateId: Int,
        @SerializedName("message") val message: Message? = null
    )

    private data class Message(
        @SerializedName("message_id") val messageId: Int = 0,
        @SerializedName("chat") val chat: Chat,
        @SerializedName("text") val text: String? = null
    )

    private data class Chat(
        @SerializedName("id") val id: Long
    )

    private data class UpdatesResponse(
        @SerializedName("ok") val ok: Boolean,
        @SerializedName("result") val result: List<Update> = emptyList()
    )

    private data class SendResponse(
        @SerializedName("ok") val ok: Boolean,
        @SerializedName("description") val description: String? = null
    )

    data class SendPayload(
        @SerializedName("chat_id") val chatId: Long,
        @SerializedName("text") val text: String,
        @SerializedName("parse_mode") val parseMode: String = "HTML"
    )

    fun start(token: String) {
        if (token.isBlank()) return
        botToken = token
        offset = 0
        _status.value = "Running"
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    pollUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error", e)
                    delay(5000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = "Disconnected"
    }

    private suspend fun pollUpdates() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?timeout=$POLL_TIMEOUT&offset=$offset"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) return

        val updates = try {
            gson.fromJson(body, UpdatesResponse::class.java)
        } catch (_: Exception) { null }

        updates?.result?.forEach { update ->
            offset = update.updateId + 1
            val msg = update.message
            if (msg != null && !msg.text.isNullOrBlank()) {
                _incomingMessages.value = TelegramMessage(
                    chatId = msg.chat.id,
                    text = msg.text,
                    messageId = msg.messageId
                )
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = SendPayload(chatId = chatId, text = text)
                val body = gson.toJson(payload).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendMessage")
                    .post(body)
                    .build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
            }
        }
    }
}
