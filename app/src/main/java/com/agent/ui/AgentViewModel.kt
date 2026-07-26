package com.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agent.AgentApp
import com.agent.agent.AgentOrchestrator
import com.agent.ai.NvidiaAIClient
import com.agent.service.AgentAccessibilityService
import com.agent.telegram.TelegramBotManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private var orchestrator: AgentOrchestrator? = null

    val botToken = AgentApp.prefs.telegramBotToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val chatId = AgentApp.prefs.telegramChatId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
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

    fun updateBotToken(token: String) {
        viewModelScope.launch { AgentApp.prefs.setTelegramBotToken(token) }
    }

    fun updateChatId(id: String) {
        viewModelScope.launch { AgentApp.prefs.setTelegramChatId(id) }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaApiKey(key) }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaBaseUrl(url) }
    }

    fun updateModel(model: String) {
        viewModelScope.launch { AgentApp.prefs.setNvidiaModel(model) }
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

        val aiClient = NvidiaAIClient(
            apiKey = key,
            baseUrl = baseUrl.value.ifBlank { "https://integrate.api.nvidia.com/v1" },
            model = modelName.value.ifBlank { "meta/llama-3.1-405b-instruct" }
        )
        val bot = TelegramBotManager(botToken = token)

        orchestrator?.stop()
        orchestrator = AgentOrchestrator(aiClient, bot).also {
            viewModelScope.launch {
                it.log.collect { newLog ->
                    _logs.value = newLog
                }
            }
            viewModelScope.launch {
                it.isRunning.collect { running ->
                    _agentRunning.value = running
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
}
