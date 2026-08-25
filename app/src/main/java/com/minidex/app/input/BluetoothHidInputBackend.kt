package com.minidex.app.input

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Native Bluetooth HID Input Backend.
 * Emulates a standard physical USB/Bluetooth Keyboard & Mouse directly over Bluetooth.
 *
 * App Name in Bluetooth: "MiniDex Keyboard & Mouse"
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

        // Standard USB-IF HID Combo (Keyboard + Mouse) Report Descriptor
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // --- KEYBOARD (Report ID 1) ---
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // USAGE (Keyboard)
            0xa1.toByte(), 0x01.toByte(), // COLLECTION (Application)
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), // REPORT_ID (1)
            0x05.toByte(), 0x07.toByte(), // USAGE_PAGE (Keyboard/Keypad)
            0x19.toByte(), 0xe0.toByte(), // USAGE_MINIMUM (Keyboard LeftControl)
            0x29.toByte(), 0xe7.toByte(), // USAGE_MAXIMUM (Keyboard Right GUI)
            0x15.toByte(), 0x00.toByte(), // LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), // LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), // REPORT_SIZE (1)
            0x95.toByte(), 0x08.toByte(), // REPORT_COUNT (8)
            0x81.toByte(), 0x02.toByte(), // INPUT (Data,Var,Abs)
            0x95.toByte(), 0x01.toByte(), // REPORT_COUNT (1)
            0x75.toByte(), 0x08.toByte(), // REPORT_SIZE (8)
            0x81.toByte(), 0x03.toByte(), // INPUT (Cnst,Var,Abs)
            0x95.toByte(), 0x06.toByte(), // REPORT_COUNT (6)
            0x75.toByte(), 0x08.toByte(), // REPORT_SIZE (8)
            0x15.toByte(), 0x00.toByte(), // LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x65.toByte(), // LOGICAL_MAXIMUM (101)
            0x05.toByte(), 0x07.toByte(), // USAGE_PAGE (Keyboard/Keypad)
            0x19.toByte(), 0x00.toByte(), // USAGE_MINIMUM (Reserved)
            0x29.toByte(), 0x65.toByte(), // USAGE_MAXIMUM (Keyboard Application)
            0x81.toByte(), 0x00.toByte(), // INPUT (Data,Ary,Abs)
            0xc0.toByte(),                // END_COLLECTION

            // --- MOUSE (Report ID 2) ---
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
            0x81.toByte(), 0x03.toByte(), // INPUT (Cnst,Var,Abs)
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
    override val name: String = "Bluetooth HID (Hardware Emulation)"
    override val requiresPrivilegedAccess: Boolean = false

    override val isAvailable: Boolean
        get() = hasBluetoothPermissions() && hidDevice != null && isRegistered

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var mouseButtons: Byte = 0
    private var isRegistered: Boolean = false

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
                        Log.i(TAG, "Bluetooth HID Disconnected from device")
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
                Log.i(TAG, "BluetoothProfile.HID_DEVICE connected proxy")
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
            val msg = "Grant Bluetooth permissions in Settings to enable Bluetooth HID"
            _lastError.value = msg
            return Result.failure(SecurityException(msg))
        }

        val adapter = getAdapter()
        if (adapter == null) {
            _lastError.value = "Bluetooth not supported on this device"
            return Result.failure(IllegalStateException("No Bluetooth"))
        }

        if (!adapter.isEnabled) {
            _lastError.value = "Bluetooth is turned OFF"
            return Result.failure(IllegalStateException("Bluetooth disabled"))
        }

        _lastError.value = null

        // Connect proxy if not already connected
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
                "MiniDex Cover Screen Keyboard & Trackpad",
                "RimorCosmicam",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HID_REPORT_DESCRIPTOR
            )
            val ok = hid.registerApp(sdp, null, null, executor, callback)
            if (ok) {
                _lastError.value = null
                Log.i(TAG, "registerApp submitted successfully for '$BT_DEVICE_NAME'")
            } else {
                _lastError.value = "HID registration busy. Retry in a moment."
                Log.w(TAG, "registerApp returned false")
            }
        } catch (e: SecurityException) {
            _lastError.value = "Bluetooth permission denied."
            Log.e(TAG, "registerApp SecurityException", e)
        } catch (e: Exception) {
            _lastError.value = "HID error: ${e.message}"
            Log.e(TAG, "registerApp Exception", e)
        }
    }

    /**
     * Connect directly to an already paired Bluetooth host.
     */
    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            _lastError.value = "HID device service not ready. Tap Start BT first."
            return
        }
        try {
            _connectionState.value = BluetoothHidConnectionState.Connecting
            val ok = hid.connect(device)
            if (!ok) {
                _lastError.value = "Failed to initiate connection to ${device.name ?: device.address}"
                _connectionState.value = BluetoothHidConnectionState.Disconnected
            }
        } catch (e: SecurityException) {
            _lastError.value = "Permission denied connecting to device"
        }
    }

    /**
     * Prepares device for Bluetooth pairing and returns the discoverable Intent.
     */
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

        // Set device name so other devices clearly see MiniDex
        try {
            adapter.name = BT_DEVICE_NAME
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot rename adapter", e)
        }

        // Ensure HID proxy and app registration are triggered
        if (hidDevice == null) {
            adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        } else if (!isRegistered) {
            registerHidApp()
        }

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
