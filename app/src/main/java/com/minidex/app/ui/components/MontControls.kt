package com.minidex.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.ui.theme.LocalMiniDexColors
import com.minidex.app.ui.theme.Poppins

/**
 * A toggle built from the same rectangle as the slider.
 *
 * Material's switch is a rounded track with a travelling bead — the most decorated object in a
 * language that has removed every other fill and corner. This is the slider stopped at two
 * positions: a white block occupying one half, with the state written in the half it has left.
 *
 * The word names what the control currently is, not what tapping it would do. A switch labelled
 * with its own opposite is a puzzle every single time you meet it.
 */
@Composable
fun MontToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val slide by animateFloatAsState(if (checked) 0f else 1f, label = "montToggle")
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier
            .width(56.dp)
            .height(18.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.width * 0.5f
            drawRect(Color.White.copy(alpha = 0.09f * alpha), Offset.Zero, size)
            drawRect(Color.White.copy(alpha = alpha), Offset(half * slide, 0f), Size(half, size.height))
        }
        Text(
            text = if (checked) "ON" else "OFF",
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .width(28.dp),
            color = Color.White.copy(alpha = alpha),
            fontFamily = Poppins,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * A slider as a rectangle that fills.
 *
 * No track, no bead, no rounding: dragging fills the bar with white from the left, so the value
 * reads as an area rather than as the position of a dot — easier to judge at a glance and far
 * easier to hit. The unfilled part carries the 9% wash rather than being literally black, because
 * on a black panel a black rectangle at zero is a control you cannot find.
 */
@Composable
fun MontSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var width by remember { mutableFloatStateOf(1f) }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)

    fun valueAt(x: Float) = range.start + (x / width).coerceIn(0f, 1f) * span

    Box(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            // One gesture handler for press and drag alike. Two separate detectors compete for the
            // same pointer stream and the second never sees an event.
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(valueAt(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        onChange(valueAt(change.position.x))
                        change.consume()
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val alpha = if (enabled) 1f else 0.35f
            drawRect(Color.White.copy(alpha = 0.09f * alpha), Offset.Zero, size)
            drawRect(Color.White.copy(alpha = alpha), Offset.Zero, Size(size.width * fraction, size.height))
        }
    }
}

/**
 * A word, full width, tappable. Bright if it does something now, dim if it is secondary.
 * No box, no pill, no border — the weight of the type is what makes it a control.
 */
@Composable
fun MontRow(
    label: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusColor: Color? = null,
    explain: String? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalMiniDexColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                color = colors.textPrimary,
                fontFamily = Poppins,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
            if (status != null) {
                Text(
                    text = status.uppercase(),
                    color = statusColor ?: colors.textSecondary,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        if (explain != null) {
            Text(
                text = explain,
                color = colors.textExplain,
                fontFamily = Poppins,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * A choice. No pill, no border, no fill — selected is bright, unselected is dim, exactly the same
 * rule as a row, because a choice is a row that happens to sit beside others.
 */
@Composable
fun MontChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color? = null
) {
    val colors = LocalMiniDexColors.current
    Text(
        text = label.uppercase(),
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        color = when {
            selected -> selectedColor ?: Color.White
            else -> colors.textSecondary
        },
        fontFamily = Poppins,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        maxLines = 1
    )
}
