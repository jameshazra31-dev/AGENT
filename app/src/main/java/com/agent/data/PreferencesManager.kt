package com.agent.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "agent_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val NVIDIA_API_KEY = stringPreferencesKey("nvidia_api_key")
        val NVIDIA_BASE_URL = stringPreferencesKey("nvidia_base_url")
        val NVIDIA_MODEL = stringPreferencesKey("nvidia_model")
        val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
        val AGENT_ENABLED = booleanPreferencesKey("agent_enabled")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val telegramBotToken: Flow<String> = context.store.data.map { it[TELEGRAM_BOT_TOKEN] ?: "" }
    val telegramChatId: Flow<String> = context.store.data.map { it[TELEGRAM_CHAT_ID] ?: "" }
    val nvidiaApiKey: Flow<String> = context.store.data.map { it[NVIDIA_API_KEY] ?: "" }
    val nvidiaBaseUrl: Flow<String> = context.store.data.map { it[NVIDIA_BASE_URL] ?: "https://integrate.api.nvidia.com/v1" }
    val nvidiaModel: Flow<String> = context.store.data.map { it[NVIDIA_MODEL] ?: "meta/llama-3.1-405b-instruct" }
    val accessibilityEnabled: Flow<Boolean> = context.store.data.map { it[ACCESSIBILITY_ENABLED] ?: false }
    val agentEnabled: Flow<Boolean> = context.store.data.map { it[AGENT_ENABLED] ?: false }

    suspend fun setTelegramBotToken(token: String) {
        context.store.edit { it[TELEGRAM_BOT_TOKEN] = token }
    }

    suspend fun setTelegramChatId(id: String) {
        context.store.edit { it[TELEGRAM_CHAT_ID] = id }
    }

    suspend fun setNvidiaApiKey(key: String) {
        context.store.edit { it[NVIDIA_API_KEY] = key }
    }

    suspend fun setNvidiaBaseUrl(url: String) {
        context.store.edit { it[NVIDIA_BASE_URL] = url }
    }

    suspend fun setNvidiaModel(model: String) {
        context.store.edit { it[NVIDIA_MODEL] = model }
    }

    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        context.store.edit { it[ACCESSIBILITY_ENABLED] = enabled }
    }

    suspend fun setAgentEnabled(enabled: Boolean) {
        context.store.edit { it[AGENT_ENABLED] = enabled }
    }
}
