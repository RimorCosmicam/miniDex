package com.minidex.app.input

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Native Bluetooth HID Input Backend.
 * Turns the Galaxy Z Flip7 into a real physical Bluetooth Keyboard & Mouse for Samsung DeX.
 * Zero disconnects, zero root, zero Shizuku, 100% hardware-level compatibility.
 *
 * The device will advertise itself as "MiniDex" in Bluetooth scanning/pairing.
 */
class BluetoothHidInputBackend(private val context: Context) : InputBackend {

    companion object {
        private const val TAG = "BluetoothHidBackend"
        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2

        /** The name this device will show as in Bluetooth pairing dialogs. */
        const val BT_DEVICE_NAME = "MiniDex"

        // Standard USB HID Keyboard & Mouse Report Descriptor
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // Keyboard
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // USAGE (Keyboard)
            0xa1.toByte(), 0x01.toByte(), // COLLECTION (Application)
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), // REPORT_ID (1)
            0x05.toByte(), 0x07.toByte(), // USAGE_PAGE (Keyboard)
            0x19.toByte(), 0xe0.toByte(), // USAGE_MINIMUM (Keyboard LeftControl)
            0x29.toByte(), 0xe7.toByte(), // USAGE_MAXIMUM (Keyboard Right GUI)
            0x15.toByte(), 0x00.toByte(), // LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), // LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), // REPORT_SIZE (1)
            0x95.toByte(), 0x08.toByte(), // REPORT_COUNT (8)
            0x81.toByte(), 0x02.toByte(), // INPUT (Data,Var,Abs)
            0x95.toByte(), 0x01.toByte(), // REPORT_COUNT (1)
            0x75.toByte(), 0x08.toByte(), // REPORT_SIZE (8)
            0x81.toByte(), 0x01.toByte(), // INPUT (Cnst,Var,Abs)
            0x95.toByte(), 0x06.toByte(), // REPORT_COUNT (6)
            0x75.toByte(), 0x08.toByte(), // REPORT_SIZE (8)
            0x15.toByte(), 0x00.toByte(), // LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x65.toByte(), // LOGICAL_MAXIMUM (101)
            0x05.toByte(), 0x07.toByte(), // USAGE_PAGE (Keyboard)
            0x19.toByte(), 0x00.toByte(), // USAGE_MINIMUM (Reserved)
            0x29.toByte(), 0x65.toByte(), // USAGE_MAXIMUM (Keyboard Application)
            0x81.toByte(), 0x00.toByte(), // INPUT (Data,Ary,Abs)
            0xc0.toByte(),                // END_COLLECTION

