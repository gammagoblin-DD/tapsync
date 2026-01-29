package com.example.tapsyncwatch.input.osc

import android.util.Log
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OscInputReceiver(
    private val clock: Clock,
    private val port: Int = 7000
) {

    // 🔔 UI-only activity pulse (legacy semantics)
    private val _oscActivity = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8
    )
    val oscActivity: SharedFlow<Unit> = _oscActivity

    private var socket: DatagramSocket? = null
    private var running = false

    fun start() {
        if (running) return
        running = true

        socket = DatagramSocket(port)

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(2048)
            while (running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet.data.copyOf(packet.length))
                } catch (e: Exception) {
                    Log.e("OSC-IN", "socket error", e)
                }
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
    }

    // =====================================================
    // ENTRY POINT
    // =====================================================

    private fun handlePacket(data: ByteArray) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        parseElement(bb)
    }

    // =====================================================
    // OSC PARSER
    // =====================================================

    private fun parseElement(bb: ByteBuffer) {
        if (!bb.hasRemaining()) return

        val startPos = bb.position()
        val address = readOscString(bb) ?: return

        if (address == "#bundle") {
            if (bb.remaining() >= 8) bb.position(bb.position() + 8)

            while (bb.remaining() >= 4) {
                val size = bb.int
                if (bb.remaining() < size) break

                val slice = bb.slice()
                slice.limit(size)
                parseElement(slice)
                bb.position(bb.position() + size)
            }
        } else {
            val typeTags = readOscString(bb) ?: return
            Log.d("OSC-IN", "addr=$address types=$typeTags")

            // 🔔 ANY OSC MESSAGE = activity pulse
            _oscActivity.tryEmit(Unit)

            handleMessage(address, typeTags, bb)
        }

        bb.position(startPos)
    }

    private fun handleMessage(address: String, typeTags: String, bb: ByteBuffer) {
        when (address) {

            // =================================================
            // Resolume Tempo Controller (bpm-clock compatible)
            // =================================================
            "/composition/tempocontroller/tempo" -> {
                val factor = readFirstNumber(typeTags, bb)
                if (factor != null) {
                    val baseBpm = 120.0
                    val bpm = factor * baseBpm

                    Log.d("OSC-IN", "tempoFactor=$factor bpm=$bpm")

                    if (bpm > 0) {
                        clock.handle(ClockEvent.ExternalBpm(bpm))
                    }
                }
            }

            // all other messages intentionally ignored
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun readFirstNumber(typeTags: String, bb: ByteBuffer): Double? {
        for (c in typeTags.drop(1)) {
            when (c) {
                'f' -> if (bb.remaining() >= 4) return bb.float.toDouble()
                'i' -> if (bb.remaining() >= 4) return bb.int.toDouble()
                'd' -> if (bb.remaining() >= 8) return bb.double
            }
        }
        return null
    }

    private fun readOscString(bb: ByteBuffer): String? {
        val start = bb.position()
        while (bb.hasRemaining()) {
            if (bb.get() == 0.toByte()) {
                val end = bb.position() - 1
                val len = end - start

                val bytes = ByteArray(len)
                bb.position(start)
                bb.get(bytes)
                bb.position(end + 1)

                while (bb.position() % 4 != 0 && bb.hasRemaining()) bb.get()
                return String(bytes)
            }
        }
        return null
    }
}
