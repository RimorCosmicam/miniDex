package com.minidex.app.dex

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

/**
 * Discovers and tracks external Samsung DeX display connections.
 * Correctly differentiates between internal folding screen (0), cover screen (1), and external DeX monitor (2+).
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

    fun setManualOverride(displayId: Int) {
        manualOverrideDisplayId = displayId
        refreshDisplays()
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

            _activeDexDisplay.value = selected ?: detectedList.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY } ?: DexDisplayInfo.Disconnected
            Log.i(TAG, "Active DeX Display detected: ${_activeDexDisplay.value}")
        }
    }

    private fun findBestDexCandidate(displays: List<DexDisplayInfo>): DexDisplayInfo? {
        // Samsung creates DeX last. This also handles glasses whose advertised
        // name is just the product name (for example "Air") rather than "DeX".
        return displays
            .asSequence()
            .filter { it.isExternal && it.isConnected }
            .maxByOrNull { it.displayId }
    }

    fun release() {
        displayManager.unregisterDisplayListener(displayListener)
    }
}
