package com.minidex.app.input

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class InputBackendManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "InputBackendManager"
    }

    private val shizukuBackend = ShizukuInputBackend()
    private val virtualDeviceBackend = VirtualDeviceInputBackend(context)
    val fallbackBackend = FallbackInputBackend()

    private val _activeBackend = MutableStateFlow<InputBackend>(fallbackBackend)
    val activeBackend: StateFlow<InputBackend> = _activeBackend.asStateFlow()

    private val _isShizukuPermissionGranted = MutableStateFlow(false)
    val isShizukuPermissionGranted: StateFlow<Boolean> = _isShizukuPermissionGranted.asStateFlow()

    private val _isShizukuRunning = MutableStateFlow(false)
    val isShizukuRunning: StateFlow<Boolean> = _isShizukuRunning.asStateFlow()

    private var preferredBackendConfig: String = "AUTO"

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            _isShizukuPermissionGranted.value = granted
            scope.launch {
                refreshBackend()
            }
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isShizukuRunning.value = true
        checkShizukuPermission()
        scope.launch {
            refreshBackend()
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        _isShizukuRunning.value = false
        _isShizukuPermissionGranted.value = false
        scope.launch {
            refreshBackend()
        }
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
            checkShizukuPermission()
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku initialization error: ${e.message}")
        }
        scope.launch {
            refreshBackend()
        }
    }

    fun setPreferredBackend(backendId: String) {
        preferredBackendConfig = backendId
        scope.launch {
            refreshBackend()
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _isShizukuPermissionGranted.value = true
                scope.launch { refreshBackend() }
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission: ${e.message}")
        }
    }

    private fun checkShizukuPermission() {
        try {
            val running = Shizuku.pingBinder()
            _isShizukuRunning.value = running
            if (running) {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                _isShizukuPermissionGranted.value = granted
            } else {
                _isShizukuPermissionGranted.value = false
            }
        } catch (e: Exception) {
            _isShizukuRunning.value = false
            _isShizukuPermissionGranted.value = false
        }
    }

    suspend fun refreshBackend() {
        checkShizukuPermission()

        val candidate: InputBackend = when (preferredBackendConfig) {
            "SHIZUKU" -> {
                if (shizukuBackend.isAvailable) shizukuBackend else fallbackBackend
            }
            "VIRTUAL_DEVICE" -> {
                if (virtualDeviceBackend.isAvailable) virtualDeviceBackend else fallbackBackend
            }
            "FALLBACK" -> fallbackBackend
            else -> { // AUTO
                if (shizukuBackend.isAvailable) {
                    shizukuBackend
                } else if (virtualDeviceBackend.isAvailable) {
                    virtualDeviceBackend
                } else {
                    fallbackBackend
                }
            }
        }

        candidate.initialize()
        _activeBackend.value = candidate
        Log.i(TAG, "Active input backend: ${candidate.name}")
    }

    fun release() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Throwable) {
            // Ignored
        }
        shizukuBackend.release()
        virtualDeviceBackend.release()
        fallbackBackend.release()
    }
}
