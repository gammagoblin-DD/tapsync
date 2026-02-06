package com.example.tapsyncwatch.domain.action

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Haptic "language":
 * - Tap: short tick
 * - Multiply: double tick
 * - Divide: long-short
 * - Resync: "thump"
 * - Nudge: tiny tick
 *
 * Notes:
 * - Watches vary wildly in amplitude support. We keep patterns primarily in timing.
 * - We keep the legacy onMultiplyDivide() for compatibility, but prefer onMultiply()/onDivide().
 */
interface HapticEventListener {
    fun onTap()

    fun onMultiply() = onMultiplyDivide()
    fun onDivide() = onMultiplyDivide()

    @Deprecated("Use onMultiply() / onDivide() for distinct haptic patterns.")
    fun onMultiplyDivide()

    fun onResync()
    fun onNudge()
}

class HapticFeedbackEngine(
    private val vibrator: Vibrator
) : HapticEventListener {

    private fun vibeOneShot(ms: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibePattern(timings: LongArray, amplitudes: IntArray? = null) {
        // API 26+ (Wear OS is 26+). If amplitudes are not supported, timing still carries the pattern.
        val effect = if (amplitudes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
    }

    override fun onTap() {
        // short tick
        vibeOneShot(28)
    }

    override fun onMultiply() {
        // double tick (tick, pause, tick)
        vibePattern(
            longArrayOf(0, 28, 42, 28),
            intArrayOf(0, 255, 0, 255)
        )
    }

    override fun onDivide() {
        // long-short (long, pause, short)
        vibePattern(
            longArrayOf(0, 78, 50, 26),
            intArrayOf(0, 255, 0, 220)
        )
    }

    @Deprecated("Use onMultiply() / onDivide().")
    override fun onMultiplyDivide() {
        // fallback: medium single pulse
        vibeOneShot(70)
    }

    override fun onResync() {
        // "thump" (heavy-ish single)
        vibeOneShot(150)
    }

    override fun onNudge() {
        // tiny tick
        vibeOneShot(18)
    }
}
