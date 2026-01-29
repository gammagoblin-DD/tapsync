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
    private var tickJob: Job? = null

    private val _mode = MutableStateFlow(ClockMode.EXTERNAL)
    val mode: StateFlow<ClockMode> = _mode.asStateFlow()

    private val _state = MutableStateFlow(
        ClockState(
            bpm = 0.0,
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

    fun setMode(mode: ClockMode) {
        _mode.value = mode

        if (mode == ClockMode.EXTERNAL) {
            stopInternalClock()
        } else {
            val bpm = _state.value.bpm.takeIf { it > 0.0 } ?: 120.0
            startInternalClock(bpm)
        }
    }

    private fun startInternalClock(bpm: Double) {
        stopInternalClock()

        val intervalMs = (60000.0 / bpm).toLong()

        tickJob = scope.launch {
            while (isActive) {
                fireDownbeat()
                delay(intervalMs)
            }
        }

        _state.value = _state.value.copy(
            bpm = bpm,
            isRunning = true
        )
    }

    private fun stopInternalClock() {
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(isRunning = false)
    }

    private fun fireDownbeat() {
        _visualState.value = _visualState.value.copy(
            downbeatId = System.nanoTime()
        )
    }

    fun handle(event: ClockEvent) {
        when (event) {

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

                if (_mode.value == ClockMode.INTERNAL && tickJob == null) {
                    startInternalClock(120.0)
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

            is ClockEvent.ExternalBpm -> {

                if (_mode.value == ClockMode.INTERNAL) {
                    return
                }

                stopInternalClock()
                _mode.value = ClockMode.EXTERNAL

                _state.value = _state.value.copy(
                    bpm = event.bpm,
                    isRunning = true
                )

                fireDownbeat()
            }

            is ClockEvent.Tick -> Unit
        }
    }
}
