package com.minidex.app.input

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fallback & Local Simulator Input Backend.
 * Used for testing on emulators, non-privileged sessions, or local feedback.
 */
class FallbackInputBackend : InputBackend {

    companion object {
        private const val TAG = "FallbackInputBackend"
    }

    override val id: String = "FALLBACK"
    override val name: String = "Simulator / Fallback (Test Mode)"
    override val isAvailable: Boolean = true
    override val requiresPrivilegedAccess: Boolean = false

    private val _eventLogs = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val eventLogs: SharedFlow<String> = _eventLogs.asSharedFlow()

    override suspend fun initialize(): Result<Unit> {
        Log.i(TAG, "FallbackInputBackend initialized")
        return Result.success(Unit)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        _eventLogs.tryEmit(msg)
    }

    override fun sendKeyDown(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        log("KeyDown: code=$keyCode, meta=$metaState, display=$displayId")
        return true
    }

    override fun sendKeyUp(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        log("KeyUp: code=$keyCode, meta=$metaState, display=$displayId")
        return true
    }

    override fun sendKeyPress(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        log("KeyPress: code=$keyCode, meta=$metaState, display=$displayId")
        return true
    }

    override fun sendText(text: String, displayId: Int): Boolean {
        log("Type: \"$text\", display=$displayId")
        return true
    }

    override fun sendPointerMove(dx: Float, dy: Float, displayId: Int): Boolean {
        log("PointerMove: dx=$dx, dy=$dy, display=$displayId")
        return true
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        log("PointerDown: button=$button, display=$displayId")
        return true
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        log("PointerUp: button=$button, display=$displayId")
        return true
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        log("PointerClick: button=$button, display=$displayId")
        return true
    }

    override fun sendScroll(dx: Float, dy: Float, displayId: Int): Boolean {
        log("Scroll: dx=$dx, dy=$dy, display=$displayId")
        return true
    }

    override fun release() {
        // No-op
    }
}
