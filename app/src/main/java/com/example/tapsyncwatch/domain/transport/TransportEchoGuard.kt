package com.example.tapsyncwatch.domain.transport

import java.util.concurrent.ConcurrentHashMap

enum class TransportType {
    TAP,
    RESYNC,
    MULTIPLY,
    DIVIDE,
    NUDGE_START,
    NUDGE_STOP
}

object TransportEchoGuard {

    // Wie lange wir "eigene" Rückläufer ignorieren (Watch -> Resolume -> Watch)
    private const val WINDOW_MS = 350L

    private val lastSentMs = ConcurrentHashMap<TransportType, Long>()

    fun markSent(type: TransportType, nowMs: Long = System.currentTimeMillis()) {
        lastSentMs[type] = nowMs
    }

    fun shouldSuppress(type: TransportType, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastSentMs[type] ?: return false
        return (nowMs - last) in 0..WINDOW_MS
    }
}
