package com.minidex.app.ui.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minidex.app.domain.model.ModifierType
import com.minidex.app.ui.components.KeyButton
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun NavKeyboard(
    keyHeight: Dp = 42.dp,
    keyGap: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
    onKeyPress: (Int) -> Unit,
    onShortcut: (String, List<Int>, List<ModifierType>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current
    val fKeyScroll = rememberScrollState()

    val fKeys = listOf(
        Pair("F1", KeyEvent.KEYCODE_F1),
        Pair("F2", KeyEvent.KEYCODE_F2),
        Pair("F3", KeyEvent.KEYCODE_F3),
        Pair("F4", KeyEvent.KEYCODE_F4),
        Pair("F5", KeyEvent.KEYCODE_F5),
        Pair("F6", KeyEvent.KEYCODE_F6),
        Pair("F7", KeyEvent.KEYCODE_F7),
        Pair("F8", KeyEvent.KEYCODE_F8),
        Pair("F9", KeyEvent.KEYCODE_F9),
        Pair("F10", KeyEvent.KEYCODE_F10),
        Pair("F11", KeyEvent.KEYCODE_F11),
        Pair("F12", KeyEvent.KEYCODE_F12)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(keyGap)
    ) {
        // Row 1: Scrollable Function Keys (F1-F12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight)
                .horizontalScroll(fKeyScroll),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            fKeys.forEach { (label, code) ->
                KeyButton(
                    label = label,
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .height(keyHeight),
                    cornerRadius = cornerRadius,
                    onTap = { onKeyPress(code) }
                )
            }
        }

        // Row 2: Desktop Navigation Keys (Home, End, PageUp, PageDown, Insert, PrtScn)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            KeyButton(
                label = "HOME",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_MOVE_HOME) }
            )
            KeyButton(
                label = "END",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_MOVE_END) }
            )
            KeyButton(
                label = "PG UP",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_PAGE_UP) }
            )
            KeyButton(
                label = "PG DN",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_PAGE_DOWN) }
            )
            KeyButton(
                label = "INS",
                modifier = Modifier.weight(0.9f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_INSERT) }
            )
            KeyButton(
                label = "PRTSC",
                modifier = Modifier.weight(1.1f).height(keyHeight),
                cornerRadius = cornerRadius,
                accentColor = colors.accent,
                onTap = { onKeyPress(KeyEvent.KEYCODE_SYSRQ) }
            )
        }

        // Row 3: Directional Cluster & Media/Quick controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            // Task View (Win+Tab)
            KeyButton(
                label = "WIN+TAB",
                subLabel = "Tasks",
                modifier = Modifier.weight(1.3f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onShortcut("TaskView", listOf(KeyEvent.KEYCODE_TAB), listOf(ModifierType.META)) }
            )

            // D-PAD: LEFT, UP, DOWN, RIGHT
            KeyButton(
                label = "←",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_DPAD_LEFT) }
            )
            KeyButton(
                label = "↑",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_DPAD_UP) }
            )
            KeyButton(
                label = "↓",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_DPAD_DOWN) }
            )
            KeyButton(
                label = "→",
                modifier = Modifier.weight(1f).height(keyHeight),
                cornerRadius = cornerRadius,
                onTap = { onKeyPress(KeyEvent.KEYCODE_DPAD_RIGHT) }
            )

            // Close Window (Alt+F4)
            KeyButton(
                label = "ALT+F4",
                subLabel = "Close",
                modifier = Modifier.weight(1.2f).height(keyHeight),
                cornerRadius = cornerRadius,
                accentColor = colors.accent,
                onTap = { onShortcut("Close", listOf(KeyEvent.KEYCODE_F4), listOf(ModifierType.ALT)) }
            )
        }
    }
}