            // Mouse
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // USAGE (Mouse)
            0xa1.toByte(), 0x01.toByte(), // COLLECTION (Application)
            0x85.toByte(), REPORT_ID_MOUSE.toByte(), // REPORT_ID (2)
            0x09.toByte(), 0x01.toByte(), // USAGE (Pointer)
            0xa1.toByte(), 0x00.toByte(), // COLLECTION (Physical)
            0x05.toByte(), 0x09.toByte(), // USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(), // USAGE_MINIMUM (Button 1)
            0x29.toByte(), 0x03.toByte(), // USAGE_MAXIMUM (Button 3)
            0x15.toByte(), 0x00.toByte(), // LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), // LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), // REPORT_SIZE (1)
            0x95.toByte(), 0x03.toByte(), // REPORT_COUNT (3)
            0x81.toByte(), 0x02.toByte(), // INPUT (Data,Var,Abs)
            0x75.toByte(), 0x05.toByte(), // REPORT_SIZE (5)
            0x95.toByte(), 0x01.toByte(), // REPORT_COUNT (1)
            0x81.toByte(), 0x01.toByte(), // INPUT (Cnst,Ary,Abs)
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // USAGE (X)
            0x09.toByte(), 0x31.toByte(), // USAGE (Y)
            0x09.toByte(), 0x38.toByte(), // USAGE (Wheel)
            0x15.toByte(), 0x81.toByte(), // LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(), // LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(), // REPORT_SIZE (8)
            0x95.toByte(), 0x03.toByte(), // REPORT_COUNT (3)
            0x81.toByte(), 0x06.toByte(), // INPUT (Data,Var,Rel)
            0xc0.toByte(),                // END_COLLECTION
            0xc0.toByte()                 // END_COLLECTION
        )
    }

    override val id: String = "BLUETOOTH_HID"
    override val name: String = "Bluetooth HID (MiniDex)"
    override val requiresPrivilegedAccess: Boolean = false

    override val isAvailable: Boolean
        get() = hasBluetoothPermissions() && hidDevice != null

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var mouseButtons: Byte = 0
    private var originalAdapterName: String? = null

    private val _connectionState = MutableStateFlow<BluetoothHidConnectionState>(BluetoothHidConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothHidConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHostDevice = device
                    _connectionState.value = BluetoothHidConnectionState.Connected(
                        deviceName = try { device?.name } catch (_: SecurityException) { null } ?: device?.address ?: "Unknown"
                    )
                    Log.i(TAG, "Bluetooth HID Connected to: ${_connectionState.value}")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = BluetoothHidConnectionState.Connecting
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHostDevice == device) {
                        connectedHostDevice = null
                    }
                    _connectionState.value = BluetoothHidConnectionState.Disconnected
                    Log.i(TAG, "Bluetooth HID Disconnected")
                }
            }
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.i(TAG, "Bluetooth HID App registration status: registered=$registered")
            if (!registered) {
                _connectionState.value = BluetoothHidConnectionState.Disconnected
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectionState.value = BluetoothHidConnectionState.Disconnected
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun getAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    override suspend fun initialize(): Result<Unit> {
        if (!hasBluetoothPermissions()) {
            val msg = "Bluetooth CONNECT permission not granted. Please grant it in Settings."
            _lastError.value = msg
            return Result.failure(SecurityException(msg))
        }

        val adapter = getAdapter()
        if (adapter == null) {
            val msg = "Bluetooth not available on this device"
            _lastError.value = msg
            return Result.failure(IllegalStateException(msg))
        }

        if (!adapter.isEnabled) {
            val msg = "Bluetooth is turned off. Please enable Bluetooth."
            _lastError.value = msg
            return Result.failure(IllegalStateException(msg))
        }

        _lastError.value = null

        // Set the Bluetooth adapter name to "MiniDex" so it shows clearly in pairing
        try {
            val currentName = adapter.name
            if (currentName != BT_DEVICE_NAME) {
                originalAdapterName = currentName
                adapter.name = BT_DEVICE_NAME
                Log.i(TAG, "Bluetooth adapter name changed from '$currentName' to '$BT_DEVICE_NAME'")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot set Bluetooth adapter name (missing permission), will use default", e)
        }

        try {
            adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            val msg = "Failed to get Bluetooth HID profile: ${e.message}"
            _lastError.value = msg
            return Result.failure(e)
        }

        return Result.success(Unit)
    }

    private fun registerHidApp() {
        val hid = hidDevice ?: return
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "MiniDex",
                "Galaxy Z Flip7 DeX Keyboard & Touchpad",
                "MiniDex",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HID_REPORT_DESCRIPTOR
            )
            val registered = hid.registerApp(sdp, null, null, executor, callback)
            if (registered) {
                _lastError.value = null
                Log.i(TAG, "Bluetooth HID App registered successfully as 'MiniDex'")
            } else {
                _lastError.value = "Failed to register HID app. Another app may be using the Bluetooth HID profile."
                Log.e(TAG, "registerApp returned false")
            }
        } catch (e: SecurityException) {
            _lastError.value = "Bluetooth permission denied. Please grant BLUETOOTH_CONNECT permission."
            Log.e(TAG, "Bluetooth permission not granted for HID device", e)
        } catch (e: Exception) {
            _lastError.value = "Bluetooth HID registration failed: ${e.message}"
            Log.e(TAG, "Failed to register HID App", e)
        }
    }

    override fun sendKeyDown(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        val hidKey = HidKeyMap.toHidUsage(keyCode)
        val hidMod = HidKeyMap.toHidModifier(metaState)
        return sendKeyboardReport(hidMod, hidKey)
    }

    override fun sendKeyUp(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        val hidMod = HidKeyMap.toHidModifier(metaState)
        return sendKeyboardReport(hidMod, 0)
    }

    override fun sendKeyPress(keyCode: Int, metaState: Int, displayId: Int): Boolean {
        val down = sendKeyDown(keyCode, metaState, displayId)
        try { Thread.sleep(15) } catch (_: Exception) {}
        val up = sendKeyUp(keyCode, metaState, displayId)
        return down && up
    }

    override fun sendText(text: String, displayId: Int): Boolean {
        for (char in text) {
            val (hidCode, shift) = HidKeyMap.charToHid(char)
            val mod = if (shift) 0x02.toByte() else 0.toByte()
            sendKeyboardReport(mod, hidCode)
            try { Thread.sleep(10) } catch (_: Exception) {}
            sendKeyboardReport(0, 0)
            try { Thread.sleep(10) } catch (_: Exception) {}
        }
        return true
    }

    override fun sendPointerMove(dx: Float, dy: Float, displayId: Int): Boolean {
        val clampedDx = dx.toInt().coerceIn(-127, 127).toByte()
        val clampedDy = dy.toInt().coerceIn(-127, 127).toByte()
        return sendMouseReport(mouseButtons, clampedDx, clampedDy, 0)
    }

    override fun sendPointerDown(button: Int, displayId: Int): Boolean {
        val btnMask = if (button == 2) 0x02.toByte() else 0x01.toByte()
        mouseButtons = (mouseButtons.toInt() or btnMask.toInt()).toByte()
        return sendMouseReport(mouseButtons, 0, 0, 0)
    }

    override fun sendPointerUp(button: Int, displayId: Int): Boolean {
        val btnMask = if (button == 2) 0x02.toByte() else 0x01.toByte()
        mouseButtons = (mouseButtons.toInt() and btnMask.toInt().inv()).toByte()
        return sendMouseReport(mouseButtons, 0, 0, 0)
    }

    override fun sendPointerClick(button: Int, displayId: Int): Boolean {
        sendPointerDown(button, displayId)
        try { Thread.sleep(20) } catch (_: Exception) {}
        sendPointerUp(button, displayId)
        return true
    }

    override fun sendScroll(dx: Float, dy: Float, displayId: Int): Boolean {
        val wheel = dy.toInt().coerceIn(-127, 127).toByte()
        return sendMouseReport(mouseButtons, 0, 0, wheel)
    }

    private fun sendKeyboardReport(modifier: Byte, key: Byte): Boolean {
        val device = connectedHostDevice ?: return false
        val hid = hidDevice ?: return false
        val report = ByteArray(8)
        report[0] = modifier
        report[1] = 0 // Reserved
        report[2] = key
        return try {
            hid.sendReport(device, REPORT_ID_KEYBOARD, report)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending keyboard report", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending keyboard report", e)
            false
        }
    }

    private fun sendMouseReport(buttons: Byte, dx: Byte, dy: Byte, wheel: Byte): Boolean {
        val device = connectedHostDevice ?: return false
        val hid = hidDevice ?: return false
        val report = ByteArray(4)
        report[0] = buttons
        report[1] = dx
        report[2] = dy
        report[3] = wheel
        return try {
            hid.sendReport(device, REPORT_ID_MOUSE, report)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending mouse report", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse report", e)
            false
        }
    }

    override fun release() {
        val hid = hidDevice
        if (hid != null) {
            try {
                hid.unregisterApp()
            } catch (_: Exception) {}
        }

        // Restore original adapter name
        if (originalAdapterName != null) {
            try {
                getAdapter()?.name = originalAdapterName
            } catch (_: SecurityException) {}
            originalAdapterName = null
        }

        val adapter = getAdapter()
        try {
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {}
        hidDevice = null
        connectedHostDevice = null
        _connectionState.value = BluetoothHidConnectionState.Disconnected
    }
}

