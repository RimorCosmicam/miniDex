package com.minidex.app.input

/**
 * Abstraction for sending input events (keyboard, mouse, touchpad, gestures) to the target DeX display.
 */
interface InputBackend {
    val id: String
    val name: String
    val isAvailable: Boolean
    val requiresPrivilegedAccess: Boolean

    suspend fun initialize(): Result<Unit>

    fun sendKeyDown(keyCode: Int, metaState: Int = 0, displayId: Int = -1): Boolean
    fun sendKeyUp(keyCode: Int, metaState: Int = 0, displayId: Int = -1): Boolean
    fun sendKeyPress(keyCode: Int, metaState: Int = 0, displayId: Int = -1): Boolean

    fun sendText(text: String, displayId: Int = -1): Boolean

    fun sendPointerMove(dx: Float, dy: Float, displayId: Int = -1): Boolean
    fun sendPointerDown(button: Int = 1, displayId: Int = -1): Boolean // 1 = PRIMARY (Left), 2 = SECONDARY (Right)
    fun sendPointerUp(button: Int = 1, displayId: Int = -1): Boolean
    fun sendPointerClick(button: Int = 1, displayId: Int = -1): Boolean

    fun sendScroll(dx: Float, dy: Float, displayId: Int = -1): Boolean

    fun release()
}
