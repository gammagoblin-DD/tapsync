package com.example.tapsyncwatch.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OscSender {

    private const val TARGET_IP = "192.168.178.24"
    private const val TARGET_PORT = 7002
    private const val OSC_PATH = "/tap"

    suspend fun sendTap() {
        val address = InetAddress.getByName(TARGET_IP)
        val socket = DatagramSocket()

        val data = buildOscMessage(OSC_PATH)
        val packet = DatagramPacket(data, data.size, address, TARGET_PORT)

        socket.send(packet)
        socket.close()
    }

    private fun buildOscMessage(path: String): ByteArray {
        val pathBytes = oscString(path)
        val typeTagBytes = oscString(",")

        val buffer = ByteBuffer.allocate(pathBytes.size + typeTagBytes.size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(pathBytes)
        buffer.put(typeTagBytes)

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
