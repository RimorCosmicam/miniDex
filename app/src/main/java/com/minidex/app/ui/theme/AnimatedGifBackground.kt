package com.minidex.app.ui.theme

import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import kotlin.math.max

@Composable
fun AnimatedGifBackground(
    uri: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    if (uri.isBlank()) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GifImageView(context).apply {
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
