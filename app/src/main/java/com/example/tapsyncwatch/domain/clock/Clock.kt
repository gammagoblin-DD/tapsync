package com.example.tapsyncwatch.domain.clock

import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlinx.coroutines.*

class Clock(
    private val oscSender: OscOutputSender,
    private val onBpmChanged: (Float) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nudgeJob: Job? = null

    fun handle(event: ClockEvent) {
        when (event) {

            is ClockEvent.Tick -> Unit

            // -----------------------------
            // TAP  (NUR EIN IMPULS!)
            // -----------------------------
            is ClockEvent.Tap -> {
                oscSender.sendInt(
                    "/composition/tempocontroller/tempotap",
                    1
                )
            }

            // -----------------------------
            // MULTIPLY ×2  (INT-Button!)
            // -----------------------------
            ClockEvent.Multiply -> {
                stopNudge()
                scope.launch {
                    oscSender.sendInt(
                        "/composition/tempocontroller/tempo/multiply",
                        1
                    )
                    delay(40)
                    oscSender.sendInt(
                        "/composition/tempocontroller/tempo/multiply",
                        0
                    )
                }
            }

            // -----------------------------
            // DIVIDE ÷2  (INT-Button!)
            // -----------------------------
            ClockEvent.Divide -> {
                stopNudge()
                scope.launch {
                    oscSender.sendInt(
                        "/composition/tempocontroller/tempo/divide",
                        1
                    )
                    delay(40)
                    oscSender.sendInt(
                        "/composition/tempocontroller/tempo/divide",
                        0
                    )
                }
            }

            // -----------------------------
            // RESYNC
            // -----------------------------
            ClockEvent.Resync -> {
                stopNudge()
                scope.launch {
                    oscSender.sendInt(
                        "/composition/tempocontroller/resync",
                        1
                    )
                    delay(40)
                    oscSender.sendInt(
                        "/composition/tempocontroller/resync",
                        0
                    )
                }
            }

            // -----------------------------
            // NUDGE (diskrete Steps)
            // -----------------------------
            ClockEvent.Nudge.RightStart ->
                startNudge("/composition/tempocontroller/tempopush")

            ClockEvent.Nudge.LeftStart ->
                startNudge("/composition/tempocontroller/tempopull")

            ClockEvent.Nudge.Stop ->
                stopNudge()

            // -----------------------------
            // EXTERNAL BPM (optional)
            // -----------------------------
            is ClockEvent.ExternalBpm -> {
                onBpmChanged(event.bpm.toFloat())
            }
        }
    }

    // ============================================
    // NUDGE = STEP-BASIERT (1 → 0 pro Schritt)
    // ============================================
    private fun startNudge(path: String) {
        stopNudge()

        nudgeJob = scope.launch {
            while (isActive) {
                oscSender.sendInt(path, 1)
                delay(30)
                oscSender.sendInt(path, 0)
                delay(180)
            }
        }
    }

    private fun stopNudge() {
        nudgeJob?.cancel()
        nudgeJob = null
    }
}
