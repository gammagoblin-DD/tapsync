package com.example.tapsyncwatch.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

object OscSender {

    private const val TARGET_IP = "192.168.178.24"
    private const val TARGET_PORT = 7002
    private const val OSC_PATH = "/composition/tempocontroller/tempotap"

    private val address = InetAddress.getByName(TARGET_IP)

    // 🔒 Socket-Status
    private var socket: DatagramSocket? = null
    private val socketBroken = AtomicBoolean(false)

    /**
     * Öffnet Socket bei Bedarf (lazy & recoverable)
     */
    private fun ensureSocket() {
        if (socket == null || socketBroken.get()) {
            try {
                socket?.close()
            } catch (_: Exception) {
            }

            socket = DatagramSocket().apply {
                reuseAddress = true
            }

            socketBroken.set(false)
        }
    }

    suspend fun sendTap() {
        try {
            ensureSocket()

            val data = buildOscMessage(OSC_PATH)
            val packet = DatagramPacket(data, data.size, address, TARGET_PORT)

            socket?.send(packet)

        } catch (_: Exception) {
            // 🔥 Socket gilt als kaputt → beim nächsten Tap neu öffnen
            socketBroken.set(true)
        }
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

    /**
     * Optional: sauberer Shutdown (nicht zwingend nötig)
     */
    fun shutdown() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        socketBroken.set(false)
    }
}
