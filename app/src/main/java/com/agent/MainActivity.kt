package com.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent.ui.AgentViewModel
import com.agent.ui.MainScreen
import com.agent.ui.theme.DarkScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DarkScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: AgentViewModel = viewModel()

                    val selectedTab by viewModel.selectedTab.collectAsState()
                    val chatInput by viewModel.chatInput.collectAsState()
                    val messages by viewModel.messages.collectAsState()
                    val chatLoading by viewModel.chatLoading.collectAsState()
                    val apiKey by viewModel.apiKey.collectAsState()
                    val baseUrl by viewModel.baseUrl.collectAsState()
                    val model by viewModel.model.collectAsState()
                    val botToken by viewModel.botToken.collectAsState()
                    val detectedChatId by viewModel.detectedChatId.collectAsState()
                    val testResult by viewModel.testResult.collectAsState()
                    val testLoading by viewModel.testLoading.collectAsState()
                    val botStatus by viewModel.botStatus.collectAsState()

                    MainScreen(
                        selectedTab = selectedTab,
                        onTabChange = viewModel::selectTab,
                        chatInput = chatInput,
                        onChatInputChange = viewModel::updateChatInput,
                        messages = messages,
                        chatLoading = chatLoading,
                        onSendMessage = viewModel::sendMessage,
                        onClearChat = viewModel::clearChat,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        botToken = botToken,
                        detectedChatId = detectedChatId,
                        testResult = testResult,
                        testLoading = testLoading,
                        botStatus = botStatus,
                        onApiKeyChange = viewModel::updateApiKey,
                        onBaseUrlChange = viewModel::updateBaseUrl,
                        onModelChange = viewModel::updateModel,
                        onBotTokenChange = viewModel::updateBotToken,
                        onTestApi = viewModel::testApiConnection,
                        onStartBot = viewModel::startTelegramBot,
                        onStopBot = viewModel::stopTelegramBot
                    )
                }
            }
        }
    }
}
