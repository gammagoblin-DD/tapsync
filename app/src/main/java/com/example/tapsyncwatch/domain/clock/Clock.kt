package com.example.tapsyncwatch.domain.clock

import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Clock(
    private val oscSender: OscOutputSender
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nudgeActivePath: String? = null

    private val _state = MutableStateFlow(
        ClockState(
            bpm = 0.0,
            phase = 0.0,
            isRunning = false
        )
    )
    val state: StateFlow<ClockState> = _state.asStateFlow()

    /* ================= VISUAL STATE (NEU) ================= */

    private val _visualState = MutableStateFlow(ClockVisualState())
    val visualState: StateFlow<ClockVisualState> = _visualState.asStateFlow()

    fun handle(event: ClockEvent) {
        when (event) {

            /* -------------------------------------------------
             * TAP — momentary (1 → 0)
             * ------------------------------------------------- */
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

            /* -------------------------------------------------
             * MULTIPLY — ONE SHOT
             * ------------------------------------------------- */
            ClockEvent.Multiply -> {
                oscSender.sendInt(
                    "/composition/tempocontroller/tempo/multiply",
                    1
                )
                _visualState.value = _visualState.value.copy(
                    lastMultiplyMs = System.currentTimeMillis()
                )
            }

            /* -------------------------------------------------
             * DIVIDE — ONE SHOT
             * ------------------------------------------------- */
            ClockEvent.Divide -> {
                oscSender.sendInt(
                    "/composition/tempocontroller/tempo/divide",
                    1
                )
                _visualState.value = _visualState.value.copy(
                    lastDivideMs = System.currentTimeMillis()
                )
            }

            /* -------------------------------------------------
             * RESYNC — momentary
             * ------------------------------------------------- */
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
            }

            /* -------------------------------------------------
             * NUDGE — HOLD SEMANTICS
             * ------------------------------------------------- */
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

            /* -------------------------------------------------
             * EXTERNAL BPM (UI only)
             * ------------------------------------------------- */
            is ClockEvent.ExternalBpm -> {
                _state.value = _state.value.copy(
                    bpm = event.bpm,
                    isRunning = true
                )

                _visualState.value = _visualState.value.copy(
                    downbeatPulse = true
                )

                scope.launch {
                    delay(50)
                    _visualState.value = _visualState.value.copy(
                        downbeatPulse = false
                    )
                }
            }


            /* -------------------------------------------------
             * TICK — intentionally ignored
             * ------------------------------------------------- */
            is ClockEvent.Tick -> Unit
        }
    }

}
