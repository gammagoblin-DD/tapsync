package com.example.tapsyncwatch.domain.clock

data class ClockVisualState(
    val downbeatId: Long = 0L,
    val lastMultiplyMs: Long? = null,
    val lastDivideMs: Long? = null,
    val nudgeActive: Boolean = false
)
