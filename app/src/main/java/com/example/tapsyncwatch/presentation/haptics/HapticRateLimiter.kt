package com.example.tapsyncwatch.presentation.haptics

class HapticRateLimiter(
    private val minIntervalMs: Long
) {
    private var lastMs: Long = 0

    fun allow(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - lastMs < minIntervalMs) return false
        lastMs = nowMs
        return true
    }
}
