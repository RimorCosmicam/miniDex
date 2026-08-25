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
import com.minidex.app.domain.model.AccentColor
import com.minidex.app.domain.model.CursorMode
import com.minidex.app.domain.model.HapticStrength
import com.minidex.app.domain.model.KeyHeightLevel
import com.minidex.app.domain.model.ThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minidex_user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
        val KEY_HEIGHT_LEVEL = stringPreferencesKey("key_height_level")
        val KEY_GAP_DP = intPreferencesKey("key_gap_dp")
        val CORNER_RADIUS_DP = intPreferencesKey("corner_radius_dp")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val MODIFIER_TIMEOUT_MS = longPreferencesKey("modifier_timeout_ms")
        val POINTER_SENSITIVITY = floatPreferencesKey("pointer_sensitivity")
        val POINTER_ACCELERATION = floatPreferencesKey("pointer_acceleration")
        val SCROLL_SENSITIVITY = floatPreferencesKey("scroll_sensitivity")
        val NATURAL_SCROLLING = booleanPreferencesKey("natural_scrolling")
        val TAP_TO_CLICK = booleanPreferencesKey("tap_to_click")
        val CURSOR_MODE = stringPreferencesKey("cursor_mode")
        val PREFERRED_BACKEND = stringPreferencesKey("preferred_backend")
        val MANUAL_DISPLAY_ID = intPreferencesKey("manual_display_id")
        val ADB_AUTO_CONNECT = booleanPreferencesKey("adb_auto_connect")
        val ADB_PORT = intPreferencesKey("adb_port")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeVariant = try {
                ThemeVariant.valueOf(prefs[Keys.THEME_VARIANT] ?: ThemeVariant.CYBER_OLED.name)
            } catch (e: Exception) {
                ThemeVariant.CYBER_OLED
            },
            accentColor = try {
                AccentColor.valueOf(prefs[Keys.ACCENT_COLOR] ?: AccentColor.NEON_CYAN.name)
            } catch (e: Exception) {
                AccentColor.NEON_CYAN
            },
            hapticStrength = try {
                HapticStrength.valueOf(prefs[Keys.HAPTIC_STRENGTH] ?: HapticStrength.CRISP.name)
            } catch (e: Exception) {
                HapticStrength.CRISP
            },
            keyHeightLevel = try {
                KeyHeightLevel.valueOf(prefs[Keys.KEY_HEIGHT_LEVEL] ?: KeyHeightLevel.BALANCED.name)
            } catch (e: Exception) {
                KeyHeightLevel.BALANCED
            },
            keyGapDp = prefs[Keys.KEY_GAP_DP] ?: 4,
            cornerRadiusDp = prefs[Keys.CORNER_RADIUS_DP] ?: 8,
            doubleTapToLockModifier = prefs[Keys.DOUBLE_TAP_LOCK] ?: true,
            modifierTimeoutMs = prefs[Keys.MODIFIER_TIMEOUT_MS] ?: 0L,
            pointerSensitivity = prefs[Keys.POINTER_SENSITIVITY] ?: 1.2f,
            pointerAcceleration = prefs[Keys.POINTER_ACCELERATION] ?: 0.5f,
            scrollSensitivity = prefs[Keys.SCROLL_SENSITIVITY] ?: 1.0f,
            naturalScrolling = prefs[Keys.NATURAL_SCROLLING] ?: false,
            tapToClick = prefs[Keys.TAP_TO_CLICK] ?: true,
            cursorMode = try {
                CursorMode.valueOf(prefs[Keys.CURSOR_MODE] ?: CursorMode.AUTO_NATIVE.name)
            } catch (_: Exception) {
                CursorMode.AUTO_NATIVE
            },
            preferredBackend = prefs[Keys.PREFERRED_BACKEND] ?: "AUTO",
            manualDisplayId = prefs[Keys.MANUAL_DISPLAY_ID] ?: -1,
            adbAutoConnect = prefs[Keys.ADB_AUTO_CONNECT] ?: true,
            adbPort = prefs[Keys.ADB_PORT] ?: 5555
        )
    }

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        context.dataStore.edit { prefs ->
            val current = UserPreferences(
                themeVariant = try {
                    ThemeVariant.valueOf(prefs[Keys.THEME_VARIANT] ?: ThemeVariant.CYBER_OLED.name)
                } catch (e: Exception) {
                    ThemeVariant.CYBER_OLED
                },
                accentColor = try {
                    AccentColor.valueOf(prefs[Keys.ACCENT_COLOR] ?: AccentColor.NEON_CYAN.name)
                } catch (e: Exception) {
                    AccentColor.NEON_CYAN
                },
                hapticStrength = try {
                    HapticStrength.valueOf(prefs[Keys.HAPTIC_STRENGTH] ?: HapticStrength.CRISP.name)
                } catch (e: Exception) {
                    HapticStrength.CRISP
                },
                keyHeightLevel = try {
                    KeyHeightLevel.valueOf(prefs[Keys.KEY_HEIGHT_LEVEL] ?: KeyHeightLevel.BALANCED.name)
                } catch (e: Exception) {
                    KeyHeightLevel.BALANCED
                },
                keyGapDp = prefs[Keys.KEY_GAP_DP] ?: 4,
                cornerRadiusDp = prefs[Keys.CORNER_RADIUS_DP] ?: 8,
                doubleTapToLockModifier = prefs[Keys.DOUBLE_TAP_LOCK] ?: true,
                modifierTimeoutMs = prefs[Keys.MODIFIER_TIMEOUT_MS] ?: 0L,
                pointerSensitivity = prefs[Keys.POINTER_SENSITIVITY] ?: 1.2f,
                pointerAcceleration = prefs[Keys.POINTER_ACCELERATION] ?: 0.5f,
                scrollSensitivity = prefs[Keys.SCROLL_SENSITIVITY] ?: 1.0f,
                naturalScrolling = prefs[Keys.NATURAL_SCROLLING] ?: false,
                tapToClick = prefs[Keys.TAP_TO_CLICK] ?: true,
                cursorMode = try {
                    CursorMode.valueOf(prefs[Keys.CURSOR_MODE] ?: CursorMode.AUTO_NATIVE.name)
                } catch (_: Exception) {
                    CursorMode.AUTO_NATIVE
                },
                preferredBackend = prefs[Keys.PREFERRED_BACKEND] ?: "AUTO",
                manualDisplayId = prefs[Keys.MANUAL_DISPLAY_ID] ?: -1,
                adbAutoConnect = prefs[Keys.ADB_AUTO_CONNECT] ?: true,
                adbPort = prefs[Keys.ADB_PORT] ?: 5555
            )
            val updated = transform(current)

            prefs[Keys.THEME_VARIANT] = updated.themeVariant.name
            prefs[Keys.ACCENT_COLOR] = updated.accentColor.name
            prefs[Keys.HAPTIC_STRENGTH] = updated.hapticStrength.name
            prefs[Keys.KEY_HEIGHT_LEVEL] = updated.keyHeightLevel.name
            prefs[Keys.KEY_GAP_DP] = updated.keyGapDp
            prefs[Keys.CORNER_RADIUS_DP] = updated.cornerRadiusDp
            prefs[Keys.DOUBLE_TAP_LOCK] = updated.doubleTapToLockModifier
            prefs[Keys.MODIFIER_TIMEOUT_MS] = updated.modifierTimeoutMs
            prefs[Keys.POINTER_SENSITIVITY] = updated.pointerSensitivity
            prefs[Keys.POINTER_ACCELERATION] = updated.pointerAcceleration
            prefs[Keys.SCROLL_SENSITIVITY] = updated.scrollSensitivity
            prefs[Keys.NATURAL_SCROLLING] = updated.naturalScrolling
            prefs[Keys.TAP_TO_CLICK] = updated.tapToClick
            prefs[Keys.CURSOR_MODE] = updated.cursorMode.name
            prefs[Keys.PREFERRED_BACKEND] = updated.preferredBackend
            prefs[Keys.MANUAL_DISPLAY_ID] = updated.manualDisplayId
            prefs[Keys.ADB_AUTO_CONNECT] = updated.adbAutoConnect
            prefs[Keys.ADB_PORT] = updated.adbPort
        }
    }
}
