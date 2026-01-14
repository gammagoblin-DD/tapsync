package com.example.tapsyncwatch.domain.clock

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Clock {

    // =========================
    // 1️⃣ Aktueller Zustand
    // =========================

    private var bpm: Double = 120.0
    private var phase: Double = 0.0
    private var lastTickTime: Long = 0L
    private var lastExternalUpdateTime: Long = 0L

    // =========================
    // 2️⃣ Feste BPM-Grenzen
    // =========================

    // Resolume-kompatible Grenzen
    private val MIN_BPM = 20.0
    private val MAX_BPM = 500.0

    // =========================
    // 3️⃣ External BPM Filter
    // =========================

    private val EXTERNAL_BPM_DEADZONE = 0.5
    private val EXTERNAL_BPM_MIN_INTERVAL = 200L
    private val EXTERNAL_BPM_ALPHA = 0.2

    // =========================
    // 4️⃣ Zentrale Event-Verarbeitung
    // =========================

    fun handle(event: ClockEvent) {
        when (event) {

            is ClockEvent.Tap -> {
                // Tap-Logik bleibt unverändert
            }

            is ClockEvent.ExternalBpm -> {
                handleExternalBpm(event.bpm, event.time)
            }

            is ClockEvent.Tick -> {
                updatePhase(event.time)
            }

            is ClockEvent.Multiply -> {
                setBpmSafely(bpm * 2.0)
            }

            is ClockEvent.Divide -> {
                setBpmSafely(bpm / 2.0)
            }

            is ClockEvent.Resync -> {
                phase = 0.0
            }

            is ClockEvent.Nudge.LeftStart -> { }
            is ClockEvent.Nudge.RightStart -> { }
            is ClockEvent.Nudge.Stop -> { }
        }
    }

    // =========================
    // 5️⃣ External BPM (gefiltert)
    // =========================

    private fun handleExternalBpm(externalBpm: Double, time: Long) {

        // Resolume-kompatible Plausibilität
        if (externalBpm < MIN_BPM || externalBpm > MAX_BPM) return

        // Rate-Limit
        if (time - lastExternalUpdateTime < EXTERNAL_BPM_MIN_INTERVAL) return

        val diff = externalBpm - bpm

        // Deadzone
        if (abs(diff) < EXTERNAL_BPM_DEADZONE) return

        // Low-Pass-Filter
        val filtered = bpm + diff * EXTERNAL_BPM_ALPHA

        setBpmSafely(filtered)

        lastExternalUpdateTime = time
    }

    // =========================
    // 6️⃣ BPM sicher setzen
    // =========================

    private fun setBpmSafely(value: Double) {
        bpm = min(MAX_BPM, max(MIN_BPM, value))
    }

    // =========================
    // 7️⃣ Phase berechnen
    // =========================

    private fun updatePhase(time: Long) {
        if (lastTickTime == 0L) {
            lastTickTime = time
            return
        }

        val deltaMs = time - lastTickTime
        lastTickTime = time

        val beatsPerMs = bpm / 60000.0
        phase += deltaMs * beatsPerMs

        phase %= 1.0
    }

    // =========================
    // 8️⃣ Öffentliche Abfragen
    // =========================

    fun getBpm(): Double = bpm
    fun getPhase(): Double = phase
}
