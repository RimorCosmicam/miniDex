package com.minidex.app.ui.main

import android.app.ActivityOptions
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minidex.app.data.MacroRepository
import com.minidex.app.data.UserPreferences
import com.minidex.app.data.UserPreferencesRepository
import com.minidex.app.dex.DexDisplayManager
import com.minidex.app.domain.model.AppMode
import com.minidex.app.domain.model.DexDisplayInfo
import com.minidex.app.domain.model.KeyboardPage
import com.minidex.app.domain.model.Macro
import com.minidex.app.domain.model.MacroStep
import com.minidex.app.domain.model.ModifierState
import com.minidex.app.domain.model.ModifierType
import com.minidex.app.input.InputBackend
import com.minidex.app.input.InputBackendManager
import com.minidex.app.input.adb.AdbConnectionStatus
import com.minidex.app.ui.components.HapticFeedbackManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = UserPreferencesRepository(application)
    private val macroRepository = MacroRepository(application)
    private val displayManager = DexDisplayManager(application, viewModelScope)
    val backendManager = InputBackendManager(application, viewModelScope)
    val hapticManager = HapticFeedbackManager(application)
    private val keyboardActions = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val macros: StateFlow<List<Macro>> = macroRepository.macrosFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, macroRepository.defaultMacros)

    val activeDexDisplay: StateFlow<DexDisplayInfo> = displayManager.activeDexDisplay
    val availableDisplays: StateFlow<List<DexDisplayInfo>> = displayManager.availableDisplays
    val activeBackend: StateFlow<InputBackend> = backendManager.activeBackend
    val isAdbConnected: StateFlow<Boolean> = backendManager.isAdbConnected
    val isAccessibilityEnabled: StateFlow<Boolean> = backendManager.isAccessibilityEnabled
    val isAccessibilityConfigured: StateFlow<Boolean> = backendManager.isAccessibilityConfigured
    val isImeEnabled: StateFlow<Boolean> = backendManager.isImeEnabled

    // ADB States
    val adbConnectionStatus: StateFlow<AdbConnectionStatus> = backendManager.adbManager.status
    val adbStatusMessage: StateFlow<String> = backendManager.adbManager.statusMessage
    val discoveredPairingPort: StateFlow<Int?> = backendManager.adbManager.mdnsDiscovery.discoveredPairingPort
    val discoveredConnectPort: StateFlow<Int?> = backendManager.adbManager.mdnsDiscovery.discoveredConnectPort
    val isShizukuAvailable: StateFlow<Boolean> = backendManager.adbManager.isShizukuAvailable

    private val _showAdbPairingDialog = MutableStateFlow(false)
    val showAdbPairingDialog: StateFlow<Boolean> = _showAdbPairingDialog.asStateFlow()

    private val _currentMode = MutableStateFlow(AppMode.TOUCHPAD)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _currentPage = MutableStateFlow(KeyboardPage.ABC)
    val currentPage: StateFlow<KeyboardPage> = _currentPage.asStateFlow()

    private val _modifierState = MutableStateFlow(ModifierState())
    val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()

    private val _showMacroEditor = MutableStateFlow(false)
    val showMacroEditor: StateFlow<Boolean> = _showMacroEditor.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /**
     * The pairing code, held here rather than in the sheet, because it is typed into a floating
     * overlay window that is a separate composition from the app's own.
     */
    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _pairingPort = MutableStateFlow("")
    val pairingPort: StateFlow<String> = _pairingPort.asStateFlow()

    /** The display MiniDex was on when it stepped aside for Settings, so it can come back to it. */
    private var pairingDisplayId: Int = 0

    fun setPairingDisplay(displayId: Int) {
        pairingDisplayId = displayId
    }

    private val _showPairingOverlay = MutableStateFlow(false)
    val showPairingOverlay: StateFlow<Boolean> = _showPairingOverlay.asStateFlow()

    /** A codeless reconnect that did not take. Silent failure here reads as a dead button. */
    private val _autoPairFailed = MutableStateFlow(false)
    val autoPairFailed: StateFlow<Boolean> = _autoPairFailed.asStateFlow()

    /**
     * Dismissed for this run only. Onboarding still opens whenever a driver is missing, but it is
     * a starting point rather than a locked door: the drivers can be set up later.
     */
    private val _onboardingDismissed = MutableStateFlow(false)
    val onboardingDismissed: StateFlow<Boolean> = _onboardingDismissed.asStateFlow()



    private val targetDisplayId: Int
        get() {
            val prefManual = userPreferences.value.manualDisplayId
            return if (prefManual != -1) prefManual else activeDexDisplay.value.displayId
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (action in keyboardActions) {
                runCatching { action() }
            }
        }

        viewModelScope.launch {
            userPreferences.collect { preferences ->
                backendManager.adbBackend.setCursorMode(preferences.cursorMode)
                displayManager.setManualOverride(preferences.manualDisplayId)
            }
        }

        // Auto-start mDNS discovery and auto-connect if enabled in preferences
        viewModelScope.launch {
            backendManager.adbManager.startMdnsDiscovery()

            val preferences = preferencesRepository.userPreferencesFlow.first()
            if (preferences.adbAutoConnect) {
                // Wireless Debugging advertises a randomized TLS port. Port 5555
                // is not a valid fallback and can leave a blocking connect alive.
                val connectPort = withTimeoutOrNull(8_000) {
                    discoveredConnectPort.filterNotNull().first()
                }
                if (connectPort != null) {
                    val host = backendManager.adbManager.mdnsDiscovery.discoveredHost.value
                    backendManager.adbManager.connect(host, connectPort)
                }
            }
        }

        viewModelScope.launch {
            var previousDexDisplayId = -1
            availableDisplays.collect { displays ->
                val currentHighestExternal = displays
                    .asSequence()
                    .filter { it.isExternal && it.isConnected }
                    .maxOfOrNull { it.displayId } ?: -1
                val previousDexWasRemoved = previousDexDisplayId >= 2 &&
                    displays.none { it.displayId == previousDexDisplayId && it.isConnected }
                if (previousDexWasRemoved) {
                    backendManager.adbManager.resetSamsungKeyboardAfterDex()
                }
                previousDexDisplayId = currentHighestExternal
            }
        }
    }

    fun toggleAppMode() {
        _currentMode.value = if (_currentMode.value == AppMode.KEYBOARD) AppMode.TOUCHPAD else AppMode.KEYBOARD
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun toggleAmoledMode() {
        updatePreferences { it.copy(amoledMode = !it.amoledMode) }
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun setSettingsVisible(visible: Boolean) {
        _showSettings.value = visible
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handleSwipeWord(word: String) {
        if (word.isBlank()) return
        keyboardActions.trySend {
            activeBackend.value.sendText("$word ", targetDisplayId)
        }
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun selectKeyboardPage(page: KeyboardPage) {
        _currentPage.value = page
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun toggleModifier(modifier: ModifierType) {
        val allowLock = userPreferences.value.doubleTapToLockModifier
        _modifierState.value = _modifierState.value.toggleModifier(modifier, allowLock)
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handleCharPress(char: Char, keyCode: Int) {
        val modifiers = _modifierState.value
        val meta = modifiers.toMetaState()
        val isShortcut = modifiers.ctrl.isActive || modifiers.alt.isActive || modifiers.meta.isActive
        if (isShortcut) {
            keyboardActions.trySend {
                activeBackend.value.sendKeyPress(keyCode, meta, targetDisplayId)
            }
        } else {
            val text = if (modifiers.shift.isActive) char.uppercase() else char.toString()
            keyboardActions.trySend {
                activeBackend.value.sendText(text, targetDisplayId)
            }
        }
        _modifierState.value = _modifierState.value.consumeLatched()
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handleKeyPress(keyCode: Int) {
        val meta = _modifierState.value.toMetaState()
        keyboardActions.trySend {
            activeBackend.value.sendKeyPress(keyCode, meta, targetDisplayId)
        }
        _modifierState.value = _modifierState.value.consumeLatched()
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handleShortcut(name: String, keyCodes: List<Int>, modifiers: List<ModifierType>) {
        keyboardActions.trySend {
            var tempState = ModifierState()
            modifiers.forEach { mod ->
                tempState = tempState.withModifier(mod, com.minidex.app.domain.model.ModifierLockState.LATCHED)
            }
            val meta = tempState.toMetaState()
            keyCodes.forEach { code ->
                activeBackend.value.sendKeyPress(code, meta, targetDisplayId)
            }
        }
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun executeMacro(macro: Macro) {
        keyboardActions.trySend {
            for (step in macro.steps) {
                when (step) {
                    is MacroStep.KeyPress -> {
                        activeBackend.value.sendKeyPress(step.keyCode, step.metaState, targetDisplayId)
                    }
                    is MacroStep.KeyDown -> {
                        activeBackend.value.sendKeyDown(step.keyCode, 0, targetDisplayId)
                    }
                    is MacroStep.KeyUp -> {
                        activeBackend.value.sendKeyUp(step.keyCode, 0, targetDisplayId)
                    }
                    is MacroStep.KeyChord -> {
                        var chordMeta = ModifierState()
                        step.modifiers.forEach { mod ->
                            chordMeta = chordMeta.withModifier(mod, com.minidex.app.domain.model.ModifierLockState.LATCHED)
                        }
                        val meta = chordMeta.toMetaState()
                        step.keyCodes.forEach { code ->
                            activeBackend.value.sendKeyPress(code, meta, targetDisplayId)
                        }
                    }
                    is MacroStep.TypeText -> {
                        activeBackend.value.sendText(step.text, targetDisplayId)
                    }
                    is MacroStep.Delay -> {
                        delay(step.millis)
                    }
                    is MacroStep.PointerClick -> {
                        activeBackend.value.sendPointerClick(step.button, targetDisplayId)
                    }
                    is MacroStep.Scroll -> {
                        activeBackend.value.sendScroll(step.dx, step.dy, targetDisplayId)
                    }
                }
                delay(15)
            }
        }
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handlePointerMove(dx: Float, dy: Float) {
        syncAdbPointerBounds()
        activeBackend.value.sendPointerMove(dx, dy, targetDisplayId)
    }

    fun handlePointerDown(button: Int) {
        syncAdbPointerBounds()
        activeBackend.value.sendPointerDown(button, targetDisplayId)
    }

    fun handlePointerUp(button: Int) {
        syncAdbPointerBounds()
        activeBackend.value.sendPointerUp(button, targetDisplayId)
    }

    fun handlePointerClick(button: Int) {
        syncAdbPointerBounds()
        activeBackend.value.sendPointerClick(button, targetDisplayId)
    }

    fun handleScroll(dx: Float, dy: Float) {
        syncAdbPointerBounds()
        activeBackend.value.sendScroll(dx, dy, targetDisplayId)
    }

    private fun syncAdbPointerBounds() {
        val display = availableDisplays.value.firstOrNull { it.displayId == targetDisplayId }
            ?: activeDexDisplay.value
        backendManager.adbBackend.setDisplayBounds(display.displayId, display.width, display.height)
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences(transform)
        }
    }

    fun saveMacro(macro: Macro) {
        viewModelScope.launch {
            macroRepository.addOrUpdateMacro(macro)
        }
    }

    fun setMacroEditorVisible(visible: Boolean) {
        _showMacroEditor.value = visible
    }

    fun setAdbPairingDialogVisible(visible: Boolean) {
        _showAdbPairingDialog.value = visible
        if (visible) {
            backendManager.adbManager.startMdnsDiscovery()
        }
    }

    /**
     * Asks for the pairing strip. Nothing is opened yet: the screen it has to float over is only
     * worth going to once the strip is actually up, or it is a one-way trip to Developer options
     * with nowhere left to type.
     */
    fun requestPairing() {
        _pairingCode.value = ""
        _pairingPort.value = ""
        _showPairingOverlay.value = true
    }

    /** Called once the strip is floating, to bring up the screen carrying the code. */
    fun openWirelessDebuggingSettings() {
        backendManager.openWirelessDebuggingSettings()
    }

    fun setPairingCode(code: String) {
        _pairingCode.value = code.filter { it.isDigit() }.take(6)
    }

    fun setPairingPort(port: String) {
        _pairingPort.value = port.filter { it.isDigit() }.take(5)
    }

    fun dismissPairingOverlay() {
        _showPairingOverlay.value = false
    }

    fun pairWithEnteredCode() {
        val port = _pairingPort.value.toIntOrNull() ?: discoveredPairingPort.value ?: return
        val code = _pairingCode.value
        if (code.length != 6) return
        viewModelScope.launch {
            val result = backendManager.adbManager.pairWithCode(port, code)
            if (result.isSuccess) {
                updatePreferences { it.copy(adbPairedBefore = true) }
                _showPairingOverlay.value = false
                _showAdbPairingDialog.value = false
                _pairingCode.value = ""
                // Settings was only ever a place to read a number off. Once it has been read,
                // come back rather than leaving the app buried behind Developer options.
                returnToApp()
            }
        }
    }

    fun pairAdbWithCode(port: Int, code: String) {
        viewModelScope.launch {
            backendManager.adbManager.pairWithCode(port, code)
        }
    }

    /** True when a codeless reconnect is possible: keys on disk and a port being advertised. */
    fun canAutoPair(): Boolean =
        userPreferences.value.adbPairedBefore &&
            backendManager.adbManager.hasStoredCredentials() &&
            discoveredConnectPort.value != null

    /** Reconnects on the stored key, with no pairing code. */
    fun autoPairAdb() {
        val port = discoveredConnectPort.value ?: run {
            _autoPairFailed.value = true
            return
        }
        _autoPairFailed.value = false
        viewModelScope.launch {
            val host = backendManager.adbManager.mdnsDiscovery.discoveredHost.value
            val result = backendManager.adbManager.connect(host, port)
            _autoPairFailed.value = result.getOrDefault(false) != true
        }
    }

    fun connectAdbDirect(port: Int) {
        viewModelScope.launch {
            val host = backendManager.adbManager.mdnsDiscovery.discoveredHost.value
            backendManager.adbManager.connect(host, port)
        }
    }

    fun requestShizukuPermission() {
        backendManager.adbManager.requestShizukuPermission()
    }

    fun sendAdbTestEvent() {
        viewModelScope.launch {
            activeBackend.value.sendPointerClick(1, targetDisplayId)
            hapticManager.performHaptic(userPreferences.value.hapticStrength)
        }
    }

    fun openAccessibilitySettings() {
        backendManager.openAccessibilitySettings()
    }

    fun openImeSettings() {
        backendManager.openImeSettings()
    }

    fun launchSamsungDexTouchpad() {
        backendManager.launchSamsungDexTouchpad()
    }

    fun completeOnboarding() {
        _onboardingDismissed.value = true
        updatePreferences { it.copy(onboardingComplete = true) }
    }

    /** Brings MiniDex back to the front, on the display it was showing on. */
    private fun returnToApp() {
        val context = getApplication<Application>()
        val intent = Intent(context, MainCoverActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val options = ActivityOptions.makeBasic().apply {
            runCatching { launchDisplayId = pairingDisplayId }
        }
        runCatching { context.startActivity(intent, options.toBundle()) }
            .onFailure { runCatching { context.startActivity(intent) } }
    }

    fun refreshBackend() {
        viewModelScope.launch {
            backendManager.refreshBackend()
        }
    }

    override fun onCleared() {
        super.onCleared()
        displayManager.release()
        backendManager.release()
    }
}
