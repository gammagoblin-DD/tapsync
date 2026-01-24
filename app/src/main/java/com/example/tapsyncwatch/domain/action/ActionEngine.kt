package com.example.tapsyncwatch.domain.action

import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActionEngine(
    private val scope: CoroutineScope,
    private val osc: OscOutputSender
) {

    // -------------------------------------------------
    // Helfer: Momentary Button (1 → 0)
    // -------------------------------------------------
    private suspend fun momentaryInt(path: String) {
        osc.sendInt(path, 1)
        delay(40)
        osc.sendInt(path, 0)
    }

    // -------------------------------------------------
    // TAP
    // -------------------------------------------------
    fun tap() {
        scope.launch {
            momentaryInt(
                "/composition/tempocontroller/tempotap"
            )
        }
    }

    // -------------------------------------------------
    // MULTIPLY ×2
    // -------------------------------------------------
    fun multiply() {
        scope.launch {
            osc.sendInt(
                "/composition/tempocontroller/tempo/multiply",
                1
            )
        }
    }

    // -------------------------------------------------
    // DIVIDE ÷2
    // -------------------------------------------------
    fun divide() {
        scope.launch {
            osc.sendInt(
                "/composition/tempocontroller/tempo/divide",
                1
            )
        }
    }

    // -------------------------------------------------
    // RESYNC
    // -------------------------------------------------
    fun resync() {
        scope.launch {
            momentaryInt(
                "/composition/tempocontroller/resync"
            )
        }
    }

    // -------------------------------------------------
    // NUDGE PUSH
    // -------------------------------------------------
    fun nudgePushStart() {
        scope.launch {
            osc.sendInt(
                "/composition/tempocontroller/tempopush",
                1
            )
        }
    }

    fun nudgePushEnd() {
        scope.launch {
            osc.sendInt(
                "/composition/tempocontroller/tempopush",
                0
            )
        }
    }

    // -------------------------------------------------
    // NUDGE PULL
    // -------------------------------------------------
    fun nudgePullStart() {
        scope.launch {
            osc.sendInt(
                "/composition/tempocontroller/tempopull",
                1
            )
        }
    }

    fun nudgePullEnd() {
        scope.launch {
            osc.sendInt(
                "/composition/tempocontroller/tempopull",
                0
            )
        }
    }
}
