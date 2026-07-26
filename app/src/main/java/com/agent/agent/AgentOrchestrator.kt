package com.agent.agent

import android.util.Log
import com.agent.ai.NvidiaAIClient
import com.agent.service.AgentAccessibilityService
import org.json.JSONObject

class AgentOrchestrator(
    private val aiClient: NvidiaAIClient,
    private val accessibilityService: AgentAccessibilityService?
) {
    private val TAG = "Orchestrator"
    private val history = mutableListOf<NvidiaAIClient.ChatMessage>()

    fun getToolDefs(): List<NvidiaAIClient.ToolDef> {
        val stringType = mapOf("type" to "string")
        val numberType = mapOf("type" to "number")

        return listOf(
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "click_text",
                description = "Click on a UI element containing the given text",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf("label" to stringType),
                    "required" to listOf("label")
                )
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "click_coord",
                description = "Click at specific x,y coordinates on screen",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "x" to numberType,
                        "y" to numberType
                    ),
                    "required" to listOf("x", "y")
                )
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "type_text",
                description = "Type text into the currently focused input field",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf("text" to stringType),
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
                            "type" to "string",
                            "enum" to listOf("up", "down", "left", "right")
                        )
                    ),
                    "required" to listOf("direction")
                )
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "go_back",
                description = "Navigate back",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String,Any>(), "required" to emptyList<String>())
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "go_home",
                description = "Go to home screen",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String,Any>(), "required" to emptyList<String>())
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "recent_apps",
                description = "Open recent apps overview",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String,Any>(), "required" to emptyList<String>())
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "swipe",
                description = "Swipe from one coordinate to another",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "x1" to numberType, "y1" to numberType,
                        "x2" to numberType, "y2" to numberType,
                        "duration" to mapOf("type" to "number", "description" to "duration in ms, default 300")
                    ),
                    "required" to listOf("x1", "y1", "x2", "y2")
                )
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "wait",
                description = "Wait for a duration",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf("ms" to numberType),
                    "required" to listOf("ms")
                )
            )),
            NvidiaAIClient.ToolDef(function = NvidiaAIClient.FunctionDef(
                name = "read_screen",
                description = "Get the current text content visible on screen",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String,Any>(), "required" to emptyList<String>())
            ))
        )
    }

    suspend fun processInput(userInput: String): String {
        val screenText = accessibilityService?.getCurrentScreenText() ?: ""
        val messages = mutableListOf<NvidiaAIClient.ChatMessage>()

        messages.add(NvidiaAIClient.ChatMessage("system", aiClient.getSystemPrompt()))
        if (screenText.isNotBlank()) {
            messages.add(NvidiaAIClient.ChatMessage(
                "system",
                "Current screen content:\n$screenText"
            ))
        }
        messages.addAll(history.takeLast(20))
        messages.add(NvidiaAIClient.ChatMessage("user", userInput))

        val result = aiClient.chat(messages, getToolDefs())
        val response = result.getOrNull() ?: return "Error: ${result.exceptionOrNull()?.message}"

        val replyMsg = response.choices?.firstOrNull()?.message
        val content = replyMsg?.content ?: ""
        val toolCalls = replyMsg?.toolCalls

        history.add(NvidiaAIClient.ChatMessage("user", userInput))
        history.add(NvidiaAIClient.ChatMessage("assistant", content.ifEmpty { "[Executing actions...]" }))

        if (toolCalls != null && toolCalls.isNotEmpty()) {
            executeToolCalls(toolCalls)
        }

        return content
    }

    private fun executeToolCalls(toolCalls: List<NvidiaAIClient.ToolCall>) {
        val service = accessibilityService ?: return
        for (tc in toolCalls) {
            val name = tc.function?.name ?: continue
            val argsJson = tc.function?.arguments ?: "{}"
            val args = try { JSONObject(argsJson) } catch (_: Exception) { JSONObject() }

            try {
                when (name) {
                    "click_text" -> service.clickText(args.optString("label", ""))
                    "click_coord" -> service.clickCoord(args.optInt("x"), args.optInt("y"))
                    "type_text" -> service.typeText(args.optString("text", ""))
                    "scroll" -> service.scroll(args.optString("direction", "down"))
                    "go_back" -> service.goBack()
                    "go_home" -> service.goHome()
                    "recent_apps" -> service.recentApps()
                    "swipe" -> service.swipe(
                        args.optInt("x1"), args.optInt("y1"),
                        args.optInt("x2"), args.optInt("y2"),
                        args.optLong("duration", 300)
                    )
                    "wait" -> service.waitFor(args.optLong("ms", 1000))
                    "read_screen" -> { /* handled via screen content */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Action $name failed", e)
            }
        }
    }

    fun clearHistory() {
        history.clear()
    }
}
