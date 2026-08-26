package com.minidex.app.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.minidex.app.domain.model.Macro
import com.minidex.app.domain.model.MacroStep
import com.minidex.app.domain.model.ModifierLockState
import com.minidex.app.domain.model.ModifierType
import com.minidex.app.ui.components.KeyButton
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun MacroEditorDialog(
    onDismiss: () -> Unit,
    onSaveMacro: (Macro) -> Unit
) {
    val colors = LocalMiniDexColors.current

    var name by remember { mutableStateOf("") }
    var textSnippet by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("CHORD") }
    var selectedModifiers by remember { mutableStateOf(setOf<ModifierType>()) }
    var selectedKey by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val modifierChoices = listOf(
        ModifierType.CTRL to "CTRL",
        ModifierType.ALT to "ALT",
        ModifierType.SHIFT to "SHIFT",
        ModifierType.META to "⌘"
    )
    val keyChoices = buildList {
        addAll(('A'..'Z').map { it.toString() to (KeyEvent.KEYCODE_A + (it - 'A')) })
        addAll(
            listOf(
                "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
                "⌫" to KeyEvent.KEYCODE_DEL,
                "ESC" to KeyEvent.KEYCODE_ESCAPE,
                "ENTER" to KeyEvent.KEYCODE_ENTER,
                "TAB" to KeyEvent.KEYCODE_TAB,
                "SPACE" to KeyEvent.KEYCODE_SPACE,
                "BACK" to KeyEvent.KEYCODE_BACK,
                "HOME" to KeyEvent.KEYCODE_HOME,
                "RECENTS" to KeyEvent.KEYCODE_APP_SWITCH
            )
        )
    }
    val chordLabel = (modifierChoices
        .filter { it.first in selectedModifiers }
        .map { it.second } + listOfNotNull(selectedKey?.first))
        .joinToString(" + ")

    val shape = RoundedCornerShape(12.dp)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceElevated, shape)
                .border(1.dp, colors.accent, shape)
                .padding(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CREATE CUSTOM MACRO",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Macro Name", fontSize = 9.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    textStyle = TextStyle(fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MacroTypePill(
                        label = "KEY CHORD",
                        selected = selectedType == "CHORD",
                        onClick = { selectedType = "CHORD" }
                    )
                    MacroTypePill(
                        label = "TYPE TEXT",
                        selected = selectedType == "TEXT",
                        onClick = { selectedType = "TEXT" }
                    )
                }

                if (selectedType == "CHORD") {
                    Text(
                        text = chordLabel.ifEmpty { "Tap modifiers, then tap a key" },
                        color = if (chordLabel.isEmpty()) colors.textSecondary else colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        modifierChoices.forEach { (modifier, label) ->
                            KeyButton(
                                label = label,
                                lockState = if (modifier in selectedModifiers) {
                                    ModifierLockState.LATCHED
                                } else {
                                    ModifierLockState.INACTIVE
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                cornerRadius = 7.dp,
                                onTap = {
                                    selectedModifiers = if (modifier in selectedModifiers) {
                                        selectedModifiers - modifier
                                    } else {
                                        selectedModifiers + modifier
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        keyChoices.forEach { key ->
                            KeyButton(
                                label = key.first,
                                lockState = if (selectedKey == key) {
                                    ModifierLockState.LATCHED
                                } else {
                                    ModifierLockState.INACTIVE
                                },
                                modifier = Modifier.width(if (key.first.length > 2) 58.dp else 40.dp)
                                    .height(38.dp),
                                cornerRadius = 7.dp,
                                onTap = { selectedKey = key }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = textSnippet,
                        onValueChange = { textSnippet = it },
                        label = { Text("Exact text to type", fontSize = 9.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        textStyle = TextStyle(fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Cancel", color = colors.textSecondary, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent)
                            .clickable {
                                val canSave = name.isNotBlank() &&
                                    ((selectedType == "CHORD" && selectedKey != null) ||
                                        (selectedType == "TEXT" && textSnippet.isNotEmpty()))
                                if (canSave) {
                                    val steps = if (selectedType == "CHORD") {
                                        listOf(
                                            MacroStep.KeyChord(
                                                keyCodes = listOf(selectedKey!!.second),
                                                modifiers = modifierChoices
                                                    .map { it.first }
                                                    .filter { it in selectedModifiers }
                                            )
                                        )
                                    } else {
                                        listOf(MacroStep.TypeText(textSnippet))
                                    }
                                    val newMacro = Macro(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = name,
                                        description = if (selectedType == "CHORD") {
                                            chordLabel
                                        } else {
                                            "Types: $textSnippet"
                                        },
                                        iconName = "keyboard",
                                        colorHex = "#00E5FF",
                                        steps = steps
                                    )
                                    onSaveMacro(newMacro)
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Save",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroTypePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalMiniDexColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.keyBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
