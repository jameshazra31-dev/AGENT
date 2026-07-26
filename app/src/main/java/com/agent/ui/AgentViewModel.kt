package com.agent.ui

import android.app.Application
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

    private var orchestrator: AgentOrchestrator? = null
    private var aiClient: NvidiaAIClient? = null

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

    fun updateBotToken(token: String) {
        viewModelScope.launch { AgentApp.prefs.setTelegramBotToken(token) }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaApiKey(key) }
        rebuildAIClient()
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaBaseUrl(url) }
        rebuildAIClient()
    }

    fun updateModel(model: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaModel(model) }
        rebuildAIClient()
    }

    private fun rebuildAIClient() {
        val key = apiKey.value
        if (key.isNotBlank()) {
            aiClient = NvidiaAIClient(
                apiKey = key,
                baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
                model = modelName.value.ifBlank { "meta/llama-3.1-405b-instruct" }
            )
        }
    }

    fun fetchModels() {
        val key = apiKey.value
        if (key.isBlank()) return
        _modelsLoading.value = true
        val client = NvidiaAIClient(
            apiKey = key,
            baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" }
        )
        viewModelScope.launch {
            val result = client.fetchModels()
            result.onSuccess { models ->
                val ids = models.mapNotNull { it.id }
                _availableModels.value = ids
                if (ids.isNotEmpty() && modelName.value.isBlank()) {
                    AgentApp.prefs.setNvidiaModel(ids.first())
                }
            }
            _modelsLoading.value = false
        }
    }

    fun sendChatMessage(text: String) {
        _chatMessages.value = _chatMessages.value + ChatMessage(text, isUser = true)
        _chatLoading.value = true

        val client = aiClient ?: run {
            val key = apiKey.value
            if (key.isBlank()) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "Please configure API Key in Settings first.", false
                )
                _chatLoading.value = false
                return
            }
            NvidiaAIClient(
                apiKey = key,
                baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
                model = modelName.value.ifBlank { "meta/llama-3.1-405b-instruct" }
            ).also { aiClient = it }
        }

        val convMessages = listOf(
            NvidiaAIClient.ChatMessage("system", client.getSystemPrompt()),
            NvidiaAIClient.ChatMessage("user", text)
        )

        viewModelScope.launch {
            val result = client.chat(convMessages)
            result.onSuccess { response ->
                val reply = response.choices?.firstOrNull()?.message?.content
                    ?: "No response from AI"
                _chatMessages.value = _chatMessages.value + ChatMessage(reply, false)
            }
            result.onFailure { error ->
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "Error: ${error.message}", false
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
        aiClient = ai
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
