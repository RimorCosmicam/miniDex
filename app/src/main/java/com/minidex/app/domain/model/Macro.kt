package com.minidex.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Macro model supporting key sequences, key chords, delays, text typing, and pointer clicks.
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val description: String = "",
    val iconName: String = "keyboard",
    val colorHex: String = "#00E5FF",
    val steps: List<MacroStep> = emptyList()
)

@Serializable
sealed class MacroStep {
    @Serializable
    data class KeyPress(val keyCode: Int, val metaState: Int = 0) : MacroStep()

    @Serializable
    data class KeyDown(val keyCode: Int) : MacroStep()

    @Serializable
    data class KeyUp(val keyCode: Int) : MacroStep()

    @Serializable
    data class KeyChord(val keyCodes: List<Int>, val modifiers: List<ModifierType> = emptyList()) : MacroStep()

    @Serializable
    data class TypeText(val text: String) : MacroStep()

    @Serializable
    data class Delay(val millis: Long) : MacroStep()

    @Serializable
    data class PointerClick(val button: Int) : MacroStep()

    @Serializable
    data class Scroll(val dx: Float, val dy: Float) : MacroStep()
}
