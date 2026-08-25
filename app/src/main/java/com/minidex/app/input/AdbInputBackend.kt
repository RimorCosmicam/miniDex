package com.minidex.app.input

import android.util.Log
import com.minidex.app.input.adb.AdbConnectionManager
import com.minidex.app.input.adb.AdbConnectionStatus

/**
 * High-performance, hardware-level InputBackend leveraging Wireless ADB.
 * Dispatches key events, text, and relative mouse / trackpad gestures directly to
 * specific Samsung DeX displays via the native Android 'input' framework.
 */
class AdbInputBackend(
    val adbManager: AdbConnectionManager
) : InputBackend {

    companion object {
        private const val TAG = "AdbInputBackend"
        private const val DEFAULT_WIDTH = 1920f
        private const val DEFAULT_HEIGHT = 1080f
    }

    override val id: String = "adb"
    override val name: String = "Wireless ADB (Zero Latency)"

    override val isAvailable: Boolean
        get() = adbManager.status.value == AdbConnectionStatus.CONNECTED

    override val requiresPrivilegedAccess: Boolean = true

    // Virtual cursor state for target display
    private var cursorX = DEFAULT_WIDTH / 2f
    private var cursorY = DEFAULT_HEIGHT / 2f
    private var isDragging = false

    override suspend fun initialize(): Result<Unit> {
        return if (isAvailable) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Wireless ADB is not connected"))
        }
    }

    private fun buildDisplayFlag(displayId: Int): String {
        return if (displayId >= 0) "-d $displayId " else ""
    }

    override fun sendKeyDown(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        val cmd = "input ${buildDisplayFlag(displayId)}keyevent --longpress $keyCode"
        return adbManager.sendCommand(cmd)
    }

    override fun sendKeyUp(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        // Android 'input' sends full keyevent; for discrete up, keyevent is dispatched
        val cmd = "input ${buildDisplayFlag(displayId)}keyevent $keyCode"
        return adbManager.sendCommand(cmd)
    }

    override fun sendKeyPress(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        val cmd = "input ${buildDisplayFlag(displayId)}keyevent $keyCode"
        return adbManager.sendCommand(cmd)
    }

    override fun sendText(text: String, displayId: Int): Boolean {
        if (!isAvailable) return false
        // Escape characters for shell and input text command
        val escaped = text
            .replace("\\", "\\\\")
            .replace(" ", "%s")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("&", "\\&")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace(";", "\\;")
            .replace("|", "\\|")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("$", "\\$")

        val cmd = "input ${buildDisplayFlag(displayId)}text '$escaped'"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerMove(dx: Float, dy: Float, displayId: Int): Boolean {
        if (!isAvailable) return false
        cursorX = (cursorX + dx).coerceIn(0f, DEFAULT_WIDTH)
        cursorY = (cursorY + dy).coerceIn(0f, DEFAULT_HEIGHT)

        if (isDragging) {
            // Drag gesture update
            val cmd = "input ${buildDisplayFlag(displayId)}motionevent MOVE ${cursorX.toInt()} ${cursorY.toInt()}"
            return adbManager.sendCommand(cmd)
        }
        return true
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        isDragging = true
        val cmd = "input ${buildDisplayFlag(displayId)}motionevent DOWN ${cursorX.toInt()} ${cursorY.toInt()}"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        isDragging = false
        val cmd = "input ${buildDisplayFlag(displayId)}motionevent UP ${cursorX.toInt()} ${cursorY.toInt()}"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        val cmd = if (button == 2) {
            // Secondary / Right Click: context menu key or tap
            "input ${buildDisplayFlag(displayId)}keyevent 82"
        } else {
            // Primary / Left Click tap
            "input ${buildDisplayFlag(displayId)}tap ${cursorX.toInt()} ${cursorY.toInt()}"
        }
        return adbManager.sendCommand(cmd)
    }

    override fun sendScroll(dx: Float, dy: Float, displayId: Int): Boolean {
        if (!isAvailable) return false
        // Invert dy for natural swipe scroll simulation
        val startX = cursorX.toInt()
        val startY = cursorY.toInt()
        val endX = (cursorX + dx * 2f).coerceIn(0f, DEFAULT_WIDTH).toInt()
        val endY = (cursorY - dy * 2f).coerceIn(0f, DEFAULT_HEIGHT).toInt()

        val cmd = "input ${buildDisplayFlag(displayId)}swipe $startX $startY $endX $endY 50"
        return adbManager.sendCommand(cmd)
    }

    override fun release() {
        isDragging = false
    }
}
