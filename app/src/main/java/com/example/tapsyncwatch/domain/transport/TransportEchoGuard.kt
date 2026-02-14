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
 * New behavior (Festival-stabil tuning):
 * - The Settings "Window" stays a single value, but is treated as BASE window.
 * - Tap/Resync get a shorter auto-window (roughly half of base, clamped to 80–120ms)
 * - Multiply/Divide + Nudge use the base window
 *
 * This gives tighter feel on Tap/Resync while still blocking the nastier echo loops.
 */
object TransportEchoGuard {

    @Volatile private var enabled: Boolean = true

    // BASE window comes from Settings UI (existing slider)
    @Volatile private var baseWindowMs: Long = 350L

    // Derived per-group windows (no Settings changes needed)
    @Volatile private var tapResyncWindowMs: Long = 120L
    @Volatile private var multDivWindowMs: Long = 350L
    @Volatile private var nudgeWindowMs: Long = 350L

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
        this.baseWindowMs = windowMs.coerceIn(0L, 3000L)

        // --- auto-derive per-group windows from base ---
        // Tap/Resync: short (80–120ms) unless base is 0
        this.tapResyncWindowMs = deriveTapResyncWindow(this.baseWindowMs)

        // Mult/Div + Nudge: keep base window (stronger suppression where echoes feel worse)
        this.multDivWindowMs = this.baseWindowMs
        this.nudgeWindowMs = this.baseWindowMs

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
        val win = windowFor(type)

        if (win <= 0L) return false
        return (nowMs - last) in 0..win
    }

    private fun windowFor(type: TransportType): Long = when (type) {
        TransportType.TAP, TransportType.RESYNC -> tapResyncWindowMs
        TransportType.MULTIPLY, TransportType.DIVIDE -> multDivWindowMs
        TransportType.NUDGE_START, TransportType.NUDGE_STOP -> nudgeWindowMs
    }

    private fun deriveTapResyncWindow(base: Long): Long {
        if (base <= 0L) return 0L
        // half base feels right; clamp to “musical” range
        val half = (base * 0.5f).toLong()
        return half.coerceIn(80L, 120L)
    }

    private fun isRuleEnabled(type: TransportType): Boolean = when (type) {
        TransportType.TAP -> ruleTap
        TransportType.RESYNC -> ruleResync
        TransportType.MULTIPLY, TransportType.DIVIDE -> ruleMultiplyDivide
        TransportType.NUDGE_START, TransportType.NUDGE_STOP -> ruleNudge
    }
}
