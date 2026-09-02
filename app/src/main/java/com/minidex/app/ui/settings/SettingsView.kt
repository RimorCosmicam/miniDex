package com.minidex.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.data.UserPreferences
import com.minidex.app.domain.model.DexDisplayInfo
import com.minidex.app.domain.model.HalftoneColorway
import com.minidex.app.domain.model.HapticStrength
import com.minidex.app.domain.model.KeyHeightLevel
import com.minidex.app.domain.model.VisualFilter
import com.minidex.app.ui.components.MontSlider
import com.minidex.app.ui.components.MontToggle
import com.minidex.app.ui.components.StudioChip
import com.minidex.app.ui.components.StudioPanel
import com.minidex.app.ui.components.StudioRow
import com.minidex.app.ui.theme.GifCropExporter
import com.minidex.app.ui.theme.Mont
import com.minidex.app.ui.theme.ground
import com.minidex.app.ui.theme.ink
import kotlinx.coroutines.launch

private enum class StudioTab(val label: String) {
    PAD("Pad"), KEYS("Keys"), LOOK("Look"), IMAGE("Image")
}

/**
 * Settings, built the way miniMate's studios are: a row of section chips, then that section's
 * options as horizontal rows of chips. Nothing is a vertical list of labelled rows — the panel is
 * small and sits over the live screen, so browsing it should never be reading a menu.
 *
 * Driver setup is not here. If a driver is missing the app opens onto onboarding, which is where
 * granting belongs; repeating it in the menu only gave the same job two homes.
 */
