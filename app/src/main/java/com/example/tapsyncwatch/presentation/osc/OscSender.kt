package com.example.tapsyncwatch.presentation.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OscSender {

    @Volatile var targetIp: String = "192.168.0.10"
    @Volatile var targetPort: Int = 7002

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null

    private fun ensureSocket() {
        if (socket == null || address == null) {
            socket = DatagramSocket()
            address = InetAddress.getByName(targetIp)
        }
    }

    /** Sendet GENAU EIN OSC-Message */
    fun send(path: String) {
        try {
            ensureSocket()
            val data = buildOscMessage(path)
            val packet = DatagramPacket(
                data,
                data.size,
                address,
                targetPort
            )
            socket?.send(packet)
        } catch (_: Exception) {
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            address = null
        }
    }

    private fun buildOscMessage(path: String): ByteArray {
        val addr = oscString(path)
        val type = oscString(",")
        val buffer = ByteBuffer.allocate(addr.size + type.size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(addr)
        buffer.put(type)
        return buffer.array()
    }

    private fun oscString(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        val len = raw.size + 1
        val pad = (4 - (len % 4)) % 4
        val buffer = ByteBuffer.allocate(len + pad)
        buffer.put(raw)
        buffer.put(0)
        repeat(pad) { buffer.put(0) }
        return buffer.array()
    }
}
