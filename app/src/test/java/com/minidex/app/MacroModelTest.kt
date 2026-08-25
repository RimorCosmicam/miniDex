package com.minidex.app

import com.minidex.app.domain.model.Macro
import com.minidex.app.domain.model.MacroStep
import com.minidex.app.domain.model.ModifierType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MacroModelTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testMacroSerializationAndDeserialization() {
        val originalMacro = Macro(
            id = "test_macro",
            name = "Test Macro",
            description = "Ctrl + Alt + T",
            iconName = "terminal",
            colorHex = "#00E5FF",
            steps = listOf(
                MacroStep.KeyChord(listOf(68), listOf(ModifierType.CTRL, ModifierType.ALT)),
                MacroStep.Delay(200),
                MacroStep.TypeText("echo hello"),
                MacroStep.KeyPress(66)
            )
        )

        val serialized = json.encodeToString(originalMacro)
        assertNotNull(serialized)

        val deserialized = json.decodeFromString<Macro>(serialized)
        assertEquals(originalMacro.id, deserialized.id)
        assertEquals(originalMacro.name, deserialized.name)
        assertEquals(4, deserialized.steps.size)
    }
}
