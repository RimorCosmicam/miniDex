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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.domain.model.AppMode
import com.minidex.app.domain.model.KeyboardPage
import com.minidex.app.ui.components.MontPill
import com.minidex.app.ui.components.MONT_TOP_INSET
import com.minidex.app.ui.components.PageBar
import com.minidex.app.ui.components.SpecialRow
import com.minidex.app.ui.keyboard.MacropadView
import com.minidex.app.ui.keyboard.NavKeyboard
import com.minidex.app.ui.keyboard.MontKeyboard
import com.minidex.app.ui.onboarding.PermissionItem
import com.minidex.app.ui.onboarding.Welcome
import com.minidex.app.ui.keyboard.SymbolKeyboard
import com.minidex.app.ui.settings.PairingOverlay
import com.minidex.app.ui.settings.PairingPanel
import com.minidex.app.ui.settings.MacroEditorDialog
import com.minidex.app.ui.settings.SettingsView
import com.minidex.app.ui.theme.LocalMiniDexColors
import com.minidex.app.ui.theme.AnimatedGifBackground
import com.minidex.app.ui.theme.HalftoneBackground
import com.minidex.app.ui.theme.MiniDexTheme
import com.minidex.app.ui.theme.Mont
import androidx.compose.ui.graphics.luminance
import com.minidex.app.ui.touchpad.EdgeControls
import com.minidex.app.ui.touchpad.EdgeRefractionSurface
import com.minidex.app.ui.touchpad.TouchpadView

/**
 * Holds its content against the bottom of the area it is given, with the pill's band left clear
 * beneath it. Keys belong under the thumb; the top of a cover display is the hardest place on it
 * to hit accurately.
 */
