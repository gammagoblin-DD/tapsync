package com.example.tapsyncwatch.domain.clock

data class ClockState(
    val bpm: Double,
    val phase: Double,
    val isRunning: Boolean
)
