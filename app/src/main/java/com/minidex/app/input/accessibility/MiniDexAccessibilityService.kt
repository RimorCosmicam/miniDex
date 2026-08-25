package com.minidex.app.input.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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
        Log.i(TAG, "MiniDex Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op - we only dispatch gestures and actions
    }

    override fun onInterrupt() {
        Log.w(TAG, "MiniDex Accessibility Service Interrupted")
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun dispatchClick(x: Float, y: Float, onComplete: (() -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled")
            }
        }, null)
    }

    fun dispatchDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 100): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, null, null)
    }

    fun dispatchScroll(x: Float, y: Float, dx: Float, dy: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x - dx, y - dy)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, null, null)
    }
}
