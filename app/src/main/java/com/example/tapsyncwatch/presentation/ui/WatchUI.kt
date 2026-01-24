package com.example.tapsyncwatch.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.domain.clock.ClockEvent
import kotlin.math.abs

private enum class NudgeState {
    IDLE,
    ARMED,
    NUDGING_UP,
    NUDGING_DOWN
}

@Composable
fun WatchUI(
    bpm: Float,
    onMultiply: () -> Unit,
    onDivide: () -> Unit,
    onResync: () -> Unit,
    onClockEvent: (ClockEvent) -> Unit
) {
    val nudgeZoneRatio = 0.25f
    val dragThreshold = 16f
    val holdThreshold = 32f
    val resyncThreshold = 60f

    var nudgeState by remember { mutableStateOf(NudgeState.IDLE) }
    var dragX by remember { mutableStateOf(0f) }
    var hasDragged by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectDragGestures(

                    onDragStart = { offset ->
                        dragX = 0f
                        hasDragged = false

                        val nudgeZoneWidth = size.width * nudgeZoneRatio
                        nudgeState =
                            if (offset.x <= nudgeZoneWidth)
                                NudgeState.ARMED
                            else
                                NudgeState.IDLE
                    },

                    onDrag = { _, dragAmount ->

                        if (
                            abs(dragAmount.x) > dragThreshold ||
                            abs(dragAmount.y) > dragThreshold
                        ) {
                            hasDragged = true
                        }

                        dragX += dragAmount.x

                        // Nudge läuft → nichts mehr auswerten
                        if (
                            nudgeState == NudgeState.NUDGING_UP ||
                            nudgeState == NudgeState.NUDGING_DOWN
                        ) return@detectDragGestures

                        // Nudge darf NUR aus ARMED starten
                        if (nudgeState != NudgeState.ARMED) return@detectDragGestures

                        // horizontale Bewegung → Abbruch
                        if (abs(dragAmount.x) > abs(dragAmount.y)) {
                            nudgeState = NudgeState.IDLE
                            onClockEvent(ClockEvent.Nudge.Stop)
                            return@detectDragGestures
                        }

                        // echter Hold → Start
                        if (abs(dragAmount.y) > holdThreshold) {
                            if (dragAmount.y < 0f) {
                                nudgeState = NudgeState.NUDGING_UP
                                onClockEvent(ClockEvent.Nudge.RightStart)
                            } else {
                                nudgeState = NudgeState.NUDGING_DOWN
                                onClockEvent(ClockEvent.Nudge.LeftStart)
                            }
                        }
                    },

                    onDragEnd = {
                        when {
                            nudgeState == NudgeState.NUDGING_UP ||
                                    nudgeState == NudgeState.NUDGING_DOWN -> {
                                onClockEvent(ClockEvent.Nudge.Stop)
                            }

                            hasDragged && dragX < -resyncThreshold -> {
                                onResync()
                            }

                            !hasDragged -> {
                                // TAP = ClockEvent
                                onClockEvent(
                                    ClockEvent.Tap(System.currentTimeMillis())
                                )
                            }
                        }

                        nudgeState = NudgeState.IDLE
                    },

                    onDragCancel = {
                        onClockEvent(ClockEvent.Nudge.Stop)
                        nudgeState = NudgeState.IDLE
                    }
                )
            }
            .padding(16.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "${bpm.toInt()} BPM",
                style = MaterialTheme.typography.h4
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row {

                Text(
                    text = "÷2",
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onDivide() }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "×2",
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onMultiply() }
                )
            }
        }
    }
}
