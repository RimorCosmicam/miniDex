package com.minidex.app.data

import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.CursorMode
import com.minidex.app.domain.model.HapticStrength
import com.minidex.app.domain.model.KeyHeightLevel
import com.minidex.app.domain.model.ThemeVariant
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val themeVariant: ThemeVariant = ThemeVariant.CYBER_OLED,
    val accentColor: AccentColor = AccentColor.NEON_CYAN,
    val hapticStrength: HapticStrength = HapticStrength.CRISP,
    val keyHeightLevel: KeyHeightLevel = KeyHeightLevel.BALANCED,
    val keyGapDp: Int = 4,
    val cornerRadiusDp: Int = 8,
    val doubleTapToLockModifier: Boolean = true,
    val modifierTimeoutMs: Long = 0L, // 0 = no timeout for latched
    val pointerSensitivity: Float = 1.2f,
    val pointerAcceleration: Float = 0.5f,
    val scrollSensitivity: Float = 1.0f,
    val naturalScrolling: Boolean = false,
    val edgeScrollOnRight: Boolean = false,
    val tapToClick: Boolean = true,
    val cursorMode: CursorMode = CursorMode.AUTO_NATIVE,
    val preferredBackend: String = "AUTO", // AUTO, SHIZUKU, VIRTUAL_DEVICE, FALLBACK
    val manualDisplayId: Int = -1, // -1 = auto detect
    val adbAutoConnect: Boolean = true,
    val adbPort: Int = 5555
)
