package com.example.tapsyncwatch.input.osc

import android.os.SystemClock
import android.util.Log
import com.example.tapsyncwatch.domain.transport.TransportEchoGuard
import com.example.tapsyncwatch.domain.transport.TransportFeedback
import com.example.tapsyncwatch.domain.transport.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private var remoteNudgeActivePush = false
private var remoteNudgeActivePull = false

/**
 * OSC Input (read-only):
 * - Transport events are emitted as UI feedback (REMOTE voice) and never change the clock.
 * - External BPM + confidence are UI-only signals (External BPM = information, not authority).
 * - Remote Ghost snapshot can use integrated phase OR true external phase/downbeat (optional).
 */
class OscInputReceiver(
    private val port: Int = 7000
) {

    /* ================= UI-only pulses ================= */

    private val _oscActivity = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val oscActivity: SharedFlow<Unit> = _oscActivity.asSharedFlow()

    // Heartbeat (Resolume Wire): ping -> pong
    private val _pongActivity = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val pongActivity: SharedFlow<Unit> = _pongActivity.asSharedFlow()

    private val _lastPongMs = MutableStateFlow(0L)
    val lastPongMs: StateFlow<Long> = _lastPongMs.asStateFlow()

    // Any OSC traffic (for optional fallback)
    private val _lastAnyRxMs = MutableStateFlow(0L)
    val lastAnyRxMs: StateFlow<Long> = _lastAnyRxMs.asStateFlow()

    private val _externalBpmActivity = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val externalBpmActivity: SharedFlow<Unit> = _externalBpmActivity.asSharedFlow()

    private val _transportIn = MutableSharedFlow<TransportFeedback>(extraBufferCapacity = 16)
    val transportIn: SharedFlow<TransportFeedback> = _transportIn.asSharedFlow()

    /* ================= External BPM (UI-only) ================= */

    private val _externalBpm = MutableStateFlow<Double?>(null)
    val externalBpm: StateFlow<Double?> = _externalBpm.asStateFlow()

    private val _externalConfidence = MutableStateFlow<Float?>(null)
    val externalConfidence: StateFlow<Float?> = _externalConfidence.asStateFlow()

    // Optional external phase/downbeat (UI-only)
    private val _externalPhase = MutableStateFlow<Float?>(null)
    val externalPhase: StateFlow<Float?> = _externalPhase.asStateFlow()

    private val _externalDownbeat = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val externalDownbeat: SharedFlow<Unit> = _externalDownbeat.asSharedFlow()

    
    /* ================= Resolume Audio Device Params (UI-only) ================= */

    private val _fftInputGain01 = MutableStateFlow<Float?>(null)
    val fftInputGain01: StateFlow<Float?> = _fftInputGain01.asStateFlow()

    /* ================= Debug ================= */

    data class OscDebugState(
        val packetsTotal: Long = 0L,
        val lastAddress: String? = null,
        val lastTypeTags: String? = null,
        val lastArgs: String? = null,
        val lastSeenMs: Long = 0L,
        val lastPongMs: Long = 0L,
        val lastAnyRxMs: Long = 0L,
        val externalTempoRaw: Double? = null,
        val externalBpm: Double? = null,
        val externalConfidence: Float? = null,
        val externalPhase: Float? = null,
        val externalDownbeatMs: Long? = null
    )

    private val _debugState = MutableStateFlow(OscDebugState())
    val debugState: StateFlow<OscDebugState> = _debugState.asStateFlow()

    /* ================= Remote Ghost ================= */

    data class RemoteGhostSnapshot(
        val bpm: Double,
        val phase: Float,          // 0..1
        val lastSeenMs: Long,
        val confidence: Float?
    )

    private val _remoteGhost = MutableStateFlow<RemoteGhostSnapshot?>(null)
    val remoteGhost: StateFlow<RemoteGhostSnapshot?> = _remoteGhost.asStateFlow()

    // integrated remote phase state
    private var remotePhase: Double = 0.0
    private var remoteLastUpdateNs: Long? = null
    private var remoteLastBpm: Double? = null
    private var remoteLastSeenMs: Long = 0L
    private var remoteLastDownbeatMs: Long? = null

    /* ================= Resolume tempo decode =================
     * Your measurements show: raw is normalized 0..1 mapped to ~20..500 BPM:
     *   BPM = 20 + raw * 480
     * We keep other modes for safety:
     *   - factor mode: 1.0 == 120 BPM (rare in your setup)
     *   - direct BPM: raw > 2.5
     */
    private val RESOLUME_FACTOR_BASE_BPM = 120.0

    // ✅ Correct range for your Resolume output
    private val RESOLUME_BPM_MIN = 20.0
    private val RESOLUME_BPM_MAX = 500.0

    private fun resolumeNormalizedToBpm(v01: Double): Double {
        val clamped = v01.coerceIn(0.0, 1.0)
        return (clamped * (RESOLUME_BPM_MAX - RESOLUME_BPM_MIN)) + RESOLUME_BPM_MIN
    }

    private fun decodeExternalBpm(raw: Double): Double {
        val r = raw.coerceAtLeast(0.0)
        if (r == 0.0) return 0.0

        // Direct BPM from custom senders
        if (r > 2.5) return r.coerceIn(0.0, 999.0)

        // Candidate A: normalized 0..1 -> 20..500 BPM (YOUR CASE)
        val bpmNorm = if (r <= 1.0) resolumeNormalizedToBpm(r) else Double.NaN

        // Candidate B: factor (1.0 == 120 BPM)
        val bpmFactor = r * RESOLUME_FACTOR_BASE_BPM

        // Heuristic:
        // - If r is in 0..1, prefer normalized mapping (matches your measurements).
        // - If r is near 1.0 but normalized would give ~500, factor might be intended.
        return when {
            r <= 1.0 && bpmNorm.isFinite() -> bpmNorm
            r in 0.85..2.50 -> bpmFactor
            else -> bpmNorm.takeIf { it.isFinite() } ?: bpmFactor
        }.coerceIn(0.0, 999.0)
    }


    /* ================= Socket ================= */

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        socket = DatagramSocket(port)

        scope.launch {
            val buffer = ByteArray(2048)
            while (isActive && running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet.data.copyOf(packet.length))
                } catch (e: Exception) {
                    Log.e("OSC-IN", "socket error", e)
                }
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
    }

    /* ================= Entry ================= */

    private fun handlePacket(data: ByteArray) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        parseElement(bb)
    }

    /* ================= OSC Parser ================= */

    private fun parseElement(bb: ByteBuffer) {
        if (!bb.hasRemaining()) return

        val startPos = bb.position()
        val address = readOscString(bb) ?: return

        if (address == "#bundle") {
            if (bb.remaining() >= 8) bb.position(bb.position() + 8)

            while (bb.remaining() >= 4) {
                val size = bb.int
                if (bb.remaining() < size) break

                val slice = bb.slice()
                slice.limit(size)
                parseElement(slice)
                bb.position(bb.position() + size)
            }
        } else {
            val typeTags = readOscString(bb) ?: return

            val nowMs = SystemClock.elapsedRealtime()
            _oscActivity.tryEmit(Unit)
            _lastAnyRxMs.value = nowMs

            val argsSummary = summarizeArgs(typeTags, bb.duplicate())

            _debugState.value = _debugState.value.copy(
                packetsTotal = _debugState.value.packetsTotal + 1L,
                lastAddress = address,
                lastTypeTags = typeTags,
                lastArgs = argsSummary,
                lastSeenMs = nowMs,
                lastPongMs = _lastPongMs.value,
                lastAnyRxMs = _lastAnyRxMs.value,
                externalTempoRaw = _debugState.value.externalTempoRaw,
                externalBpm = _externalBpm.value,
                externalConfidence = _externalConfidence.value,
                externalPhase = _externalPhase.value,
                externalDownbeatMs = remoteLastDownbeatMs
            )

            handleMessage(address, typeTags, bb)
        }

        bb.position(startPos)
    }

    private fun handleMessage(address: String, typeTags: String, bb: ByteBuffer) {
        when (address) {

            /* =================================================
             * Resolume Tempo Controller (transport events)
             * UI-only: DO NOT change internal clock.
             * ================================================= */

            "/composition/tempocontroller/tempotap" -> {
                val v = readFirstNumber(typeTags, bb)?.toInt() ?: 1
                if (v == 1 && !TransportEchoGuard.shouldSuppress(TransportType.TAP)) {
                    _transportIn.tryEmit(TransportFeedback.Tap)
                }
            }

            "/composition/tempocontroller/resync" -> {
                val v = readFirstNumber(typeTags, bb)?.toInt() ?: 1
                if (v == 1 && !TransportEchoGuard.shouldSuppress(TransportType.RESYNC)) {
                    _transportIn.tryEmit(TransportFeedback.Resync)
                }
            }

            "/composition/tempocontroller/tempo/multiply" -> {
                val v = readFirstNumber(typeTags, bb)?.toInt() ?: 1
                if (v == 1 && !TransportEchoGuard.shouldSuppress(TransportType.MULTIPLY)) {
                    _transportIn.tryEmit(TransportFeedback.Multiply)
                }
            }

            "/composition/tempocontroller/tempo/divide" -> {
                val v = readFirstNumber(typeTags, bb)?.toInt() ?: 1
                if (v == 1 && !TransportEchoGuard.shouldSuppress(TransportType.DIVIDE)) {
                    _transportIn.tryEmit(TransportFeedback.Divide)
                }
            }

            "/composition/tempocontroller/tempopush" -> {
                val v = readFirstNumber(typeTags, bb)?.toInt() ?: 1
                if (v == 1) {
                    if (!remoteNudgeActivePush) {
                        remoteNudgeActivePush = true
                        if (!TransportEchoGuard.shouldSuppress(TransportType.NUDGE_START)) {
                            _transportIn.tryEmit(TransportFeedback.NudgeStart)
                        }
                    }
                } else {
                    if (remoteNudgeActivePush) {
                        remoteNudgeActivePush = false
                        if (!TransportEchoGuard.shouldSuppress(TransportType.NUDGE_STOP)) {
                            _transportIn.tryEmit(TransportFeedback.NudgeStop)
                        }
                    }
                }
            }

            "/composition/tempocontroller/tempopull" -> {
                val v = readFirstNumber(typeTags, bb)?.toInt() ?: 1
                if (v == 1) {
                    if (!remoteNudgeActivePull) {
                        remoteNudgeActivePull = true
                        if (!TransportEchoGuard.shouldSuppress(TransportType.NUDGE_START)) {
                            _transportIn.tryEmit(TransportFeedback.NudgeStart)
                        }
                    }
                } else {
                    if (remoteNudgeActivePull) {
                        remoteNudgeActivePull = false
                        if (!TransportEchoGuard.shouldSuppress(TransportType.NUDGE_STOP)) {
                            _transportIn.tryEmit(TransportFeedback.NudgeStop)
                        }
                    }
                }
            }

            /* =================================================
             * External BPM (read-only): UI only
             * Resolume commonly sends either:
             * - seconds-per-beat (period)  OR
             * - tempo factor / normalized (depending on mapping)
             * ================================================= */

            "/composition/tempocontroller/tempo",
            "/composition/tempocontroller/tempo/value" -> {
                val raw = readFirstNumber(typeTags, bb) ?: return

                // ✅ IMPORTANT: for our normalized mapping (20..500),
                // raw==0 means 20 BPM, not "no bpm"
                val bpm = if (raw == 0.0) 20.0 else decodeExternalBpm(raw)

                _debugState.value = _debugState.value.copy(externalTempoRaw = raw)
                onExternalBpm(bpm) // ✅ don't gate with >0 here
            }

            // Custom / TD
            "/external/bpm", "/tapsync/external/bpm", "/tapsync/bpm" -> {
                val raw = readFirstNumber(typeTags, bb) ?: return
                val bpm = decodeExternalBpm(raw)
                _debugState.value = _debugState.value.copy(externalTempoRaw = raw)
                if (bpm > 0.0) onExternalBpm(bpm)
            }

            /* =================================================
             * External Confidence (read-only): UI only
             * ================================================= */

            "/external/confidence",
            "/tapsync/external/confidence",
            "/tapsync/confidence",
            "/composition/tempocontroller/tempo/confidence" -> {
                val c = readFirstNumber(typeTags, bb)?.toFloat() ?: return
                _externalConfidence.value = c.coerceIn(0f, 1f)
                _debugState.value = _debugState.value.copy(externalConfidence = _externalConfidence.value)
            }

            /* =================================================
             * Optional external phase/downbeat (UI-only)
             * ================================================= */

            "/external/phase",
            "/tapsync/external/phase",
            "/tapsync/phase" -> {
                val p = readFirstNumber(typeTags, bb)?.toFloat() ?: return
                onExternalPhase(p)
            }

            "/external/downbeat",
            "/tapsync/external/downbeat",
            "/tapsync/downbeat" -> {
                val v = readFirstNumber(typeTags, bb)
                val trigger = v == null || v > 0.5
                if (trigger) onExternalDownbeat()
            }
            
            /* =================================================
             * Resolume Audio Device Params (UI-only)
             * ================================================= */

            "/audiodevicemanager/params/fftinputgain" -> {
                // Float 0..1 (maps to -24..+24 dB in Resolume). Default is 0.5 (= 0 dB).
                val v = readFirstNumber(typeTags, bb)?.toFloat() ?: return
                _fftInputGain01.value = v.coerceIn(0f, 1f)
            }

            /* =================================================
             * Heartbeat Pong (Resolume Wire)
             * ================================================= */

            "/tapsync/pong" -> {
                // Optional arg: echo/nonce. Not required for link detection.
                _lastPongMs.value = SystemClock.elapsedRealtime()
                _pongActivity.tryEmit(Unit)

                _debugState.value = _debugState.value.copy(lastPongMs = _lastPongMs.value)
            }


            // all other messages intentionally ignored
        }
    }

    private fun onExternalBpm(bpm: Double) {
        _externalBpm.value = bpm
        _externalBpmActivity.tryEmit(Unit)

        val nowNs = SystemClock.elapsedRealtimeNanos()
        val nowMs = SystemClock.elapsedRealtime()

        remoteLastSeenMs = nowMs

        // If we have true external phase, trust it
        val truePhase = _externalPhase.value
        if (truePhase != null) {
            remotePhase = truePhase.toDouble().coerceIn(0.0, 1.0)
            remoteLastUpdateNs = nowNs
            remoteLastBpm = bpm
            publishGhostSnapshot(bpm)
            return
        }

        val prevNs = remoteLastUpdateNs
        val prevBpm = remoteLastBpm

        if (prevNs != null && prevBpm != null) {
            val dtSec = (nowNs - prevNs).toDouble() / 1_000_000_000.0
            val beats = dtSec * (prevBpm / 60.0)
            remotePhase = (remotePhase + beats) % 1.0
        } else {
            remotePhase = 0.0
        }

        remoteLastUpdateNs = nowNs
        remoteLastBpm = bpm

        publishGhostSnapshot(bpm)
    }

    private fun onExternalPhase(phase01: Float) {
        val nowMs = SystemClock.elapsedRealtime()
        val nowNs = SystemClock.elapsedRealtimeNanos()

        val p = phase01.coerceIn(0f, 1f)
        _externalPhase.value = p
        remotePhase = p.toDouble()
        remoteLastUpdateNs = nowNs
        remoteLastSeenMs = nowMs

        val bpm = _externalBpm.value
        if (bpm != null && bpm > 0.0) publishGhostSnapshot(bpm)

        _debugState.value = _debugState.value.copy(externalPhase = p)
    }

    private fun onExternalDownbeat() {
        val nowMs = SystemClock.elapsedRealtime()
        val nowNs = SystemClock.elapsedRealtimeNanos()

        remoteLastDownbeatMs = nowMs
        remoteLastSeenMs = nowMs

        _externalPhase.value = 0f
        remotePhase = 0.0
        remoteLastUpdateNs = nowNs

        _externalDownbeat.tryEmit(Unit)

        val bpm = _externalBpm.value
        if (bpm != null && bpm > 0.0) publishGhostSnapshot(bpm)

        _debugState.value = _debugState.value.copy(externalPhase = 0f, externalDownbeatMs = nowMs)
    }

    private fun publishGhostSnapshot(bpm: Double) {
        _remoteGhost.value = RemoteGhostSnapshot(
            bpm = bpm,
            phase = remotePhase.toFloat().coerceIn(0f, 1f),
            lastSeenMs = remoteLastSeenMs,
            confidence = _externalConfidence.value
        )

        _debugState.value = _debugState.value.copy(
            externalTempoRaw = _debugState.value.externalTempoRaw,
            externalBpm = _externalBpm.value,
            externalConfidence = _externalConfidence.value,
            externalPhase = _externalPhase.value,
            externalDownbeatMs = remoteLastDownbeatMs
        )
    }

    /* ================= Helpers ================= */

    private fun readFirstNumber(typeTags: String, bb: ByteBuffer): Double? {
        for (c in typeTags.drop(1)) {
            when (c) {
                'f' -> if (bb.remaining() >= 4) return bb.float.toDouble()
                'i' -> if (bb.remaining() >= 4) return bb.int.toDouble()
                'd' -> if (bb.remaining() >= 8) return bb.double
            }
        }
        return null
    }

    private fun readOscString(bb: ByteBuffer): String? {
        val start = bb.position()
        while (bb.hasRemaining()) {
            if (bb.get() == 0.toByte()) {
                val end = bb.position() - 1
                val len = end - start

                val bytes = ByteArray(len)
                bb.position(start)
                bb.get(bytes)
                bb.position(end + 1)

                while (bb.position() % 4 != 0 && bb.hasRemaining()) bb.get()
                return String(bytes)
            }
        }
        return null
    }

    private fun summarizeArgs(typeTags: String, bb: ByteBuffer): String? {
        if (!typeTags.startsWith(",")) return null
        val out = mutableListOf<String>()

        for (c in typeTags.drop(1)) {
            when (c) {
                'i' -> if (bb.remaining() >= 4) out += bb.int.toString()
                'f' -> if (bb.remaining() >= 4) out += "%.3f".format(bb.float)
                'd' -> if (bb.remaining() >= 8) out += "%.3f".format(bb.double)
                's' -> {
                    val s = readOscString(bb) ?: ""
                    out += "\"${s.take(24)}\""
                }
                else -> out += c.toString()
            }
        }

        return out.joinToString(prefix = "[", postfix = "]")
    }
}
