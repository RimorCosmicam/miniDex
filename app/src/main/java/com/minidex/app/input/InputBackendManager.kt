package com.minidex.app.input

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
import com.minidex.app.input.adb.AdbConnectionManager
import com.minidex.app.input.adb.AdbConnectionStatus
import com.minidex.app.input.ime.MiniDexInputMethodService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the multi-tier driver architecture for Samsung DeX control:
 * 1. Wireless ADB Driver — hardware-level input injection (zero-latency, full scancode scancodes & multi-display)
 * 2. Accessibility Service — native gesture dispatch fallback (touchpad clicks, drags, scrolls)
 * 3. IME InputMethodService — text input integration
 * 4. Fallback backend — local simulation/test
 */
class InputBackendManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "InputBackendManager"
    }

    val adbManager = AdbConnectionManager(context, scope)
    val adbBackend = AdbInputBackend(adbManager)
    val accessibilityBackend = AccessibilityInputBackend(context)
    val fallbackBackend = FallbackInputBackend()

    private val _activeBackend = MutableStateFlow<InputBackend>(accessibilityBackend)
    val activeBackend: StateFlow<InputBackend> = _activeBackend.asStateFlow()

    private val _isAdbConnected = MutableStateFlow(false)
    val isAdbConnected: StateFlow<Boolean> = _isAdbConnected.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isImeEnabled = MutableStateFlow(false)
    val isImeEnabled: StateFlow<Boolean> = _isImeEnabled.asStateFlow()

    init {
        scope.launch {
            // Collect ADB status changes
            launch {
                adbManager.status.collect { status ->
                    _isAdbConnected.value = (status == AdbConnectionStatus.CONNECTED)
                    refreshBackend()
                }
            }

            refreshBackend()

            // Heartbeat: detect when user toggles Accessibility, IME, or Wireless Debugging
            while (isActive) {
                delay(1500)
                refreshBackend()
            }
        }
    }

    fun openWirelessDebuggingSettings() {
        adbManager.openWirelessDebuggingSettings()
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

    fun openImeSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open IME settings", e)
        }
    }

    fun launchSamsungDexTouchpad() {
        try {
            // Attempt 1: Samsung Desktop Launcher Touchpad Action
            val intent = Intent("com.sec.android.app.desktoplauncher.action.SHOW_TOUCHPAD").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}

        try {
            // Attempt 2: Direct component launch for Samsung DeX Pad / SystemUI Touchpad
            val intent = Intent().apply {
                setClassName("com.sec.android.app.desktoplauncher", "com.sec.android.app.desktoplauncher.TouchPadActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun checkAccessibilityServiceConfigured(): Boolean {
        if (MiniDexAccessibilityService.isServiceEnabled()) return true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val packageName = context.packageName

        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun checkImeConfigured(): Boolean {
        if (MiniDexInputMethodService.isImeActive()) return true

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        val enabledImes = imm.enabledInputMethodList
        val packageName = context.packageName

        return enabledImes.any { it.packageName == packageName }
    }

    suspend fun refreshBackend() {
        val adbActive = adbBackend.isAvailable
        val a11yActive = checkAccessibilityServiceConfigured()
        val imeActive = checkImeConfigured()

        _isAdbConnected.value = adbActive
        _isAccessibilityEnabled.value = a11yActive
        _isImeEnabled.value = imeActive

        val candidate: InputBackend = when {
            adbActive -> adbBackend
            a11yActive -> accessibilityBackend
            else -> fallbackBackend
        }

        candidate.initialize()
        _activeBackend.value = candidate
    }

    fun release() {
        adbBackend.release()
        adbManager.disconnect()
        accessibilityBackend.release()
        fallbackBackend.release()
    }
}
