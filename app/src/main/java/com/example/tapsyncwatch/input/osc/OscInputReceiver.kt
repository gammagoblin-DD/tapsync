package com.example.tapsyncwatch.input.osc

import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OscInputReceiver(
    private val clock: Clock,
    private val port: Int = 7000
) {

    private var socket: DatagramSocket? = null
    private var running = false

    fun start() {
        if (running) return
        running = true

        socket = DatagramSocket(port)

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)

            while (running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet.data.copyOf(packet.length))
                } catch (_: Exception) {
                    // OSC darf App niemals crashen
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
    // OSC → ClockEvent
    // =====================================================

    private fun handlePacket(data: ByteArray) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        val address = readOscString(bb) ?: return
        val typeTags = readOscString(bb) ?: return

        when (address) {

            // -------------------------------------------------
            // Resolume → BPM (Float)
            // /composition/tempocontroller/tempo
            // -------------------------------------------------
            "/composition/tempocontroller/tempo" -> {
                if (typeTags == ",f" && bb.remaining() >= 4) {
                    val bpm = bb.float.toDouble()
                    clock.handle(
                        ClockEvent.ExternalBpm(bpm)
                    )
                }
            }

            // -------------------------------------------------
            // Resolume → Tap
            // /composition/tempocontroller/tempotap
            // -------------------------------------------------
            "/composition/tempocontroller/tempotap" -> {
                clock.handle(
                    ClockEvent.Tap(
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }

            // -------------------------------------------------
            // Resolume → Multiply / Divide
            // -------------------------------------------------
            "/composition/tempocontroller/tempo/multiply" -> {
                clock.handle(ClockEvent.Multiply)
            }

            "/composition/tempocontroller/tempo/divide" -> {
                clock.handle(ClockEvent.Divide)
            }

            // -------------------------------------------------
            // Resolume → Resync
            // -------------------------------------------------
            "/composition/tempocontroller/resync" -> {
                clock.handle(ClockEvent.Resync)
            }

            // -------------------------------------------------
            // Resolume → Nudge (Phase 3 – unverändert)
            // -------------------------------------------------
            "/composition/tempocontroller/tempopush" -> {
                clock.handle(ClockEvent.Nudge.RightStart)
            }

            "/composition/tempocontroller/tempopull" -> {
                clock.handle(ClockEvent.Nudge.LeftStart)
            }

            "/composition/tempocontroller/tempo/release" -> {
                clock.handle(ClockEvent.Nudge.Stop)
            }
        }
    }

    // =====================================================
    // OSC String Parser (RFC-konform, 4-Byte aligned)
    // =====================================================

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

                // Padding auf 4-Byte-Boundary
                while (bb.position() % 4 != 0 && bb.hasRemaining()) {
                    bb.get()
                }

                return String(bytes)
            }
        }
        return null
    }
}
