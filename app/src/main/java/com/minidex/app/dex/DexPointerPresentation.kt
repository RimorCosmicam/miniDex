package com.minidex.app.dex

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.minidex.app.R

/**
 * Overlay presentation rendered directly on the external DeX display.
 * Displays a hardware-accelerated cursor that moves smoothly in real time across the DeX monitor
 * as the user interacts with the Z Flip7 cover screen touchpad.
 */
class DexPointerPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private var cursorView: View? = null
    private var rootLayout: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window transparent, non-focusable, and touch-pass-through
        window?.apply {
            setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
            )
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            setBackgroundDrawable(null)
        }

        rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Sleek cyber cursor dot with glowing ring
        cursorView = View(context).apply {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE00E5FF.toInt())
                setStroke(2, 0xFFFFFFFF.toInt())
                setSize(24, 24)
            }
            background = drawable
            layoutParams = FrameLayout.LayoutParams(24, 24).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            alpha = 0.9f
        }

        rootLayout?.addView(cursorView)
        setContentView(rootLayout)
    }

    fun updatePointerPosition(x: Float, y: Float) {
        cursorView?.let { cursor ->
            cursor.x = x - (cursor.width / 2f)
            cursor.y = y - (cursor.height / 2f)
        }
    }
}
