package com.minidex.app

import android.view.KeyEvent
import com.minidex.app.domain.model.ModifierLockState
import com.minidex.app.domain.model.ModifierState
import com.minidex.app.domain.model.ModifierType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierStateTest {

    @Test
    fun testInitialStateInactive() {
        val state = ModifierState()
        assertEquals(ModifierLockState.INACTIVE, state.shift)
        assertEquals(ModifierLockState.INACTIVE, state.ctrl)
        assertEquals(ModifierLockState.INACTIVE, state.alt)
        assertEquals(ModifierLockState.INACTIVE, state.meta)
        assertEquals(0, state.toMetaState())
    }

    @Test
    fun testToggleModifierCycle() {
        var state = ModifierState()

        // Single tap -> Latched
        state = state.toggleModifier(ModifierType.CTRL, allowLock = true)
        assertEquals(ModifierLockState.LATCHED, state.ctrl)
        assertTrue(state.ctrl.isActive)

        // Double tap -> Locked
        state = state.toggleModifier(ModifierType.CTRL, allowLock = true)
        assertEquals(ModifierLockState.LOCKED, state.ctrl)
        assertTrue(state.ctrl.isActive)

        // Third tap -> Inactive
        state = state.toggleModifier(ModifierType.CTRL, allowLock = true)
        assertEquals(ModifierLockState.INACTIVE, state.ctrl)
        assertFalse(state.ctrl.isActive)
    }

    @Test
    fun testConsumeLatchedModifiers() {
        var state = ModifierState(
            shift = ModifierLockState.LATCHED,
            ctrl = ModifierLockState.LOCKED,
            alt = ModifierLockState.LATCHED
        )

        val consumed = state.consumeLatched()

        assertEquals(ModifierLockState.INACTIVE, consumed.shift)
        assertEquals(ModifierLockState.LOCKED, consumed.ctrl) // Locked remains active!
        assertEquals(ModifierLockState.INACTIVE, consumed.alt)
    }
}
