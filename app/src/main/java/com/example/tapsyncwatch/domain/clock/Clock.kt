package com.example.tapsyncwatch.domain.clock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Clock(
    initialBpm: Double = 120.0,
    initialPhase: Double = 0.0
) {

    private var bpm: Double = initialBpm
    private var phase: Double = initialPhase

    private val tapIntervals = ArrayDeque<Long>(6)
    private var lastTapTimestamp: Long? = null
    private var externalCooldownMs: Long = 0L

    private val MIN_BPM = 20.0
    private val MAX_BPM = 500.0

    private val EXTERNAL_BPM_DEADZONE = 0.5
    private val EXTERNAL_BPM_MIN_INTERVAL_MS = 200L
    private val EXTERNAL_BPM_ALPHA = 0.2

    private val _stateFlow = MutableStateFlow(
        ClockState(
            bpm = bpm,
            phase = phase,
            isRunning = true
        )
    )

    val stateFlow: StateFlow<ClockState> = _stateFlow

    fun handle(event: ClockEvent) {
        when (event) {

            is ClockEvent.Tick -> {
                advancePhase(event.deltaMs)
                externalCooldownMs =
                    max(0L, externalCooldownMs - event.deltaMs)
            }

            is ClockEvent.Tap -> handleTap(event.timestampMs)

            is ClockEvent.ExternalBpm -> applyExternalBpm(event.bpm)

            ClockEvent.Multiply -> setBpmSafely(bpm * 2.0)
            ClockEvent.Divide -> setBpmSafely(bpm / 2.0)

            ClockEvent.Resync -> phase = 0.0

            ClockEvent.Nudge.LeftStart,
            ClockEvent.Nudge.RightStart,
            ClockEvent.Nudge.Stop -> {
                // Phase 3 – bewusst leer
            }
        }

        publishState()
    }

    private fun handleTap(timestampMs: Long) {
        lastTapTimestamp?.let { last ->
            val interval = timestampMs - last

            if (interval > 2000) {
                tapIntervals.clear()
                lastTapTimestamp = timestampMs
                return
            }

            if (interval in 80..2000) {
                tapIntervals.addLast(interval)
                if (tapIntervals.size > 6) {
                    tapIntervals.removeFirst()
                }

                if (tapIntervals.size >= 2) {
                    val avgInterval = tapIntervals.average()
                    val newBpm = 60_000.0 / avgInterval
                    setBpmSafely(newBpm)
                    phase = 0.0
                }
            }
        }
        lastTapTimestamp = timestampMs
    }

    private fun advancePhase(deltaMs: Long) {
        if (deltaMs <= 0L) return
        val beatsPerMs = bpm / 60_000.0
        phase = (phase + deltaMs * beatsPerMs) % 1.0
        if (phase < 0.0) phase += 1.0
    }

    private fun applyExternalBpm(externalBpm: Double) {
        if (externalBpm !in MIN_BPM..MAX_BPM) return
        if (externalCooldownMs > 0L) return

        val diff = externalBpm - bpm
        if (abs(diff) < EXTERNAL_BPM_DEADZONE) return

        val filtered = bpm + diff * EXTERNAL_BPM_ALPHA
        setBpmSafely(filtered)

        externalCooldownMs = EXTERNAL_BPM_MIN_INTERVAL_MS
    }

    private fun setBpmSafely(value: Double) {
        bpm = min(MAX_BPM, max(MIN_BPM, value))
    }

    private fun publishState() {
        _stateFlow.value = ClockState(
            bpm = bpm,
            phase = phase,
            isRunning = true
        )
    }

    val state: ClockState
        get() = _stateFlow.value
}
