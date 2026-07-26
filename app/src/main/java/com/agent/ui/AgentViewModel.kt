package com.agent.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agent.AgentApp
import com.agent.agent.AgentOrchestrator
import com.agent.ai.NvidiaAIClient
import com.agent.service.AgentAccessibilityService
import com.agent.telegram.TelegramBotManager
import com.agent.ui.screens.ChatMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AgentViewModel"
    }

    private var orchestrator: AgentOrchestrator? = null
    private var _aiClient: NvidiaAIClient? = null

    val botToken = AgentApp.prefs.telegramBotToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val apiKey = AgentApp.prefs.nvidiaApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val baseUrl = AgentApp.prefs.nvidiaBaseUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val modelName = AgentApp.prefs.nvidiaModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val accessibilityEnabled = AgentApp.prefs.accessibilityEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val agentEnabled = AgentApp.prefs.agentEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _serviceStatus = MutableStateFlow(false)
    val serviceStatus: StateFlow<Boolean> = _serviceStatus

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _agentRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _agentRunning

    private val _detectedChatId = MutableStateFlow("")
    val detectedChatId: StateFlow<String> = _detectedChatId

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading

    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError

    private fun getOrCreateClient(): NvidiaAIClient? {
        _aiClient?.let { return it }
        val key = apiKey.value
        if (key.isBlank()) return null
        return NvidiaAIClient(
            apiKey = key,
            baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
            model = modelName.value.ifBlank { "meta/llama-3.1-405b-instruct" }
        ).also { _aiClient = it }
    }

    private fun rebuildClient() {
        val key = apiKey.value
        _aiClient = if (key.isNotBlank()) {
            NvidiaAIClient(
                apiKey = key,
                baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
                model = modelName.value.ifBlank { "meta/llama-3.1-405b-instruct" }
            )
        } else null
    }

    fun updateBotToken(token: String) {
        viewModelScope.launch { AgentApp.prefs.setTelegramBotToken(token) }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaApiKey(key) }
        rebuildClient()
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaBaseUrl(url) }
        rebuildClient()
    }

    fun updateModel(model: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaModel(model) }
        rebuildClient()
    }

    fun fetchModels() {
        val key = apiKey.value
        if (key.isBlank()) {
            _modelError.value = "Enter API Key first"
            return
        }
        _modelsLoading.value = true
        _modelError.value = null

        val client = NvidiaAIClient(
            apiKey = key,
            baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" }
        )

        viewModelScope.launch {
            try {
                val result = client.fetchModels()
                result.onSuccess { models ->
                    val ids = models.mapNotNull { it.id }
                    if (ids.isNotEmpty()) {
                        _availableModels.value = ids
                        _modelError.value = "Found ${ids.size} models"
                        Log.d(TAG, "Models fetched: $ids")
                        if (modelName.value.isBlank() || modelName.value !in ids) {
                            AgentApp.prefs.setNvidiaModel(ids.first())
                        }
                    } else {
                        fallbackModels()
                    }
                }
                result.onFailure { error ->
                    Log.e(TAG, "Fetch models failed: ${error.message}")
                    fallbackModels()
                    _modelError.value = "API error: ${error.message}. Using default models."
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchModels unexpected error: ${e.message}", e)
                fallbackModels()
                _modelError.value = "Error: ${e.message}"
            }
            _modelsLoading.value = false
        }
    }

    private fun fallbackModels() {
        if (_availableModels.value.isEmpty()) {
            _availableModels.value = NvidiaAIClient.FALLBACK_MODELS
            if (modelName.value.isBlank()) {
                viewModelScope.launch {
                    AgentApp.prefs.setNvidiaModel(NvidiaAIClient.FALLBACK_MODELS.first())
                }
            }
        }
    }

    fun sendChatMessage(text: String) {
        _chatMessages.value = _chatMessages.value + ChatMessage(text, isUser = true)
        _chatLoading.value = true

        val client = getOrCreateClient()
        if (client == null) {
            _chatMessages.value = _chatMessages.value + ChatMessage(
                "⚠️ API Key not configured. Go to Settings and enter your NVIDIA API Key.",
                false
            )
            _chatLoading.value = false
            return
        }

        val convMessages = listOf(
            NvidiaAIClient.ChatMessage("system", client.getSystemPrompt()),
            NvidiaAIClient.ChatMessage("user", text)
        )

        viewModelScope.launch {
            try {
                val result = client.chat(convMessages)
                result.onSuccess { response ->
                    val reply = response.choices?.firstOrNull()?.message?.content
                    if (reply != null && reply.isNotBlank()) {
                        _chatMessages.value = _chatMessages.value + ChatMessage(reply, false)
                    } else {
                        _chatMessages.value = _chatMessages.value + ChatMessage(
                            "⚠️ Empty response from AI. Check your model and API key in Settings.",
                            false
                        )
                    }
                }
                result.onFailure { error ->
                    val errMsg = error.message ?: "Unknown error"
                    Log.e(TAG, "Chat failed: $errMsg")
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        "⚠️ API Error: $errMsg\n\nCheck your API key and model in Settings.",
                        false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendChatMessage error: ${e.message}", e)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "⚠️ Error: ${e.message}",
                    false
                )
            }
            _chatLoading.value = false
        }
    }

    fun addSystemMessage(text: String) {
        _chatMessages.value = _chatMessages.value + ChatMessage(text, false)
    }

    fun setupAccessibilityService() {
        AgentAccessibilityService.start(getApplication())
    }

    fun startAgent() {
        val token = botToken.value
        val key = apiKey.value
        if (token.isBlank() || key.isBlank()) return
        if (!AgentAccessibilityService.isRunning) {
            setupAccessibilityService()
        }

        val ai = NvidiaAIClient(
            apiKey = key,
            baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
            model = modelName.value.ifBlank { "meta/llama-3.1-405b-instruct" }
        )
        _aiClient = ai
        val bot = TelegramBotManager(botToken = token)

        orchestrator?.stop()
        orchestrator = AgentOrchestrator(ai, bot).also {
            viewModelScope.launch { it.log.collect { newLog -> _logs.value = newLog } }
            viewModelScope.launch { it.isRunning.collect { running -> _agentRunning.value = running } }
            viewModelScope.launch {
                it.detectedChatIdFlow.collect { id ->
                    if (id.isNotBlank()) _detectedChatId.value = id
                }
            }
            it.start()
        }

        viewModelScope.launch { AgentApp.prefs.setAgentEnabled(true) }
    }

    fun stopAgent() {
        orchestrator?.stop()
        orchestrator = null
        viewModelScope.launch { AgentApp.prefs.setAgentEnabled(false) }
    }

    fun checkServiceStatus() {
        _serviceStatus.value = AgentAccessibilityService.isRunning
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }
}
