package com.minidex.app.ui.main

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
import com.minidex.app.ui.components.HapticFeedbackManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = UserPreferencesRepository(application)
    private val macroRepository = MacroRepository(application)
    private val displayManager = DexDisplayManager(application, viewModelScope)
    val backendManager = InputBackendManager(application, viewModelScope)
    val hapticManager = HapticFeedbackManager(application)

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val macros: StateFlow<List<Macro>> = macroRepository.macrosFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, macroRepository.defaultMacros)

    val activeDexDisplay: StateFlow<DexDisplayInfo> = displayManager.activeDexDisplay
    val activeBackend: StateFlow<InputBackend> = backendManager.activeBackend
    val isAccessibilityEnabled: StateFlow<Boolean> = backendManager.isAccessibilityEnabled
    val isBluetoothHidReady: StateFlow<Boolean> = backendManager.isBluetoothHidReady

    private val _currentMode = MutableStateFlow(AppMode.KEYBOARD)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _currentPage = MutableStateFlow(KeyboardPage.ABC)
    val currentPage: StateFlow<KeyboardPage> = _currentPage.asStateFlow()

    private val _modifierState = MutableStateFlow(ModifierState())
    val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()

    private val _showMacroEditor = MutableStateFlow(false)
    val showMacroEditor: StateFlow<Boolean> = _showMacroEditor.asStateFlow()

    init {
        backendManager.accessibilityBackend.onPointerUpdate = { x, y ->
            displayManager.updatePointerPosition(x, y)
        }
    }

    private val targetDisplayId: Int
        get() {
            val prefManual = userPreferences.value.manualDisplayId
            return if (prefManual != -1) prefManual else activeDexDisplay.value.displayId
        }

    fun toggleAppMode() {
        _currentMode.value = if (_currentMode.value == AppMode.KEYBOARD) AppMode.TOUCHPAD else AppMode.KEYBOARD
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
        val meta = _modifierState.value.toMetaState()
        val text = if (_modifierState.value.shift.isActive) char.uppercase() else char.toString()
        activeBackend.value.sendText(text, targetDisplayId)
        activeBackend.value.sendKeyPress(keyCode, meta, targetDisplayId)
        _modifierState.value = _modifierState.value.consumeLatched()
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handleKeyPress(keyCode: Int) {
        val meta = _modifierState.value.toMetaState()
        activeBackend.value.sendKeyPress(keyCode, meta, targetDisplayId)
        _modifierState.value = _modifierState.value.consumeLatched()
        hapticManager.performHaptic(userPreferences.value.hapticStrength)
    }

    fun handleShortcut(name: String, keyCodes: List<Int>, modifiers: List<ModifierType>) {
        viewModelScope.launch {
            var tempState = ModifierState()
            modifiers.forEach { mod ->
                tempState = tempState.withModifier(mod, com.minidex.app.domain.model.ModifierLockState.LATCHED)
            }
            val meta = tempState.toMetaState()
            keyCodes.forEach { code ->
                activeBackend.value.sendKeyPress(code, meta, targetDisplayId)
            }
            hapticManager.performHaptic(userPreferences.value.hapticStrength)
        }
    }

    fun executeMacro(macro: Macro) {
        viewModelScope.launch {
            hapticManager.performHaptic(userPreferences.value.hapticStrength)
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
    }

    fun handlePointerMove(dx: Float, dy: Float) {
        activeBackend.value.sendPointerMove(dx, dy, targetDisplayId)
    }

    fun handlePointerDown(button: Int) {
        activeBackend.value.sendPointerDown(button, targetDisplayId)
    }

    fun handlePointerUp(button: Int) {
        activeBackend.value.sendPointerUp(button, targetDisplayId)
    }

    fun handlePointerClick(button: Int) {
        activeBackend.value.sendPointerClick(button, targetDisplayId)
    }

    fun handleScroll(dx: Float, dy: Float) {
        activeBackend.value.sendScroll(dx, dy, targetDisplayId)
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

    fun openAccessibilitySettings() {
        backendManager.openAccessibilitySettings()
    }

    fun startBluetoothPairing(): Intent? {
        return backendManager.startBluetoothPairing()
    }

    fun openBluetoothSettings() {
        backendManager.openBluetoothSettings()
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