@Composable
private fun LowerHalf(
    currentPage: KeyboardPage,
    isAdbConnected: Boolean,
    onPageSelected: (KeyboardPage) -> Unit,
    modifierState: com.minidex.app.domain.model.ModifierState,
    keyHeight: androidx.compose.ui.unit.Dp,
    onModifierToggle: (com.minidex.app.domain.model.ModifierType) -> Unit,
    onKeyPress: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // The cover display's cutout is the lower right corner; the keys hold off it.
            .padding(bottom = 92.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PageBar(
                currentPage = currentPage,
                isAdbConnected = isAdbConnected,
                onPageSelected = onPageSelected
            )
            SpecialRow(
                modifierState = modifierState,
                keyHeight = keyHeight,
                onModifierToggle = onModifierToggle,
                onKeyPress = onKeyPress
            )
            content()
        }
    }
}

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
    val isAccessibilityConfigured by viewModel.isAccessibilityConfigured.collectAsState()
    val isImeEnabled by viewModel.isImeEnabled.collectAsState()
    val showMacroEditor by viewModel.showMacroEditor.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showAdbPairingDialog by viewModel.showAdbPairingDialog.collectAsState()
    val adbConnectionStatus by viewModel.adbConnectionStatus.collectAsState()
    val adbStatusMessage by viewModel.adbStatusMessage.collectAsState()
    val discoveredPairingPort by viewModel.discoveredPairingPort.collectAsState()
    val discoveredConnectPort by viewModel.discoveredConnectPort.collectAsState()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val showPairingOverlay by viewModel.showPairingOverlay.collectAsState()
    val autoPairFailed by viewModel.autoPairFailed.collectAsState()
    val onboardingDismissed by viewModel.onboardingDismissed.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    MiniDexTheme(
        colorway = preferences.colorway,
        amoledMode = preferences.amoledMode
    ) {
        val colors = LocalMiniDexColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            if (!preferences.amoledMode) {
                EdgeRefractionSurface(
                    railEnabled = currentMode == AppMode.TOUCHPAD && preferences.edgeScrollEnabled,
                    cornerEnabled = currentMode == AppMode.TOUCHPAD && preferences.edgeRightClickEnabled,
                    railOnRight = preferences.edgeScrollOnRight,
                    railScale = preferences.edgeRailScale,
                    cornerScale = preferences.edgeCornerScale,
                    modifier = Modifier.fillMaxSize()
                ) {
                if (preferences.backgroundGifUri.isBlank()) {
                    HalftoneBackground(
                        colorway = preferences.colorway,
                        filter = preferences.visualFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AnimatedGifBackground(
                        uri = preferences.backgroundGifUri,
                        scale = preferences.backgroundGifScale,
                        offsetX = preferences.backgroundGifOffsetX,
                        offsetY = preferences.backgroundGifOffsetY,
                        opacity = preferences.backgroundGifOpacity,
                        filter = preferences.visualFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 4.dp, top = MONT_TOP_INSET, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Main Content Area: Switches between Touchpad & Keyboard pages
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (showSettings) return@Box
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
                                    label = "page_crossfade",
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    when (page) {
                                        KeyboardPage.ABC -> LowerHalf(
                                            currentPage = currentPage,
                                            isAdbConnected = isAdbConnected,
                                            onPageSelected = { viewModel.selectKeyboardPage(it) },
                                            modifierState = modifierState,
                                            keyHeight = (preferences.keyHeightLevel.heightDp - 12).dp,
                                            onModifierToggle = { viewModel.toggleModifier(it) },
                                            onKeyPress = { viewModel.handleKeyPress(it) }
                                        ) {
                                            MontKeyboard(
                                                modifierState = modifierState,
                                                keyHeight = preferences.keyHeightLevel.heightDp / 46f,
                                                onCharPress = { char, code -> viewModel.handleCharPress(char, code) },
                                                onSwipeWord = { viewModel.handleSwipeWord(it) },
                                                onModifierToggle = { viewModel.toggleModifier(it) },
                                                onKeyPress = { viewModel.handleKeyPress(it) },
                                                onHaptic = { viewModel.hapticManager.performHaptic(preferences.hapticStrength) }
                                            )
                                        }

                                        KeyboardPage.SYMBOLS -> LowerHalf(
                                            currentPage = currentPage,
                                            isAdbConnected = isAdbConnected,
                                            onPageSelected = { viewModel.selectKeyboardPage(it) },
                                            modifierState = modifierState,
                                            keyHeight = (preferences.keyHeightLevel.heightDp - 12).dp,
                                            onModifierToggle = { viewModel.toggleModifier(it) },
                                            onKeyPress = { viewModel.handleKeyPress(it) }
                                        ) {
                                            SymbolKeyboard(
                                                keyHeight = preferences.keyHeightLevel.heightDp.dp,
                                                keyGap = preferences.keyGapDp.dp,
                                                onCharPress = { char, code -> viewModel.handleCharPress(char, code) },
                                                onKeyPress = { viewModel.handleKeyPress(it) }
                                            )
                                        }

                                        KeyboardPage.NAV -> LowerHalf(
                                            currentPage = currentPage,
                                            isAdbConnected = isAdbConnected,
                                            onPageSelected = { viewModel.selectKeyboardPage(it) },
                                            modifierState = modifierState,
                                            keyHeight = (preferences.keyHeightLevel.heightDp - 12).dp,
                                            onModifierToggle = { viewModel.toggleModifier(it) },
                                            onKeyPress = { viewModel.handleKeyPress(it) }
                                        ) {
                                            NavKeyboard(
                                                keyHeight = preferences.keyHeightLevel.heightDp.dp,
                                                keyGap = preferences.keyGapDp.dp,
                                                onKeyPress = { viewModel.handleKeyPress(it) }
                                            )
                                        }

                                        KeyboardPage.MACROS -> {
                                            MacropadView(
                                                macros = macros,
                                                onExecuteMacro = { viewModel.executeMacro(it) },
                                                onAddMacroClick = { viewModel.setMacroEditorVisible(true) }
                                            )
                                        }

                                        // Unreachable: the page bar hides it and settings is the
                                        // studio the pill opens.
                                        KeyboardPage.SETTINGS -> Unit
                                    }
                                }
                            }
                        }
                    }
                }

            }

            if (showSettings) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SettingsView(
                        preferences = preferences,
                        availableDisplays = availableDisplays,
                        isAdbConnected = isAdbConnected,
                        onLaunchSamsungDexTouchpad = { viewModel.launchSamsungDexTouchpad() },
                        onUpdatePreferences = { viewModel.updatePreferences(it) },
                        onClose = { viewModel.setSettingsVisible(false) },
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }

            if (currentMode == AppMode.TOUCHPAD && !showSettings) {
                EdgeControls(
                    railEnabled = preferences.edgeScrollEnabled,
                    rightClickEnabled = preferences.edgeRightClickEnabled,
                    railScale = preferences.edgeRailScale,
                    cornerScale = preferences.edgeCornerScale,
                    railOnRight = preferences.edgeScrollOnRight,
                    markLight = colors.background.luminance() < 0.5f,
                    scrollSensitivity = preferences.scrollSensitivity,
                    naturalScrolling = preferences.naturalScrolling,
                    onScroll = { dx, dy -> viewModel.handleScroll(dx, dy) },
                    onRightClick = { viewModel.handlePointerClick(2) },
                    onHaptic = { viewModel.hapticManager.performHaptic(preferences.hapticStrength) }
                )
            }

            // The pill sits at the lower left of the cover display: clear of the camera, and
            // under the thumb. It is the only way in and out of everything else.
            MontPill(
                isAmoled = preferences.amoledMode,
                isConnected = isAdbConnected || isAccessibilityEnabled,
                onTap = { viewModel.toggleAppMode() },
                onDoubleTap = { viewModel.toggleAmoledMode() },
                onLongPress = { viewModel.setSettingsVisible(true) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 50.dp, bottom = 30.dp)
            )

            // The pairing panel is a window of its own, so it survives MiniDex going to the
            // background when Android's Wireless Debugging screen comes forward.
            LaunchedEffect(showPairingOverlay, activeDexDisplay.displayId) {
                if (showPairingOverlay) {
                    val displayId = context.display?.displayId ?: 0
                    viewModel.setPairingDisplay(displayId)
                    val floated = PairingOverlay.show(
                        appContext = context.applicationContext,
                        displayId = displayId
                    ) {
                        val code by viewModel.pairingCode.collectAsState()
                        val typedPort by viewModel.pairingPort.collectAsState()
                        val port by viewModel.discoveredPairingPort.collectAsState()
                        val status by viewModel.adbStatusMessage.collectAsState()
                        val connection by viewModel.adbConnectionStatus.collectAsState()
                        MiniDexTheme(
                            colorway = preferences.colorway,
                            amoledMode = preferences.amoledMode
                        ) {
                            PairingPanel(
                                code = code,
                                port = typedPort,
                                discoveredPort = port,
                                statusMessage = status,
                                isBusy = connection == com.minidex.app.input.adb.AdbConnectionStatus.PAIRING ||
                                    connection == com.minidex.app.input.adb.AdbConnectionStatus.CONNECTING,
                                onCode = { viewModel.setPairingCode(it) },
                                onPort = { viewModel.setPairingPort(it) },
                                onPair = { viewModel.pairWithEnteredCode() },
                                onClose = { viewModel.dismissPairingOverlay() }
                            )
                        }
                    }
                    // Only now is it worth leaving for Developer options: the strip is up and will
                    // stay up over it. If it could not attach, going there would strand the user.
                    if (floated) {
                        viewModel.openWirelessDebuggingSettings()
                    } else {
                        viewModel.dismissPairingOverlay()
                        viewModel.openAccessibilitySettings()
                    }
                } else {
                    PairingOverlay.hide()
                }
            }
            DisposableEffect(Unit) {
                onDispose { PairingOverlay.hide() }
            }

            val driversReady = isAdbConnected && isAccessibilityEnabled && isImeEnabled
            if ((!driversReady || !preferences.onboardingComplete) && !onboardingDismissed) {
                Welcome(
                    permissions = listOf(
                        // Accessibility leads, and not by preference: it is the window type the
                        // pairing strip floats in. Without it, opening Android's debugging screen
                        // puts MiniDex in the background and there is nowhere left to type the
                        // code — which is the whole reason the strip exists.
                        PermissionItem(
                            label = "Accessibility",
                            // Listed but unbound is the state that follows reinstalling the app,
                            // and it looks identical to "on" unless it is said out loud.
                            detail = if (isAccessibilityConfigured && !isAccessibilityEnabled) {
                                "Listed but not running — open it and toggle it off and on"
                            } else {
                                "Gesture driver, and the pairing strip's window"
                            },
                            granted = isAccessibilityEnabled,
                            onGrant = { viewModel.openAccessibilitySettings() }
                        ),
                        PermissionItem(
                            label = "Keyboard IME",
                            detail = "Types into DeX windows",
                            granted = isImeEnabled,
                            onGrant = { viewModel.openImeSettings() }
                        ),
                        PermissionItem(
                            label = "Wireless ADB",
                            detail = when {
                                isAccessibilityEnabled -> "Hardware injection, lowest latency"
                                isAccessibilityConfigured ->
                                    "Accessibility is listed but not running — restart it first"
                                else -> "Turn on Accessibility first"
                            },
                            granted = isAdbConnected,
                            onGrant = {
                                if (isAccessibilityEnabled) {
                                    viewModel.requestPairing()
                                } else {
                                    viewModel.openAccessibilitySettings()
                                }
                            },
                            shortcutLabel = when {
                                viewModel.canAutoPair() -> "Auto"
                                isShizukuAvailable -> "Shizuku"
                                else -> null
                            },
                            onShortcut = when {
                                viewModel.canAutoPair() -> {
                                    { viewModel.autoPairAdb() }
                                }
                                isShizukuAvailable -> {
                                    { viewModel.requestShizukuPermission() }
                                }
                                else -> null
                            },
                            shortcutFailed = autoPairFailed
                        )
                    ),
                    onFinished = { viewModel.completeOnboarding() }
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
