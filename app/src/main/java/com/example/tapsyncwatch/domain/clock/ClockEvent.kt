
package com.example.tapsyncwatch.domain.clock

sealed interface ClockEvent {

    data class Tick(val deltaMs: Long) : ClockEvent
    data class Tap(val timestampMs: Long) : ClockEvent
    data class ExternalBpm(val bpm: Double) : ClockEvent

    object Multiply : ClockEvent
    object Divide : ClockEvent
    object Resync : ClockEvent

    sealed interface Nudge : ClockEvent {
        object LeftStart : Nudge
        object RightStart : Nudge
        object Stop : Nudge
    }
}

