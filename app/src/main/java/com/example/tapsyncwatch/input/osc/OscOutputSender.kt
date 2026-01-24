package com.example.tapsyncwatch.input.osc

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OscOutputSender(
    host: String,
    private val port: Int
) {

    private val address: InetAddress = InetAddress.getByName(host)

    private var socket: DatagramSocket? = null

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    // -------------------------------------------------
    // PUBLIC API (exakt wie alte Version)
    // -------------------------------------------------

    fun sendInt(path: String, value: Int) {
        sendAsync(
            buildOscMessage(path, ",i") { bb ->
                bb.putInt(value)
            }
        )
    }

    fun sendFloat(path: String, value: Float) {
        sendAsync(
            buildOscMessage(path, ",f") { bb ->
                bb.putFloat(value)
            }
        )
    }

    // -------------------------------------------------
    // Core send (Background Thread)
    // -------------------------------------------------

    private fun sendAsync(data: ByteArray) {
        executor.execute {
            try {
                if (socket == null || socket?.isClosed == true) {
                    socket = DatagramSocket()
                }

                val packet = DatagramPacket(
                    data,
                    data.size,
                    address,
                    port
                )

                socket?.send(packet)

                Log.d(
                    "OSC",
                    "SEND ${data.size} bytes → ${address.hostAddress}:$port"
                )
            } catch (e: Exception) {
                Log.e("OSC", "SEND FAILED", e)
            }
        }
    }

    // -------------------------------------------------
    // OSC Message Builder
    // -------------------------------------------------

    private fun buildOscMessage(
        path: String,
        typeTag: String,
        payload: (ByteBuffer) -> Unit
    ): ByteArray {

        val bb = ByteBuffer
            .allocate(256)
            .order(ByteOrder.BIG_ENDIAN)

        writeOscString(bb, path)
        writeOscString(bb, typeTag)
        payload(bb)

        return bb.array().copyOf(bb.position())
    }

    private fun writeOscString(bb: ByteBuffer, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        bb.put(bytes)
        bb.put(0)
        while (bb.position() % 4 != 0) bb.put(0)
    }
}
