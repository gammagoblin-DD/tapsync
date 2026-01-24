package com.example.tapsyncwatch.domain.action

import com.example.tapsyncwatch.presentation.network.OscSender
import kotlinx.coroutines.*

class ActionEngine(
    private val scope: CoroutineScope
) {

    // =====================================================
    // HELFER: Momentary INT Button (1 → 0)
    // =====================================================
    private suspend fun momentaryInt(path: String) {
        OscSender.sendInt(path, 1)
        delay(40)
        OscSender.sendInt(path, 0)
    }

    // =====================================================
    // TAP
    // ALT: 1 → delay → 0
    // =====================================================
    fun tap() {
        scope.launch {
            momentaryInt(
                "/composition/tempocontroller/tempotap"
            )
        }
    }

    // =====================================================
    // MULTIPLY ×2
    // ALT: INT = 1, KEIN 0
    // =====================================================
    fun multiply() {
        scope.launch {
            OscSender.sendInt(
                "/composition/tempocontroller/tempo/multiply",
                1
            )
        }
    }

    // =====================================================
    // DIVIDE ÷2
    // ALT: INT = 1, KEIN 0
    // =====================================================
    fun divide() {
        scope.launch {
            OscSender.sendInt(
                "/composition/tempocontroller/tempo/divide",
                1
            )
        }
    }

    // =====================================================
    // RESYNC
    // ALT: 1 → delay → 0
    // =====================================================
    fun resync() {
        scope.launch {
            momentaryInt(
                "/composition/tempocontroller/resync"
            )
        }
    }

    // =====================================================
    // NUDGE PUSH  (Tempo +)
    // ALT: Button HOLD
    // =====================================================
    fun nudgePushStart() {
        scope.launch {
            OscSender.sendInt(
                "/composition/tempocontroller/tempopush",
                1
            )
        }
    }

    fun nudgePushEnd() {
        scope.launch {
            OscSender.sendInt(
                "/composition/tempocontroller/tempopush",
                0
            )
        }
    }

    // =====================================================
    // NUDGE PULL  (Tempo −)
    // ALT: Button HOLD
    // =====================================================
    fun nudgePullStart() {
        scope.launch {
            OscSender.sendInt(
                "/composition/tempocontroller/tempopull",
                1
            )
        }
    }

    fun nudgePullEnd() {
        scope.launch {
            OscSender.sendInt(
                "/composition/tempocontroller/tempopull",
                0
            )
        }
    }
}
