package com.minidex.app.ui.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minidex.app.domain.model.ModifierLockState
import com.minidex.app.domain.model.ModifierType
import com.minidex.app.ui.components.KeyButton
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun QwertyKeyboard(
    shiftState: ModifierLockState,
    keyHeight: Dp = 44.dp,
    keyGap: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
    onCharPress: (Char, Int) -> Unit,
    onModifierToggle: (ModifierType) -> Unit,
    onKeyPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isShifted = shiftState.isActive
    val colors = LocalMiniDexColors.current

    val row1 = listOf(
        Triple('q', KeyEvent.KEYCODE_Q, "1"),
        Triple('w', KeyEvent.KEYCODE_W, "2"),
        Triple('e', KeyEvent.KEYCODE_E, "3"),
        Triple('r', KeyEvent.KEYCODE_R, "4"),
        Triple('t', KeyEvent.KEYCODE_T, "5"),
        Triple('y', KeyEvent.KEYCODE_Y, "6"),
        Triple('u', KeyEvent.KEYCODE_U, "7"),
        Triple('i', KeyEvent.KEYCODE_I, "8"),
        Triple('o', KeyEvent.KEYCODE_O, "9"),
        Triple('p', KeyEvent.KEYCODE_P, "0")
    )

    val row2 = listOf(
        Triple('a', KeyEvent.KEYCODE_A, "@"),
        Triple('s', KeyEvent.KEYCODE_S, "#"),
        Triple('d', KeyEvent.KEYCODE_D, "$"),
        Triple('f', KeyEvent.KEYCODE_F, "%"),
        Triple('g', KeyEvent.KEYCODE_G, "&"),
        Triple('h', KeyEvent.KEYCODE_H, "-"),
        Triple('j', KeyEvent.KEYCODE_J, "+"),
        Triple('k', KeyEvent.KEYCODE_K, "("),
        Triple('l', KeyEvent.KEYCODE_L, ")")
    )

    val row3 = listOf(
        Pair('z', KeyEvent.KEYCODE_Z),
        Pair('x', KeyEvent.KEYCODE_X),
        Pair('c', KeyEvent.KEYCODE_C),
        Pair('v', KeyEvent.KEYCODE_V),
        Pair('b', KeyEvent.KEYCODE_B),
        Pair('n', KeyEvent.KEYCODE_N),
        Pair('m', KeyEvent.KEYCODE_M)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(keyGap)
    ) {
        // Row 1: Q W E R T Y U I O P
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            row1.forEach { (char, keyCode, sub) ->
                val displayChar = if (isShifted) char.uppercaseChar() else char
                KeyButton(
                    label = displayChar.toString(),
                    subLabel = sub,
                    modifier = Modifier.weight(1f).height(keyHeight),
                    cornerRadius = cornerRadius,
                    onTap = { onCharPress(displayChar, keyCode) }
                )
            }
        }

        // Row 2: A S D F G H J K L
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            Spacer(modifier = Modifier.weight(0.5f))
            row2.forEach { (char, keyCode, sub) ->
                val displayChar = if (isShifted) char.uppercaseChar() else char
                KeyButton(
                    label = displayChar.toString(),
                    subLabel = sub,
                    modifier = Modifier.weight(1f).height(keyHeight),
                    cornerRadius = cornerRadius,
                    onTap = { onCharPress(displayChar, keyCode) }
                )
            }
            Spacer(modifier = Modifier.weight(0.5f))
        }

        // Row 3: SHIFT | Z X C V B N M | BACKSPACE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            // Shift Key with latched indicator
            KeyButton(
                label = "⇧",
                lockState = shiftState,
                modifier = Modifier.weight(1.5f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onModifierToggle(ModifierType.SHIFT) },
                onDoubleTap = { onModifierToggle(ModifierType.SHIFT) }
            )

            row3.forEach { (char, keyCode) ->
                val displayChar = if (isShifted) char.uppercaseChar() else char
                KeyButton(
                    label = displayChar.toString(),
                    modifier = Modifier.weight(1f).height(keyHeight),
                    cornerRadius = cornerRadius,
                    onTap = { onCharPress(displayChar, keyCode) }
                )
            }

            // Backspace Key
            KeyButton(
                label = "⌫",
                modifier = Modifier.weight(1.5f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_DEL) }
            )
        }

        // Row 4: , | SPACE | . | ENTER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            KeyButton(
                label = ",",
                subLabel = "?",
                modifier = Modifier.weight(1.2f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onCharPress(',', KeyEvent.KEYCODE_COMMA) }
            )

            // Spacebar
            KeyButton(
                label = "␣",
                subLabel = "SPACE",
                modifier = Modifier.weight(4.5f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_SPACE) }
            )

            KeyButton(
                label = ".",
                subLabel = "!",
                modifier = Modifier.weight(1.2f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onCharPress('.', KeyEvent.KEYCODE_PERIOD) }
            )

            // Enter Key
            KeyButton(
                label = "↵",
                modifier = Modifier.weight(1.8f).height(keyHeight),
                cornerRadius = cornerRadius,
                accentColor = colors.accent,
                onTap = { onKeyPress(KeyEvent.KEYCODE_ENTER) }
            )
        }
    }
}
