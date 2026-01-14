package com.example.tapsyncwatch.domain.externalclock

enum class TransportState {
    STOPPED,
    PLAYING,
    PAUSED
}

data class ExternalClockState(
    val bpm: Double = 120.0,
    val phase: Double = 0.0,
    val lastAuthoritativeTimeNs: Long = 0L,
    val transport: TransportState = TransportState.PLAYING
)
