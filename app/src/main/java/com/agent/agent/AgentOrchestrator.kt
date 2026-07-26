package com.agent.agent

import android.util.Log
import com.agent.ai.NvidiaAIClient
import com.agent.service.AgentAccessibilityService
import com.agent.service.PhoneAction
import com.agent.service.ScrollDirection
import com.agent.telegram.TelegramBotManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentOrchestrator(
    private val aiClient: NvidiaAIClient,
    private val telegramBot: TelegramBotManager
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val gson = Gson()
    private var pollingJob: Job? = null
    private var detectedChatId: String? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _detectedChatId = MutableStateFlow("")
    val detectedChatIdFlow: StateFlow<String> = _detectedChatId

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val messages = mutableListOf(
        NvidiaAIClient.ChatMessage("system", aiClient.getSystemPrompt())
    )

    private val toolDefs = listOf(
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "click_text",
            description = "Click on a UI element containing specific text",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "text" to mapOf("type" to "string", "description" to "Text to find and click")
                ),
                "required" to listOf("text")
            )
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "click_coordinates",
            description = "Click at specific x, y coordinates on screen",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "x" to mapOf("type" to "integer", "description" to "X coordinate"),
                    "y" to mapOf("type" to "integer", "description" to "Y coordinate")
                ),
                "required" to listOf("x", "y")
            )
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "type_text",
            description = "Type text into the currently focused input field",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "text" to mapOf("type" to "string", "description" to "Text to type")
                ),
                "required" to listOf("text")
            )
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "scroll",
            description = "Scroll the screen in a direction",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "direction" to mapOf(
                        "type" to "string", "enum" to listOf("up", "down", "left", "right"),
                        "description" to "Direction to scroll"
                    )
                ),
                "required" to listOf("direction")
            )
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "go_back",
            description = "Press the system back button",
            parameters = mapOf("type" to "object", "properties" to mapOf<String, Any>())
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "go_home",
            description = "Press the system home button",
            parameters = mapOf("type" to "object", "properties" to mapOf<String, Any>())
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "get_screen",
            description = "Get the current screen content as structured text",
            parameters = mapOf("type" to "object", "properties" to mapOf<String, Any>())
        )),
        NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
            name = "wait",
            description = "Wait for a specified duration in milliseconds",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "ms" to mapOf("type" to "integer", "description" to "Milliseconds to wait")
                ),
                "required" to listOf("ms")
            )
        ))
    )

    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        addLog("Agent started")
        pollingJob = scope.launch {
            while (isActive && _isRunning.value) {
                try {
                    val updates = telegramBot.getUpdates()
                    for (update in updates) {
                        val msg = update.message
                        if (msg != null && msg.text != null) {
                            val chatId = msg.chat.id.toString()
                            if (detectedChatId == null) {
                                detectedChatId = chatId
                                _detectedChatId.value = chatId
                                addLog("Chat ID detected: $chatId")
                            }
                            handleUserMessage(chatId, msg.text!!)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        pollingJob?.cancel()
        pollingJob = null
        addLog("Agent stopped")
    }

    private suspend fun handleUserMessage(chatId: String, text: String) {
        addLog("Received: $text")
        messages.add(NvidiaAIClient.ChatMessage("user", text))

        val result = aiClient.chat(messages, toolDefs)

        result.fold(
            onSuccess = { response ->
                val choice = response.choices?.firstOrNull()
                val msg = choice?.message

                if (msg?.toolCalls != null) {
                    for (toolCall in msg.toolCalls) {
                        val func = toolCall.function ?: continue
                        val name = func.name ?: continue
                        val args = func.arguments ?: "{}"

                        addLog("Executing: $name($args)")
                        val resultText = executeTool(name, args, chatId)

                        messages.add(NvidiaAIClient.ChatMessage(
                            "assistant",
                            "Executed $name with args: $args. Result: ${resultText ?: "done"}"
                        ))
                    }
                } else if (msg?.content != null) {
                    val reply = msg.content
                    messages.add(NvidiaAIClient.ChatMessage("assistant", reply))
                    telegramBot.sendMessage(reply, chatId)
                    addLog("Reply: $reply")
                }
            },
            onFailure = { error ->
                val errorMsg = "Error: ${error.message}"
                telegramBot.sendMessage(errorMsg, chatId)
                addLog(errorMsg)
            }
        )
    }

    private suspend fun executeTool(name: String, argsJson: String, chatId: String): String? {
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        val args: Map<String, Any?> = try {
            gson.fromJson(argsJson, mapType)
        } catch (e: Exception) {
            emptyMap()
        }

        return when (name) {
            "click_text" -> {
                val text = args["text"] as? String ?: return null
                awaitAction(PhoneAction.Click(text = text))
            }
            "click_coordinates" -> {
                val x = (args["x"] as? Double)?.toInt() ?: return null
                val y = (args["y"] as? Double)?.toInt() ?: return null
                awaitAction(PhoneAction.ClickCoordinates(x = x, y = y))
            }
            "type_text" -> {
                val text = args["text"] as? String ?: return null
                awaitAction(PhoneAction.TypeText(text = text))
            }
            "scroll" -> {
                val dir = args["direction"] as? String ?: return null
                val direction = when (dir.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "down" -> ScrollDirection.DOWN
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> return null
                }
                awaitAction(PhoneAction.Scroll(direction = direction))
            }
            "go_back" -> awaitAction(PhoneAction.GoBack)
            "go_home" -> awaitAction(PhoneAction.GoHome)
            "get_screen" -> {
                val screenContent = awaitScreenContent()
                val truncated = screenContent.take(3000)
                messages.add(NvidiaAIClient.ChatMessage(
                    "user",
                    "Current screen content:\n$truncated"
                ))
                telegramBot.sendMessage("📱 Screen captured (${screenContent.length} chars)", chatId)
                "screen captured"
            }
            "wait" -> {
                val ms = (args["ms"] as? Double)?.toLong() ?: 2000L
                delay(ms)
                "waited ${ms}ms"
            }
            else -> null
        }
    }

    private suspend fun awaitAction(action: PhoneAction): String? {
        return suspendCancellableCoroutine { cont ->
            val wrapped = when (action) {
                is PhoneAction.Click -> action.copy(callback = { cont.resume(if (it) "done" else "failed") })
                is PhoneAction.ClickCoordinates -> action.copy(callback = { cont.resume(if (it) "done" else "failed") })
                is PhoneAction.TypeText -> action.copy(callback = { cont.resume(if (it) "done" else "failed") })
                is PhoneAction.Scroll -> action.copy(callback = { cont.resume(if (it) "done" else "failed") })
                is PhoneAction.GoBack -> { cont.resume("done"); return@suspendCancellableCoroutine }
                is PhoneAction.GoHome -> { cont.resume("done"); return@suspendCancellableCoroutine }
                is PhoneAction.OpenRecentApps -> { cont.resume("done"); return@suspendCancellableCoroutine }
                is PhoneAction.Swipe -> action.copy(callback = { cont.resume(if (it) "done" else "failed") })
                is PhoneAction.GetScreenContent -> { cont.resume("done"); return@suspendCancellableCoroutine }
                is PhoneAction.Wait -> action.copy(callback = { cont.resume(if (it) "done" else "failed") })
            }
            AgentAccessibilityService.executeAction(wrapped)
        }
    }

    private suspend fun awaitScreenContent(): String {
        return suspendCancellableCoroutine { cont ->
            AgentAccessibilityService.executeAction(
                PhoneAction.GetScreenContent { content ->
                    cont.resume(content)
                }
            )
        }
    }

    private fun addLog(msg: String) {
        _log.value = _log.value + msg
        Log.d(TAG, msg)
    }
}
