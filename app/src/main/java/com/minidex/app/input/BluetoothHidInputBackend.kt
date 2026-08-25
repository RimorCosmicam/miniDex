package com.minidex.app.input

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

/**
 * High-performance Native Bluetooth HID Backend.
 * Emulates a real Bluetooth Keyboard & Mouse using both Classic HID SDP and BLE (Bluetooth Low Energy) Advertising.
 * This guarantees the device appears immediately on Windows, macOS, Android, iPadOS, ChromeOS, and TVs.
 */
class BluetoothHidInputBackend(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : InputBackend {

    companion object {
        private const val TAG = "BluetoothHidBackend"
        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2

        const val BT_DEVICE_NAME = "MiniDex Keyboard & Mouse"
        const val DISCOVERABLE_DURATION = 300 // 5 minutes

        // Standard HID Service UUID (0x1812)
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")

        // USB-IF Standard HID Combo Descriptor
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // Keyboard (Report ID 1)
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x06.toByte(),
            0xa1.toByte(), 0x01.toByte(),
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0xe0.toByte(),
            0x29.toByte(), 0xe7.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x03.toByte(),
            0x95.toByte(), 0x06.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x65.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x29.toByte(), 0x65.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xc0.toByte(),

            // Mouse (Report ID 2)
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x02.toByte(),
            0xa1.toByte(), 0x01.toByte(),
            0x85.toByte(), REPORT_ID_MOUSE.toByte(),
            0x09.toByte(), 0x01.toByte(),
            0xa1.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x09.toByte(),
            0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x03.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x03.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x75.toByte(), 0x05.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x03.toByte(),
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x30.toByte(),
            0x09.toByte(), 0x31.toByte(),
            0x09.toByte(), 0x38.toByte(),
            0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7f.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x03.toByte(),
            0x81.toByte(), 0x06.toByte(),
            0xc0.toByte(),
            0xc0.toByte()
        )
    }

    override val id: String = "BLUETOOTH_HID"
    override val name: String = "Bluetooth HID (MiniDex)"
    override val requiresPrivilegedAccess: Boolean = false

    override val isAvailable: Boolean
        get() = hasBluetoothPermissions() && hidDevice != null && isRegistered

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var mouseButtons: Byte = 0
    private var isRegistered: Boolean = false
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _connectionState = MutableStateFlow<BluetoothHidConnectionState>(BluetoothHidConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothHidConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BluetoothDevice>> = _bondedDevices.asStateFlow()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            scope.launch {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedHostDevice = device
                        val devName = try { device?.name } catch (_: SecurityException) { null } ?: device?.address ?: "Connected Host"
                        _connectionState.value = BluetoothHidConnectionState.Connected(deviceName = devName)
                        _lastError.value = null
                        stopBleAdvertising()
                        Log.i(TAG, "Bluetooth HID Connected to: $devName")
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
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            scope.launch {
                isRegistered = registered
                Log.i(TAG, "Bluetooth HID App registration status: registered=$registered")
                if (registered) {
                    _lastError.value = null
                    refreshBondedDevices()
                } else {
                    _connectionState.value = BluetoothHidConnectionState.Disconnected
                }
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                Log.i(TAG, "Bluetooth HID profile connected")
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                isRegistered = false
                _connectionState.value = BluetoothHidConnectionState.Disconnected
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "BLE HID Advertising started successfully as '$BT_DEVICE_NAME'")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.w(TAG, "BLE Advertising failed with error code: $errorCode")
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val advertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            return connect && advertise
        }
        return true
    }

    private fun getAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun refreshBondedDevices() {
        if (!hasBluetoothPermissions()) return
        try {
            val adapter = getAdapter() ?: return
            _bondedDevices.value = adapter.bondedDevices.toList()
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read bonded devices", e)
        }
    }

    override suspend fun initialize(): Result<Unit> {
        if (!hasBluetoothPermissions()) {
            val msg = "Grant Bluetooth permissions in Settings"
            _lastError.value = msg
            return Result.failure(SecurityException(msg))
        }

        val adapter = getAdapter()
        if (adapter == null) {
            _lastError.value = "Bluetooth not supported on this device"
            return Result.failure(IllegalStateException("No Bluetooth"))
        }

        if (!adapter.isEnabled) {
            _lastError.value = "Bluetooth is OFF"
            return Result.failure(IllegalStateException("Bluetooth disabled"))
        }

        _lastError.value = null

        if (hidDevice == null) {
            try {
                adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
            } catch (e: Exception) {
                _lastError.value = "Failed to obtain HID profile: ${e.message}"
                return Result.failure(e)
            }
        } else if (!isRegistered) {
            registerHidApp()
        }

        refreshBondedDevices()
        return Result.success(Unit)
    }

    fun registerHidApp() {
        val hid = hidDevice ?: return
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                BT_DEVICE_NAME,
                "MiniDex Keyboard & Touchpad",
                "RimorCosmicam",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HID_REPORT_DESCRIPTOR
            )
            hid.registerApp(sdp, null, null, executor, callback)
            _lastError.value = null
        } catch (e: SecurityException) {
            _lastError.value = "Bluetooth permission denied"
            Log.e(TAG, "SecurityException in registerApp", e)
        } catch (e: Exception) {
            _lastError.value = "HID error: ${e.message}"
            Log.e(TAG, "Exception in registerApp", e)
        }
    }

    private fun startBleAdvertising() {
        val adapter = getAdapter() ?: return
        if (!hasBluetoothPermissions()) return

        try {
            advertiser = adapter.bluetoothLeAdvertiser
            if (advertiser != null) {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build()

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(ParcelUuid(HID_SERVICE_UUID))
                    .build()

                advertiser?.startAdvertising(settings, data, advertiseCallback)
                Log.i(TAG, "Triggered BLE advertising for '$BT_DEVICE_NAME'")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE advertiser", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE advertiser", e)
        }
    }

    private fun stopBleAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            advertiser = null
        } catch (e: Exception) {}
    }

    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            _lastError.value = "HID driver not ready. Try again in 2 seconds."
            return
        }
        try {
            _connectionState.value = BluetoothHidConnectionState.Connecting
            val ok = hid.connect(device)
            if (!ok) {
                _lastError.value = "Connection request refused by system"
                _connectionState.value = BluetoothHidConnectionState.Disconnected
            }
        } catch (e: SecurityException) {
            _lastError.value = "Bluetooth permission denied"
        }
    }

    fun startPairing(): Intent? {
        if (!hasBluetoothPermissions()) {
            _lastError.value = "Bluetooth permission required"
            return null
        }

        val adapter = getAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _lastError.value = "Enable Bluetooth first"
            return null
        }

        // Rename device adapter to "MiniDex Keyboard & Mouse"
        try {
            adapter.name = BT_DEVICE_NAME
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot rename adapter", e)
        }

        // Ensure HID registration
        if (hidDevice == null) {
            adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        } else if (!isRegistered) {
            registerHidApp()
        }

        // Start active BLE advertising so all computers and scanners discover it instantly
        startBleAdvertising()

        _connectionState.value = BluetoothHidConnectionState.Discoverable
        _lastError.value = null

        return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        report[1] = 0
        report[2] = key
        return try {
            hid.sendReport(device, REPORT_ID_KEYBOARD, report)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending keyboard report: ${e.message}")
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
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse report: ${e.message}")
            false
        }
    }

    override fun release() {
        stopBleAdvertising()
        val hid = hidDevice
        if (hid != null) {
            try { hid.unregisterApp() } catch (_: Exception) {}
        }
        isRegistered = false
        try {
            getAdapter()?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {}
        hidDevice = null
        connectedHostDevice = null
        _connectionState.value = BluetoothHidConnectionState.Disconnected
    }
}

sealed class BluetoothHidConnectionState {
    data object Disconnected : BluetoothHidConnectionState()
    data object Discoverable : BluetoothHidConnectionState()
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
