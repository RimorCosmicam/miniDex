package com.minidex.app.input

import android.util.Log
import android.view.KeyEvent
import com.minidex.app.domain.model.CursorMode
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
    private var displayWidth = DEFAULT_WIDTH
    private var displayHeight = DEFAULT_HEIGHT
    private var cursorX = displayWidth / 2f
    private var cursorY = displayHeight / 2f
    private var isDragging = false
    private var hidRemainderX = 0f
    private var hidRemainderY = 0f
    private var scrollRemainderX = 0f
    private var scrollRemainderY = 0f
    private var cursorMode = CursorMode.AUTO_NATIVE

    fun setCursorMode(mode: CursorMode) {
        cursorMode = mode
        adbManager.setCursorMode(mode)
    }

    fun setDisplayBounds(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        displayWidth = width.toFloat()
        displayHeight = height.toFloat()
        cursorX = cursorX.coerceIn(0f, displayWidth - 1f)
        cursorY = cursorY.coerceIn(0f, displayHeight - 1f)
    }

    override suspend fun initialize(): Result<Unit> {
        return if (isAvailable) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Wireless ADB is not connected"))
        }
    }

    private fun inputPrefix(source: String, displayId: Int): String {
        val display = if (displayId >= 0) " -d $displayId" else ""
        return "input $source$display"
    }

    private fun keyCombination(keyCode: Int, metaState: Int): List<Int> = buildList {
        if (metaState and KeyEvent.META_CTRL_ON != 0) add(KeyEvent.KEYCODE_CTRL_LEFT)
        if (metaState and KeyEvent.META_ALT_ON != 0) add(KeyEvent.KEYCODE_ALT_LEFT)
        if (metaState and KeyEvent.META_SHIFT_ON != 0) add(KeyEvent.KEYCODE_SHIFT_LEFT)
        if (metaState and KeyEvent.META_META_ON != 0) add(KeyEvent.KEYCODE_META_LEFT)
        add(keyCode)
    }

    override fun sendKeyDown(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        val cmd = "${inputPrefix("keyboard", displayId)} keyevent --longpress $keyCode"
        return adbManager.sendCommand(cmd)
    }

    override fun sendKeyUp(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        // Android 'input' sends full keyevent; for discrete up, keyevent is dispatched
        val cmd = "${inputPrefix("keyboard", displayId)} keyevent $keyCode"
        return adbManager.sendCommand(cmd)
    }

    override fun sendKeyPress(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        val keys = keyCombination(keyCode, metaState)
        val cmd = if (keys.size > 1) {
            "${inputPrefix("keyboard", displayId)} keycombination ${keys.joinToString(" ")}"
        } else {
            "${inputPrefix("keyboard", displayId)} keyevent $keyCode"
        }
        return adbManager.sendCommand(cmd)
    }

    override fun sendText(text: String, displayId: Int): Boolean {
        if (!isAvailable) return false
        val encoded = text.replace(" ", "%s")
        val shellQuoted = "'${encoded.replace("'", "'\"'\"'")}'"
        val cmd = "${inputPrefix("keyboard", displayId)} text $shellQuoted"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerMove(dx: Float, dy: Float, displayId: Int): Boolean {
        if (!isAvailable) return false
        cursorX = (cursorX + dx).coerceIn(0f, displayWidth - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, displayHeight - 1f)

        val totalX = dx + hidRemainderX
        val totalY = dy + hidRemainderY
        val relativeX = totalX.toInt()
        val relativeY = totalY.toInt()
        hidRemainderX = totalX - relativeX
        hidRemainderY = totalY - relativeY
        if (adbManager.sendHidPointerMove(relativeX, relativeY)) return true

        // Compatibility fallback for devices whose ROM omits Android's `hid` command.
        val cmd = "${inputPrefix("mouse", displayId)} motionevent MOVE ${cursorX.toInt()} ${cursorY.toInt()}"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        isDragging = true
        if (adbManager.sendHidPointerButton(button, true)) return true
        val cmd = "${inputPrefix("mouse", displayId)} motionevent DOWN ${cursorX.toInt()} ${cursorY.toInt()}"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        isDragging = false
        if (adbManager.sendHidPointerButton(button, false)) return true
        val cmd = "${inputPrefix("mouse", displayId)} motionevent UP ${cursorX.toInt()} ${cursorY.toInt()}"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        if (adbManager.sendHidPointerClick(button)) return true
        val cmd = if (button == 2) {
            // Secondary / Right Click: context menu key or tap
            "${inputPrefix("keyboard", displayId)} keyevent ${KeyEvent.KEYCODE_MENU}"
        } else {
            "${inputPrefix("mouse", displayId)} tap ${cursorX.toInt()} ${cursorY.toInt()}"
        }
        return adbManager.sendCommand(cmd)
    }

    override fun sendScroll(dx: Float, dy: Float, displayId: Int): Boolean {
        if (!isAvailable) return false
        val totalX = scrollRemainderX - dx
        val totalY = scrollRemainderY - dy
        val horizontalWheel = totalX.toInt()
        val verticalWheel = totalY.toInt()
        scrollRemainderX = totalX - horizontalWheel
        scrollRemainderY = totalY - verticalWheel
        if (adbManager.sendHidScroll(horizontalWheel, verticalWheel)) return true

        // Invert dy for natural swipe scroll simulation
        val startX = cursorX.toInt()
        val startY = cursorY.toInt()
        val endX = (cursorX + dx * 2f).coerceIn(0f, displayWidth - 1f).toInt()
        val endY = (cursorY - dy * 2f).coerceIn(0f, displayHeight - 1f).toInt()

        val cmd = "${inputPrefix("touchscreen", displayId)} swipe $startX $startY $endX $endY 50"
        return adbManager.sendCommand(cmd)
    }

    override fun release() {
        if (isDragging) adbManager.sendHidPointerButton(1, false)
        isDragging = false
    }
}
