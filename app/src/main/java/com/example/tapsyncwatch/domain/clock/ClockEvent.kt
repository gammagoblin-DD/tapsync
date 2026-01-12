package com.example.tapsyncwatch.domain.clock

sealed interface ClockEvent {

    data class Tap(val nowNs: Long) : ClockEvent

    object Multiply : ClockEvent
    object Divide : ClockEvent

    data class NudgeStart(val direction: Int) : ClockEvent
    object NudgeEnd : ClockEvent

    data class Resync(val nowNs: Long) : ClockEvent
    data class Tick(val nowNs: Long) : ClockEvent
}
