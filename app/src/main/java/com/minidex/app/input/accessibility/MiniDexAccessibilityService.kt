package com.minidex.app.input.accessibility

import android.accessibilityservice.AccessibilityService
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
import android.view.accessibility.AccessibilityWindowInfo

class MiniDexAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MiniDexA11yService"
        var instance: MiniDexAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "MiniDex Accessibility Service Connected and Active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active event stream
    }

    override fun onInterrupt() {
        Log.w(TAG, "MiniDex Accessibility Service Interrupted")
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Finds the active/focused node on the target DeX display or active window.
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
                            if (focused != null) return focused
                        }
                    }
                }
            }
        }

        // Fallback: search default active window root
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    /**
     * Injects text directly into the focused input field on DeX using Accessibility actions.
     */
    fun injectText(text: String, displayId: Int = -1): Boolean {
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

            // Fallback: Paste via Clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("MiniDex Input", text)
                clipboard.setPrimaryClip(clip)
                if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Dispatches key code actions (e.g. Back, Home, Enter, Del) to the focused node or global system.
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
     * Dispatches a tap gesture directly to the specified display (DeX external display).
     */
    fun dispatchClick(x: Float, y: Float, displayId: Int = -1, onComplete: (() -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayId >= 0) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click gesture completed on display $displayId at ($x, $y)")
                onComplete?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Click gesture cancelled on display $displayId at ($x, $y)")
            }
        }, null)
    }

    /**
     * Dispatches a drag gesture directly to the specified display.
     */
    fun dispatchDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, displayId: Int = -1, durationMs: Long = 80): Boolean {
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
     * Dispatches a scroll gesture directly to the specified display.
     */
    fun dispatchScroll(x: Float, y: Float, dx: Float, dy: Float, displayId: Int = -1): Boolean {
        val path = Path().apply {
            moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            lineTo((x - dx).coerceAtLeast(0f), (y - dy).coerceAtLeast(0f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayId >= 0) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, null, null)
    }
}
