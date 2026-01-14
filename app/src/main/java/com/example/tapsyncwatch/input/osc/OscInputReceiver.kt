package com.example.tapsyncwatch.input.osc

import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockEvent
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class OscInputReceiver(
    private val clock: Clock,
    private val port: Int = 7000
) {

    private var socket: DatagramSocket? = null
    private var running = false

    // =========================
    // 1️⃣ Start listening
    // =========================
    fun start() {
        if (running) return

        running = true

        thread(start = true, isDaemon = true) {
            socket = DatagramSocket(port)

            val buffer = ByteArray(1024)

            while (running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    handlePacket(packet)

                } catch (e: Exception) {
                    // Fehler ignorieren → Receiver darf nie App killen
                }
            }
        }
    }

    // =========================
    // 2️⃣ Stop listening
    // =========================
    fun stop() {
        running = false
        socket?.close()
        socket = null
    }

    // =========================
    // 3️⃣ Paket verarbeiten
    // =========================
    private fun handlePacket(packet: DatagramPacket) {

        val data = packet.data
        val length = packet.length

        // Wir lesen nur rohe Bytes
        // Keine OSC-Library, keine Magie
        val message = String(data, 0, length)

        // Wir interessieren uns NUR für Tempo-Nachrichten
        if (!message.contains("/composition/tempocontroller/tempo")) {
            return
        }

        // BPM aus den Bytes lesen
        val bpm = extractFloat(data, length) ?: return

        // Grober Schutz vor Unsinn
        if (bpm < 10f || bpm > 600f) return

        // Event an die Clock schicken
        clock.handle(
            ClockEvent.ExternalBpm(
                bpm = bpm.toDouble(),
                time = System.currentTimeMillis()
            )
        )
    }

    // =========================
    // 4️⃣ Float aus Bytes lesen
    // =========================
    private fun extractFloat(data: ByteArray, length: Int): Float? {
        if (length < 4) return null

        return try {
            ByteBuffer
                .wrap(data, length - 4, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .float
        } catch (e: Exception) {
            null
        }
    }
}
