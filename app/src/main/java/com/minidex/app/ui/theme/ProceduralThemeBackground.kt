package com.minidex.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.minidex.app.domain.model.ThemeVariant
import com.minidex.app.domain.model.VisualFilter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ProceduralThemeBackground(
    variant: ThemeVariant,
    accent: Color,
    filter: VisualFilter,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "theme_motion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "theme_phase"
    )
    val tint = filter.applyTo(accent)

    Canvas(modifier = modifier) {
        when (variant) {
            ThemeVariant.CYBER_OLED -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * (0.25f + phase * 0.5f), size.height * 0.35f),
                        radius = size.maxDimension * 0.72f
                    )
                )
                val spacing = 34f
                var x = -size.height + phase * spacing
                while (x < size.width) {
                    drawLine(tint.copy(alpha = 0.045f), Offset(x, 0f), Offset(x + size.height, size.height), 1f)
                    x += spacing
                }
            }
            ThemeVariant.MIDNIGHT_SLATE -> {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(tint.copy(alpha = 0.22f), Color.Transparent, tint.copy(alpha = 0.10f)),
                        start = Offset(size.width * phase, 0f),
                        end = Offset(size.width * (1f - phase), size.height)
                    )
                )
                repeat(3) { index ->
                    val y = size.height * (0.22f + index * 0.25f)
                    val path = Path().apply {
                        moveTo(-40f, y)
                        cubicTo(size.width * 0.25f, y - 120f, size.width * 0.72f, y + 120f, size.width + 40f, y - 20f)
                    }
                    drawPath(path, tint.copy(alpha = 0.09f + index * 0.02f), style = Stroke(42f))
                }
            }
            ThemeVariant.RETRO_AMBER -> {
                val center = Offset(size.width * 0.76f, size.height * 0.24f)
                repeat(6) { index ->
                    drawCircle(
                        color = tint.copy(alpha = 0.11f - index * 0.012f),
                        radius = 44f + index * 48f + sin(phase * PI * 2).toFloat() * 8f,
                        center = center,
                        style = Stroke(2.4f)
                    )
                }
                repeat(18) { index ->
                    val angle = index / 18f * PI.toFloat() * 2f + phase * 0.25f
                    val start = Offset(center.x + cos(angle) * 42f, center.y + sin(angle) * 42f)
                    val end = Offset(center.x + cos(angle) * size.maxDimension, center.y + sin(angle) * size.maxDimension)
                    drawLine(tint.copy(alpha = 0.035f), start, end, 1.5f)
                }
            }
            ThemeVariant.VAPOR_PURPLE -> {
                val blobs = listOf(
                    Offset(size.width * (0.2f + phase * 0.15f), size.height * 0.25f),
                    Offset(size.width * (0.82f - phase * 0.12f), size.height * 0.62f),
                    Offset(size.width * 0.42f, size.height * (0.88f - phase * 0.15f))
                )
                blobs.forEachIndexed { index, center ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(tint.copy(alpha = 0.18f - index * 0.025f), Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.48f
                        ),
                        radius = size.minDimension * 0.48f,
                        center = center
                    )
                }
            }
            ThemeVariant.LIQUID_GLASS -> {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.55f),
                        radius = size.maxDimension * 0.7f
                    )
                )
                repeat(6) { index ->
                    val baseY = size.height * (0.16f + index * 0.14f)
                    val path = Path().apply {
                        moveTo(0f, baseY)
                        val segments = 24
                        for (segment in 1..segments) {
                            val x = size.width * segment / segments
                            val wave = sin((segment / 3.2f + phase * 6f + index) * PI).toFloat() * 15f
                            lineTo(x, baseY + wave)
                        }
                    }
                    drawPath(path, tint.copy(alpha = 0.055f + index * 0.012f), style = Stroke(2.2f))
                }
            }
        }
    }
}
