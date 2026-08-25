package com.minidex.app.input

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InputBackendManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "InputBackendManager"
    }

    val bluetoothHidBackend = BluetoothHidInputBackend(context)
    val accessibilityBackend = AccessibilityInputBackend(context)
    val virtualDeviceBackend = VirtualDeviceInputBackend(context)
    val fallbackBackend = FallbackInputBackend()

    private val _activeBackend = MutableStateFlow<InputBackend>(fallbackBackend)
    val activeBackend: StateFlow<InputBackend> = _activeBackend.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isBluetoothHidReady = MutableStateFlow(false)
    val isBluetoothHidReady: StateFlow<Boolean> = _isBluetoothHidReady.asStateFlow()

    private var preferredBackendConfig: String = "AUTO"

    init {
        scope.launch {
            refreshBackend()

            // Heartbeat: detect when user toggles Accessibility or Bluetooth in Settings
            while (isActive) {
                delay(1500)
                refreshBackend()
            }
        }
    }

    fun setPreferredBackend(backendId: String) {
        preferredBackendConfig = backendId
        scope.launch { refreshBackend() }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
        }
    }

    /**
     * Starts the Bluetooth HID pairing flow.
     * Returns an Intent to launch (ACTION_REQUEST_DISCOVERABLE), or null if BT isn't ready.
     */
    fun startBluetoothPairing(): Intent? {
        return bluetoothHidBackend.startPairing()
    }

    fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open bluetooth settings", e)
        }
    }

    private fun checkAccessibilityServiceConfigured(): Boolean {
        if (MiniDexAccessibilityService.isServiceEnabled()) return true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val packageName = context.packageName

        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    suspend fun refreshBackend() {
        val a11yActive = checkAccessibilityServiceConfigured()
        _isAccessibilityEnabled.value = a11yActive

        val btConnected = bluetoothHidBackend.connectionState.value is BluetoothHidConnectionState.Connected
        _isBluetoothHidReady.value = bluetoothHidBackend.isAvailable

        val candidate: InputBackend = when (preferredBackendConfig) {
            "BLUETOOTH_HID" -> {
                if (btConnected) bluetoothHidBackend else fallbackBackend
            }
            "ACCESSIBILITY" -> {
                if (a11yActive) accessibilityBackend else fallbackBackend
            }
            "VIRTUAL_DEVICE" -> {
                if (virtualDeviceBackend.isAvailable) virtualDeviceBackend else fallbackBackend
            }
            "FALLBACK" -> fallbackBackend
            else -> { // AUTO: prefer BT HID when connected, then Accessibility, then fallback
                if (btConnected) {
                    bluetoothHidBackend
                } else if (a11yActive) {
                    accessibilityBackend
                } else {
                    fallbackBackend
                }
            }
        }

        candidate.initialize()
        _activeBackend.value = candidate
    }

    fun release() {
        bluetoothHidBackend.release()
        accessibilityBackend.release()
        virtualDeviceBackend.release()
        fallbackBackend.release()
    }
}
