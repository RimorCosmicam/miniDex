package com.minidex.app.input

import android.util.Log
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import com.minidex.app.domain.model.CursorMode
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
import com.minidex.app.input.adb.AdbConnectionManager
import com.minidex.app.input.adb.AdbConnectionStatus

/** Sends keyboard and pointer input through an authenticated Wireless ADB connection. */
class AdbInputBackend(
    val adbManager: AdbConnectionManager,
    private val fakeCursor: FakeCursorOverlay
) : InputBackend {

    companion object {
        private const val TAG = "AdbInputBackend"
        private const val DEFAULT_WIDTH = 1920f
        private const val DEFAULT_HEIGHT = 1080f
    }

    override val id: String = "adb"
    override val name: String = "Wireless ADB"

    override val isAvailable: Boolean
        get() = adbManager.status.value == AdbConnectionStatus.CONNECTED

    override val requiresPrivilegedAccess: Boolean = true

    // Virtual cursor state for target display
    private var displayWidth = DEFAULT_WIDTH
    private var displayHeight = DEFAULT_HEIGHT
    private var cursorX = displayWidth / 2f
    private var cursorY = displayHeight / 2f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragDisplayId = -1
    private var cursorMode = CursorMode.AUTO_NATIVE
    private var targetDisplayId = -1
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingScrollX = 0f
    private var pendingScrollY = 0f
    private var pendingScrollDisplayId = -1
    private var scrollFlushScheduled = false
    private val flushScroll = Runnable { flushPendingScroll() }

    fun setCursorMode(mode: CursorMode) {
        cursorMode = mode
        adbManager.setCursorMode(mode)
    }

    fun setDisplayBounds(displayId: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (targetDisplayId != displayId) {
            adbManager.keepFlexViewExclusiveFor(displayId)
        }
        targetDisplayId = displayId
        displayWidth = width.toFloat()
        displayHeight = height.toFloat()
        cursorX = cursorX.coerceIn(0f, displayWidth - 1f)
        cursorY = cursorY.coerceIn(0f, displayHeight - 1f)
        fakeCursor.showAt(displayId, cursorX, cursorY)
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

        targetDisplayId = displayId
        fakeCursor.showAt(displayId, cursorX, cursorY)
        // Do not move the global HID pointer: Samsung associates that hidden
        // device with FlexView/default display and launches apps there.
        return true
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        isDragging = true
        dragStartX = cursorX
        dragStartY = cursorY
        dragDisplayId = displayId
        return true
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        if (!isDragging) return true
        isDragging = false
        val target = if (dragDisplayId >= 0) dragDisplayId else displayId
        if (MiniDexAccessibilityService.instance?.dispatchDrag(
                dragStartX,
                dragStartY,
                cursorX,
                cursorY,
                target,
                80L
            ) == true
        ) return true
        val cmd = "${inputPrefix("touchscreen", target)} swipe " +
            "${dragStartX.toInt()} ${dragStartY.toInt()} ${cursorX.toInt()} ${cursorY.toInt()} 80"
        return adbManager.sendCommand(cmd)
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        if (!isAvailable) return false
        adbManager.guardNextLaunchOnDisplay(displayId)
        if (button == 1 && MiniDexAccessibilityService.instance?.dispatchClick(
                cursorX,
                cursorY,
                displayId
            ) == true
        ) return true
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
        pendingScrollX += dx
        pendingScrollY += dy
        pendingScrollDisplayId = displayId
        if (!scrollFlushScheduled) {
            scrollFlushScheduled = true
            mainHandler.postDelayed(flushScroll, 60L)
        }
        return true
    }

    private fun flushPendingScroll() {
        scrollFlushScheduled = false
        val dx = pendingScrollX
        val dy = pendingScrollY
        val displayId = pendingScrollDisplayId
        pendingScrollX = 0f
        pendingScrollY = 0f
        if (displayId < 0 || (dx == 0f && dy == 0f)) return
        val startX = cursorX.toInt()
        val startY = cursorY.toInt()
        val gestureX = dx * 12f
        val gestureY = dy * 12f
        val endX = (cursorX + gestureX).coerceIn(0f, displayWidth - 1f).toInt()
        val endY = (cursorY - gestureY).coerceIn(0f, displayHeight - 1f).toInt()

        if (MiniDexAccessibilityService.instance?.dispatchScroll(
                cursorX,
                cursorY,
                gestureX,
                gestureY,
                displayId
            ) == true
        ) return

        val cmd = "${inputPrefix("touchscreen", displayId)} swipe $startX $startY $endX $endY 50"
        adbManager.sendCommand(cmd)
    }

    override fun release() {
        isDragging = false
        mainHandler.removeCallbacks(flushScroll)
        scrollFlushScheduled = false
        fakeCursor.remove()
    }
}
