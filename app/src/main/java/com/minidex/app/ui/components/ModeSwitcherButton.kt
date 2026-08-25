package com.minidex.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.domain.model.AppMode
import com.minidex.app.ui.theme.LocalMiniDexColors

/**
 * Persistent switch button nestled in the bottom-left camera safe space of the Z Flip7 cover screen.
 * Tapping instantaneously toggles Keyboard ⇄ Touchpad.
 */
@Composable
fun ModeSwitcherButton(
    currentMode: AppMode,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current
    val isKeyboard = currentMode == AppMode.KEYBOARD

    val shape = RoundedCornerShape(12.dp)
    val bgColor by animateColorAsState(
        targetValue = if (isKeyboard) colors.accent.copy(alpha = 0.22f) else colors.accent.copy(alpha = 0.35f),
        label = "switcher_bg"
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.5.dp, colors.accent, shape)
            .clickable { onToggleMode() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isKeyboard) Icons.Default.Mouse else Icons.Default.Keyboard,
                contentDescription = "Switch Mode",
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isKeyboard) "PAD" else "KEY",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}
