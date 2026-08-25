package com.minidex.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minidex.app.domain.model.AppMode
import com.minidex.app.domain.model.KeyboardPage
import com.minidex.app.input.BluetoothHidConnectionState
import com.minidex.app.ui.components.ModeSwitcherButton
import com.minidex.app.ui.components.PageBar
import com.minidex.app.ui.components.SpecialRow
import com.minidex.app.ui.keyboard.MacropadView
import com.minidex.app.ui.keyboard.NavKeyboard
import com.minidex.app.ui.keyboard.QwertyKeyboard
import com.minidex.app.ui.keyboard.SymbolKeyboard
import com.minidex.app.ui.settings.MacroEditorDialog
import com.minidex.app.ui.settings.SettingsView
import com.minidex.app.ui.theme.LocalMiniDexColors
import com.minidex.app.ui.theme.MiniDexTheme
import com.minidex.app.ui.touchpad.TouchpadView

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val preferences by viewModel.userPreferences.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val modifierState by viewModel.modifierState.collectAsState()
    val macros by viewModel.macros.collectAsState()
    val activeDexDisplay by viewModel.activeDexDisplay.collectAsState()
    val activeBackend by viewModel.activeBackend.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val isBluetoothHidReady by viewModel.isBluetoothHidReady.collectAsState()
    val bluetoothConnectionState by viewModel.backendManager.bluetoothHidBackend.connectionState.collectAsState()
    val bluetoothError by viewModel.backendManager.bluetoothHidBackend.lastError.collectAsState()
    val bondedDevices by viewModel.bondedDevices.collectAsState()
    val showMacroEditor by viewModel.showMacroEditor.collectAsState()

    MiniDexTheme(
        variant = preferences.themeVariant,
        accent = preferences.accentColor
    ) {
        val colors = LocalMiniDexColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Header: Page Bar (Only in Keyboard mode)
                AnimatedVisibility(
                    visible = currentMode == AppMode.KEYBOARD,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PageBar(
                        currentPage = currentPage,
                        onPageSelected = { viewModel.selectKeyboardPage(it) }
                    )
                }

                // Sub-header: Special Key Row (Only on Keyboard ABC, Symbols, and Nav pages)
                AnimatedVisibility(
                    visible = currentMode == AppMode.KEYBOARD && (currentPage == KeyboardPage.ABC || currentPage == KeyboardPage.SYMBOLS || currentPage == KeyboardPage.NAV),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SpecialRow(
                        modifierState = modifierState,
                        cornerRadius = preferences.cornerRadiusDp.dp,
                        keyHeight = (preferences.keyHeightLevel.heightDp - 12).dp,
                        onModifierToggle = { viewModel.toggleModifier(it) },
                        onKeyPress = { viewModel.handleKeyPress(it) }
                    )
                }

                // Main Content Area: Switches between Touchpad & Keyboard pages
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Crossfade(
                        targetState = currentMode,
                        label = "mode_crossfade"
                    ) { mode ->
                        when (mode) {
                            AppMode.TOUCHPAD -> {
                                TouchpadView(
                                    userPreferences = preferences,
                                    onPointerMove = { dx, dy -> viewModel.handlePointerMove(dx, dy) },
                                    onPointerDown = { btn -> viewModel.handlePointerDown(btn) },
                                    onPointerUp = { btn -> viewModel.handlePointerUp(btn) },
                                    onPointerClick = { btn -> viewModel.handlePointerClick(btn) },
                                    onScroll = { dx, dy -> viewModel.handleScroll(dx, dy) },
                                    onHapticClick = { viewModel.hapticManager.performHaptic(preferences.hapticStrength) }
                                )
                            }

                            AppMode.KEYBOARD -> {
                                Crossfade(
                                    targetState = currentPage,
                                    label = "page_crossfade"
                                ) { page ->
                                    when (page) {
                                        KeyboardPage.ABC -> {
                                            QwertyKeyboard(
                                                shiftState = modifierState.shift,
                                                keyHeight = preferences.keyHeightLevel.heightDp.dp,
                                                keyGap = preferences.keyGapDp.dp,
                                                cornerRadius = preferences.cornerRadiusDp.dp,
                                                onCharPress = { char, code -> viewModel.handleCharPress(char, code) },
                                                onModifierToggle = { viewModel.toggleModifier(it) },
                                                onKeyPress = { viewModel.handleKeyPress(it) }
                                            )
                                        }

                                        KeyboardPage.SYMBOLS -> {
                                            SymbolKeyboard(
                                                keyHeight = preferences.keyHeightLevel.heightDp.dp,
                                                keyGap = preferences.keyGapDp.dp,
                                                cornerRadius = preferences.cornerRadiusDp.dp,
                                                onCharPress = { char, code -> viewModel.handleCharPress(char, code) },
                                                onKeyPress = { viewModel.handleKeyPress(it) }
                                            )
                                        }

                                        KeyboardPage.NAV -> {
                                            NavKeyboard(
                                                keyHeight = preferences.keyHeightLevel.heightDp.dp,
                                                keyGap = preferences.keyGapDp.dp,
                                                cornerRadius = preferences.cornerRadiusDp.dp,
                                                onKeyPress = { viewModel.handleKeyPress(it) },
                                                onShortcut = { name, codes, mods -> viewModel.handleShortcut(name, codes, mods) }
                                            )
                                        }

                                        KeyboardPage.MACROS -> {
                                            MacropadView(
                                                macros = macros,
                                                onExecuteMacro = { viewModel.executeMacro(it) },
                                                onAddMacroClick = { viewModel.setMacroEditorVisible(true) },
                                                cornerRadius = preferences.cornerRadiusDp.dp
                                            )
                                        }

                                        KeyboardPage.SETTINGS -> {
                                            val settingsContext = LocalContext.current
                                            SettingsView(
                                                preferences = preferences,
                                                dexDisplayInfo = activeDexDisplay,
                                                isAccessibilityEnabled = isAccessibilityEnabled,
                                                isBluetoothHidReady = isBluetoothHidReady,
                                                activeBackendName = activeBackend.name,
                                                bluetoothConnectionState = bluetoothConnectionState,
                                                bluetoothError = bluetoothError,
                                                bondedDevices = bondedDevices,
                                                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                                onStartBluetoothPairing = {
                                                    val intent = viewModel.startBluetoothPairing()
                                                    if (intent != null) {
                                                        settingsContext.startActivity(intent)
                                                    }
                                                },
                                                onConnectToBluetoothDevice = { viewModel.connectToBluetoothDevice(it) },
                                                onOpenBluetoothSettings = { viewModel.openBluetoothSettings() },
                                                onUpdatePreferences = { viewModel.updatePreferences(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Anchor Bar: houses the persistent Mode Switcher in the camera nook
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeSwitcherButton(
                        currentMode = currentMode,
                        onToggleMode = { viewModel.toggleAppMode() }
                    )
                }
            }

            // Macro Editor Dialog
            if (showMacroEditor) {
                MacroEditorDialog(
                    onDismiss = { viewModel.setMacroEditorVisible(false) },
                    onSaveMacro = { newMacro -> viewModel.saveMacro(newMacro) }
                )
            }
        }
    }
}
