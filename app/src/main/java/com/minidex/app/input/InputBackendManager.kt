package com.minidex.app.input

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
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
 * Manages the native same-device drivers for Samsung DeX control:
 * 1. Accessibility Service — multi-display gesture dispatch (touchpad clicks, drags, scrolls)
 * 2. IME InputMethodService — native keyboard text injection into DeX windows
 */
class InputBackendManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "InputBackendManager"
    }

    val accessibilityBackend = AccessibilityInputBackend(context)
    val fallbackBackend = FallbackInputBackend()

    private val _activeBackend = MutableStateFlow<InputBackend>(accessibilityBackend)
    val activeBackend: StateFlow<InputBackend> = _activeBackend.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isImeEnabled = MutableStateFlow(false)
    val isImeEnabled: StateFlow<Boolean> = _isImeEnabled.asStateFlow()

    init {
        scope.launch {
            refreshBackend()

            // Heartbeat: detect when user toggles Accessibility or IME in Settings
            while (isActive) {
                delay(1500)
                refreshBackend()
            }
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
        val a11yActive = checkAccessibilityServiceConfigured()
        val imeActive = checkImeConfigured()

        _isAccessibilityEnabled.value = a11yActive
        _isImeEnabled.value = imeActive

        val candidate: InputBackend = if (a11yActive) {
            accessibilityBackend
        } else {
            fallbackBackend
        }

        candidate.initialize()
        _activeBackend.value = candidate
    }

    fun release() {
        accessibilityBackend.release()
        fallbackBackend.release()
    }
}
