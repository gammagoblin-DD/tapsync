package com.example.tapsyncwatch.domain.action

import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActionEngine(
    private val scope: CoroutineScope,
    private val osc: OscOutputSender
) {

    // -------------------------------------------------
    // BPM (AUSSCHLIESSLICH extern – Resolume ist Master)
    // -------------------------------------------------

    private val _bpm = MutableStateFlow(0f)
    val bpm: StateFlow<Float> = _bpm.asStateFlow()

    /** letzter gültiger BPM-Wert (UI-Hold, keine Logik) */
    private var lastValidBpm: Float? = null

    /** Telemetrie: Zeitpunkt des letzten empfangenen BPM */
    private val _lastBpmAt = MutableStateFlow(0L)
    val lastBpmAt: StateFlow<Long> = _lastBpmAt.asStateFlow()

    /** Wird von Clock / ExternalBpm aufgerufen */
    fun setExternalBpm(bpm: Double) {
        val value = bpm.toFloat()
        _bpm.value = value
        lastValidBpm = value
        _lastBpmAt.value = System.currentTimeMillis()
    }

    /** Für UI: letzter gültiger BPM oder null */
    fun getLastValidBpm(): Float? = lastValidBpm

    // -------------------------------------------------
    // Helfer: Momentary Button (1 → 0)
    // -------------------------------------------------

    private suspend fun momentaryInt(path: String) {
        osc.sendInt(path, 1)
        delay(40)
        osc.sendInt(path, 0)
    }

    // -------------------------------------------------
    // ACTIONS (SEND ONLY)
    // -------------------------------------------------

    fun tap() {
        scope.launch {
            momentaryInt("/composition/tempocontroller/tempotap")
        }
    }

    fun multiply() {
        scope.launch {
            osc.sendInt("/composition/tempocontroller/tempo/multiply", 1)
        }
    }

    fun divide() {
        scope.launch {
            osc.sendInt("/composition/tempocontroller/tempo/divide", 1)
        }
    }

    fun resync() {
        scope.launch {
            momentaryInt("/composition/tempocontroller/resync")
        }
    }

    fun nudgePushStart() {
        scope.launch {
            osc.sendInt("/composition/tempocontroller/tempopush", 1)
        }
    }

    fun nudgePushEnd() {
        scope.launch {
            osc.sendInt("/composition/tempocontroller/tempopush", 0)
        }
    }

    fun nudgePullStart() {
        scope.launch {
            osc.sendInt("/composition/tempocontroller/tempopull", 1)
        }
    }

    fun nudgePullEnd() {
        scope.launch {
            osc.sendInt("/composition/tempocontroller/tempopull", 0)
        }
    }
}
