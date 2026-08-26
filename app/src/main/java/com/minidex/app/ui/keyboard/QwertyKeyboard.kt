package com.minidex.app.ui.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.domain.model.ModifierLockState
import com.minidex.app.domain.model.ModifierType
import com.minidex.app.ui.components.KeyButton
import com.minidex.app.ui.theme.LocalMiniDexColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QwertyKeyboard(
    shiftState: ModifierLockState,
    keyHeight: Dp = 44.dp,
    keyGap: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
    onCharPress: (Char, Int) -> Unit,
    onSwipeWord: (String) -> Unit,
    onModifierToggle: (ModifierType) -> Unit,
    onKeyPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isShifted = shiftState.isActive
    val colors = LocalMiniDexColors.current
    val scope = rememberCoroutineScope()
    var glideTrail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var glideCandidate by remember { mutableStateOf("") }
    var clearTrailJob by remember { mutableStateOf<Job?>(null) }

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .swipeTyping(
                onTrailChanged = { points ->
                    if (points.isNotEmpty()) clearTrailJob?.cancel()
                    glideTrail = points
                },
                onCandidateChanged = { glideCandidate = it },
                onWord = { word ->
                    onSwipeWord(word)
                    clearTrailJob?.cancel()
                    clearTrailJob = scope.launch {
                        delay(220)
                        glideTrail = emptyList()
                        glideCandidate = ""
                    }
                }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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

        if (glideTrail.size > 1) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(glideTrail.first().x, glideTrail.first().y)
                    glideTrail.drop(1).forEach { point -> lineTo(point.x, point.y) }
                }
                drawPath(
                    path = path,
                    color = colors.accent.copy(alpha = 0.82f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
                drawCircle(
                    color = colors.accent,
                    radius = 5.dp.toPx(),
                    center = glideTrail.last()
                )
            }
        }

        if (glideCandidate.isNotEmpty()) {
            Text(
                text = glideCandidate,
                color = colors.background,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .background(colors.accent, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
