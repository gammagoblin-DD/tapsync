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
    private var nudgeJob: Job? = null

    private val _state = MutableStateFlow(
        ClockState(
            bpm = 0.0,
            phase = 0.0,
            isRunning = false
        )
    )
    val state: StateFlow<ClockState> = _state.asStateFlow()

    fun handle(event: ClockEvent) {
        when (event) {

            is ClockEvent.Tick -> Unit

            is ClockEvent.Tap -> {
                oscSender.sendInt(
                    "/composition/tempocontroller/tempotap",
                    1
                )
            }

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

            ClockEvent.Nudge.RightStart ->
                startNudge("/composition/tempocontroller/tempopush")

            ClockEvent.Nudge.LeftStart ->
                startNudge("/composition/tempocontroller/tempopull")

            ClockEvent.Nudge.Stop ->
                stopNudge()

            is ClockEvent.ExternalBpm -> {
                _state.value = _state.value.copy(
                    bpm = event.bpm.toDouble(),
                    isRunning = true
                )
            }
        }
    }

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
