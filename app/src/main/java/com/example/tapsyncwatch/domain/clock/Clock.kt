package com.example.tapsyncwatch.domain.clock

import kotlin.math.max
import kotlin.math.min

class Clock {

    private val tapHistory = TapHistory()
    private var nudgeDirection: Int = 0

    var state: ClockState = ClockState()
        private set

    fun handle(event: ClockEvent) {
        when (event) {

            /* ---------- TAP ---------- */
            is ClockEvent.Tap -> {
                tapHistory.addTap(event.nowNs)

                if (tapHistory.isLocked()) {
                    val intervalNs = tapHistory.medianIntervalNs()
                    val bpm = 60_000_000_000.0 / intervalNs.toDouble()

                    state = state.copy(
                        bpm = bpm.coerceIn(40.0, 300.0),
                        isLocked = true,
                        lastBeatTimeNs = event.nowNs,
                        phase = 0.0
                    )
                }
            }

            /* ---------- MULTIPLY / DIVIDE ---------- */
            ClockEvent.Multiply -> {
                state = state.copy(bpm = state.bpm * 2.0)
            }

            ClockEvent.Divide -> {
                state = state.copy(bpm = state.bpm * 0.5)
            }

            /* ---------- NUDGE ---------- */
            is ClockEvent.NudgeStart -> {
                nudgeDirection = event.direction
            }

            ClockEvent.NudgeEnd -> {
                nudgeDirection = 0
            }

            /* ---------- RESYNC ---------- */
            is ClockEvent.Resync -> {
                state = state.copy(
                    phase = 0.0,
                    lastBeatTimeNs = event.nowNs
                )
            }

            /* ---------- TICK ---------- */
            is ClockEvent.Tick -> {
                if (!state.isLocked) return

                val beatDurationNs = 60_000_000_000.0 / state.bpm
                val elapsed = event.nowNs - state.lastBeatTimeNs

                var phase =
                    ((elapsed / beatDurationNs) % 1.0)

                if (nudgeDirection != 0) {
                    phase += nudgeDirection * 0.1
                }

                phase = phase.coerceIn(0.0, 1.0)

                state = state.copy(phase = phase)
            }
        }
    }
}
