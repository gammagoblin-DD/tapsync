package com.example.tapsyncwatch.domain.clock

data class ClockVisualState(
    val downbeatPulse: Boolean = false,
    val lastMultiplyMs: Long? = null,
    val lastDivideMs: Long? = null,
    val nudgeActive: Boolean = false
)
