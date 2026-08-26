package com.minidex.app.ui.theme

import android.graphics.Matrix
import android.graphics.Canvas
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.minidex.app.domain.model.VisualFilter
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import kotlin.math.max

private class FilteredGifImageView(context: Context) : GifImageView(context) {
    var visualFilter: VisualFilter = VisualFilter.NONE
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (visualFilter != VisualFilter.CHROMATIC || drawable == null) {
            super.onDraw(canvas)
            return
        }

        val image = drawable
        val originalFilter = image.colorFilter
        val originalAlpha = image.alpha
        super.onDraw(canvas)

        val shift = 4f * resources.displayMetrics.density
        image.alpha = 92
        image.colorFilter = chromaticRedFilter()
        canvas.save()
        canvas.translate(-shift, 0f)
        super.onDraw(canvas)
        canvas.restore()

        image.colorFilter = chromaticCyanFilter()
        canvas.save()
        canvas.translate(shift, 0f)
        super.onDraw(canvas)
        canvas.restore()

        image.alpha = originalAlpha
        image.colorFilter = originalFilter
    }
}

@Composable
fun AnimatedGifBackground(
    uri: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    opacity: Float,
    filter: VisualFilter = VisualFilter.NONE,
    modifier: Modifier = Modifier
) {
    if (uri.isBlank()) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FilteredGifImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.MATRIX
            }
        },
        update = { view ->
            if (view.tag != uri) {
                runCatching {
                    GifDrawable(view.context.contentResolver, Uri.parse(uri))
                }.onSuccess { drawable ->
                    view.setImageDrawable(drawable)
                    drawable.start()
                    view.tag = uri
                }
            }
            view.alpha = opacity.coerceIn(0f, 1f)
            view.visualFilter = filter
            view.colorFilter = filter.toAndroidColorFilter()
            view.post {
                val drawable = view.drawable ?: return@post
                if (view.width == 0 || view.height == 0) return@post
                val base = max(
                    view.width.toFloat() / drawable.intrinsicWidth.coerceAtLeast(1),
                    view.height.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)
                )
                val actualScale = base * scale.coerceIn(1f, 4f)
                val drawnWidth = drawable.intrinsicWidth * actualScale
                val drawnHeight = drawable.intrinsicHeight * actualScale
                val tx = (view.width - drawnWidth) / 2f + offsetX.coerceIn(-1f, 1f) * view.width / 2f
                val ty = (view.height - drawnHeight) / 2f + offsetY.coerceIn(-1f, 1f) * view.height / 2f
                view.imageMatrix = Matrix().apply {
                    setScale(actualScale, actualScale)
                    postTranslate(tx, ty)
                }
            }
        }
    )
}
