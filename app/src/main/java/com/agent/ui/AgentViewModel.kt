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

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    private val _testLoading = MutableStateFlow(false)
    val testLoading: StateFlow<Boolean> = _testLoading

    private val _availableModels = MutableStateFlow(NvidiaAIClient.KNOWN_MODELS)
    val availableModels: StateFlow<List<String>> = _availableModels

    init {
        if (modelName.value.isBlank()) {
            viewModelScope.launch {
                AgentApp.prefs.setNvidiaModel(NvidiaAIClient.KNOWN_MODELS.first())
            }
        }
    }

    private fun buildClient(): NvidiaAIClient? {
        val key = apiKey.value
        if (key.isBlank()) return null
        return NvidiaAIClient(
            apiKey = key,
            baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
            model = modelName.value.ifBlank { NvidiaAIClient.KNOWN_MODELS.first() }
        )
    }

    fun updateBotToken(token: String) {
        viewModelScope.launch { AgentApp.prefs.setTelegramBotToken(token) }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaApiKey(key) }
        _aiClient = null
        _testResult.value = null
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaBaseUrl(url) }
        _aiClient = null
        _testResult.value = null
    }

    fun updateModel(model: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaModel(model) }
        _aiClient = null
    }

    fun testApiConnection() {
        val client = buildClient() ?: run {
            _testResult.value = "Enter API Key first"
            return
        }
        _testLoading.value = true
        _testResult.value = "Testing..."
        viewModelScope.launch {
            val result = client.testConnection()
            result.onSuccess {
                _testResult.value = "✅ API working! Model: ${client.model}"
                _aiClient = client
            }
            result.onFailure { error ->
                _testResult.value = "❌ ${error.message}"
                Log.e(TAG, "API test failed: ${error.message}")
            }
            _testLoading.value = false
        }
    }

    fun sendChatMessage(text: String) {
        _chatMessages.value = _chatMessages.value + ChatMessage(text, true)
        _chatLoading.value = true

        val client = _aiClient ?: buildClient()
        if (client == null) {
            _chatMessages.value = _chatMessages.value + ChatMessage(
                "Enter NVIDIA API Key in Settings and tap 'Test API' first.", false
            )
            _chatLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                val msgs = listOf(
                    NvidiaAIClient.ChatMessage("system", client.getSystemPrompt()),
                    NvidiaAIClient.ChatMessage("user", text)
                )
                val result = client.chat(msgs)
                result.onSuccess { response ->
                    val reply = response.choices?.firstOrNull()?.message?.content
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        reply ?: "Empty response", false
                    )
                }
                result.onFailure { error ->
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        "API Error: ${error.message}", false
                    )
                }
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "Error: ${e.message ?: "Unknown"}", false
                )
            }
            _chatLoading.value = false
        }
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
            model = modelName.value.ifBlank { NvidiaAIClient.KNOWN_MODELS.first() }
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
