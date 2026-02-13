package com.example.tapsyncwatch.domain.action

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

interface HapticEventListener {
    fun onTap()
    fun onMultiply()
    fun onDivide()
    fun onResync()
    fun onNudge()
}

class HapticFeedbackEngine(
    private val vibrator: Vibrator
) : HapticEventListener {

    private fun oneShot(ms: Long, amp: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (ms <= 0L) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    private fun wave(timings: LongArray, amps: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings.sum())
        }
    }

    override fun onTap() = oneShot(28L)

    override fun onMultiply() = wave(longArrayOf(0, 26, 42, 26), intArrayOf(0, 255, 0, 255))

    override fun onDivide() = wave(longArrayOf(0, 70, 45, 24), intArrayOf(0, 255, 0, 220))

    override fun onResync() = oneShot(150L)

    override fun onNudge() = oneShot(18L)
}
