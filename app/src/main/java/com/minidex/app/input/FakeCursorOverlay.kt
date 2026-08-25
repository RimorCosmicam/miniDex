package com.minidex.app.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.minidex.app.input.accessibility.MiniDexAccessibilityService

/** A non-interactive cursor rendered on the target display independently of Android's pointer icon. */
class FakeCursorOverlay(private val appContext: Context) {
    companion object {
        private const val TAG = "FakeCursorOverlay"
        private const val CURSOR_WIDTH = 34
        private const val CURSOR_HEIGHT = 46
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var cursorView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var activeDisplayId = -1
    private var retryScheduled = false
    private var pendingDisplayId = -1
    private var pendingX = 0f
    private var pendingY = 0f

    fun showAt(displayId: Int, x: Float, y: Float) {
        if (displayId < 0) return
        pendingDisplayId = displayId
        pendingX = x
        pendingY = y
        mainHandler.post {
            val accessibilityService = MiniDexAccessibilityService.instance ?: run {
                scheduleRetry()
                return@post
            }
            if (activeDisplayId != displayId || cursorView == null) {
                attachToDisplay(displayId, accessibilityService)
            }
            val view = cursorView ?: return@post
            val params = layoutParams ?: return@post
            params.x = x.toInt() - 2
            params.y = y.toInt() - 2
            runCatching { windowManager?.updateViewLayout(view, params) }
                .onFailure { Log.e(TAG, "Could not move fake cursor", it) }
        }
    }

    fun remove() {
        mainHandler.post { removeNow() }
    }

    private fun scheduleRetry() {
        if (retryScheduled) return
        retryScheduled = true
        mainHandler.postDelayed({
            retryScheduled = false
            if (pendingDisplayId >= 0) showAt(pendingDisplayId, pendingX, pendingY)
        }, 250L)
    }

    private fun attachToDisplay(
        displayId: Int,
        accessibilityService: MiniDexAccessibilityService
    ) {
        removeNow()
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(displayId) ?: return
        val windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val displayContext = accessibilityService.createDisplayContext(display)
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(
                windowType,
                null
            )
        } else {
            displayContext
        }
        val manager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = CursorView(windowContext)
        val params = WindowManager.LayoutParams(
            CURSOR_WIDTH,
            CURSOR_HEIGHT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "MiniDex visible cursor"
        }
        runCatching { manager.addView(view, params) }
            .onSuccess {
                windowManager = manager
                cursorView = view
                layoutParams = params
                activeDisplayId = displayId
                Log.i(TAG, "Fake cursor attached to display $displayId on highest available layer")
            }
            .onFailure {
                Log.e(TAG, "Could not attach fake cursor to display $displayId", it)
                scheduleRetry()
            }
    }

    private fun removeNow() {
        val view = cursorView
        if (view != null) runCatching { windowManager?.removeViewImmediate(view) }
        windowManager = null
        cursorView = null
        layoutParams = null
        activeDisplayId = -1
    }

    private class CursorView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeJoin = Paint.Join.ROUND
        }
        private val cursorPath = Path().apply {
            moveTo(3f, 2f)
            lineTo(3f, 36f)
            lineTo(11f, 28f)
            lineTo(17f, 43f)
            lineTo(24f, 40f)
            lineTo(18f, 25f)
            lineTo(31f, 25f)
            close()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(cursorPath, fill)
            canvas.drawPath(cursorPath, outline)
        }
    }
}
