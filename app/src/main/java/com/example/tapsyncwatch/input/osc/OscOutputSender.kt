package com.example.tapsyncwatch.input.osc

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class OscStatus(
    val connected: Boolean,
    val lastSentLabel: String?,
    val lastSentAt: Long?
)

class OscOutputSender(
    host: String,
    port: Int
) {

    private var address: InetAddress = InetAddress.getByName(host)
    private var targetPort: Int = port

    private var socket: DatagramSocket = DatagramSocket().apply {
        reuseAddress = true
    }

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val _status = MutableStateFlow(
        OscStatus(
            connected = false,
            lastSentLabel = null,
            lastSentAt = null
        )
    )
    val status: StateFlow<OscStatus> = _status.asStateFlow()

    // -------------------------------------------------
    // LIVE UPDATE TARGET (IP / PORT)
    // -------------------------------------------------
    fun updateTarget(host: String, port: Int) {
        try {
            address = InetAddress.getByName(host)
            targetPort = port
            socket.close()
            socket = DatagramSocket().apply { reuseAddress = true }

            _status.value = _status.value.copy(
                connected = true
            )
        } catch (e: Exception) {
            Log.e("OSC", "UPDATE TARGET FAILED", e)
            _status.value = _status.value.copy(
                connected = false
            )
        }
    }

    // -------------------------------------------------
    // PUBLIC SEND API
    // -------------------------------------------------
    fun sendInt(path: String, value: Int) {
        sendAsync(path) {
            buildOscMessage(path, ",i") { it.putInt(value) }
        }
    }

    fun sendFloat(path: String, value: Float) {
        sendAsync(path) {
            buildOscMessage(path, ",f") { it.putFloat(value) }
        }
    }

    // -------------------------------------------------
    // CORE SEND
    // -------------------------------------------------
    private fun sendAsync(
        label: String,
        builder: () -> ByteArray
    ) {
        executor.execute {
            try {
                val data = builder()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    address,
                    targetPort
                )

                socket.send(packet)

                _status.value = OscStatus(
                    connected = true,
                    lastSentLabel = label,
                    lastSentAt = System.currentTimeMillis()
                )

                Log.d("OSC", "SEND → ${address.hostAddress}:$targetPort $label")
            } catch (e: Exception) {
                Log.e("OSC", "SEND FAILED", e)
                _status.value = _status.value.copy(
                    connected = false
                )
            }
        }
    }

    // -------------------------------------------------
    // OSC MESSAGE BUILDER
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
