package com.example.tapsyncwatch.input.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OscOutputSender(
    targetIp: String,
    private val targetPort: Int
) {

    private val socket = DatagramSocket()
    private val address = InetAddress.getByName(targetIp)

    // Debounce, damit ein Tap wirklich EIN Tap ist
    private var lastTapTime: Long = 0L
    private val TAP_COOLDOWN_MS = 150L

    fun sendTempoTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < TAP_COOLDOWN_MS) return
        lastTapTime = now

        // 1️⃣ TAP DOWN (1.0)
        sendOscFloat(
            address = "/composition/tempocontroller/tempotap",
            value = 1.0f
        )

        // 2️⃣ TAP UP (0.0) – kurz danach
        Thread {
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {}

            sendOscFloat(
                address = "/composition/tempocontroller/tempotap",
                value = 0.0f
            )
        }.start()
    }

    private fun sendOscFloat(address: String, value: Float) {
        val addressBytes = padOscString(address)
        val typeTagBytes = padOscString(",f")

        val buffer = ByteBuffer
            .allocate(addressBytes.size + typeTagBytes.size + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(addressBytes)
            .put(typeTagBytes)
            .putFloat(value)
            .array()

        val packet = DatagramPacket(
            buffer,
            buffer.size,
            this.address,
            targetPort
        )

        socket.send(packet)
    }

    private fun padOscString(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.US_ASCII)
        val lengthWithNull = raw.size + 1
        val paddedLength = (lengthWithNull + 3) and -4

        return ByteBuffer
            .allocate(paddedLength)
            .order(ByteOrder.BIG_ENDIAN)
            .put(raw)
            .put(0)
            .array()
    }

    fun close() {
        socket.close()
    }
}