sealed class BluetoothHidConnectionState {
    data object Disconnected : BluetoothHidConnectionState()
    data object Connecting : BluetoothHidConnectionState()
    data class Connected(val deviceName: String) : BluetoothHidConnectionState()
}

/**
 * Standard HID Keycode translation table.
 */
object HidKeyMap {
    fun toHidModifier(metaState: Int): Byte {
        var mod = 0
        if ((metaState and android.view.KeyEvent.META_CTRL_ON) != 0) mod = mod or 0x01
        if ((metaState and android.view.KeyEvent.META_SHIFT_ON) != 0) mod = mod or 0x02
        if ((metaState and android.view.KeyEvent.META_ALT_ON) != 0) mod = mod or 0x04
        if ((metaState and android.view.KeyEvent.META_META_ON) != 0) mod = mod or 0x08
        return mod.toByte()
    }

    fun toHidUsage(keyCode: Int): Byte {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_A -> 0x04
            android.view.KeyEvent.KEYCODE_B -> 0x05
            android.view.KeyEvent.KEYCODE_C -> 0x06
            android.view.KeyEvent.KEYCODE_D -> 0x07
            android.view.KeyEvent.KEYCODE_E -> 0x08
            android.view.KeyEvent.KEYCODE_F -> 0x09
            android.view.KeyEvent.KEYCODE_G -> 0x0A
            android.view.KeyEvent.KEYCODE_H -> 0x0B
            android.view.KeyEvent.KEYCODE_I -> 0x0C
            android.view.KeyEvent.KEYCODE_J -> 0x0D
            android.view.KeyEvent.KEYCODE_K -> 0x0E
            android.view.KeyEvent.KEYCODE_L -> 0x0F
            android.view.KeyEvent.KEYCODE_M -> 0x10
            android.view.KeyEvent.KEYCODE_N -> 0x11
            android.view.KeyEvent.KEYCODE_O -> 0x12
            android.view.KeyEvent.KEYCODE_P -> 0x13
            android.view.KeyEvent.KEYCODE_Q -> 0x14
            android.view.KeyEvent.KEYCODE_R -> 0x15
            android.view.KeyEvent.KEYCODE_S -> 0x16
            android.view.KeyEvent.KEYCODE_T -> 0x17
            android.view.KeyEvent.KEYCODE_U -> 0x18
            android.view.KeyEvent.KEYCODE_V -> 0x19
            android.view.KeyEvent.KEYCODE_W -> 0x1A
            android.view.KeyEvent.KEYCODE_X -> 0x1B
            android.view.KeyEvent.KEYCODE_Y -> 0x1C
            android.view.KeyEvent.KEYCODE_Z -> 0x1D
            android.view.KeyEvent.KEYCODE_1 -> 0x1E
            android.view.KeyEvent.KEYCODE_2 -> 0x1F
            android.view.KeyEvent.KEYCODE_3 -> 0x20
            android.view.KeyEvent.KEYCODE_4 -> 0x21
            android.view.KeyEvent.KEYCODE_5 -> 0x22
            android.view.KeyEvent.KEYCODE_6 -> 0x23
            android.view.KeyEvent.KEYCODE_7 -> 0x24
            android.view.KeyEvent.KEYCODE_8 -> 0x25
            android.view.KeyEvent.KEYCODE_9 -> 0x26
            android.view.KeyEvent.KEYCODE_0 -> 0x27
            android.view.KeyEvent.KEYCODE_ENTER -> 0x28
            android.view.KeyEvent.KEYCODE_ESCAPE -> 0x29
            android.view.KeyEvent.KEYCODE_DEL -> 0x2A
            android.view.KeyEvent.KEYCODE_TAB -> 0x2B
            android.view.KeyEvent.KEYCODE_SPACE -> 0x2C
            android.view.KeyEvent.KEYCODE_MINUS -> 0x2D
            android.view.KeyEvent.KEYCODE_EQUALS -> 0x2E
            android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> 0x2F
            android.view.KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x30
            android.view.KeyEvent.KEYCODE_BACKSLASH -> 0x31
            android.view.KeyEvent.KEYCODE_SEMICOLON -> 0x33
            android.view.KeyEvent.KEYCODE_APOSTROPHE -> 0x34
            android.view.KeyEvent.KEYCODE_GRAVE -> 0x35
            android.view.KeyEvent.KEYCODE_COMMA -> 0x36
            android.view.KeyEvent.KEYCODE_PERIOD -> 0x37
            android.view.KeyEvent.KEYCODE_SLASH -> 0x38
            android.view.KeyEvent.KEYCODE_CAPS_LOCK -> 0x39
            android.view.KeyEvent.KEYCODE_F1 -> 0x3A
            android.view.KeyEvent.KEYCODE_F2 -> 0x3B
            android.view.KeyEvent.KEYCODE_F3 -> 0x3C
            android.view.KeyEvent.KEYCODE_F4 -> 0x3D
            android.view.KeyEvent.KEYCODE_F5 -> 0x3E
            android.view.KeyEvent.KEYCODE_F6 -> 0x3F
            android.view.KeyEvent.KEYCODE_F7 -> 0x40
            android.view.KeyEvent.KEYCODE_F8 -> 0x41
            android.view.KeyEvent.KEYCODE_F9 -> 0x42
            android.view.KeyEvent.KEYCODE_F10 -> 0x43
            android.view.KeyEvent.KEYCODE_F11 -> 0x44
            android.view.KeyEvent.KEYCODE_F12 -> 0x45
            android.view.KeyEvent.KEYCODE_SYSRQ -> 0x46
            android.view.KeyEvent.KEYCODE_INSERT -> 0x49
            android.view.KeyEvent.KEYCODE_MOVE_HOME -> 0x4A
            android.view.KeyEvent.KEYCODE_PAGE_UP -> 0x4B
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> 0x4C
            android.view.KeyEvent.KEYCODE_MOVE_END -> 0x4D
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> 0x4E
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 0x4F
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 0x50
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 0x51
            android.view.KeyEvent.KEYCODE_DPAD_UP -> 0x52
            else -> 0x00
        }.toByte()
    }

    fun charToHid(char: Char): Pair<Byte, Boolean> {
        return when (char) {
            in 'a'..'z' -> Pair((0x04 + (char - 'a')).toByte(), false)
            in 'A'..'Z' -> Pair((0x04 + (char - 'A')).toByte(), true)
            in '1'..'9' -> Pair((0x1E + (char - '1')).toByte(), false)
            '0' -> Pair(0x27.toByte(), false)
            ' ' -> Pair(0x2C.toByte(), false)
            '\n' -> Pair(0x28.toByte(), false)
            '\t' -> Pair(0x2B.toByte(), false)
            '!' -> Pair(0x1E.toByte(), true)
            '@' -> Pair(0x1F.toByte(), true)
            '#' -> Pair(0x20.toByte(), true)
            '$' -> Pair(0x21.toByte(), true)
            '%' -> Pair(0x22.toByte(), true)
            '^' -> Pair(0x23.toByte(), true)
            '&' -> Pair(0x24.toByte(), true)
            '*' -> Pair(0x25.toByte(), true)
            '(' -> Pair(0x26.toByte(), true)
            ')' -> Pair(0x27.toByte(), true)
            '-' -> Pair(0x2D.toByte(), false)
            '_' -> Pair(0x2D.toByte(), true)
            '=' -> Pair(0x2E.toByte(), false)
            '+' -> Pair(0x2E.toByte(), true)
            '[' -> Pair(0x2F.toByte(), false)
            '{' -> Pair(0x2F.toByte(), true)
            ']' -> Pair(0x30.toByte(), false)
            '}' -> Pair(0x30.toByte(), true)
            '\\' -> Pair(0x31.toByte(), false)
            '|' -> Pair(0x31.toByte(), true)
            ';' -> Pair(0x33.toByte(), false)
            ':' -> Pair(0x33.toByte(), true)
            '\'' -> Pair(0x34.toByte(), false)
            '"' -> Pair(0x34.toByte(), true)
            '`' -> Pair(0x35.toByte(), false)
            '~' -> Pair(0x35.toByte(), true)
            ',' -> Pair(0x36.toByte(), false)
            '<' -> Pair(0x36.toByte(), true)
            '.' -> Pair(0x37.toByte(), false)
            '>' -> Pair(0x37.toByte(), true)
            '/' -> Pair(0x38.toByte(), false)
            '?' -> Pair(0x38.toByte(), true)
            else -> Pair(0.toByte(), false)
        }
    }
}