@Composable
fun SettingsView(
    preferences: UserPreferences,
    availableDisplays: List<DexDisplayInfo> = emptyList(),
    isAdbConnected: Boolean = false,
    onLaunchSamsungDexTouchpad: () -> Unit = {},
    onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(StudioTab.PAD) }
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

    StudioPanel(modifier = modifier, maxHeight = 190.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StudioTab.entries.forEach { value ->
                StudioChip(value.label.uppercase(), tab == value) { tab = value }
            }
        }

        when (tab) {
            StudioTab.PAD -> {
                SliderLine("Speed", preferences.pointerSensitivity, 0.5f..3f) { value ->
                    onUpdatePreferences { it.copy(pointerSensitivity = value) }
                }
                SliderLine("Accel", preferences.pointerAcceleration, 0f..2f) { value ->
                    onUpdatePreferences { it.copy(pointerAcceleration = value) }
                }
                SliderLine("Scroll", preferences.scrollSensitivity, 0.25f..3f) { value ->
                    onUpdatePreferences { it.copy(scrollSensitivity = value) }
                }
                PanelRow {
                    StudioChip("RAIL", preferences.edgeScrollEnabled) {
                        onUpdatePreferences { it.copy(edgeScrollEnabled = !it.edgeScrollEnabled) }
                    }
                    StudioChip("CORNER", preferences.edgeRightClickEnabled) {
                        onUpdatePreferences { it.copy(edgeRightClickEnabled = !it.edgeRightClickEnabled) }
                    }
                    StudioChip("TAP CLICK", preferences.tapToClick) {
                        onUpdatePreferences { it.copy(tapToClick = !it.tapToClick) }
                    }
                    StudioChip("NATURAL", preferences.naturalScrolling) {
                        onUpdatePreferences { it.copy(naturalScrolling = !it.naturalScrolling) }
                    }
                    StudioChip("RAIL LEFT", !preferences.edgeScrollOnRight) {
                        onUpdatePreferences { it.copy(edgeScrollOnRight = false) }
                    }
                    StudioChip("RAIL RIGHT", preferences.edgeScrollOnRight) {
                        onUpdatePreferences { it.copy(edgeScrollOnRight = true) }
                    }
                }
                SliderLine("Rail size", preferences.edgeRailScale, 0.65f..1.8f) { value ->
                    onUpdatePreferences { it.copy(edgeRailScale = value) }
                }
                SliderLine("Corner size", preferences.edgeCornerScale, 0.65f..1.8f) { value ->
                    onUpdatePreferences { it.copy(edgeCornerScale = value) }
                }
                PanelRow {
                    StudioChip("AUTO", preferences.manualDisplayId == -1) {
                        onUpdatePreferences { it.copy(manualDisplayId = -1) }
                    }
                    val displays = availableDisplays.ifEmpty {
                        listOf(
                            DexDisplayInfo(0, "Main", 1080, 2640, 120f, false, false, true),
                            DexDisplayInfo(1, "Cover", 948, 1048, 120f, false, true, true),
                            DexDisplayInfo(2, "DeX", 1920, 1080, 60f, true, true, true)
                        )
                    }
                    displays.forEach { display ->
                        StudioChip("#${display.displayId}", preferences.manualDisplayId == display.displayId) {
                            onUpdatePreferences { it.copy(manualDisplayId = display.displayId) }
                        }
                    }
                    if (isAdbConnected) {
                        StudioChip("SAMSUNG PAD", false) { onLaunchSamsungDexTouchpad() }
                    }
                }
            }

            StudioTab.KEYS -> {
                PanelRow {
                    KeyHeightLevel.entries.forEach { level ->
                        StudioChip(level.displayName.uppercase(), level == preferences.keyHeightLevel) {
                            onUpdatePreferences { it.copy(keyHeightLevel = level) }
                        }
                    }
                }
                PanelRow {
                    HapticStrength.entries.forEach { strength ->
                        StudioChip(strength.displayName.uppercase(), strength == preferences.hapticStrength) {
                            onUpdatePreferences { it.copy(hapticStrength = strength) }
                        }
                    }
                }
                ToggleLine("Lock", preferences.doubleTapToLockModifier) { on ->
                    onUpdatePreferences { it.copy(doubleTapToLockModifier = on) }
                }
            }

            StudioTab.LOOK -> {
                PanelRow {
                    HalftoneColorway.entries.forEach { colorway ->
                        val selected = colorway == preferences.colorway
                        // One cell of the field itself: the ground with its dot on it. The
                        // colours are the decision being made, so the swatch shows them.
                        Canvas(
                            Modifier
                                .size(if (selected) 26.dp else 20.dp)
                                .clickable { onUpdatePreferences { it.copy(colorway = colorway) } }
                        ) {
                            drawRect(colorway.ground())
                            drawCircle(colorway.ink(), size.minDimension * 0.32f, center)
                        }
                    }
                }
                PanelRow {
                    VisualFilter.entries.forEach { filter ->
                        StudioChip(filter.displayName.uppercase(), filter == preferences.visualFilter) {
                            onUpdatePreferences { it.copy(visualFilter = filter) }
                        }
                    }
                }
                ToggleLine("Amoled", preferences.amoledMode) { on ->
                    onUpdatePreferences { it.copy(amoledMode = on) }
                }
            }

            StudioTab.IMAGE -> {
                PanelRow {
                    StudioChip(
                        if (preferences.backgroundGifUri.isBlank()) "CHOOSE GIF" else "REPLACE",
                        false
                    ) { gifPicker.launch(arrayOf("image/gif")) }
                    if (preferences.backgroundGifUri.isNotBlank()) {
                        StudioChip("SAVE CROP", false) {
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
                                    if (result.isSuccess) "Saved to Pictures/MiniDex" else "Could not save",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        StudioChip("REMOVE", false) {
                            onUpdatePreferences { it.copy(backgroundGifUri = "") }
                        }
                    }
                }
                if (preferences.backgroundGifUri.isNotBlank()) {
                    SliderLine("Zoom", preferences.backgroundGifScale, 1f..4f) { value ->
                        onUpdatePreferences { it.copy(backgroundGifScale = value) }
                    }
                    SliderLine("Crop X", preferences.backgroundGifOffsetX, -1f..1f) { value ->
                        onUpdatePreferences { it.copy(backgroundGifOffsetX = value) }
                    }
                    SliderLine("Crop Y", preferences.backgroundGifOffsetY, -1f..1f) { value ->
                        onUpdatePreferences { it.copy(backgroundGifOffsetY = value) }
                    }
                    SliderLine("Fade", preferences.backgroundGifOpacity, 0.1f..1f) { value ->
                        onUpdatePreferences { it.copy(backgroundGifOpacity = value) }
                    }
                }
            }
        }

        StudioRow(label = "CLOSE", dim = true, onClick = onClose)
    }
}

/** A tray of choices that scrolls sideways rather than wrapping into a list. */
@Composable
private fun PanelRow(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0x4D000000))
            .padding(vertical = 5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) { content() }
    }
}

/** A named control on one line: the label holds a fixed column so every line starts alike. */
@Composable
private fun SliderLine(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            modifier = Modifier.width(62.dp)
        )
        MontSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            range = range,
            onChange = onChange,
            modifier = Modifier.weight(1f).height(22.dp)
        )
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            modifier = Modifier.width(62.dp)
        )
        MontToggle(checked = checked, onChange = onChange)
    }
}
