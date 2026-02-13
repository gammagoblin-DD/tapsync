package com.example.tapsyncwatch.domain.transport

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

enum class TransportType {
    TAP,
    RESYNC,
    MULTIPLY,
    DIVIDE,
    NUDGE_START,
    NUDGE_STOP
}

/**
 * Suppresses fast "echo" events that we sent ourselves (Watch → Host → Watch).
 *
 * Config is runtime-adjustable via SettingsStore.
 */
object TransportEchoGuard {

    @Volatile private var enabled: Boolean = true
    @Volatile private var windowMs: Long = 350L

    @Volatile private var ruleTap: Boolean = true
    @Volatile private var ruleResync: Boolean = true
    @Volatile private var ruleMultiplyDivide: Boolean = true
    @Volatile private var ruleNudge: Boolean = true

    private val lastSentMs = ConcurrentHashMap<TransportType, Long>()

    fun configure(
        enabled: Boolean,
        windowMs: Long,
        ruleTap: Boolean,
        ruleResync: Boolean,
        ruleMultiplyDivide: Boolean,
        ruleNudge: Boolean
    ) {
        this.enabled = enabled
        this.windowMs = windowMs.coerceIn(0L, 3000L)
        this.ruleTap = ruleTap
        this.ruleResync = ruleResync
        this.ruleMultiplyDivide = ruleMultiplyDivide
        this.ruleNudge = ruleNudge
    }

    fun markSent(type: TransportType, nowMs: Long = SystemClock.elapsedRealtime()) {
        lastSentMs[type] = nowMs
    }

    fun shouldSuppress(type: TransportType, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!enabled) return false
        if (!isRuleEnabled(type)) return false
        val last = lastSentMs[type] ?: return false
        return (nowMs - last) in 0..windowMs
    }

    private fun isRuleEnabled(type: TransportType): Boolean = when (type) {
        TransportType.TAP -> ruleTap
        TransportType.RESYNC -> ruleResync
        TransportType.MULTIPLY, TransportType.DIVIDE -> ruleMultiplyDivide
        TransportType.NUDGE_START, TransportType.NUDGE_STOP -> ruleNudge
    }
}
