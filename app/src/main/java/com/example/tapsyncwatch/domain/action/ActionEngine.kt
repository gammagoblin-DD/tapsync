package com.example.tapsyncwatch.domain.action

import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockEvent
import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ActionEngine(
    private val scope: CoroutineScope,
    private val clock: Clock
) {

    val oscSender: OscOutputSender
        get() = clock.oscSender

    fun tap() {
        scope.launch {
            clock.handle(
                ClockEvent.Tap(System.currentTimeMillis())
            )
        }
    }

    fun multiply() {
        scope.launch {
            clock.handle(ClockEvent.Multiply)
        }
    }

    fun divide() {
        scope.launch {
            clock.handle(ClockEvent.Divide)
        }
    }

    fun resync() {
        scope.launch {
            clock.handle(ClockEvent.Resync)
        }
    }

    fun nudgePushStart() {
        scope.launch {
            clock.handle(ClockEvent.Nudge.RightStart)
        }
    }

    fun nudgePushEnd() {
        scope.launch {
            clock.handle(ClockEvent.Nudge.Stop)
        }
    }

    fun nudgePullStart() {
        scope.launch {
            clock.handle(ClockEvent.Nudge.LeftStart)
        }
    }

    fun nudgePullEnd() {
        scope.launch {
            clock.handle(ClockEvent.Nudge.Stop)
        }
    }
}
