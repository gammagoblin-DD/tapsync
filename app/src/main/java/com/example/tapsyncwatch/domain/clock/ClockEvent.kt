package com.example.tapsyncwatch.domain.clock

sealed interface ClockEvent {

    // =========================
    // 1️⃣ TAP (User tippt)
    // =========================
    data class Tap(
        val time: Long
    ) : ClockEvent


    // =========================
    // 2️⃣ EXTERNES BPM
    // (z. B. von Resolume)
    // =========================
    data class ExternalBpm(
        val bpm: Double,
        val time: Long
    ) : ClockEvent


    // =========================
    // 3️⃣ ZEIT-TICK
    // (läuft ständig)
    // =========================
    data class Tick(
        val time: Long
    ) : ClockEvent


    // =========================
    // 4️⃣ TEMPO-OPERATIONEN
    // =========================
    object Multiply : ClockEvent
    object Divide : ClockEvent


    // =========================
    // 5️⃣ RESYNC
    // =========================
    object Resync : ClockEvent


    // =========================
    // 6️⃣ NUDGE (Feinjustierung)
    // =========================
    sealed interface Nudge : ClockEvent {

        // Beat minimal nach links schieben
        object LeftStart : Nudge

        // Beat minimal nach rechts schieben
        object RightStart : Nudge

        // Nudge stoppen
        object Stop : Nudge
    }
}
