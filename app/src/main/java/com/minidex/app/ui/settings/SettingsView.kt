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
import androidx.compose.foundation.layout.width
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
        // Section: System & Integration
        SettingsGroup(title = "SYSTEM") {
            // DeX Status Row
            SettingsRow(
                label = "DeX Display",
                value = if (dexDisplayInfo.isConnected) "Connected" else "Not detected",
                valueColor = if (dexDisplayInfo.isConnected) colors.accent else colors.textSecondary
            )

            SettingsDivider()

            // Accessibility Permission Row
            SettingsClickableRow(
                label = "Touchpad & Gestures",
                status = if (isAccessibilityEnabled) "Active" else "Enable ›",
                statusColor = if (isAccessibilityEnabled) colors.accent else Color(0xFFFFB74D),
                onClick = onOpenAccessibilitySettings
            )

            SettingsDivider()

            // Keyboard IME Setting Row
            SettingsClickableRow(
                label = "DeX Keyboard",
                status = if (isImeEnabled) "Configured" else "Setup ›",
                statusColor = if (isImeEnabled) colors.accent else Color(0xFFFFB74D),
                onClick = onOpenImeSettings
            )

            SettingsDivider()

            // Samsung Native Touchpad Summoner
            SettingsClickableRow(
                label = "Samsung Touchpad",
                status = "Open ↗",
                statusColor = colors.textSecondary,
                onClick = onLaunchSamsungDexTouchpad
            )
        }

        // Section: Appearance
        SettingsGroup(title = "APPEARANCE") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Color swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccentColor.entries.forEach { accent ->
                        val isSelected = accent == preferences.accentColor
                        val color = accent.toColor()
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onUpdatePreferences { it.copy(accentColor = accent) }
                                }
                        )
                    }
                }

                SettingsDivider()

                // Theme selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    ThemeVariant.entries.forEach { theme ->
                        val isSelected = theme == preferences.themeVariant
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surfaceElevated)
                                .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                                .clickable { onUpdatePreferences { it.copy(themeVariant = theme) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = theme.displayName,
                                color = if (isSelected) colors.accent else colors.textSecondary,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Section: Keyboard
        SettingsGroup(title = "KEYBOARD") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Key Height
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Key Sizing", color = colors.textSecondary, fontSize = 8.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        KeyHeightLevel.entries.forEach { level ->
                            val isSelected = level == preferences.keyHeightLevel
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surfaceElevated)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable { onUpdatePreferences { it.copy(keyHeightLevel = level) } }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = level.displayName,
                                    color = if (isSelected) colors.accent else colors.textSecondary,
                                    fontSize = 7.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                SettingsDivider()

                // Haptics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Haptic Feedback", color = colors.textSecondary, fontSize = 8.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        HapticStrength.entries.forEach { strength ->
                            val isSelected = strength == preferences.hapticStrength
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surfaceElevated)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable { onUpdatePreferences { it.copy(hapticStrength = strength) } }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = strength.displayName,
                                    color = if (isSelected) colors.accent else colors.textSecondary,
                                    fontSize = 7.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Trackpad
        SettingsGroup(title = "TRACKPAD") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Tracking Speed", color = colors.textSecondary, fontSize = 8.5.sp)
                    Text(text = "${"%.1f".format(preferences.pointerSensitivity)}x", color = colors.accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = preferences.pointerSensitivity,
                    onValueChange = { value -> onUpdatePreferences { it.copy(pointerSensitivity = value) } },
                    valueRange = 0.5f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                    modifier = Modifier.height(20.dp)
                )

                SettingsDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Natural Scrolling", color = colors.textSecondary, fontSize = 8.5.sp)
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
fun SettingsGroup(
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
            .border(1.dp, colors.border.copy(alpha = 0.35f), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingsRow(
    label: String,
    value: String,
    valueColor: Color
) {
    val colors = LocalMiniDexColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textPrimary, fontSize = 8.5.sp)
        Text(text = value, color = valueColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsClickableRow(
    label: String,
    status: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    val colors = LocalMiniDexColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textPrimary, fontSize = 8.5.sp)
        Text(text = status, color = statusColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsDivider() {
    val colors = LocalMiniDexColors.current
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(colors.border.copy(alpha = 0.2f))
    )
}
