package com.minidex.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minidex.app.domain.model.CursorMode
import com.minidex.app.domain.model.HalftoneColorway
import com.minidex.app.domain.model.HapticStrength
import com.minidex.app.domain.model.KeyHeightLevel
import com.minidex.app.domain.model.VisualFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minidex_user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val COLORWAY = stringPreferencesKey("halftone_colorway")
        val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
        val VISUAL_FILTER = stringPreferencesKey("visual_filter")
        val HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
        val KEY_HEIGHT_LEVEL = stringPreferencesKey("key_height_level")
        val KEY_GAP_DP = intPreferencesKey("key_gap_dp")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val MODIFIER_TIMEOUT_MS = longPreferencesKey("modifier_timeout_ms")
        val POINTER_SENSITIVITY = floatPreferencesKey("pointer_sensitivity")
        val POINTER_ACCELERATION = floatPreferencesKey("pointer_acceleration")
        val SCROLL_SENSITIVITY = floatPreferencesKey("scroll_sensitivity")
        val NATURAL_SCROLLING = booleanPreferencesKey("natural_scrolling")
        val EDGE_SCROLL_ON_RIGHT = booleanPreferencesKey("edge_scroll_on_right")
        val EDGE_SCROLL_ENABLED = booleanPreferencesKey("edge_scroll_enabled")
        val EDGE_RIGHT_CLICK_ENABLED = booleanPreferencesKey("edge_right_click_enabled")
        val EDGE_RAIL_SCALE = floatPreferencesKey("edge_rail_scale")
        val EDGE_CORNER_SCALE = floatPreferencesKey("edge_corner_scale")
        val BACKGROUND_GIF_URI = stringPreferencesKey("background_gif_uri")
        val BACKGROUND_GIF_SCALE = floatPreferencesKey("background_gif_scale")
        val BACKGROUND_GIF_OFFSET_X = floatPreferencesKey("background_gif_offset_x")
        val BACKGROUND_GIF_OFFSET_Y = floatPreferencesKey("background_gif_offset_y")
        val BACKGROUND_GIF_OPACITY = floatPreferencesKey("background_gif_opacity")
        val TAP_TO_CLICK = booleanPreferencesKey("tap_to_click")
        val CURSOR_MODE = stringPreferencesKey("cursor_mode")
        val PREFERRED_BACKEND = stringPreferencesKey("preferred_backend")
        val MANUAL_DISPLAY_ID = intPreferencesKey("manual_display_id")
        val ADB_AUTO_CONNECT = booleanPreferencesKey("adb_auto_connect")
        val ADB_PORT = intPreferencesKey("adb_port")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ADB_PAIRED_BEFORE = booleanPreferencesKey("adb_paired_before")
    }

    /**
     * One reader, used by both the flow and the read-modify-write. Enum names that no longer
     * resolve — a colourway written by an older build, say — fall back to the default rather than
     * throwing, which is also what stands in for a migration here.
     */
    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        fallback: T
    ): T = try {
        this[key]?.let { enumValueOf<T>(it) } ?: fallback
    } catch (_: IllegalArgumentException) {
        fallback
    }

    private fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
        colorway = enum(Keys.COLORWAY, HalftoneColorway.MUSTARD),
        amoledMode = this[Keys.AMOLED_MODE] ?: false,
        visualFilter = enum(Keys.VISUAL_FILTER, VisualFilter.NONE),
        hapticStrength = enum(Keys.HAPTIC_STRENGTH, HapticStrength.CRISP),
        keyHeightLevel = enum(Keys.KEY_HEIGHT_LEVEL, KeyHeightLevel.BALANCED),
        keyGapDp = this[Keys.KEY_GAP_DP] ?: 4,
        doubleTapToLockModifier = this[Keys.DOUBLE_TAP_LOCK] ?: true,
        modifierTimeoutMs = this[Keys.MODIFIER_TIMEOUT_MS] ?: 0L,
        pointerSensitivity = this[Keys.POINTER_SENSITIVITY] ?: 1.2f,
        pointerAcceleration = this[Keys.POINTER_ACCELERATION] ?: 0.5f,
        scrollSensitivity = this[Keys.SCROLL_SENSITIVITY] ?: 1.0f,
        naturalScrolling = this[Keys.NATURAL_SCROLLING] ?: false,
        edgeScrollOnRight = this[Keys.EDGE_SCROLL_ON_RIGHT] ?: false,
        edgeScrollEnabled = this[Keys.EDGE_SCROLL_ENABLED] ?: true,
        edgeRightClickEnabled = this[Keys.EDGE_RIGHT_CLICK_ENABLED] ?: true,
        edgeRailScale = this[Keys.EDGE_RAIL_SCALE] ?: 1f,
        edgeCornerScale = this[Keys.EDGE_CORNER_SCALE] ?: 1f,
        backgroundGifUri = this[Keys.BACKGROUND_GIF_URI] ?: "",
        backgroundGifScale = this[Keys.BACKGROUND_GIF_SCALE] ?: 1f,
        backgroundGifOffsetX = this[Keys.BACKGROUND_GIF_OFFSET_X] ?: 0f,
        backgroundGifOffsetY = this[Keys.BACKGROUND_GIF_OFFSET_Y] ?: 0f,
        backgroundGifOpacity = this[Keys.BACKGROUND_GIF_OPACITY] ?: 0.55f,
        tapToClick = this[Keys.TAP_TO_CLICK] ?: true,
        cursorMode = enum(Keys.CURSOR_MODE, CursorMode.AUTO_NATIVE),
        preferredBackend = this[Keys.PREFERRED_BACKEND] ?: "AUTO",
        manualDisplayId = this[Keys.MANUAL_DISPLAY_ID] ?: -1,
        adbAutoConnect = this[Keys.ADB_AUTO_CONNECT] ?: true,
        adbPort = this[Keys.ADB_PORT] ?: 5555,
        onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: false,
        adbPairedBefore = this[Keys.ADB_PAIRED_BEFORE] ?: false
    )

    val userPreferencesFlow: Flow<UserPreferences> =
        context.dataStore.data.map { it.toUserPreferences() }

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toUserPreferences())

            prefs[Keys.COLORWAY] = updated.colorway.name
            prefs[Keys.AMOLED_MODE] = updated.amoledMode
            prefs[Keys.VISUAL_FILTER] = updated.visualFilter.name
            prefs[Keys.HAPTIC_STRENGTH] = updated.hapticStrength.name
            prefs[Keys.KEY_HEIGHT_LEVEL] = updated.keyHeightLevel.name
            prefs[Keys.KEY_GAP_DP] = updated.keyGapDp
            prefs[Keys.DOUBLE_TAP_LOCK] = updated.doubleTapToLockModifier
            prefs[Keys.MODIFIER_TIMEOUT_MS] = updated.modifierTimeoutMs
            prefs[Keys.POINTER_SENSITIVITY] = updated.pointerSensitivity
            prefs[Keys.POINTER_ACCELERATION] = updated.pointerAcceleration
            prefs[Keys.SCROLL_SENSITIVITY] = updated.scrollSensitivity
            prefs[Keys.NATURAL_SCROLLING] = updated.naturalScrolling
            prefs[Keys.EDGE_SCROLL_ON_RIGHT] = updated.edgeScrollOnRight
            prefs[Keys.EDGE_SCROLL_ENABLED] = updated.edgeScrollEnabled
            prefs[Keys.EDGE_RIGHT_CLICK_ENABLED] = updated.edgeRightClickEnabled
            prefs[Keys.EDGE_RAIL_SCALE] = updated.edgeRailScale
            prefs[Keys.EDGE_CORNER_SCALE] = updated.edgeCornerScale
            prefs[Keys.BACKGROUND_GIF_URI] = updated.backgroundGifUri
            prefs[Keys.BACKGROUND_GIF_SCALE] = updated.backgroundGifScale
            prefs[Keys.BACKGROUND_GIF_OFFSET_X] = updated.backgroundGifOffsetX
            prefs[Keys.BACKGROUND_GIF_OFFSET_Y] = updated.backgroundGifOffsetY
            prefs[Keys.BACKGROUND_GIF_OPACITY] = updated.backgroundGifOpacity
            prefs[Keys.TAP_TO_CLICK] = updated.tapToClick
            prefs[Keys.CURSOR_MODE] = updated.cursorMode.name
            prefs[Keys.PREFERRED_BACKEND] = updated.preferredBackend
            prefs[Keys.MANUAL_DISPLAY_ID] = updated.manualDisplayId
            prefs[Keys.ADB_AUTO_CONNECT] = updated.adbAutoConnect
            prefs[Keys.ADB_PORT] = updated.adbPort
            prefs[Keys.ONBOARDING_COMPLETE] = updated.onboardingComplete
            prefs[Keys.ADB_PAIRED_BEFORE] = updated.adbPairedBefore
        }
    }
}
