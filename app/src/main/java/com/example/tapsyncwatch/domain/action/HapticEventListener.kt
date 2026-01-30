package com.example.tapsyncwatch.domain.action

import android.os.VibrationEffect
import android.os.Vibrator

interface HapticEventListener {
    fun onTap()
    fun onMultiplyDivide()
    fun onResync()
    fun onNudge()
}

class HapticFeedbackEngine(
    private val vibrator: Vibrator
) : HapticEventListener {

    override fun onTap() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    override fun onMultiplyDivide() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    override fun onResync() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    override fun onNudge() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
