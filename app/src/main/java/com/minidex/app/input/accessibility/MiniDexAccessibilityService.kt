package com.minidex.app.input.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MiniDexAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MiniDexA11yService"
        var instance: MiniDexAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }

    private var lastTargetNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Could not set dynamic service flags", e)
        }
        Log.i(TAG, "MiniDex Accessibility Service Connected and Active for DeX")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val source = event.source
        if (source != null && (source.isFocused || source.isEditable)) {
            lastTargetNode = source
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "MiniDex Accessibility Service Interrupted")
        instance = null
        lastTargetNode = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        lastTargetNode = null
    }

    /**
     * Finds the active, editable, or focused node on the target DeX display.
     */
    fun findFocusedNodeOnDisplay(displayId: Int = -1): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val allDisplayWindows = windowsOnAllDisplays
            if (allDisplayWindows.size() > 0) {
                for (i in 0 until allDisplayWindows.size()) {
                    val currentDisplayId = allDisplayWindows.keyAt(i)
                    if (displayId < 0 || currentDisplayId == displayId) {
                        val windowList = allDisplayWindows.valueAt(i)
                        for (window in windowList) {
                            val root = window.root ?: continue
                            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                                ?: findEditableNodeRecursive(root)
                            if (focused != null) {
                                lastTargetNode = focused
                                return focused
                            }
                        }
                    }
                }
            }
        }

        // Fallback: check cached target or active window root
        val cached = lastTargetNode
        if (cached != null) {
            try {
                if (cached.refresh()) return cached
            } catch (_: Exception) {}
        }

        val root = rootInActiveWindow ?: return null
        val found = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findEditableNodeRecursive(root)

        if (found != null) {
            lastTargetNode = found
        }
        return found
    }

    private fun findEditableNodeRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable) return node
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNodeRecursive(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Injects text directly into the target input field on DeX.
     */
    fun injectText(text: String, displayId: Int = -1): Boolean {
        // Copy to system clipboard first as universal delivery
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("MiniDex Input", text)
                clipboard.setPrimaryClip(clip)
            }
        } catch (_: Exception) {}

        val node = findFocusedNodeOnDisplay(displayId)
        if (node != null) {
            val currentText = node.text?.toString() ?: ""
            val newText = currentText + text
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return true
            }

            // Clipboard Paste fallback
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                return true
            }
        }
        return false
    }

    /**
     * Dispatches key actions (Back, Home, Enter, Del) to DeX.
     */
    fun handleSpecialKey(keyCode: Int, displayId: Int = -1): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            KeyEvent.KEYCODE_HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            KeyEvent.KEYCODE_APP_SWITCH -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            KeyEvent.KEYCODE_DEL -> {
                val node = findFocusedNodeOnDisplay(displayId)
                if (node != null && node.text != null) {
                    val currentText = node.text.toString()
                    if (currentText.isNotEmpty()) {
                        val newText = currentText.dropLast(1)
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                        }
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_ENTER -> {
                val node = findFocusedNodeOnDisplay(displayId)
                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Dispatches a tap gesture directly to the specified DeX external display.
     */
    fun dispatchClick(x: Float, y: Float, displayId: Int = -1, onComplete: (() -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayId >= 0) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap executed on display $displayId at ($x, $y)")
                onComplete?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled on display $displayId at ($x, $y)")
            }
        }, null)
    }

    /**
     * Dispatches a drag gesture directly to the specified DeX external display.
     */
    fun dispatchDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, displayId: Int = -1, durationMs: Long = 60): Boolean {
        val path = Path().apply {
            moveTo(fromX.coerceAtLeast(0f), fromY.coerceAtLeast(0f))
            lineTo(toX.coerceAtLeast(0f), toY.coerceAtLeast(0f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(20L))
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayId >= 0) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches a scroll gesture directly to the specified DeX external display.
     */
    fun dispatchScroll(x: Float, y: Float, dx: Float, dy: Float, displayId: Int = -1): Boolean {
        val path = Path().apply {
            moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            lineTo((x - dx).coerceAtLeast(0f), (y - dy).coerceAtLeast(0f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayId >= 0) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, null, null)
    }
}
