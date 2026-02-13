package com.example.tapsyncwatch.input.osc

import android.os.SystemClock
import android.util.Log
import com.example.tapsyncwatch.domain.transport.TransportFeedback
import com.example.tapsyncwatch.osc.OscHealth
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class OscOutputSender(
    host: String,
    port: Int
) {

    @Volatile
    private var address: InetAddress = InetAddress.getByName(host)

    @Volatile
    private var port: Int = port

    @Volatile
    private var sendEnabled: Boolean = true

    /** Global throttle for non-critical messages (ms). 0 disables throttling. */
    @Volatile
    private var throttleMs: Long = 0L

    @Volatile
    private var lastThrottledSendMs: Long = 0L

    private var socket: DatagramSocket? = null

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    /* ================= OSC HEALTH ================= */

    private val _health = MutableStateFlow<OscHealth>(OscHealth.Idle)
    val health: StateFlow<OscHealth> = _health.asStateFlow()

    /* ================= TRANSPORT FEEDBACK ================= */

    private val _transportFeedback =
        MutableSharedFlow<TransportFeedback>(extraBufferCapacity = 16)

    val transportFeedback: SharedFlow<TransportFeedback> =
        _transportFeedback.asSharedFlow()

    /* ================= TARGET ================= */

    fun setTarget(host: String, port: Int) {
        this.address = InetAddress.getByName(host)
        this.port = port
    }

    fun setSendEnabled(enabled: Boolean) {
        this.sendEnabled = enabled
    }

    fun setThrottleMs(ms: Long) {
        this.throttleMs = ms.coerceIn(0L, 2000L)
    }

    /* ================= SEND API ================= */

    fun sendInt(path: String, value: Int) {
        sendAsync(buildOscMessage(path, ",i") { it.putInt(value) })
    }

    fun sendFloat(path: String, value: Float) {
        sendAsync(buildOscMessage(path, ",f") { it.putFloat(value) })
    }

    fun sendIntTo(host: String, port: Int, path: String, value: Int) {
        val dest = InetAddress.getByName(host)
        sendAsync(buildOscMessage(path, ",i") { it.putInt(value) }, destAddress = dest, destPort = port)
    }

    fun sendFloatTo(host: String, port: Int, path: String, value: Float) {
        val dest = InetAddress.getByName(host)
        sendAsync(buildOscMessage(path, ",f") { it.putFloat(value) }, destAddress = dest, destPort = port)
    }

    /* ================= FFT INPUT GAIN ================= */

    private companion object {
        const val OSC_FFT_INPUT_GAIN = "/audiodevicemanager/params/fftinputgain"
    }

    /** Send FFT input gain (0..1). Center/default is 0.5 (= 0 dB). */
    fun sendFftInputGain01(value01: Float) {
        sendFloat(OSC_FFT_INPUT_GAIN, value01.coerceIn(0f, 1f))
    }

    /** Hard reset FFT input gain to default (0.5). */
    fun resetFftInputGainToDefault() {
        sendFftInputGain01(0.5f)
    }

    /* ================= CORE SEND ================= */

    private fun sendAsync(
        data: ByteArray,
        destAddress: InetAddress? = null,
        destPort: Int? = null
    ) {
        executor.execute {
            if (!sendEnabled) return@execute

            val path = extractOscPath(data)
            if (shouldThrottle(path)) {
                return@execute
            }

            try {
                if (socket == null || socket?.isClosed == true) {
                    socket = DatagramSocket()
                }

                _health.value = OscHealth.Sending(SystemClock.elapsedRealtime())

                val a = destAddress ?: address
                val p = destPort ?: port

                val packet = DatagramPacket(
                    data,
                    data.size,
                    a,
                    p
                )

                socket?.send(packet)

                emitTransportFeedback(data)

                Log.d(
                    "OSC",
                    "SEND ${data.size} bytes → ${a.hostAddress}:$p"
                )
            } catch (e: Exception) {
                _health.value = OscHealth.Error(e)
                Log.e("OSC", "SEND FAILED", e)
            }
        }
    }

    private fun shouldThrottle(path: String?): Boolean {
        val t = throttleMs
        if (t <= 0L) return false
        if (path == null) return false
        if (isThrottleExempt(path)) return false

        val now = SystemClock.elapsedRealtime()
        val last = lastThrottledSendMs
        if (now - last < t) return true
        lastThrottledSendMs = now
        return false
    }

    private fun isThrottleExempt(path: String): Boolean {
        return path.startsWith("/tapsync/ping") ||
            path.startsWith("/composition/tempocontroller/")
    }

    /* ================= FEEDBACK ================= */

    private fun emitTransportFeedback(data: ByteArray) {
        val path = extractOscPath(data) ?: return

        when (path) {
            "/composition/tempocontroller/tempotap" ->
                _transportFeedback.tryEmit(TransportFeedback.Tap)

            "/composition/tempocontroller/resync" ->
                _transportFeedback.tryEmit(TransportFeedback.Resync)

            "/composition/tempocontroller/tempo/multiply" ->
                _transportFeedback.tryEmit(TransportFeedback.Multiply)

            "/composition/tempocontroller/tempo/divide" ->
                _transportFeedback.tryEmit(TransportFeedback.Divide)

            "/composition/tempocontroller/tempopush",
            "/composition/tempocontroller/tempopull" ->
                _transportFeedback.tryEmit(TransportFeedback.NudgeStart)
        }
    }

    private fun extractOscPath(data: ByteArray): String? {
        val zeroIndex = data.indexOf(0)
        if (zeroIndex <= 0) return null
        return try {
            String(data, 0, zeroIndex, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /* ================= OSC BUILD ================= */

    private fun buildOscMessage(
        path: String,
        typeTag: String,
        payload: (ByteBuffer) -> Unit
    ): ByteArray {

        val bb = ByteBuffer
            .allocate(256)
            .order(ByteOrder.BIG_ENDIAN)

        writeOscString(bb, path)
        writeOscString(bb, typeTag)
        payload(bb)

        return bb.array().copyOf(bb.position())
    }

    private fun writeOscString(bb: ByteBuffer, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        bb.put(bytes)
        bb.put(0)
        while (bb.position() % 4 != 0) bb.put(0)
    }
}
