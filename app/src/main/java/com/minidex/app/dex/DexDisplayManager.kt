package com.minidex.app.dex

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import com.minidex.app.domain.model.DexDisplayInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Discovers and tracks external Samsung DeX display connections and coordinates the visual pointer presentation.
 */
class DexDisplayManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "DexDisplayManager"
    }

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val _activeDexDisplay = MutableStateFlow(DexDisplayInfo.Disconnected)
    val activeDexDisplay: StateFlow<DexDisplayInfo> = _activeDexDisplay.asStateFlow()

    private val _availableDisplays = MutableStateFlow<List<DexDisplayInfo>>(emptyList())
    val availableDisplays: StateFlow<List<DexDisplayInfo>> = _availableDisplays.asStateFlow()

    private var manualOverrideDisplayId: Int = -1
    private var pointerPresentation: DexPointerPresentation? = null
    private var activityRef: WeakReference<Activity>? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshDisplays()
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        refreshDisplays()
    }

    fun setManualOverride(displayId: Int) {
        manualOverrideDisplayId = displayId
        refreshDisplays()
    }

    fun updatePointerPosition(x: Float, y: Float) {
        pointerPresentation?.updatePointerPosition(x, y)
    }

    private fun updatePresentation(activeDisplay: DexDisplayInfo) {
        val activity = activityRef?.get() ?: return
        if (activeDisplay.isConnected && activeDisplay.isExternal) {
            val display = displayManager.getDisplay(activeDisplay.displayId)
            if (display != null && pointerPresentation?.display?.displayId != display.displayId) {
                try {
                    pointerPresentation?.dismiss()
                    pointerPresentation = DexPointerPresentation(activity, display).apply {
                        show()
                    }
                    Log.i(TAG, "Successfully showed cursor presentation on DeX display #${activeDisplay.displayId}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not show pointer presentation overlay on display ${activeDisplay.displayId}: ${e.message}")
                }
            }
        } else {
            try {
                pointerPresentation?.dismiss()
                pointerPresentation = null
            } catch (e: Exception) {}
        }
    }

    fun refreshDisplays() {
        scope.launch {
            val displays = displayManager.displays
            val detectedList = mutableListOf<DexDisplayInfo>()

            for (d in displays) {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                d.getRealMetrics(metrics)

                val isPresentation = (d.flags and Display.FLAG_PRESENTATION) != 0
                val isExternal = d.displayId != Display.DEFAULT_DISPLAY

                detectedList.add(
                    DexDisplayInfo(
                        displayId = d.displayId,
                        name = d.name ?: "Display ${d.displayId}",
                        width = metrics.widthPixels,
                        height = metrics.heightPixels,
                        refreshRate = d.refreshRate,
                        isPresentation = isPresentation,
                        isExternal = isExternal,
                        isConnected = d.state == Display.STATE_ON || d.state == Display.STATE_DOZE
                    )
                )
            }

            _availableDisplays.value = detectedList

            // Select active DeX display
            val selected = if (manualOverrideDisplayId != -1) {
                detectedList.firstOrNull { it.displayId == manualOverrideDisplayId }
                    ?: findBestDexCandidate(detectedList)
            } else {
                findBestDexCandidate(detectedList)
            }

            val result = selected ?: DexDisplayInfo.Disconnected
            _activeDexDisplay.value = result
            updatePresentation(result)
        }
    }

    private fun findBestDexCandidate(displays: List<DexDisplayInfo>): DexDisplayInfo? {
        // Priority 1: Explicit DeX name matching
        displays.firstOrNull { it.name.contains("DeX", ignoreCase = true) }?.let { return it }

        // Priority 2: External Presentation display
        displays.firstOrNull { it.isPresentation && it.isExternal }?.let { return it }

        // Priority 3: Any external display with typical desktop resolution (>= 1280x720) and not display 0
        displays.firstOrNull {
            it.isExternal && (it.width >= 1280 || it.height >= 1280)
        }?.let { return it }

        // Priority 4: Any non-default display
        displays.firstOrNull { it.isExternal }?.let { return it }

        return null
    }

    fun release() {
        try {
            pointerPresentation?.dismiss()
            pointerPresentation = null
        } catch (e: Exception) {}
        displayManager.unregisterDisplayListener(displayListener)
    }
}
