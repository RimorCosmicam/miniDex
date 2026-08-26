package com.minidex.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.data.UserPreferences
import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.DexDisplayInfo
import com.minidex.app.domain.model.HapticStrength
import com.minidex.app.domain.model.KeyHeightLevel
import com.minidex.app.domain.model.ThemeVariant
import com.minidex.app.domain.model.VisualFilter
import com.minidex.app.ui.theme.LocalMiniDexColors
import com.minidex.app.ui.theme.AnimatedGifBackground
import com.minidex.app.ui.theme.GifCropExporter
import com.minidex.app.ui.theme.toColor
import com.minidex.app.ui.theme.getMiniDexColorScheme
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    preferences: UserPreferences,
    dexDisplayInfo: DexDisplayInfo,
    availableDisplays: List<DexDisplayInfo> = emptyList(),
    isAdbConnected: Boolean = false,
    adbStatusMessage: String = "Disconnected",
    isAccessibilityEnabled: Boolean,
    isImeEnabled: Boolean,
    activeBackendName: String,
    onOpenAdbPairing: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onLaunchSamsungDexTouchpad: () -> Unit = {},
    onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onUpdatePreferences {
                it.copy(
                    backgroundGifUri = uri.toString(),
                    backgroundGifScale = 1f,
                    backgroundGifOffsetX = 0f,
                    backgroundGifOffsetY = 0f
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("GENERAL", "THEMING").forEachIndexed { index, label ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.accent.copy(alpha = 0.24f) else colors.surface)
                            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(50))
                            .clickable { selectedTab = index }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) colors.accent else colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (selectedTab == 0) {
        // Section: Wireless debugging connection
        item {
            SettingsGroup(title = "CONNECTION") {
                SettingsClickableRow(
                    label = "Wireless debugging",
                    status = if (isAdbConnected) "Connected" else "Pair device ›",
                    statusColor = if (isAdbConnected) Color(0xFF00E676) else colors.accent,
                    onClick = onOpenAdbPairing
                )

                SettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column {
                        Text(text = "Auto-Connect on Launch", color = colors.textPrimary, fontSize = 13.sp)
                        Text(text = "Connects to localhost on start", color = colors.textSecondary, fontSize = 10.sp)
                    }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Switch(
                            checked = preferences.adbAutoConnect,
                            onCheckedChange = { checked -> onUpdatePreferences { it.copy(adbAutoConnect = checked) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedTrackColor = colors.surfaceElevated
                            )
                        )
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label = "Connection status",
                    value = if (isAdbConnected) "Connected" else adbStatusMessage.take(24),
                    valueColor = if (isAdbConnected) Color(0xFF00E676) else colors.textSecondary
                )
            }
        }

        // Section: System & Integration
        item {
            SettingsGroup(title = "SYSTEM & FALLBACKS") {
                // DeX Status Row
                SettingsRow(
                    label = "DeX Display",
                    value = if (dexDisplayInfo.isConnected) "Connected (#${dexDisplayInfo.displayId})" else "Not detected",
                    valueColor = if (dexDisplayInfo.isConnected) colors.accent else colors.textSecondary
                )

                SettingsDivider()

                // Manual Display Selector Fallback
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Target Display Override", color = colors.textPrimary, fontSize = 13.sp)
                    Text(
                        text = if (preferences.manualDisplayId == -1) "Auto (#${dexDisplayInfo.displayId})" else "Manual (#${preferences.manualDisplayId})",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Display Selection Pills
                    val displayScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(displayScrollState),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Auto Pill
                        val isAutoSelected = preferences.manualDisplayId == -1
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAutoSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                                .border(1.dp, if (isAutoSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable { onUpdatePreferences { it.copy(manualDisplayId = -1) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Auto",
                                color = if (isAutoSelected) colors.accent else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // Explicit Display options
                        val displaysToShow = if (availableDisplays.isNotEmpty()) availableDisplays else listOf(
                            DexDisplayInfo(displayId = 0, name = "Display 0 (Main)", width = 1080, height = 2640, refreshRate = 120f, isPresentation = false, isExternal = false, isConnected = true),
                            DexDisplayInfo(displayId = 1, name = "Display 1 (Cover)", width = 720, height = 748, refreshRate = 60f, isPresentation = false, isExternal = true, isConnected = true),
                            DexDisplayInfo(displayId = 2, name = "Display 2 (External DeX)", width = 1920, height = 1080, refreshRate = 60f, isPresentation = true, isExternal = true, isConnected = true)
                        )

                        displaysToShow.forEach { d ->
                            val isThisSelected = preferences.manualDisplayId == d.displayId
                            Box(
                                modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isThisSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                                .border(1.dp, if (isThisSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable { onUpdatePreferences { it.copy(manualDisplayId = d.displayId) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Display ${d.displayId}${if (d.name.isNotBlank()) " (${d.name.take(8)})" else ""}",
                                    color = if (isThisSelected) colors.accent else colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isThisSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                SettingsDivider()

                // Accessibility Permission Row
                SettingsClickableRow(
                    label = "Accessibility Driver",
                    status = if (isAccessibilityEnabled) "Active" else "Enable ›",
                    statusColor = if (isAccessibilityEnabled) colors.accent else Color(0xFFFFB74D),
                    onClick = onOpenAccessibilitySettings
                )

                SettingsDivider()

                // Keyboard IME Setting Row
                SettingsClickableRow(
                    label = "DeX Keyboard IME",
                    status = if (isImeEnabled) "Configured" else "Setup ›",
                    statusColor = if (isImeEnabled) colors.accent else Color(0xFFFFB74D),
                    onClick = onOpenImeSettings
                )

                SettingsDivider()

                // Samsung Native Touchpad Summoner
                SettingsClickableRow(
                    label = "Samsung Touchpad",
                    status = if (isAdbConnected) "Open ↗" else "Connect ADB first",
                    statusColor = if (isAdbConnected) colors.accent else colors.textSecondary,
                    onClick = onLaunchSamsungDexTouchpad
                )
            }
        }
        }

        if (selectedTab == 1) {
        // Section: Appearance
        item {
            SettingsGroup(title = "APPEARANCE") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Color swatches with big tap targets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentColor.entries.forEach { accent ->
                            val isSelected = accent == preferences.accentColor
                            val color = accent.toColor()
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
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

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("FILTER", color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            VisualFilter.entries.forEach { filter ->
                                val selected = filter == preferences.visualFilter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) colors.accent.copy(alpha = 0.22f) else colors.surfaceElevated)
                                        .border(1.dp, if (selected) colors.accent else colors.border.copy(alpha = 0.45f), RoundedCornerShape(50))
                                        .clickable { onUpdatePreferences { it.copy(visualFilter = filter) } }
                                        .padding(horizontal = 11.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        filter.displayName,
                                        color = if (selected) colors.accent else colors.textSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AMOLED", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Double-tap the mode pill", color = colors.textSecondary, fontSize = 9.sp)
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Switch(
                                checked = preferences.amoledMode,
                                onCheckedChange = { enabled -> onUpdatePreferences { it.copy(amoledMode = enabled) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                    uncheckedTrackColor = colors.surfaceElevated
                                )
                            )
                        }
                    }

                    SettingsDivider()

                    // Two columns keep theme names readable on the narrow cover display.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThemeVariant.entries.chunked(2).forEach { themes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                themes.forEach { theme ->
                                    val isSelected = theme == preferences.themeVariant
                                    val preview = getMiniDexColorScheme(theme, preferences.accentColor)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        preview.background,
                                                        preview.keyBackground,
                                                        preview.accent.copy(alpha = if (isSelected) 0.38f else 0.18f)
                                                    )
                                                )
                                            )
                                            .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                                            .clickable { onUpdatePreferences { it.copy(themeVariant = theme) } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = theme.displayName,
                                            color = if (isSelected) colors.accent else colors.textSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (themes.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                }
            }
        }

        item {
            SettingsGroup(title = "ANIMATED BACKGROUND") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preferences.backgroundGifUri.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        ) {
                            AnimatedGifBackground(
                                uri = preferences.backgroundGifUri,
                                scale = preferences.backgroundGifScale,
                                offsetX = preferences.backgroundGifOffsetX,
                                offsetY = preferences.backgroundGifOffsetY,
                                opacity = 1f,
                                filter = preferences.visualFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    SettingsClickableRow(
                        label = if (preferences.backgroundGifUri.isBlank()) "Choose GIF" else "Replace GIF",
                        status = "Browse ›",
                        statusColor = colors.accent,
                        onClick = { gifPicker.launch(arrayOf("image/gif")) }
                    )
                    if (preferences.backgroundGifUri.isNotBlank()) {
                        Text("Crop zoom", color = colors.textPrimary, fontSize = 12.sp)
                        Slider(
                            value = preferences.backgroundGifScale,
                            onValueChange = { value -> onUpdatePreferences { it.copy(backgroundGifScale = value) } },
                            valueRange = 1f..4f,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                        )
                        Text("Horizontal crop", color = colors.textPrimary, fontSize = 12.sp)
                        Slider(
                            value = preferences.backgroundGifOffsetX,
                            onValueChange = { value -> onUpdatePreferences { it.copy(backgroundGifOffsetX = value) } },
                            valueRange = -1f..1f,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                        )
                        Text("Vertical crop", color = colors.textPrimary, fontSize = 12.sp)
                        Slider(
                            value = preferences.backgroundGifOffsetY,
                            onValueChange = { value -> onUpdatePreferences { it.copy(backgroundGifOffsetY = value) } },
                            valueRange = -1f..1f,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                        )
                        Text("Background opacity", color = colors.textPrimary, fontSize = 12.sp)
                        Slider(
                            value = preferences.backgroundGifOpacity,
                            onValueChange = { value -> onUpdatePreferences { it.copy(backgroundGifOpacity = value) } },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsActionPill("SAVE CROPPED", Modifier.weight(1f)) {
                                scope.launch {
                                    val result = GifCropExporter.saveToGallery(
                                        context,
                                        preferences.backgroundGifUri,
                                        preferences.backgroundGifScale,
                                        preferences.backgroundGifOffsetX,
                                        preferences.backgroundGifOffsetY,
                                        preferences.visualFilter
                                    )
                                    Toast.makeText(
                                        context,
                                        if (result.isSuccess) "Cropped GIF saved to Pictures/MiniDex" else "Could not save cropped GIF",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            SettingsActionPill("REMOVE", Modifier.weight(1f)) {
                                onUpdatePreferences { it.copy(backgroundGifUri = "") }
                            }
                        }
                    }
                }
            }
        }
        }

        if (selectedTab == 0) {
        // Section: Keyboard
        item {
            SettingsGroup(title = "KEYBOARD") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Key Height
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Key Sizing", color = colors.textPrimary, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            KeyHeightLevel.entries.forEach { level ->
                                val isSelected = level == preferences.keyHeightLevel
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                                        .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable { onUpdatePreferences { it.copy(keyHeightLevel = level) } }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = level.displayName,
                                        color = if (isSelected) colors.accent else colors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    // Haptics
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Haptic Feedback", color = colors.textPrimary, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HapticStrength.entries.forEach { strength ->
                                val isSelected = strength == preferences.hapticStrength
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                                        .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable { onUpdatePreferences { it.copy(hapticStrength = strength) } }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = strength.displayName,
                                        color = if (isSelected) colors.accent else colors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Section: Trackpad
        item {
            SettingsGroup(title = "TRACKPAD") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(text = "Hardware Cursor", color = colors.textPrimary, fontSize = 13.sp)
                        Text(
                            text = "Uses the same shell Binder and legacy UHID protocol as the supplied working app. Connection is only marked active after the mouse service responds.",
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "Tracking Speed", color = colors.textPrimary, fontSize = 13.sp)
                        Text(text = "${"%.1f".format(preferences.pointerSensitivity)}x", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = preferences.pointerSensitivity,
                        onValueChange = { value -> onUpdatePreferences { it.copy(pointerSensitivity = value) } },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Natural Scrolling", color = colors.textPrimary, fontSize = 13.sp)
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Switch(
                                checked = preferences.naturalScrolling,
                                onCheckedChange = { checked -> onUpdatePreferences { it.copy(naturalScrolling = checked) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                    uncheckedTrackColor = colors.surfaceElevated
                                )
                            )
                        }
                    }

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(text = "Edge Scroll Side", color = colors.textPrimary, fontSize = 13.sp)
                        Text(
                            text = "Use the marked edge like a physical scroll strip",
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(false to "Left", true to "Right").forEach { (rightSide, label) ->
                                val selected = preferences.edgeScrollOnRight == rightSide
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) colors.accent.copy(alpha = 0.25f) else colors.surfaceElevated)
                                        .border(
                                            1.dp,
                                            if (selected) colors.accent else colors.border.copy(alpha = 0.4f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            onUpdatePreferences { it.copy(edgeScrollOnRight = rightSide) }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) colors.accent else colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Extra spacing so camera cutout & mode button never overlap any settings
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalMiniDexColors.current
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border.copy(alpha = 0.4f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 6.dp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = label, color = colors.textPrimary, fontSize = 13.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = colors.textPrimary, fontSize = 13.sp)
        Text(
            text = status,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SettingsActionPill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalMiniDexColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.accent.copy(alpha = 0.18f))
            .border(1.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsDivider() {
    val colors = LocalMiniDexColors.current
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(colors.border.copy(alpha = 0.25f))
    )
}
