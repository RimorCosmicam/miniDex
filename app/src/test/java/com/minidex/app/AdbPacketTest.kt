package com.minidex.app

import com.minidex.app.input.adb.AdbPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdbPacketTest {

    @Test
    fun testAdbPacketSerializationAndHeaderParsing() {
        val payload = "shell:input keyevent 66".toByteArray(Charsets.UTF_8)
        val packet = AdbPacket(
            command = AdbPacket.A_OPEN,
            arg0 = 1,
            arg1 = 0,
            data = payload
        )

        val serialized = packet.toByteArray()
        assertEquals(24 + payload.size, serialized.size)

        // Parse header back
        val headerBytes = serialized.copyOfRange(0, 24)
        val header = AdbPacket.parseHeader(headerBytes)

        assertEquals(AdbPacket.A_OPEN, header.command)
        assertEquals(1, header.arg0)
        assertEquals(0, header.arg1)
        assertEquals(payload.size, header.length)
        assertEquals(AdbPacket.calculateCrc32(payload), header.crc)
    }

    @Test
    fun testCrc32Calculation() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val crc = AdbPacket.calculateCrc32(data)
        assertEquals(15, crc)
    }
}
