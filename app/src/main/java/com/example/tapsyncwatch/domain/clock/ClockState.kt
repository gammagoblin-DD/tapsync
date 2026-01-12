package com.example.tapsyncwatch.domain.clock

data class ClockState(
    val bpm: Double = 120.0,
    val phase: Double = 0.0,          // 0.0 .. <1.0
    val isLocked: Boolean = false,
    val lastBeatTimeNs: Long = 0L
)
