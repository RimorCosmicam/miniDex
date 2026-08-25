package com.minidex.app.dex

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Overlay presentation rendered directly on the external DeX display.
 * Displays a hardware-accelerated glowing cursor that moves smoothly in real time across the DeX monitor
 * as the user interacts with the Z Flip7 cover screen touchpad.
 */
class DexPointerPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private var cursorView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make presentation window non-focusable and transparent
        window?.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            setBackgroundDrawable(null)
        }

        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Sleek glowing cyber cursor
        val cursor = View(context).apply {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE00E5FF.toInt())
                setStroke(3, 0xFFFFFFFF.toInt())
                setSize(28, 28)
            }
            background = drawable
            layoutParams = FrameLayout.LayoutParams(28, 28).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            alpha = 0.95f
        }

        cursorView = cursor
        rootLayout.addView(cursor)
        setContentView(rootLayout)
    }

    fun updatePointerPosition(x: Float, y: Float) {
        cursorView?.let { cursor ->
            cursor.x = x - (cursor.width / 2f)
            cursor.y = y - (cursor.height / 2f)
        }
    }
}
