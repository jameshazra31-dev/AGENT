package com.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "Accessibility"
        var instance: AgentAccessibilityService? = null
            private set
    }

    private val _screenContent = MutableStateFlow("")
    val screenContent: StateFlow<String> = _screenContent

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            readScreen()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun readScreen() {
        val root = rootInActiveWindow ?: return
        val text = extractText(root)
        root.recycle()
        if (text.isNotBlank()) {
            _screenContent.value = text
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrBlank()) parts.add(text)
        if (!contentDesc.isNullOrBlank()) parts.add(contentDesc)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                parts.add(extractText(child))
                child.recycle()
            }
        }
        return parts.joinToString(" | ")
    }

    fun clickText(label: String) {
        val root = rootInActiveWindow ?: return
        val matches = findNodesByText(root, label)
        root.recycle()
        for (node in matches) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return
            }
            val parent = node.parent
            if (parent != null) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return
            }
        }
    }

    fun clickCoord(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val editable = findEditableNodes(root)
        root.recycle()
        if (editable.isNotEmpty()) {
            val node = editable[0]
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(300)
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            node.recycle()
        }
    }

    fun scroll(direction: String) {
        val root = rootInActiveWindow ?: return
        val scrollable = findScrollable(root)
        root.recycle()
        val action = when (direction.lowercase()) {
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        scrollable.forEach {
            it.performAction(action)
            it.recycle()
        }
    }

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recentApps() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun waitFor(delayMs: Long) {
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {}
    }

    fun getCurrentScreenText(): String = _screenContent.value

    private fun findNodesByText(
        node: AccessibilityNodeInfo,
        text: String
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val nodeText = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text in nodeText || text in desc) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findNodesByText(child, text))
                child.recycle()
            }
        }
        return results
    }

    private fun findEditableNodes(
        node: AccessibilityNodeInfo
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (node.isEditable) results.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findEditableNodes(child))
                child.recycle()
            }
        }
        return results
    }

    private fun findScrollable(
        node: AccessibilityNodeInfo
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (node.isScrollable) results.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findScrollable(child))
                child.recycle()
            }
        }
        return results
    }
}
