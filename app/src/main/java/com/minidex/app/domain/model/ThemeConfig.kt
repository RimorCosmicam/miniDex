package com.minidex.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeVariant(val displayName: String) {
    CYBER_OLED("Obsidian"),
    MIDNIGHT_SLATE("Aurora"),
    RETRO_AMBER("Solar"),
    VAPOR_PURPLE("Vapor"),
    LIQUID_GLASS("Liquid")
}

@Serializable
enum class AccentColor(val displayName: String, val hex: String) {
    NEON_CYAN("Sky", "#67D4FF"),
    CYBER_MINT("Mint", "#62D6A7"),
    ELECTRIC_PINK("Rose", "#FF7597"),
    AMBER_GOLD("Amber", "#FFB45C"),
    VIOLET_GLOW("Lavender", "#A995FF")
}

@Serializable
enum class VisualFilter(val displayName: String) {
    NONE("Clean"),
    VIVID("Vivid"),
    MONO("Mono"),
    WARM("Warm"),
    COOL("Cool")
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
