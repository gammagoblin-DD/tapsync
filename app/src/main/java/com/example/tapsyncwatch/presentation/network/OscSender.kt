package com.example.tapsyncwatch.presentation.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OscSender {

    @Volatile var targetIp: String = "192.168.178.24"
    @Volatile var targetPort: Int = 7002

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null

    private fun ensureSocket() {
        if (socket == null || address == null) {
            socket = DatagramSocket()
            address = InetAddress.getByName(targetIp)
        }
    }

    fun sendInt(path: String, value: Int) {
        try {
            ensureSocket()
            val data = buildInt(path, value)
            socket?.send(DatagramPacket(data, data.size, address, targetPort))
        } catch (_: Exception) {
            socket?.close()
            socket = null
            address = null
        }
    }

    fun sendFloat(path: String, value: Float) {
        try {
            ensureSocket()
            val data = buildFloat(path, value)
            socket?.send(DatagramPacket(data, data.size, address, targetPort))
        } catch (_: Exception) {
            socket?.close()
            socket = null
            address = null
        }
    }

    private fun buildInt(path: String, value: Int): ByteArray {
        val addr = oscString(path)
        val type = oscString(",i")
        return ByteBuffer.allocate(addr.size + type.size + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(addr)
            .put(type)
            .putInt(value)
            .array()
    }

    private fun buildFloat(path: String, value: Float): ByteArray {
        val addr = oscString(path)
        val type = oscString(",f")
        return ByteBuffer.allocate(addr.size + type.size + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(addr)
            .put(type)
            .putFloat(value)
            .array()
    }

    private fun oscString(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        val len = raw.size + 1
        val pad = (4 - len % 4) % 4
        return ByteBuffer.allocate(len + pad)
            .put(raw)
            .put(0)
            .apply { repeat(pad) { put(0) } }
            .array()
    }
}
