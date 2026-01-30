package com.example.tapsyncwatch.domain.action

import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockEvent
import kotlinx.coroutines.CoroutineScope

class ActionEngine(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val haptics: HapticEventListener
) {

    fun tap() {
        haptics.onTap()
        clock.handle(ClockEvent.Tap(System.currentTimeMillis()))
    }

    fun multiply() {
        haptics.onMultiplyDivide()
        clock.handle(ClockEvent.Multiply)
    }

    fun divide() {
        haptics.onMultiplyDivide()
        clock.handle(ClockEvent.Divide)
    }

    fun resync() {
        haptics.onResync()
        clock.handle(ClockEvent.Resync)
    }

    fun nudgeLeftStart() {
        haptics.onNudge()
        clock.handle(ClockEvent.Nudge.LeftStart)
    }

    fun nudgeRightStart() {
        haptics.onNudge()
        clock.handle(ClockEvent.Nudge.RightStart)
    }

    fun nudgeStop() {
        clock.handle(ClockEvent.Nudge.Stop)
    }
    // 🔧 Compatibility für TapScreen (legacy)
    val oscSender get() = clock.oscSender

}
