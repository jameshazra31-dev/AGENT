package com.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.AgentApp

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning = false
            private set
        var instance: AgentAccessibilityService? = null
            private set

        fun start(context: Context) {
            context.startService(Intent(context, AgentForegroundService::class.java))
            context.startActivity(
                Intent("android.settings.ACCESSIBILITY_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun executeAction(action: PhoneAction, callback: ((Boolean) -> Unit)? = null) {
            instance?.performAction(action, callback)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var actionCallback: ((Boolean) -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handled via direct API calls
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
    }

    private fun performAction(action: PhoneAction, callback: ((Boolean) -> Unit)?) {
        actionCallback = callback
        when (action) {
            is PhoneAction.Click -> clickNode(action.text, action.callback)
            is PhoneAction.ClickCoordinates -> clickCoordinates(action.x, action.y, action.callback)
            is PhoneAction.TypeText -> typeText(action.text, action.callback)
            is PhoneAction.Scroll -> scroll(action.direction, action.callback)
            is PhoneAction.GoBack -> performGlobalAction(GLOBAL_ACTION_BACK, action.callback)
            is PhoneAction.GoHome -> performGlobalAction(GLOBAL_ACTION_HOME, action.callback)
            is PhoneAction.OpenRecentApps -> performGlobalAction(GLOBAL_ACTION_RECENTS, action.callback)
            is PhoneAction.Swipe -> swipe(action.x1, action.y1, action.x2, action.y2, action.callback)
            is PhoneAction.GetScreenContent -> getScreenContent(action.callback)
            is PhoneAction.Wait -> {
                handler.postDelayed({
                    action.callback(true)
                    callback?.invoke(true)
                }, action.delayMs)
            }
        }
    }

    private fun clickNode(text: String, callback: ((Boolean) -> Unit)?) {
        val root = rootInActiveWindow ?: run {
            callback?.invoke(false)
            return
        }
        val nodes = findNodesByText(root, text)
        if (nodes.isNotEmpty()) {
            val node = nodes.first()
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                val parent = node.parent
                parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            callback?.invoke(true)
        } else {
            callback?.invoke(false)
        }
    }

    private fun clickCoordinates(x: Int, y: Int, callback: ((Boolean) -> Unit)?) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                callback?.invoke(false)
            }
        }, null)
    }

    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, callback: ((Boolean) -> Unit)?) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                callback?.invoke(false)
            }
        }, null)
    }

    private fun typeText(text: String, callback: ((Boolean) -> Unit)?) {
        val root = rootInActiveWindow ?: run {
            callback?.invoke(false)
            return
        }
        var focused = findFocusedNode(root)
        if (focused == null) {
            focused = findFirstEditableNode(root)
        }
        focused?.let { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            callback?.invoke(true)
        } ?: callback?.invoke(false)
    }

    private fun scroll(direction: ScrollDirection, callback: ((Boolean) -> Unit)?) {
        val root = rootInActiveWindow ?: run {
            callback?.invoke(false)
            return
        }
        val scrollable = findScrollableNode(root)
        scrollable?.let { node ->
            val action = when (direction) {
                ScrollDirection.UP -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
                ScrollDirection.DOWN -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                ScrollDirection.LEFT -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
                ScrollDirection.RIGHT -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
            }
            node.performAction(action.id)
            callback?.invoke(true)
        } ?: callback?.invoke(false)
    }

    private fun performGlobalAction(action: Int, callback: ((Boolean) -> Unit)?) {
        val result = performGlobalAction(action)
        callback?.invoke(result)
    }

    private fun getScreenContent(callback: ((String) -> Unit)?) {
        val root = rootInActiveWindow ?: run {
            callback?.invoke("")
            return
        }
        val content = StringBuilder()
        extractText(root, content, 0)
        callback?.invoke(content.toString())
    }

    private fun extractText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (node == null) return
        val text = node.text
        if (text != null && text.isNotEmpty()) {
            if (node.isClickable || node.contentDescription != null) {
                sb.append("  ".repeat(depth))
                sb.append("- ")
                node.contentDescription?.let { sb.append("[$it] ") }
                sb.append(text)
                if (node.isClickable) sb.append(" (clickable)")
                sb.append("\n")
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractText(child, sb, depth + 1)
                child.recycle()
            }
        }
    }

    private fun findNodesByText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.text?.toString()?.let { t ->
                if (t.contains(text, ignoreCase = true)) results.add(node)
            }
            node.contentDescription?.toString()?.let { t ->
                if (t.contains(text, ignoreCase = true)) results.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return results
    }

    private fun findFocusedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isFocused) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}

sealed class PhoneAction {
    data class Click(val text: String, val callback: ((Boolean) -> Unit)? = null) : PhoneAction()
    data class ClickCoordinates(val x: Int, val y: Int, val callback: ((Boolean) -> Unit)? = null) : PhoneAction()
    data class TypeText(val text: String, val callback: ((Boolean) -> Unit)? = null) : PhoneAction()
    data class Scroll(val direction: ScrollDirection, val callback: ((Boolean) -> Unit)? = null) : PhoneAction()
    data object GoBack : PhoneAction()
    data object GoHome : PhoneAction()
    data object OpenRecentApps : PhoneAction()
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val callback: ((Boolean) -> Unit)? = null) : PhoneAction()
    data class GetScreenContent(val callback: ((String) -> Unit)? = null) : PhoneAction()
    data class Wait(val delayMs: Long = 2000, val callback: ((Boolean) -> Unit)? = null) : PhoneAction()
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
