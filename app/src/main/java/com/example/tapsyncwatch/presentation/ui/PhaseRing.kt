package com.example.tapsyncwatch.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PhaseRing
 *
 * Read-only UI Component zur Darstellung der musikalischen Phase.
 *
 * - phase ∈ [0.0 .. 1.0)
 * - Keine Abhängigkeit von Clock, Gesten oder OSC
 * - Reines UI-Element
 */
@Composable
fun PhaseRing(
    phase: Double,
    color: Color = Color.Green,
    alpha: Float = 0.6f,
    strokeWidth: Dp = 6.dp
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sweep = (phase.coerceIn(0.0, 1.0) * 360.0).toFloat()

        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}
