package com.minidex.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.data.UserPreferences
import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.DexDisplayInfo
import com.minidex.app.domain.model.HapticStrength
import com.minidex.app.domain.model.KeyHeightLevel
import com.minidex.app.domain.model.ThemeVariant
import com.minidex.app.ui.theme.LocalMiniDexColors
import com.minidex.app.ui.theme.toColor

@Composable
fun SettingsView(
    preferences: UserPreferences,
    dexDisplayInfo: DexDisplayInfo,
    isAccessibilityEnabled: Boolean,
    isImeEnabled: Boolean,
    activeBackendName: String,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onLaunchSamsungDexTouchpad: () -> Unit = {},
    onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section: DeX Status & Same-Device Drivers
        SettingsCard(title = "SAMSUNG DEX & LOCAL DRIVERS") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (dexDisplayInfo.isConnected) "DeX Monitor: Connected (#${dexDisplayInfo.displayId})" else "DeX Monitor: Standalone / Cover",
                            color = if (dexDisplayInfo.isConnected) colors.accent else colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active Engine: $activeBackendName",
                            color = colors.textSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                // Driver 1: Multi-Display Accessibility Direct Driver (Touchpad, Gestures, Clicks)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAccessibilityEnabled) colors.accent.copy(alpha = 0.2f) else colors.surfaceElevated)
                        .border(1.dp, if (isAccessibilityEnabled) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { onOpenAccessibilitySettings() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "1. Touchpad & Gestures Driver",
                                color = colors.textPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAccessibilityEnabled) "✓ Multi-Display Engine Active" else "Tap to Enable in Accessibility",
                                color = if (isAccessibilityEnabled) colors.accent else colors.textSecondary,
                                fontSize = 7.5.sp
                            )
                        }
                        Text(
                            text = if (isAccessibilityEnabled) "ACTIVE" else "ENABLE →",
                            color = if (isAccessibilityEnabled) colors.accent else colors.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Driver 2: Native Android IME Keyboard Engine (Text input into DeX windows)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isImeEnabled) colors.accent.copy(alpha = 0.2f) else colors.surfaceElevated)
                        .border(1.dp, if (isImeEnabled) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { onOpenImeSettings() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "2. Native Keyboard IME Engine",
                                color = colors.textPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isImeEnabled) "✓ DeX Keyboard Enabled in System" else "Tap to Enable in Keyboard Settings",
                                color = if (isImeEnabled) colors.accent else colors.textSecondary,
                                fontSize = 7.5.sp
                            )
                        }
                        Text(
                            text = if (isImeEnabled) "ACTIVE" else "ENABLE →",
                            color = if (isImeEnabled) colors.accent else colors.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Driver 3: Samsung Official Native DeX Touchpad (Hardware kernel injection)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2196F3).copy(alpha = 0.18f))
                        .border(1.dp, Color(0xFF2196F3).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onLaunchSamsungDexTouchpad() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "3. Samsung Official DeX Touchpad",
                                color = Color(0xFF90CAF9),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Summons Samsung's system touchpad overlay",
                                color = colors.textSecondary,
                                fontSize = 7.5.sp
                            )
                        }
                        Text(
                            text = "LAUNCH ↗",
                            color = Color(0xFF90CAF9),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Accent Color
        SettingsCard(title = "ACCENT COLOR") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AccentColor.entries.forEach { accent ->
                    val isSelected = accent == preferences.accentColor
                    val color = accent.toColor()
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onUpdatePreferences { it.copy(accentColor = accent) }
                            }
                    )
                }
            }
        }

        // Section: Theme Variant
        SettingsCard(title = "THEME VARIANT") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeVariant.entries.forEach { theme ->
                    val isSelected = theme == preferences.themeVariant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                            .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onUpdatePreferences { it.copy(themeVariant = theme) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = theme.displayName.take(8),
                            color = if (isSelected) colors.accent else colors.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Haptics & Feedback
        SettingsCard(title = "HAPTIC FEEDBACK") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HapticStrength.entries.forEach { strength ->
                    val isSelected = strength == preferences.hapticStrength
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                            .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onUpdatePreferences { it.copy(hapticStrength = strength) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strength.displayName.take(6),
                            color = if (isSelected) colors.accent else colors.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Key Height
        SettingsCard(title = "KEYBOARD SIZING") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyHeightLevel.entries.forEach { level ->
                    val isSelected = level == preferences.keyHeightLevel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                            .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onUpdatePreferences { it.copy(keyHeightLevel = level) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level.displayName,
                            color = if (isSelected) colors.accent else colors.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Touchpad Physics
        SettingsCard(title = "TOUCHPAD SENSITIVITY") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Pointer Speed", color = colors.textSecondary, fontSize = 9.sp)
                    Text(text = "${"%.1f".format(preferences.pointerSensitivity)}x", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = preferences.pointerSensitivity,
                    onValueChange = { value -> onUpdatePreferences { it.copy(pointerSensitivity = value) } },
                    valueRange = 0.5f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                    modifier = Modifier.height(24.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Natural Scrolling", color = colors.textSecondary, fontSize = 9.sp)
                    Switch(
                        checked = preferences.naturalScrolling,
                        onCheckedChange = { checked -> onUpdatePreferences { it.copy(naturalScrolling = checked) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent,
                            uncheckedTrackColor = colors.surfaceElevated
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalMiniDexColors.current
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border.copy(alpha = 0.4f), shape)
            .padding(6.dp)
    ) {
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}
