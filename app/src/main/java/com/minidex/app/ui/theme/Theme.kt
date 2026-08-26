package com.minidex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.ThemeVariant

val LocalMiniDexColors = staticCompositionLocalOf {
    getMiniDexColorScheme(ThemeVariant.CYBER_OLED, AccentColor.NEON_CYAN)
}

@Composable
fun MiniDexTheme(
    variant: ThemeVariant = ThemeVariant.CYBER_OLED,
    accent: AccentColor = AccentColor.NEON_CYAN,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val customColors = getMiniDexColorScheme(variant, accent).let {
        if (amoledMode) it.asAmoled() else it
    }

    val materialColors = darkColorScheme(
        primary = customColors.accent,
        background = customColors.background,
        surface = customColors.surface,
        onPrimary = customColors.background,
        onBackground = customColors.textPrimary,
        onSurface = customColors.textPrimary
    )

    CompositionLocalProvider(LocalMiniDexColors provides customColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = Typography,
            content = content
        )
    }
}
