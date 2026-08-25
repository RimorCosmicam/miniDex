package com.minidex.app.input

import android.content.Context
import android.util.Log
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
import com.minidex.app.input.ime.MiniDexInputMethodService

/**
 * Stable, zero-disconnect native input backend leveraging Android's Multi-Display Accessibility & IME frameworks.
 * Dispatches clicks, drags, swipes, scrolls, and text actions directly to the focused Samsung DeX display.
 */
class AccessibilityInputBackend(private val context: Context) : InputBackend {

    companion object {
        private const val TAG = "AccessibilityBackend"
    }

    override val id: String = "ACCESSIBILITY"
    override val name: String = "Native DeX Direct Driver"
    override val requiresPrivilegedAccess: Boolean = false

    override val isAvailable: Boolean
        get() = MiniDexAccessibilityService.isServiceEnabled() || MiniDexInputMethodService.isImeActive()

    private var pointerX: Float = 960f
    private var pointerY: Float = 540f
    private var isDragging: Boolean = false
    private var dragStartX: Float = 0f
    private var dragStartY: Float = 0f

    var onPointerUpdate: ((Float, Float) -> Unit)? = null

    override suspend fun initialize(): Result<Unit> {
        return if (isAvailable) {
            Log.i(TAG, "AccessibilityInputBackend ready for DeX injection")
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Accessibility Service or IME not enabled in system Settings"))
        }
    }

    override fun sendKeyDown(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (MiniDexInputMethodService.isImeActive()) {
            return MiniDexInputMethodService.sendKeyEvent(keyCode, metaState)
        }
        val service = MiniDexAccessibilityService.instance ?: return false
        return service.handleSpecialKey(keyCode, displayId)
    }

    override fun sendKeyUp(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        return true
    }

    override fun sendKeyPress(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (MiniDexInputMethodService.isImeActive()) {
            return MiniDexInputMethodService.sendKeyEvent(keyCode, metaState)
        }
        val service = MiniDexAccessibilityService.instance ?: return false
        return service.handleSpecialKey(keyCode, displayId)
    }

    override fun sendText(text: String, displayId: Int): Boolean {
        if (MiniDexInputMethodService.isImeActive()) {
            return MiniDexInputMethodService.commitText(text)
        }
        val service = MiniDexAccessibilityService.instance ?: return false
        return service.injectText(text, displayId)
    }

    override fun sendPointerMove(dx: Float, dy: Float, displayId: Int): Boolean {
        val newX = (pointerX + dx).coerceIn(0f, 3840f)
        val newY = (pointerY + dy).coerceIn(0f, 2160f)

        if (isDragging) {
            val service = MiniDexAccessibilityService.instance
            service?.dispatchDrag(pointerX, pointerY, newX, newY, displayId, 40)
        }

        pointerX = newX
        pointerY = newY
        onPointerUpdate?.invoke(pointerX, pointerY)
        return true
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        isDragging = true
        dragStartX = pointerX
        dragStartY = pointerY
        return true
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        if (isDragging) {
            isDragging = false
            val service = MiniDexAccessibilityService.instance
            service?.dispatchDrag(dragStartX, dragStartY, pointerX, pointerY, displayId, 60)
        }
        return true
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        val service = MiniDexAccessibilityService.instance ?: return false
        return service.dispatchClick(pointerX, pointerY, displayId)
    }

    override fun sendScroll(dx: Float, dy: Float, displayId: Int): Boolean {
        val service = MiniDexAccessibilityService.instance ?: return false
        return service.dispatchScroll(pointerX, pointerY, dx * 10f, dy * 10f, displayId)
    }

    override fun release() {
        isDragging = false
    }
}
