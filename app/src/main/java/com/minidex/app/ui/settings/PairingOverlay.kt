package com.minidex.app.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.minidex.app.input.accessibility.MiniDexAccessibilityService
import com.minidex.app.ui.theme.MontSurface
import com.minidex.app.ui.theme.Mont

/**
 * The pairing panel, floated over whatever is on the cover screen.
 *
 * The six-digit code lives in Android's own Wireless Debugging dialog, and that dialog is a
 * separate activity: opening it puts MiniDex in the background, so a pairing sheet drawn inside
 * MiniDex's own window is a sheet you cannot see at the moment you need it. This is an
 * accessibility overlay instead — the same window type the DeX cursor already uses — so it sits on
 * top of Settings and the code stays readable while it is typed.
 *
 * It carries its own keypad for the same reason: raising the system IME over an overlay window is
 * not reliable, and six digits do not need a full keyboard.
 */
object PairingOverlay {

    private const val TAG = "PairingOverlay"

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = overlayView != null

    /**
     * A window that is not an activity still has to answer the three owners Compose looks for on
     * the view tree, or `ComposeView` refuses to compose into it.
     */
    private class OverlayLifecycleOwner :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun create() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        appContext: Context,
        displayId: Int,
        content: @Composable () -> Unit
    ): Boolean {
        hide()
        val service = MiniDexAccessibilityService.instance ?: run {
            Log.w(TAG, "Accessibility service is not running; cannot float the pairing panel")
            return false
        }
        return runCatching {
            val displayManager =
                appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(displayId)
                ?: displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            val displayContext = service.createDisplayContext(display)
            val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                displayContext.createWindowContext(windowType, null)
            } else {
                displayContext
            }
            val manager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val owner = OverlayLifecycleOwner().apply { create() }
            val view = ComposeView(windowContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent(content)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                // Focusable, because the panel is typed into — but NOT touch-modal, so every tap
                // outside its own bounds still reaches the pairing screen behind it. Without this
                // the strip owns the whole display and nothing else can be touched.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // The panel is typed into, so it has to keep focus and ride above the keyboard
                // rather than sit under it.
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                title = "MiniDex pairing"
            }

            manager.addView(view, params)
            windowManager = manager
            overlayView = view
            lifecycleOwner = owner
            Log.i(TAG, "Pairing panel floated on display $displayId")
            true
        }.getOrElse {
            Log.e(TAG, "Could not float the pairing panel", it)
            false
        }
    }

    fun hide() {
        val view = overlayView
        if (view != null) runCatching { windowManager?.removeViewImmediate(view) }
        lifecycleOwner?.destroy()
        overlayView = null
        windowManager = null
        lifecycleOwner = null
    }
}

/**
 * The panel itself: one line, held to the bottom of the screen.
 *
 * It is deliberately the smallest thing that can do the job. The whole point of floating it is
 * that Android's pairing dialog stays readable behind it, and a panel that fills the screen to
 * offer a keypad defeats that — so this is two fields and the two commitments, nothing else.
 */
@Composable
fun PairingPanel(
    code: String,
    port: String,
    discoveredPort: Int?,
    statusMessage: String,
    isBusy: Boolean,
    onCode: (String) -> Unit,
    onPort: (String) -> Unit,
    onPair: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectivePort = port.ifBlank { discoveredPort?.toString().orEmpty() }
    val canPair = !isBusy && code.length == 6 && effectivePort.isNotBlank()

    Column(
        modifier
            .fillMaxWidth()
            .background(MontSurface)
            .padding(start = 22.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = statusMessage.uppercase(),
            color = Color.White.copy(alpha = 0.62f),
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Field(
                value = port,
                placeholder = discoveredPort?.toString() ?: "PORT",
                onValue = onPort,
                modifier = Modifier.width(72.dp)
            )
            Field(
                value = code,
                placeholder = "CODE",
                onValue = onCode,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "PAIR",
                color = if (canPair) Color.White else Color.White.copy(alpha = 0.35f),
                fontFamily = Mont,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = canPair, onClick = onPair)
                    .padding(horizontal = 6.dp, vertical = 7.dp)
            )
            Text(
                text = "CLOSE",
                color = Color.White.copy(alpha = 0.58f),
                fontFamily = Mont,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(horizontal = 4.dp, vertical = 7.dp)
            )
        }
    }
}

/**
 * A field is a white block with black type — the same filled rectangle the toggle uses for its
 * live half. No outline, no corner, no label above it.
 */
@Composable
private fun Field(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(30.dp)
            .background(Color.White)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontFamily = Mont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 2.sp
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.Black.copy(alpha = 0.35f),
                fontFamily = Mont,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}
