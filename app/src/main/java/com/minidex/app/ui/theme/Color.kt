package com.minidex.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.ThemeVariant

// Calmer accents with enough luminance to remain legible on every dark foundation.
val NeonCyan = Color(0xFF67D4FF)
val CyberMint = Color(0xFF62D6A7)
val ElectricPink = Color(0xFFFF7597)
val AmberGold = Color(0xFFFFB45C)
val VioletGlow = Color(0xFFA995FF)

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
            background = Color(0xFF050607),
            surface = Color(0xFF101216),
            surfaceElevated = Color(0xFF181B21),
            border = Color(0xFF30343D),
            keyBackground = Color(0xFF1A1D23),
            keyPressed = Color(0xFF292E37),
            keyLatched = accentCol.copy(alpha = 0.20f),
            keyLocked = accentCol.copy(alpha = 0.42f),
            textPrimary = Color(0xFFF1F3F5),
            textSecondary = Color(0xFF9CA3AF),
            accent = accentCol
        )
        ThemeVariant.MIDNIGHT_SLATE -> MiniDexColorScheme(
            background = Color(0xFF090D14),
            surface = Color(0xFF111824),
            surfaceElevated = Color(0xFF192335),
            border = Color(0xFF2C3A50),
            keyBackground = Color(0xFF1A2433),
            keyPressed = Color(0xFF29374C),
            keyLatched = accentCol.copy(alpha = 0.20f),
            keyLocked = accentCol.copy(alpha = 0.42f),
            textPrimary = Color(0xFFEAF0F7),
            textSecondary = Color(0xFF9AABC0),
            accent = accentCol
        )
        ThemeVariant.RETRO_AMBER -> MiniDexColorScheme(
            background = Color(0xFF100E0C),
            surface = Color(0xFF191613),
            surfaceElevated = Color(0xFF231F1B),
            border = Color(0xFF3A332C),
            keyBackground = Color(0xFF211D19),
            keyPressed = Color(0xFF342D26),
            keyLatched = accentCol.copy(alpha = 0.20f),
            keyLocked = accentCol.copy(alpha = 0.42f),
            textPrimary = Color(0xFFF2ECE6),
            textSecondary = Color(0xFFB2A69C),
            accent = accentCol
        )
        ThemeVariant.VAPOR_PURPLE -> MiniDexColorScheme(
            background = Color(0xFF0E0B12),
            surface = Color(0xFF18131E),
            surfaceElevated = Color(0xFF241C2C),
            border = Color(0xFF3D3049),
            keyBackground = Color(0xFF211A28),
            keyPressed = Color(0xFF34283F),
            keyLatched = accentCol.copy(alpha = 0.20f),
            keyLocked = accentCol.copy(alpha = 0.42f),
            textPrimary = Color(0xFFF2EDF5),
            textSecondary = Color(0xFFB3A6BC),
            accent = accentCol
        )
        ThemeVariant.LIQUID_GLASS -> MiniDexColorScheme(
            background = Color(0xFF07100F),
            surface = Color(0xFF0D1B19),
            surfaceElevated = Color(0xFF142724),
            border = Color(0xFF2B4944),
            keyBackground = Color(0xCC17302C),
            keyPressed = Color(0xFF28504A),
            keyLatched = accentCol.copy(alpha = 0.20f),
            keyLocked = accentCol.copy(alpha = 0.42f),
            textPrimary = Color(0xFFF0FAF8),
            textSecondary = Color(0xFF9BBDB7),
            accent = accentCol
        )
    }
}

fun MiniDexColorScheme.asAmoled(): MiniDexColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceElevated = Color.Black,
    border = accent.copy(alpha = 0.42f),
    keyBackground = Color.Black,
    keyPressed = accent.copy(alpha = 0.22f),
    keyLatched = accent.copy(alpha = 0.18f),
    keyLocked = accent.copy(alpha = 0.38f),
    textPrimary = Color.White,
    textSecondary = accent.copy(alpha = 0.72f)
)
