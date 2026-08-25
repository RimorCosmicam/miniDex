package com.minidex.app.input.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard ADB binary packet.
 * Header: 24 bytes (command, arg0, arg1, data_length, data_crc32, magic).
 */
data class AdbPacket(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0)
) {
    companion object {
        const val A_SYNC = 0x434e5953
        const val A_CNXN = 0x4e584e43
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_AUTH = 0x48545541

        const val A_VERSION = 0x01000000
        const val MAX_PAYLOAD = 4096

        const val AUTH_TYPE_TOKEN = 1
        const val AUTH_TYPE_SIGNATURE = 2
        const val AUTH_TYPE_RSA_PUBLIC = 3

        fun parseHeader(headerBytes: ByteArray): Header {
            val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = buf.int
            val a0 = buf.int
            val a1 = buf.int
            val len = buf.int
            val crc = buf.int
            val magic = buf.int

            if (magic != (cmd xor -0x1)) {
                throw IllegalStateException("Invalid ADB magic: expected ${(cmd xor -0x1)}, got $magic")
            }

            return Header(cmd, a0, a1, len, crc)
        }

        fun calculateCrc32(data: ByteArray): Int {
            var sum = 0
            for (b in data) {
                sum += (b.toInt() and 0xFF)
            }
            return sum
        }
    }

    data class Header(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val length: Int,
        val crc: Int
    )

    fun toByteArray(): ByteArray {
        val buf = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(data.size)
        buf.putInt(calculateCrc32(data))
        buf.putInt(command xor -0x1)
        if (data.isNotEmpty()) {
            buf.put(data)
        }
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AdbPacket
        if (command != other.command) return false
        if (arg0 != other.arg0) return false
        if (arg1 != other.arg1) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + data.contentHashCode()
        return result
    }
}
