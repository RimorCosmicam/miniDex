package com.minidex.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.minidex.app.domain.model.AppMode
import com.minidex.app.domain.model.KeyboardPage
import com.minidex.app.ui.components.ModeSwitcherButton
import com.minidex.app.ui.components.PageBar
import com.minidex.app.ui.components.SpecialRow
import com.minidex.app.ui.keyboard.MacropadView
import com.minidex.app.ui.keyboard.NavKeyboard
import com.minidex.app.ui.keyboard.QwertyKeyboard
import com.minidex.app.ui.keyboard.SymbolKeyboard
import com.minidex.app.ui.settings.AdbPairingDialog
import com.minidex.app.ui.settings.MacroEditorDialog
import com.minidex.app.ui.settings.SettingsView
import com.minidex.app.ui.theme.LocalMiniDexColors
import com.minidex.app.ui.theme.AnimatedGifBackground
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
    val availableDisplays by viewModel.availableDisplays.collectAsState()
    val activeBackend by viewModel.activeBackend.collectAsState()
    val isAdbConnected by viewModel.isAdbConnected.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val isImeEnabled by viewModel.isImeEnabled.collectAsState()
    val showMacroEditor by viewModel.showMacroEditor.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showAdbPairingDialog by viewModel.showAdbPairingDialog.collectAsState()
    val adbConnectionStatus by viewModel.adbConnectionStatus.collectAsState()
    val adbStatusMessage by viewModel.adbStatusMessage.collectAsState()
    val discoveredPairingPort by viewModel.discoveredPairingPort.collectAsState()
    val discoveredConnectPort by viewModel.discoveredConnectPort.collectAsState()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()

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
            AnimatedGifBackground(
                uri = preferences.backgroundGifUri,
                scale = preferences.backgroundGifScale,
                offsetX = preferences.backgroundGifOffsetX,
                offsetY = preferences.backgroundGifOffsetY,
                opacity = preferences.backgroundGifOpacity,
                modifier = Modifier.fillMaxSize()
            )
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
                        isAdbConnected = isAdbConnected,
                        onPageSelected = { viewModel.selectKeyboardPage(it) },
                        onAdbBadgeClick = { viewModel.setAdbPairingDialogVisible(true) }
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
                                                onSwipeWord = { viewModel.handleSwipeWord(it) },
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
                                                onKeyPress = { viewModel.handleKeyPress(it) }
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
                                            SettingsView(
                                                preferences = preferences,
                                                dexDisplayInfo = activeDexDisplay,
                                                availableDisplays = availableDisplays,
                                                isAdbConnected = isAdbConnected,
                                                adbStatusMessage = adbStatusMessage,
                                                isAccessibilityEnabled = isAccessibilityEnabled,
                                                isImeEnabled = isImeEnabled,
                                                activeBackendName = activeBackend.name,
                                                onOpenAdbPairing = { viewModel.setAdbPairingDialogVisible(true) },
                                                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                                onOpenImeSettings = { viewModel.openImeSettings() },
                                                onLaunchSamsungDexTouchpad = { viewModel.launchSamsungDexTouchpad() },
                                                onUpdatePreferences = { viewModel.updatePreferences(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            ModeSwitcherButton(
                currentMode = currentMode,
                onToggleMode = { viewModel.toggleAppMode() },
                onOpenSettings = { viewModel.setSettingsVisible(true) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 54.dp, bottom = 24.dp)
            )

            if (showSettings) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                ) {
                    SettingsView(
                        preferences = preferences,
                        dexDisplayInfo = activeDexDisplay,
                        availableDisplays = availableDisplays,
                        isAdbConnected = isAdbConnected,
                        adbStatusMessage = adbStatusMessage,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isImeEnabled = isImeEnabled,
                        activeBackendName = activeBackend.name,
                        onOpenAdbPairing = { viewModel.setAdbPairingDialogVisible(true) },
                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                        onOpenImeSettings = { viewModel.openImeSettings() },
                        onLaunchSamsungDexTouchpad = { viewModel.launchSamsungDexTouchpad() },
                        onUpdatePreferences = { viewModel.updatePreferences(it) },
                        modifier = Modifier.padding(top = 34.dp)
                    )
                    androidx.compose.material3.Text(
                        text = "CLOSE",
                        color = colors.accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable { viewModel.setSettingsVisible(false) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // Wireless ADB Pairing Dialog
            if (showAdbPairingDialog) {
                AdbPairingDialog(
                    connectionStatus = adbConnectionStatus,
                    statusMessage = adbStatusMessage,
                    discoveredPairingPort = discoveredPairingPort,
                    discoveredConnectPort = discoveredConnectPort,
                    isShizukuAvailable = isShizukuAvailable,
                    onOpenSettings = { viewModel.openWirelessDebuggingSettings() },
                    onPairWithCode = { port, code -> viewModel.pairAdbWithCode(port, code) },
                    onConnectDirect = { port -> viewModel.connectAdbDirect(port) },
                    onRequestShizuku = { viewModel.requestShizukuPermission() },
                    onSendTestEvent = { viewModel.sendAdbTestEvent() },
                    onDismiss = { viewModel.setAdbPairingDialogVisible(false) }
                )
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
