package com.agent.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agent.agent.AgentOrchestrator
import com.agent.ai.NvidiaAIClient
import com.agent.service.AgentAccessibilityService
import com.agent.telegram.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AgentViewModel"

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://integrate.api.nvidia.com/v1")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _model = MutableStateFlow("meta/llama-3.1-405b-instruct")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _botToken = MutableStateFlow("")
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _testResult = MutableStateFlow("")
    val testResult: StateFlow<String> = _testResult.asStateFlow()

    private val _testLoading = MutableStateFlow(false)
    val testLoading: StateFlow<Boolean> = _testLoading.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _detectedChatId = MutableStateFlow<Long?>(null)
    val detectedChatId: StateFlow<Long?> = _detectedChatId.asStateFlow()

    private val _botStatus = MutableStateFlow("Stopped")
    val botStatus: StateFlow<String> = _botStatus.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _modelsError = MutableStateFlow("")
    val modelsError: StateFlow<String> = _modelsError.asStateFlow()

    private val _apiConnected = MutableStateFlow(false)
    val apiConnected: StateFlow<Boolean> = _apiConnected.asStateFlow()

    private val _accessibilityEnabled = MutableStateFlow(false)
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val _actionLog = MutableStateFlow<List<String>>(emptyList())
    val actionLog: StateFlow<List<String>> = _actionLog.asStateFlow()

    private var aiClient: NvidiaAIClient? = null
    private var orchestrator: AgentOrchestrator? = null
    val telegramBot = TelegramBot()

    init {
        checkAccessibility()
        viewModelScope.launch {
            telegramBot.incomingMessages.collect { msg ->
                if (msg != null) {
                    if (_detectedChatId.value == null) {
                        _detectedChatId.value = msg.chatId
                    }
                    addMessage("[Telegram] ${msg.text}", isUser = true)
                    if (_apiConnected.value) {
                        processRemoteInput(msg.text, msg.chatId)
                    } else {
                        addMessage("Configure API first in Settings", isUser = false)
                    }
                }
            }
        }
        viewModelScope.launch {
            telegramBot.status.collect { _botStatus.value = it }
        }
    }

    fun updateApiKey(value: String) {
        _apiKey.value = value
        _apiConnected.value = false
        _testResult.value = ""
    }

    fun updateBaseUrl(value: String) {
        _baseUrl.value = value
        _apiConnected.value = false
    }

    fun updateModel(value: String) {
        _model.value = value
        aiClient?.model = value
    }

    fun updateBotToken(value: String) { _botToken.value = value }
    fun updateChatInput(value: String) { _chatInput.value = value }
    fun selectTab(index: Int) { _selectedTab.value = index }

    fun checkAccessibility() {
        val ctx = getApplication<Application>()
        val enabled = Settings.Secure.getInt(
            ctx.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        _accessibilityEnabled.value = enabled
    }

    fun fetchModels() {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            _modelsError.value = "Enter API key first"
            return
        }
        _modelsLoading.value = true
        _modelsError.value = ""
        val client = NvidiaAIClient(key, _baseUrl.value.trim().trimEnd('/'), _model.value)
        viewModelScope.launch {
            val result = client.fetchModels()
            result.onSuccess { models ->
                _availableModels.value = models
                if (models.isNotEmpty()) {
                    _modelsError.value = "Found ${models.size} models"
                }
            }
            result.onFailure { error ->
                _modelsError.value = "Error: ${error.message}"
                _availableModels.value = NvidiaAIClient.FALLBACK_MODELS
            }
            _modelsLoading.value = false
        }
    }

    fun testApiConnection() {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            _testResult.value = "Enter API key first"
            return
        }
        _testLoading.value = true
        _testResult.value = "Testing..."

        val client = NvidiaAIClient(
            apiKey = key,
            baseUrl = _baseUrl.value.trim().trimEnd('/'),
            model = _model.value
        )

        viewModelScope.launch {
            try {
                val result = client.testConnection()
                result.onSuccess { msg ->
                    _testResult.value = "✅ $msg"
                    aiClient = client
                    orchestrator = AgentOrchestrator(client, AgentAccessibilityService.instance)
                    _apiConnected.value = true
                    if (_availableModels.value.isEmpty()) {
                        fetchModels()
                    }
                }
                result.onFailure { error ->
                    _testResult.value = "❌ ${error.message ?: "Unknown error"}"
                    _apiConnected.value = false
                    Log.e(TAG, "API test failed: ${error.message}")
                }
            } catch (e: Exception) {
                _testResult.value = "❌ Crash: ${e.message}"
                Log.e(TAG, "testConnection crash", e)
            }
            _testLoading.value = false
        }
    }

    fun startTelegramBot() {
        val token = _botToken.value.trim()
        if (token.isBlank()) return
        telegramBot.start(token)
    }

    fun stopTelegramBot() {
        telegramBot.stop()
    }

    fun sendMessage() {
        val input = _chatInput.value.trim()
        if (input.isBlank()) return
        if (!_apiConnected.value || orchestrator == null) {
            addMessage(input, isUser = true)
            addMessage("Test API connection first in Settings", isUser = false)
            return
        }
        _chatInput.value = ""
        addMessage(input, isUser = true)
        _chatLoading.value = true
        val orch = orchestrator ?: return
        val currentActions = _actionLog.value.toMutableList()

        viewModelScope.launch {
            try {
                val reply = orch.processInput(input, ::onActionExecuted)
                addMessage(reply.ifEmpty { "[Actions executed]" }, isUser = false)
            } catch (e: Exception) {
                addMessage("Error: ${e.message}", isUser = false)
                Log.e(TAG, "sendMessage error", e)
            }
            _chatLoading.value = false
        }
    }

    private fun onActionExecuted(action: String) {
        _actionLog.value = _actionLog.value + action
    }

    fun clearChat() {
        _messages.value = emptyList()
        _actionLog.value = emptyList()
        orchestrator?.clearHistory()
    }

    private suspend fun processRemoteInput(text: String, chatId: Long) {
        val orch = orchestrator ?: return
        try {
            val reply = orch.processInput(text, ::onActionExecuted)
            if (reply.isNotBlank()) {
                telegramBot.sendMessage(chatId, reply)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Telegram processing error", e)
            telegramBot.sendMessage(chatId, "Error: ${e.message}")
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _messages.value = _messages.value + ChatMessageUi(text, isUser)
    }

    data class ChatMessageUi(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
}
