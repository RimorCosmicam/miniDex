package com.minidex.app.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.minidex.app.domain.model.ModifierType
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun MacroEditorDialog(
    onDismiss: () -> Unit,
    onSaveMacro: (Macro) -> Unit
) {
    val colors = LocalMiniDexColors.current

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var textSnippet by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("TEXT") } // TEXT or CHORD

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

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Label", fontSize = 9.sp) },
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

                OutlinedTextField(
                    value = textSnippet,
                    onValueChange = { textSnippet = it },
                    label = { Text("Text or Command to Type", fontSize = 9.sp) },
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
                                if (name.isNotBlank()) {
                                    val newMacro = Macro(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = name,
                                        description = description.ifBlank { "Custom Action" },
                                        iconName = "keyboard",
                                        colorHex = "#00E5FF",
                                        steps = listOf(
                                            MacroStep.TypeText(textSnippet),
                                            MacroStep.KeyPress(KeyEvent.KEYCODE_ENTER)
                                        )
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
