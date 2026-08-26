package com.minidex.app.data

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minidex.app.domain.model.Macro
import com.minidex.app.domain.model.MacroStep
import com.minidex.app.domain.model.ModifierType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.macroDataStore: DataStore<Preferences> by preferencesDataStore(name = "minidex_macros")

class MacroRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val MACROS_KEY = stringPreferencesKey("custom_macros_json")

    val defaultMacros: List<Macro> = listOf(
        Macro(
            id = "copy",
            name = "Copy",
            description = "Ctrl + C",
            iconName = "content_copy",
            colorHex = "#00E5FF",
            steps = listOf(
                MacroStep.KeyChord(listOf(KeyEvent.KEYCODE_C), listOf(ModifierType.CTRL))
            )
        ),
        Macro(
            id = "paste",
            name = "Paste",
            description = "Ctrl + V",
            iconName = "content_paste",
            colorHex = "#00FFA3",
            steps = listOf(
                MacroStep.KeyChord(listOf(KeyEvent.KEYCODE_V), listOf(ModifierType.CTRL))
            )
        ),
        Macro(
            id = "undo",
            name = "Undo",
            description = "Ctrl + Z",
            iconName = "undo",
            colorHex = "#FFB300",
            steps = listOf(
                MacroStep.KeyChord(listOf(KeyEvent.KEYCODE_Z), listOf(ModifierType.CTRL))
            )
        ),
        Macro(
            id = "back",
            name = "Back",
            description = "Android Back",
            iconName = "monitor_heart",
            colorHex = "#FF007F",
            steps = listOf(
                MacroStep.KeyPress(KeyEvent.KEYCODE_BACK)
            )
        ),
        Macro(
            id = "search",
            name = "Search",
            description = "Android Search",
            iconName = "terminal",
            colorHex = "#B388FF",
            steps = listOf(
                MacroStep.KeyPress(KeyEvent.KEYCODE_SEARCH)
            )
        ),
        Macro(
            id = "screenshot",
            name = "Capture",
            description = "Screenshot",
            iconName = "screenshot",
            colorHex = "#00E5FF",
            steps = listOf(
                MacroStep.KeyPress(KeyEvent.KEYCODE_SYSRQ)
            )
        ),
        Macro(
            id = "home",
            name = "Home",
            description = "Android Home",
            iconName = "desktop_windows",
            colorHex = "#00FFA3",
            steps = listOf(
                MacroStep.KeyPress(KeyEvent.KEYCODE_HOME)
            )
        ),
        Macro(
            id = "recents",
            name = "Recents",
            description = "Android app switcher",
            iconName = "close",
            colorHex = "#FF5252",
            steps = listOf(
                MacroStep.KeyPress(KeyEvent.KEYCODE_APP_SWITCH)
            )
        )
    )

    val macrosFlow: Flow<List<Macro>> = context.macroDataStore.data.map { prefs ->
        val rawJson = prefs[MACROS_KEY]
        if (rawJson.isNullOrBlank()) {
            defaultMacros
        } else {
            try {
                json.decodeFromString<List<Macro>>(rawJson)
            } catch (e: Exception) {
                defaultMacros
            }
        }
    }

    suspend fun saveMacros(macros: List<Macro>) {
        context.macroDataStore.edit { prefs ->
            prefs[MACROS_KEY] = json.encodeToString(macros)
        }
    }

    suspend fun addOrUpdateMacro(macro: Macro) {
        context.macroDataStore.edit { prefs ->
            val currentList = try {
                prefs[MACROS_KEY]?.let { json.decodeFromString<List<Macro>>(it) } ?: defaultMacros
            } catch (e: Exception) {
                defaultMacros
            }.toMutableList()

            val existingIndex = currentList.indexOfFirst { it.id == macro.id }
            if (existingIndex >= 0) {
                currentList[existingIndex] = macro
            } else {
                currentList.add(macro)
            }
            prefs[MACROS_KEY] = json.encodeToString(currentList)
        }
    }

    suspend fun deleteMacro(macroId: String) {
        context.macroDataStore.edit { prefs ->
            val currentList = try {
                prefs[MACROS_KEY]?.let { json.decodeFromString<List<Macro>>(it) } ?: defaultMacros
            } catch (e: Exception) {
                defaultMacros
            }.filterNot { it.id == macroId }
            prefs[MACROS_KEY] = json.encodeToString(currentList)
        }
    }

    suspend fun resetToDefaults() {
        context.macroDataStore.edit { prefs ->
            prefs[MACROS_KEY] = json.encodeToString(defaultMacros)
        }
    }
}
