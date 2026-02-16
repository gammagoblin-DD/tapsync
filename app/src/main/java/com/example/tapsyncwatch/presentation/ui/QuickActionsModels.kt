package com.example.tapsyncwatch.presentation.ui

/**
 * Shared models for the "Test ALL" quick-check UI.
 * Kept in its own file so the models survive even if screens move around.
 */

enum class QuickPingKind { OK, TIMEOUT, SEND_FAIL }

data class QuickPingRow(
    val index: Int,
    val name: String,
    val kind: QuickPingKind,
    val rttMs: Long? = null,
    val pongFrom: String? = null
)
