package com.minidex.app

import com.minidex.app.data.UserPreferences
import com.minidex.app.input.adb.AdbConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbPreferencesAndStatusTest {

    @Test
    fun testUserPreferencesDefaults() {
        val prefs = UserPreferences()
        assertTrue(prefs.adbAutoConnect)
        assertEquals(5555, prefs.adbPort)
        assertEquals(-1, prefs.manualDisplayId)
    }

    @Test
    fun testUserPreferencesCustomAdb() {
        val prefs = UserPreferences(
            adbAutoConnect = false,
            adbPort = 38475,
            manualDisplayId = 2
        )
        assertFalse(prefs.adbAutoConnect)
        assertEquals(38475, prefs.adbPort)
        assertEquals(2, prefs.manualDisplayId)
    }

    @Test
    fun testAdbConnectionStatusTransitions() {
        val statuses = AdbConnectionStatus.entries
        assertTrue(statuses.contains(AdbConnectionStatus.DISCONNECTED))
        assertTrue(statuses.contains(AdbConnectionStatus.SEARCHING_MDNS))
        assertTrue(statuses.contains(AdbConnectionStatus.PAIRING))
        assertTrue(statuses.contains(AdbConnectionStatus.CONNECTING))
        assertTrue(statuses.contains(AdbConnectionStatus.CONNECTED))
        assertTrue(statuses.contains(AdbConnectionStatus.ERROR))
    }
}
