package com.example.tapsyncwatch.domain.clock

data class ClockVisualState(
    val downbeatId: Long = 0L,

    // Transport feedback timestamps (UI-only)
    val lastTapMs: Long? = null,
    val lastResyncMs: Long? = null,
    val lastMultiplyMs: Long? = null,
    val lastDivideMs: Long? = null,

    // Continuous transport state
    val nudgeActive: Boolean = false
)
