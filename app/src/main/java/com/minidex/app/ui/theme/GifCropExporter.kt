package com.minidex.app.ui.theme

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import com.minidex.app.domain.model.VisualFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import java.util.concurrent.TimeUnit
import kotlin.math.max

object GifCropExporter {
    suspend fun saveToGallery(
        context: Context,
        sourceUri: String,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        filter: VisualFilter = VisualFilter.NONE,
        outputWidth: Int = 720,
        outputHeight: Int = 748
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "MiniDex-${System.currentTimeMillis()}.gif")
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiniDex")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val outputUri = checkNotNull(
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            )
            try {
                context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                    checkNotNull(output)
                    val drawable = GifDrawable(context.contentResolver, Uri.parse(sourceUri))
                    try {
                        drawable.stop()
                        val frames = drawable.numberOfFrames.coerceAtLeast(1)
                        val frameDelay = (drawable.duration / frames).coerceAtLeast(20)
                        val encoder = GifEncoder(output, outputWidth, outputHeight, 0)
                        repeat(frames) { frameIndex ->
                            drawable.seekTo((frameIndex * frameDelay).coerceAtMost(drawable.duration - 1))
                            val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                            val base = max(
                                outputWidth.toFloat() / drawable.intrinsicWidth.coerceAtLeast(1),
                                outputHeight.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)
                            )
                            val actualScale = base * scale.coerceIn(1f, 4f)
                            val matrix = Matrix().apply {
                                setScale(actualScale, actualScale)
                                postTranslate(
                                    (outputWidth - drawable.intrinsicWidth * actualScale) / 2f + offsetX * outputWidth / 2f,
                                    (outputHeight - drawable.intrinsicHeight * actualScale) / 2f + offsetY * outputHeight / 2f
                                )
                            }
                            val canvas = Canvas(bitmap)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                colorFilter = filter.toAndroidColorFilter()
                            }
                            canvas.drawBitmap(drawable.currentFrame, matrix, paint)
                            if (filter == VisualFilter.CHROMATIC) {
                                val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 92 }
                                ghostPaint.colorFilter = chromaticRedFilter()
                                canvas.drawBitmap(drawable.currentFrame, Matrix(matrix).apply { postTranslate(-8f, 0f) }, ghostPaint)
                                ghostPaint.colorFilter = chromaticCyanFilter()
                                canvas.drawBitmap(drawable.currentFrame, Matrix(matrix).apply { postTranslate(8f, 0f) }, ghostPaint)
                            }
                            val pixels = IntArray(outputWidth * outputHeight)
                            bitmap.getPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
                            val rgb = Array(outputHeight) { row ->
                                IntArray(outputWidth) { column -> pixels[row * outputWidth + column] and 0xFFFFFF }
                            }
                            val options = ImageOptions().setDelay(frameDelay.toLong(), TimeUnit.MILLISECONDS)
                            encoder.addImage(rgb, options)
                            bitmap.recycle()
                        }
                        encoder.finishEncoding()
                    } finally {
                        drawable.recycle()
                    }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(outputUri, values, null, null)
                outputUri
            } catch (error: Throwable) {
                context.contentResolver.delete(outputUri, null, null)
                throw error
            }
        }
    }
}
