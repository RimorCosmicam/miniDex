package com.minidex.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.domain.model.ModifierLockState
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    shiftLabel: String? = null,
    lockState: ModifierLockState = ModifierLockState.INACTIVE,
    accentColor: Color? = null,
    customBackgroundColor: Color? = null,
    cornerRadius: Dp = 8.dp,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null
) {
    val colors = LocalMiniDexColors.current
    var isPressed by remember { mutableStateOf(false) }

    val isLatched = lockState == ModifierLockState.LATCHED
    val isLocked = lockState == ModifierLockState.LOCKED

    val targetBg = when {
        isLocked -> colors.keyLocked
        isLatched -> colors.keyLatched
        customBackgroundColor != null -> customBackgroundColor
        else -> colors.keyBackground
    }

    val targetBorder = when {
        isLocked -> colors.accent
        isLatched -> colors.accent.copy(alpha = 0.8f)
        accentColor != null -> accentColor.copy(alpha = 0.5f)
        else -> colors.border.copy(alpha = 0.6f)
    }

    val textColor = when {
        isLocked -> Color.White
        isLatched -> colors.accent
        accentColor != null -> accentColor
        else -> colors.textPrimary
    }

    val shape = RoundedCornerShape(cornerRadius)
    val animatedBackground by animateColorAsState(
        targetValue = if (isPressed) colors.accent.copy(alpha = 0.34f) else targetBg,
        animationSpec = tween(70),
        label = "key_background"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(70),
        label = "key_scale"
    )

    Box(
        modifier = modifier
            .scale(animatedScale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isPressed) colors.accent.copy(alpha = 0.42f) else animatedBackground,
                        animatedBackground.copy(alpha = 0.86f)
                    )
                ),
                shape
            )
            .border(1.dp, targetBorder, shape)
            .pointerInput(onTap, onLongPress, onDoubleTap) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onTap() },
                    onLongPress = onLongPress?.let { lp -> { lp() } },
                    onDoubleTap = onDoubleTap?.let { dt -> { dt() } }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (shiftLabel != null) {
                Text(
                    text = shiftLabel,
                    color = colors.textSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 9.sp
                )
            }

            Text(
                text = label,
                color = textColor,
                fontSize = if (label.length > 2) 11.sp else 14.sp,
                fontWeight = if (isLocked || isLatched) FontWeight.ExtraBold else FontWeight.Bold,
                lineHeight = 15.sp
            )

            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = colors.textSecondary.copy(alpha = 0.8f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 8.sp
                )
            }
        }

        // Indicator dot for latched / locked modifier state
        if (isLatched || isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isLocked) colors.accent else colors.accent.copy(alpha = 0.7f))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (isLocked) "LOCK" else "ON",
                    color = Color.Black,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 7.sp
                )
            }
        }
    }
}
