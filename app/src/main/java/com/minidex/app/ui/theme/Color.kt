package com.minidex.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.ThemeVariant

// AMOLED Cyber Palette
val TrueBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF0D1117)
val DarkSurfaceElevated = Color(0xFF161B22)
val DarkSurfaceBorder = Color(0xFF30363D)

val KeyBackgroundDark = Color(0xFF1E242C)
val KeyBackgroundPressed = Color(0xFF2E3846)
val KeyBackgroundLatched = Color(0xFF1E3A4A)
val KeyBackgroundLocked = Color(0xFF0D47A1)

val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF8B949E)
val TextTertiary = Color(0xFF6E7681)

// Accent Colors
val NeonCyan = Color(0xFF00E5FF)
val CyberMint = Color(0xFF00FFA3)
val ElectricPink = Color(0xFFFF007F)
val AmberGold = Color(0xFFFFB300)
val VioletGlow = Color(0xFFB388FF)

// Variant Backgrounds
val SlateBackground = Color(0xFF0B1015)
val SlateKey = Color(0xFF1A222D)

val RetroAmberBackground = Color(0xFF120E08)
val RetroAmberKey = Color(0xFF231B10)

val PurpleBackground = Color(0xFF0F0A17)
val PurpleKey = Color(0xFF211633)

fun AccentColor.toColor(): Color {
    return when (this) {
        AccentColor.NEON_CYAN -> NeonCyan
        AccentColor.CYBER_MINT -> CyberMint
        AccentColor.ELECTRIC_PINK -> ElectricPink
        AccentColor.AMBER_GOLD -> AmberGold
        AccentColor.VIOLET_GLOW -> VioletGlow
    }
}

data class MiniDexColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val keyBackground: Color,
    val keyPressed: Color,
    val keyLatched: Color,
    val keyLocked: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
)

fun getMiniDexColorScheme(variant: ThemeVariant, accent: AccentColor): MiniDexColorScheme {
    val accentCol = accent.toColor()
    return when (variant) {
        ThemeVariant.CYBER_OLED -> MiniDexColorScheme(
            background = TrueBlack,
            surface = DarkSurface,
            surfaceElevated = DarkSurfaceElevated,
            border = DarkSurfaceBorder,
            keyBackground = KeyBackgroundDark,
            keyPressed = KeyBackgroundPressed,
            keyLatched = accentCol.copy(alpha = 0.25f),
            keyLocked = accentCol.copy(alpha = 0.5f),
            textPrimary = TextPrimary,
            textSecondary = TextSecondary,
            accent = accentCol
        )
        ThemeVariant.MIDNIGHT_SLATE -> MiniDexColorScheme(
            background = SlateBackground,
            surface = Color(0xFF121922),
            surfaceElevated = Color(0xFF1E2836),
            border = Color(0xFF2A3748),
            keyBackground = SlateKey,
            keyPressed = Color(0xFF2E3E52),
            keyLatched = accentCol.copy(alpha = 0.25f),
            keyLocked = accentCol.copy(alpha = 0.5f),
            textPrimary = Color(0xFFE6EDF3),
            textSecondary = Color(0xFF94A3B8),
            accent = accentCol
        )
        ThemeVariant.RETRO_AMBER -> MiniDexColorScheme(
            background = RetroAmberBackground,
            surface = Color(0xFF18130B),
            surfaceElevated = Color(0xFF251D12),
            border = Color(0xFF3D2F1D),
            keyBackground = RetroAmberKey,
            keyPressed = Color(0xFF382B1B),
            keyLatched = AmberGold.copy(alpha = 0.25f),
            keyLocked = AmberGold.copy(alpha = 0.5f),
            textPrimary = Color(0xFFFFE082),
            textSecondary = Color(0xFFFFB74D),
            accent = AmberGold
        )
        ThemeVariant.VAPOR_PURPLE -> MiniDexColorScheme(
            background = PurpleBackground,
            surface = Color(0xFF160F22),
            surfaceElevated = Color(0xFF241838),
            border = Color(0xFF392758),
            keyBackground = PurpleKey,
            keyPressed = Color(0xFF372456),
            keyLatched = VioletGlow.copy(alpha = 0.25f),
            keyLocked = VioletGlow.copy(alpha = 0.5f),
            textPrimary = Color(0xFFF3E8FF),
            textSecondary = Color(0xFFC084FC),
            accent = VioletGlow
        )
    }
}
