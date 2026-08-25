package com.minidex.app.input

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Method

/**
 * Privileged Input Backend leveraging Shizuku to inject real hardware-level
 * KeyEvents and MotionEvents targeted at the Samsung DeX display.
 */
class ShizukuInputBackend : InputBackend {

    companion object {
        private const val TAG = "ShizukuInputBackend"
        private const val INJECT_MODE_ASYNC = 0
    }

    override val id: String = "SHIZUKU"
    override val name: String = "Shizuku Privileged (DeX Direct)"
    override val requiresPrivilegedAccess: Boolean = true

    override val isAvailable: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

    private var iInputManager: Any? = null
    private var injectInputEventMethod: Method? = null
    private var setDisplayIdMethod: Method? = null

    // Track simulated pointer coordinates for relative mouse movements
    private var pointerX: Float = 960f
    private var pointerY: Float = 540f
    private var isLeftButtonDown: Boolean = false
    private var isRightButtonDown: Boolean = false

    override suspend fun initialize(): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("")
            }

            if (!Shizuku.pingBinder()) {
                return Result.failure(IllegalStateException("Shizuku service is not running"))
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return Result.failure(SecurityException("Shizuku permission not granted"))
            }

            // Obtain privileged IInputManager service binder via ServiceManager reflection wrapped in ShizukuBinderWrapper
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val rawBinder = getServiceMethod.invoke(null, "input") as? IBinder
                ?: return Result.failure(IllegalStateException("Failed to get input service from ServiceManager"))

            val wrappedBinder = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            iInputManager = asInterfaceMethod.invoke(null, wrappedBinder)

            val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
            injectInputEventMethod = try {
                iInputManagerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            } catch (e: NoSuchMethodException) {
                // Android 14+ may have injectInputEventToTarget or overload
                iInputManagerClass.methods.firstOrNull { it.name == "injectInputEvent" }
            }

            // Cache setDisplayId method for InputEvent
            setDisplayIdMethod = try {
                InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
            } catch (e: Exception) {
                null
            }

            Log.i(TAG, "ShizukuInputBackend initialized successfully")
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize ShizukuInputBackend", e)
            Result.failure(e)
        }
    }

    private fun injectEvent(event: InputEvent, displayId: Int): Boolean {
        if (displayId >= 0 && setDisplayIdMethod != null) {
            try {
                setDisplayIdMethod?.invoke(event, displayId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set displayId on event: ${e.message}")
            }
        }

        val manager = iInputManager
        val method = injectInputEventMethod

        if (manager != null && method != null) {
            return try {
                val result = method.invoke(manager, event, INJECT_MODE_ASYNC) as? Boolean ?: true
                result
            } catch (e: Exception) {
                Log.e(TAG, "Direct injection failed: ${e.message}")
                false
            }
        }
        return false
    }

    override fun sendKeyDown(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        return injectEvent(event, displayId)
    }

    override fun sendKeyUp(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        return injectEvent(event, displayId)
    }

    override fun sendKeyPress(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        val down = sendKeyDown(keyCode, metaState, displayId)
        val up = sendKeyUp(keyCode, metaState, displayId)
        return down && up
    }

    override fun sendText(text: String, displayId: Int): Boolean {
        var allSuccess = true
        for (char in text) {
            val keyCodes = KeyCharacterMapCompat.getEventsForChar(char)
            if (keyCodes != null) {
                for (event in keyCodes) {
                    if (!injectEvent(event, displayId)) {
                        allSuccess = false
                    }
                }
            } else {
                // Fallback: send simple ASCII key code if available
                val asciiCode = char.code
                val fallbackEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, 0)
                injectEvent(fallbackEvent, displayId)
            }
        }
        return allSuccess
    }

    override fun sendPointerMove(dx: Float, dy: Float, displayId: Int): Boolean {
        pointerX = (pointerX + dx).coerceIn(0f, 3840f)
        pointerY = (pointerY + dy).coerceIn(0f, 2160f)

        val now = SystemClock.uptimeMillis()
        val action = if (isLeftButtonDown || isRightButtonDown) {
            MotionEvent.ACTION_MOVE
        } else {
            MotionEvent.ACTION_HOVER_MOVE
        }

        val buttonState = (if (isLeftButtonDown) MotionEvent.BUTTON_PRIMARY else 0) or
                (if (isRightButtonDown) MotionEvent.BUTTON_SECONDARY else 0)

        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )

        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = pointerX
                y = pointerY
                setAxisValue(MotionEvent.AXIS_RELATIVE_X, dx)
                setAxisValue(MotionEvent.AXIS_RELATIVE_Y, dy)
            }
        )

        val event = MotionEvent.obtain(
            now, now,
            action,
            1,
            props,
            coords,
            0,
            buttonState,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        )

        val success = injectEvent(event, displayId)
        event.recycle()
        return success
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val buttonFlag = if (button == 2) MotionEvent.BUTTON_SECONDARY else MotionEvent.BUTTON_PRIMARY

        if (button == 2) isRightButtonDown = true else isLeftButtonDown = true

        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = pointerX
                y = pointerY
            }
        )

        val event = MotionEvent.obtain(
            now, now,
            MotionEvent.ACTION_DOWN,
            1,
            props,
            coords,
            0,
            buttonFlag,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        )

        val success = injectEvent(event, displayId)
        event.recycle()
        return success
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val buttonFlag = if (button == 2) MotionEvent.BUTTON_SECONDARY else MotionEvent.BUTTON_PRIMARY

        if (button == 2) isRightButtonDown = false else isLeftButtonDown = false

        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = pointerX
                y = pointerY
            }
        )

        val event = MotionEvent.obtain(
            now, now,
            MotionEvent.ACTION_UP,
            1,
            props,
            coords,
            0,
            buttonFlag,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        )

        val success = injectEvent(event, displayId)
        event.recycle()
        return success
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        val down = sendPointerDown(button, displayId)
        SystemClock.sleep(20)
        val up = sendPointerUp(button, displayId)
        return down && up
    }

    override fun sendScroll(dx: Float, dy: Float, displayId: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = pointerX
                y = pointerY
                setAxisValue(MotionEvent.AXIS_VSCROLL, dy)
                setAxisValue(MotionEvent.AXIS_HSCROLL, dx)
            }
        )

        val event = MotionEvent.obtain(
            now, now,
            MotionEvent.ACTION_SCROLL,
            1,
            props,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        )

        val success = injectEvent(event, displayId)
        event.recycle()
        return success
    }

    override fun release() {
        iInputManager = null
        injectInputEventMethod = null
        setDisplayIdMethod = null
    }
}

/**
 * Key Character Map helper for translating characters to KeyEvent sequences.
 */
object KeyCharacterMapCompat {
    private val keyCharacterMap = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)

    fun getEventsForChar(char: Char): Array<KeyEvent>? {
        return keyCharacterMap.getEvents(charArrayOf(char))
    }
}
