package com.minidex.app

import io.github.muntashirakon.adb.PairingConnectionCtx
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibAdbCheckTest {
    @Test
    fun testPairingConnectionCtxExists() {
        assertNotNull(PairingConnectionCtx::class.java)
    }
}
