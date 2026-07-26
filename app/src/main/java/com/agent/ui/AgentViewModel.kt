package com.agent.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agent.agent.AgentOrchestrator
import com.agent.ai.NvidiaAIClient
import com.agent.service.AgentAccessibilityService
import com.agent.telegram.TelegramBot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AgentViewModel"

    // Settings state
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _baseUrl = MutableStateFlow("https://integrate.api.nvidia.com/v1")
    val baseUrl: StateFlow<String> = _baseUrl

    private val _model = MutableStateFlow("meta/llama-3.1-405b-instruct")
    val model: StateFlow<String> = _model

    private val _botToken = MutableStateFlow("")
    val botToken: StateFlow<String> = _botToken

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    // Test connection state
    private val _testResult = MutableStateFlow("")
    val testResult: StateFlow<String> = _testResult

    private val _testLoading = MutableStateFlow(false)
    val testLoading: StateFlow<Boolean> = _testLoading

    // Chat state
    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput

    // Telegram state
    private val _detectedChatId = MutableStateFlow<Long?>(null)
    val detectedChatId: StateFlow<Long?> = _detectedChatId

    private val _botStatus = MutableStateFlow("Disconnected")
    val botStatus: StateFlow<String> = _botStatus

    // AIClient and Orchestrator (recreated when api key changes)
    private var aiClient: NvidiaAIClient? = null
    private var orchestrator: AgentOrchestrator? = null
    val telegramBot = TelegramBot()

    // Accessibility
    private val accessibilityService: AgentAccessibilityService?
        get() = AgentAccessibilityService.instance

    init {
        viewModelScope.launch {
            telegramBot.incomingMessages.collect { msg ->
                if (msg != null) {
                    if (_detectedChatId.value == null) {
                        _detectedChatId.value = msg.chatId
                    }
                    // Auto-reply via AI if configured
                    processTelegramMessage(msg)
                }
            }
        }
        viewModelScope.launch {
            telegramBot.status.collect { _botStatus.value = it }
        }
    }

    fun updateApiKey(value: String) { _apiKey.value = value }
    fun updateBaseUrl(value: String) { _baseUrl.value = value }
    fun updateModel(value: String) { _model.value = value }
    fun updateBotToken(value: String) { _botToken.value = value }
    fun updateChatInput(value: String) { _chatInput.value = value }
    fun selectTab(index: Int) { _selectedTab.value = index }

    fun testApiConnection() {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            _testResult.value = "Enter an API key first"
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
            val result = client.testConnection()
            result.onSuccess { msg ->
                _testResult.value = "✅ $msg"
                aiClient = client
                orchestrator = AgentOrchestrator(client, accessibilityService)
            }
            result.onFailure { error ->
                _testResult.value = "❌ ${error.message}"
                Log.e(TAG, "API test failed: ${error.message}")
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
        _chatInput.value = ""

        val userMsg = ChatMessageUi(
            text = input,
            isUser = true
        )
        _messages.value = _messages.value + userMsg

        val orch = orchestrator
        if (orch == null) {
            _messages.value = _messages.value + ChatMessageUi(
                text = "Configure API key and test connection in Settings first",
                isUser = false
            )
            return
        }

        _chatLoading.value = true
        viewModelScope.launch {
            val reply = orch.processInput(input)
            _messages.value = _messages.value + ChatMessageUi(
                text = reply.ifEmpty { "[Actions executed]" },
                isUser = false
            )
            _chatLoading.value = false
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        orchestrator?.clearHistory()
    }

    private suspend fun processTelegramMessage(msg: com.agent.telegram.TelegramBot.TelegramMessage) {
        val orch = orchestrator ?: return
        val reply = orch.processInput(msg.text)
        if (reply.isNotBlank()) {
            telegramBot.sendMessage(msg.chatId, reply)
        }
        // Also show in chat UI
        _messages.value = _messages.value + listOf(
            ChatMessageUi(text = "[Telegram] ${msg.text}", isUser = true),
            ChatMessageUi(text = reply.ifEmpty { "[Actions executed]" }, isUser = false)
        )
    }

    data class ChatMessageUi(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
}
