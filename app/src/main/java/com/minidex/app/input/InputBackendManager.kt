package com.minidex.app.input

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _activeBackend = MutableStateFlow<InputBackend>(accessibilityBackend)
    val activeBackend: StateFlow<InputBackend> = _activeBackend.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isBluetoothHidReady = MutableStateFlow(false)
    val isBluetoothHidReady: StateFlow<Boolean> = _isBluetoothHidReady.asStateFlow()

    private var preferredBackendConfig: String = "AUTO"

    init {
        scope.launch {
            bluetoothHidBackend.initialize()
            refreshBackend()
        }
    }

    fun setPreferredBackend(backendId: String) {
        preferredBackendConfig = backendId
        scope.launch {
            refreshBackend()
        }
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

    suspend fun refreshBackend() {
        val a11yEnabled = MiniDexAccessibilityService.isServiceEnabled()
        _isAccessibilityEnabled.value = a11yEnabled
        _isBluetoothHidReady.value = bluetoothHidBackend.isAvailable

        val candidate: InputBackend = when (preferredBackendConfig) {
            "BLUETOOTH_HID" -> {
                if (bluetoothHidBackend.isAvailable) bluetoothHidBackend else fallbackBackend
            }
            "ACCESSIBILITY" -> {
                if (a11yEnabled) accessibilityBackend else fallbackBackend
            }
            "VIRTUAL_DEVICE" -> {
                if (virtualDeviceBackend.isAvailable) virtualDeviceBackend else fallbackBackend
            }
            "FALLBACK" -> fallbackBackend
            else -> { // AUTO
                if (a11yEnabled) {
                    accessibilityBackend
                } else if (bluetoothHidBackend.isAvailable) {
                    bluetoothHidBackend
                } else {
                    fallbackBackend
                }
            }
        }

        candidate.initialize()
        _activeBackend.value = candidate
        Log.i(TAG, "Active input backend selected: ${candidate.name}")
    }

    fun release() {
        bluetoothHidBackend.release()
        accessibilityBackend.release()
        virtualDeviceBackend.release()
        fallbackBackend.release()
    }
}
