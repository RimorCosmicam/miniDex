package com.minidex.app.domain.model

/**
 * Information regarding the discovered external DeX display.
 */
data class DexDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val isPresentation: Boolean,
    val isExternal: Boolean,
    val isConnected: Boolean
) {
    companion object {
        val Disconnected = DexDisplayInfo(
            displayId = -1,
            name = "No DeX Display Detected",
            width = 0,
            height = 0,
            refreshRate = 0f,
            isPresentation = false,
            isExternal = false,
            isConnected = false
        )
    }
}
