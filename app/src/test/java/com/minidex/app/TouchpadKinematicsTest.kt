package com.minidex.app

import com.minidex.app.ui.touchpad.TouchpadKinematics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadKinematicsTest {

    @Test
    fun testZeroDeltaProducesZero() {
        val (dx, dy) = TouchpadKinematics.calculatePointerDelta(0f, 0f, 1.0f, 0.5f)
        assertEquals(0f, dx, 0.001f)
        assertEquals(0f, dy, 0.001f)
    }

    @Test
    fun testAccelerationIncreasesMagnitude() {
        val (linearDx, _) = TouchpadKinematics.calculatePointerDelta(10f, 0f, 1.0f, 0.0f)
        val (accelDx, _) = TouchpadKinematics.calculatePointerDelta(10f, 0f, 1.0f, 1.0f)

        assertTrue("Accelerated delta ($accelDx) should be greater than linear delta ($linearDx)", accelDx > linearDx)
    }

    @Test
    fun testNaturalScrollingInvertsDirection() {
        val (stdX, stdY) = TouchpadKinematics.calculateScrollDelta(0f, 10f, 1.0f, naturalScrolling = false)
        val (natX, natY) = TouchpadKinematics.calculateScrollDelta(0f, 10f, 1.0f, naturalScrolling = true)

        assertEquals(-stdY, natY, 0.001f)
    }
}
