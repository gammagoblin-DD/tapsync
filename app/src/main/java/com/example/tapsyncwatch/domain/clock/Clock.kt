package com.example.tapsyncwatch.domain.clock

import android.os.SystemClock
import android.util.Log
import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Clock(
    private val oscSender: OscOutputSender
) {

    /* ================= DEBUG ================= */

    private val DEBUG_CLOCK = true

    private val TAG_CLOCK = "ClockTiming"

    /* ================= CORE ================= */

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var nudgeActivePath: String? = null
    private var tickJob: Job? = null

    private var enabled: Boolean = true

    /** 🔒 Absoluter Phasenanker (Resync-Zeitpunkt) */
    private var phaseAnchorNs: Long? = null

    private val _mode = MutableStateFlow(ClockMode.EXTERNAL)
    val mode: StateFlow<ClockMode> = _mode.asStateFlow()

    private val _state = MutableStateFlow(
        ClockState(
            bpm = 120.0,
            phase = 0.0,
            isRunning = false
        )
    )
    val state: StateFlow<ClockState> = _state.asStateFlow()

    private val _visualState = MutableStateFlow(
        ClockVisualState(
            downbeatId = 0L
        )
    )
    val visualState: StateFlow<ClockVisualState> = _visualState.asStateFlow()

    /* ================= ENABLE ================= */

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!enabled) {
            stopInternalClock()
            phaseAnchorNs = null
        }
    }

    /* ================= MODE ================= */

    fun setMode(mode: ClockMode) {
        _mode.value = mode

        if (!enabled) {
            stopInternalClock()
            return
        }

        // Mode switch does NOT generate beats
        if (mode == ClockMode.EXTERNAL) {
            stopInternalClock()
        }
    }

    /* ================= INTERNAL CLOCK ================= */

    private fun startInternalClock() {
        val bpm = _state.value.bpm
        val anchor = phaseAnchorNs ?: return

        stopInternalClock()

        val intervalNs = (60_000_000_000.0 / bpm).toLong()

        if (DEBUG_CLOCK) {
            Log.d(TAG_CLOCK, "START internal clock bpm=$bpm anchor=$anchor")
        }

        tickJob = scope.launch {
            var beatIndex = 1L

            while (isActive) {
                val targetTime = anchor + beatIndex * intervalNs

                while (true) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    val remainingNs = targetTime - now

                    if (remainingNs <= 0) break

                    if (remainingNs > 2_000_000) {
                        delay(1)
                    } else {
                        while (SystemClock.elapsedRealtimeNanos() < targetTime) {
                            Thread.onSpinWait()
                        }
                        break
                    }
                }

                val actualTime = SystemClock.elapsedRealtimeNanos()
                val driftUs = (actualTime - targetTime) / 1_000

                if (DEBUG_CLOCK) {
                    Log.d(TAG_CLOCK, "Beat=$beatIndex drift=${driftUs}µs")
                }

                fireDownbeat()
                beatIndex++
            }
        }

        _state.value = _state.value.copy(isRunning = true)
    }

    private fun stopInternalClock() {
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(isRunning = false)
    }

    private fun fireDownbeat() {
        _visualState.value = _visualState.value.copy(
            downbeatId = SystemClock.elapsedRealtimeNanos()
        )
    }

    /* ================= EVENTS ================= */

    fun handle(event: ClockEvent) {

        when (event) {

            /* =====================================================
             * TRANSPORT EVENTS — IMMER ERLAUBT (OSC)
             * ===================================================== */

            is ClockEvent.Tap -> {
                scope.launch {
                    oscSender.sendInt(
                        "/composition/tempocontroller/tempotap",
                        1
                    )
                    delay(40)
                    oscSender.sendInt(
                        "/composition/tempocontroller/tempotap",
                        0
                    )
                }
            }

            ClockEvent.Multiply -> {
                oscSender.sendInt(
                    "/composition/tempocontroller/tempo/multiply",
                    1
                )
                _visualState.value = _visualState.value.copy(
                    lastMultiplyMs = System.currentTimeMillis()
                )
            }

            ClockEvent.Divide -> {
                oscSender.sendInt(
                    "/composition/tempocontroller/tempo/divide",
                    1
                )
                _visualState.value = _visualState.value.copy(
                    lastDivideMs = System.currentTimeMillis()
                )
            }

            ClockEvent.Nudge.RightStart -> {
                nudgeActivePath =
                    "/composition/tempocontroller/tempopush"
                oscSender.sendInt(nudgeActivePath!!, 1)
                _visualState.value = _visualState.value.copy(
                    nudgeActive = true
                )
            }

            ClockEvent.Nudge.LeftStart -> {
                nudgeActivePath =
                    "/composition/tempocontroller/tempopull"
                oscSender.sendInt(nudgeActivePath!!, 1)
                _visualState.value = _visualState.value.copy(
                    nudgeActive = true
                )
            }

            ClockEvent.Nudge.Stop -> {
                nudgeActivePath?.let { path ->
                    oscSender.sendInt(path, 0)
                }
                nudgeActivePath = null
                _visualState.value = _visualState.value.copy(
                    nudgeActive = false
                )
            }

            /* =====================================================
             * RESYNC — OSC IMMER, INTERNAL NUR WENN ENABLED
             * ===================================================== */

            ClockEvent.Resync -> {

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

                // ⛔ interne Clock nur wenn enabled
                if (!enabled) return

                phaseAnchorNs = SystemClock.elapsedRealtimeNanos()

                if (DEBUG_CLOCK) {
                    Log.d(TAG_CLOCK, "RESYNC anchor=$phaseAnchorNs")
                }

                fireDownbeat()

                if (_mode.value == ClockMode.INTERNAL) {
                    startInternalClock()
                }
            }

            /* =====================================================
             * ENGINE EVENTS — NUR INTERN
             * ===================================================== */

            is ClockEvent.ExternalBpm -> {
                _state.value = _state.value.copy(
                    bpm = event.bpm,
                    isRunning = true
                )

                if (_mode.value == ClockMode.EXTERNAL) {
                    fireDownbeat()
                }
            }

            is ClockEvent.Tick -> {
                if (!enabled) return
            }
        }
    }

}
