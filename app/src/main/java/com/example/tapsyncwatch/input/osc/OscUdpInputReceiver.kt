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

/**
 * EINZIGER erlaubter OSC-IN-Endpunkt.
 *
 * Resolume ist MASTER:
 * - OSC-IN verarbeitet NUR BPM-Feedback
 * - Steuer-Events dürfen NICHT zurück in die Clock (Echo-Schutz)
 */
class OscUdpInputReceiver(
    private val clock: Clock,
    private val port: Int = 7000
) {

    // ------------------------------------------------------------------
    // ⚠️ LEGACY-KONSTRUKTOR (DARF NIEMALS AUFGERUFEN WERDEN)
    // ------------------------------------------------------------------
    @Deprecated(
        message = "Legacy constructor kept only to satisfy a stale compiler resolution. DO NOT USE.",
        level = DeprecationLevel.WARNING
    )
    constructor(
        oscSender: OscOutputSender? = null,
        onBpmChanged: ((Float) -> Unit)? = null
    ) : this(
        clock = throw IllegalStateException(
            "Legacy OscUdpInputReceiver constructor must never be called"
        )
    )

    // ------------------------------------------------------------------
    // Runtime State
    // ------------------------------------------------------------------

    @Volatile
    private var running = false

    private var socket: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true

        socket = DatagramSocket(port)

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(2048)

            while (true) {
                if (!running) break

                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet.data.copyOf(packet.length))
                } catch (_: Exception) {
                    if (!running) break
                    // OSC darf die App niemals crashen
                }
            }

            // expliziter, erreichbarer Cleanup-Pfad
            socket?.close()
            socket = null
            running = false
        }
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
    }

    // =====================================================
    // OSC → ClockEvent  (NUR BPM-FEEDBACK!)
    // =====================================================

    private fun handlePacket(data: ByteArray) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        val header = readOscString(bb) ?: return

        // =================================================
        // OSC BUNDLE SUPPORT (Resolume sendet Bundles)
        // =================================================
        if (header == "#bundle") {
            if (bb.remaining() < 8) return
            bb.long // timetag ignorieren

            while (bb.remaining() >= 4) {
                val size = bb.int
                if (size <= 0 || size > bb.remaining()) break

                val msgBytes = ByteArray(size)
                bb.get(msgBytes)

                // Rekursiv einzelne Messages verarbeiten
                handlePacket(msgBytes)
            }
            return
        }

        // =================================================
        // NORMALE OSC MESSAGE
        // =================================================
        val address = header
        val typeTags = readOscString(bb) ?: return

        when (address) {

            // -------------------------------------------------
            // Resolume → TEMPO (normiert 0.0–1.0)
            // EINZIGER erlaubter Rückkanal
            // -------------------------------------------------
            "/composition/tempocontroller/tempo" -> {
                if (bb.remaining() >= 4) {

                    val norm = when (typeTags) {
                        ",f" -> bb.float.toDouble()
                        ",i" -> bb.int.toDouble()
                        else -> return
                    }.coerceIn(0.0, 1.0)

                    // 0.0–1.0 → 20–500 BPM
                    val bpm = 20.0 + norm * 480.0

                    clock.handle(
                        ClockEvent.ExternalBpm(bpm)
                    )
                }
            }

            // -------------------------------------------------
            // ALLE ANDEREN OSC-PFADE IGNORIEREN (Echo-Schutz)
            // -------------------------------------------------
            else -> Unit
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

                while (bb.position() % 4 != 0 && bb.hasRemaining()) {
                    bb.get()
                }

                return String(bytes)
            }
        }
        return null
    }
}
