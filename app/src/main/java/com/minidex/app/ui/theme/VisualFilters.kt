package com.minidex.app.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.minidex.app.domain.model.VisualFilter

fun VisualFilter.applyTo(color: Color): Color = when (this) {
    VisualFilter.NONE -> color
    VisualFilter.VIVID -> lerp(color, Color.White, 0.12f)
    VisualFilter.MONO -> {
        val luminance = color.red * 0.213f + color.green * 0.715f + color.blue * 0.072f
        Color(luminance, luminance, luminance, color.alpha)
    }
    VisualFilter.WARM -> lerp(color, Color(0xFFFF9A55), 0.28f)
    VisualFilter.COOL -> lerp(color, Color(0xFF6EC8FF), 0.28f)
    VisualFilter.CHROMATIC -> color
}

fun VisualFilter.toAndroidColorFilter(): android.graphics.ColorFilter? {
    if (this == VisualFilter.NONE || this == VisualFilter.CHROMATIC) return null
    val matrix = when (this) {
        VisualFilter.NONE -> ColorMatrix()
        VisualFilter.VIVID -> ColorMatrix().apply { setSaturation(1.45f) }
        VisualFilter.MONO -> ColorMatrix().apply { setSaturation(0f) }
        VisualFilter.WARM -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 8f,
                0f, 1.02f, 0f, 0f, 2f,
                0f, 0f, 0.84f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        VisualFilter.COOL -> ColorMatrix(
            floatArrayOf(
                0.88f, 0f, 0f, 0f, -2f,
                0f, 1.02f, 0f, 0f, 2f,
                0f, 0f, 1.14f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        VisualFilter.CHROMATIC -> ColorMatrix()
    }
    return ColorMatrixColorFilter(matrix)
}

fun chromaticRedFilter(): android.graphics.ColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f
        )
    )
)

fun chromaticCyanFilter(): android.graphics.ColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f
        )
    )
)
