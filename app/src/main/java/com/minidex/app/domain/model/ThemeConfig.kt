package com.minidex.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeVariant(val displayName: String) {
    CYBER_OLED("Cyber OLED"),
    MIDNIGHT_SLATE("Midnight Slate"),
    RETRO_AMBER("Retro Amber"),
    VAPOR_PURPLE("Vapor Purple")
}

@Serializable
enum class AccentColor(val displayName: String, val hex: String) {
    NEON_CYAN("Neon Cyan", "#00E5FF"),
    CYBER_MINT("Cyber Mint", "#00FFA3"),
    ELECTRIC_PINK("Electric Pink", "#FF007F"),
    AMBER_GOLD("Amber Gold", "#FFB300"),
    VIOLET_GLOW("Violet Glow", "#B388FF")
}

@Serializable
enum class HapticStrength(val displayName: String, val durationMs: Long) {
    OFF("Off", 0L),
    SUBTLE("Subtle (10ms)", 10L),
    CRISP("Crisp (20ms)", 20L),
    STRONG("Strong (35ms)", 35L)
}

@Serializable
enum class KeyHeightLevel(val displayName: String, val heightDp: Int) {
    COMPACT("Compact", 40),
    BALANCED("Balanced", 46),
    TALL("Tall", 52)
}
