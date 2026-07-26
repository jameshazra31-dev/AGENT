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

    fun getSystemPrompt(): String = buildString {
        appendLine("You are AGENT, an AI assistant that controls an Android phone.")
        appendLine("You can perform actions on the phone using these tools:")
        appendLine("- click_text(label): Click UI element by text")
        appendLine("- click_coord(x, y): Tap coordinates")
        appendLine("- type_text(text): Type text into focused field")
        appendLine("- scroll(direction): up/down/left/right")
        appendLine("- go_back(): Press back button")
        appendLine("- go_home(): Go to home screen")
        appendLine("- recent_apps(): Open recent apps")
        appendLine("- swipe(x1, y1, x2, y2, duration): Swipe gesture")
        appendLine("- open_app(package_name): Open app by package")
        appendLine("- wait(ms): Wait milliseconds")
        appendLine("- read_screen(): Get current screen content")
        appendLine("")
        appendLine("When user asks an action, call the right tools. Explain what you did.")
    }

    fun getToolDefs(): List<NvidiaAIClient.ToolDef> {
        val stringType = mapOf("type" to "string")
        val numberType = mapOf("type" to "number")
        val noProps = mapOf("type" to "object", "properties" to emptyMap<String, Any>())

        return listOf(
            tool("click_text", "Click on UI element containing given text",
                mapOf("label" to stringType), listOf("label")),
            tool("click_coord", "Tap at specific coordinates",
                mapOf("x" to numberType, "y" to numberType), listOf("x", "y")),
            tool("type_text", "Type text into focused input field",
                mapOf("text" to stringType), listOf("text")),
            tool("scroll", "Scroll page",
                mapOf("direction" to mapOf("type" to "string", "enum" to listOf("up","down","left","right"))),
                listOf("direction")),
            tool("go_back", "Press back button", emptyMap(), emptyList()),
            tool("go_home", "Go to home screen", emptyMap(), emptyList()),
            tool("recent_apps", "Open recent apps", emptyMap(), emptyList()),
            tool("swipe", "Swipe from one coord to another",
                mapOf("x1" to numberType, "y1" to numberType,
                      "x2" to numberType, "y2" to numberType,
                      "duration" to mapOf("type" to "number", "description" to "duration ms, default 300")),
                listOf("x1","y1","x2","y2")),
            tool("open_app", "Open app by package name",
                mapOf("package_name" to stringType), listOf("package_name")),
            tool("wait", "Wait for milliseconds",
                mapOf("ms" to numberType), listOf("ms")),
            tool("read_screen", "Get text on current screen", emptyMap(), emptyList())
        )
    }

    private fun tool(
        name: String,
        desc: String,
        props: Map<String, Any>,
        required: List<String>
    ): NvidiaAIClient.ToolDef {
        return NvidiaAIClient.ToolDef(
            function = NvidiaAIClient.FunctionDef(
                name = name,
                description = desc,
                parameters = mapOf(
                    "type" to "object",
                    "properties" to if (props.isEmpty()) emptyMap() else props,
                    "required" to required
                )
            )
        )
    }

    suspend fun processInput(
        userInput: String,
        onActionExecuted: (String) -> Unit = {}
    ): String {
        val screenText = accessibilityService?.getCurrentScreenText() ?: ""
        val messages = mutableListOf<NvidiaAIClient.ChatMessage>()
        messages.add(NvidiaAIClient.ChatMessage("system", getSystemPrompt()))
        if (screenText.isNotBlank()) {
            messages.add(NvidiaAIClient.ChatMessage("system", "Screen:\n\n$screenText".take(2000)))
        }
        messages.addAll(history.takeLast(20))
        messages.add(NvidiaAIClient.ChatMessage("user", userInput))

        val result = aiClient.chat(messages, getToolDefs())
        val response = result.getOrNull()
            ?: return "Error: ${result.exceptionOrNull()?.message}"

        val replyMsg = response.choices?.firstOrNull()?.message
        val content = replyMsg?.content ?: ""
        val toolCalls = replyMsg?.toolCalls

        history.add(NvidiaAIClient.ChatMessage("user", userInput))
        history.add(NvidiaAIClient.ChatMessage("assistant", content.ifEmpty { "[Acting]" }))

        if (toolCalls != null && toolCalls.isNotEmpty()) {
            executeToolCalls(toolCalls, onActionExecuted)
        }

        return content
    }

    private fun executeToolCalls(
        toolCalls: List<NvidiaAIClient.ToolCall>,
        onActionExecuted: (String) -> Unit
    ) {
        val service = accessibilityService
        for (tc in toolCalls) {
            val name = tc.function?.name ?: continue
            val args = try {
                JSONObject(tc.function?.arguments ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }
            try {
                val feedback = when (name) {
                    "click_text" -> {
                        val label = args.optString("label")
                        service?.clickText(label)
                        "Clicked '$label'"
                    }
                    "click_coord" -> {
                        val x = args.optInt("x"); val y = args.optInt("y")
                        service?.clickCoord(x, y)
                        "Tapped ($x,$y)"
                    }
                    "type_text" -> {
                        val text = args.optString("text")
                        service?.typeText(text)
                        "Typed '$text'"
                    }
                    "scroll" -> {
                        val dir = args.optString("direction", "down")
                        service?.scroll(dir)
                        "Scrolled $dir"
                    }
                    "go_back" -> { service?.goBack(); "Back" }
                    "go_home" -> { service?.goHome(); "Home" }
                    "recent_apps" -> { service?.recentApps(); "Recent apps" }
                    "swipe" -> {
                        service?.swipe(
                            args.optInt("x1"), args.optInt("y1"),
                            args.optInt("x2"), args.optInt("y2"),
                            args.optLong("duration", 300)
                        )
                        "Swipe done"
                    }
                    "open_app" -> {
                        try {
                            val pkg = args.optString("package_name")
                            val ctx = service
                            if (ctx != null && pkg.isNotBlank()) {
                                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                                if (intent != null) {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                    "Opened $pkg"
                                } else "App not found: $pkg"
                            } else "No context"
                        } catch (e: Exception) { "Error: ${e.message}" }
                    }
                    "wait" -> {
                        service?.waitFor(args.optLong("ms", 1000))
                        "Waited ${args.optLong("ms", 1000)}ms"
                    }
                    "read_screen" -> {
                        "Screen: ${(service?.getCurrentScreenText() ?: "N/A").take(500)}"
                    }
                    else -> "Unknown: $name"
                }
                onActionExecuted(feedback)
            } catch (e: Exception) {
                Log.e(TAG, "Action $name failed", e)
                onActionExecuted("Failed $name: ${e.message}")
            }
        }
    }

    fun clearHistory() { history.clear() }
}
