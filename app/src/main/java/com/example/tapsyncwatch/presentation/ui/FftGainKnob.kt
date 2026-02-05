package com.example.tapsyncwatch.presentation.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.*

/**
 * FFT Gain visual ring (center-zero).
 *
 * This project runs an older Compose stack; this file avoids newer text/draw APIs and uses nativeCanvas.
 *
 * Resolume expects float 0..1 where:
 *   0.0 = -24 dB
 *   0.5 =  0 dB (default / center)
 *   1.0 = +24 dB
 *
 * NOTE: Input is handled by FftGainScreen swipe gesture. This is visual-only.
 */
@Composable
fun FftGainKnobVisual(
    value01: Float,
    modifier: Modifier = Modifier,
    pulse: Float = 0f, // 0..1 short flash on step/reset
) {
    val v = value01.coerceIn(0f, 1f)
    val db = (v - 0.5f) * 48f

    // TapScreen-ish palette (brown/orange). Keep it consistent and readable on stage.
    val goblinBg = Color(0xFF0B0B0B)
    val goblinBrown = Color(0xFF8C5A2B)
    val goblinOrange = Color(0xFFE8802A) // darker / less harsh

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val s = min(w, h)
            val center = Offset(w / 2f, h / 2f)

            // Ring geometry (bezel-safe). We draw arcs on the MID radius so ticks sit centered in the fill band.
            val outerR = s * 0.475f
            val innerR = s * 0.34f
            val ringW = outerR - innerR
            val arcMidR = (outerR + innerR) * 0.5f
            val trackStroke = ringW * 0.64f

            val sweepHalf = 150f
            val sweepTotal = 300f
            val startAngle = -90f - sweepHalf
            val rel = ((v - 0.5f) / 0.5f).coerceIn(-1f, 1f) * sweepHalf

            val arcTopLeft = Offset(center.x - arcMidR, center.y - arcMidR)
            val arcSize = Size(arcMidR * 2f, arcMidR * 2f)

            // Subtle inner depth (dark, not white)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goblinBg.copy(alpha = 0.00f),
                        goblinBg.copy(alpha = 0.55f),
                        goblinBg.copy(alpha = 0.95f),
                    ),
                    center = center,
                    radius = innerR * 1.25f
                ),
                radius = innerR * 1.10f,
                center = center
            )

            // Ambient plate glow (subtle)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goblinBrown.copy(alpha = 0.08f + 0.12f * pulse),
                        goblinOrange.copy(alpha = 0.05f + 0.10f * pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerR * 1.20f
                ),
                radius = outerR * 1.20f,
                center = center
            )

            // Track (thin and calm)
            drawArc(
                color = goblinBrown.copy(alpha = 0.16f),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = trackStroke, cap = StrokeCap.Round)
            )

            // Active arc: less bright + less fat
            if (rel != 0f) {
                val activeStart = if (rel > 0f) -90f else (-90f + rel)
                val activeSweep = abs(rel)

                val activeBrush = Brush.sweepGradient(colors = listOf(goblinOrange, goblinBrown, goblinOrange))

                // Glow layer (soft)
                drawIntoCanvas { canvas ->
                    val p = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeWidth = trackStroke * 1.06f
                        color = android.graphics.Color.argb(
                            (12 + (14 * pulse)).toInt(),
                            (goblinOrange.red * 255).toInt(),
                            (goblinOrange.green * 255).toInt(),
                            (goblinOrange.blue * 255).toInt()
                        )
                        setShadowLayer(
                            trackStroke * (0.26f + 0.10f * pulse),
                            0f,
                            0f,
                            android.graphics.Color.argb(
                                (52 + (46 * pulse)).toInt(),
                                (goblinOrange.red * 255).toInt(),
                                (goblinOrange.green * 255).toInt(),
                                (goblinOrange.blue * 255).toInt()
                            )
                        )
                    }
                    val rect = android.graphics.RectF(arcTopLeft.x, arcTopLeft.y, arcTopLeft.x + arcSize.width, arcTopLeft.y + arcSize.height)
                    canvas.nativeCanvas.drawArc(rect, activeStart, activeSweep, false, p)
                }

                // Main active arc (thin)
                drawArc(
                    brush = activeBrush,
                    startAngle = activeStart,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = trackStroke, cap = StrokeCap.Round)
                )
            }

            // Micro ticks (centered in the same band as the fill arc)
            for (i in -150..150 step 3) {
                val isMajor = (i % 30 == 0)
                val isMid = (i % 15 == 0)

                val aRad = Math.toRadians((i - 90).toDouble()).toFloat()

                val tickLen = when {
                    isMajor -> 18f
                    isMid -> 14f
                    else -> 10f
                }
                val rOuter = arcMidR + tickLen * 0.5f
                val rInner = arcMidR - tickLen * 0.5f

                val p0 = Offset(center.x + cos(aRad) * rOuter, center.y + sin(aRad) * rOuter)
                val p1 = Offset(center.x + cos(aRad) * rInner, center.y + sin(aRad) * rInner)

                val col = when {
                    isMajor -> goblinOrange.copy(alpha = 0.32f)
                    else -> goblinBrown.copy(alpha = 0.22f)
                }
                val sw = when {
                    isMajor -> 3.0f
                    isMid -> 2.4f
                    else -> 1.6f
                }

                drawLine(color = col, start = p0, end = p1, strokeWidth = sw, cap = StrokeCap.Round)
            }


            // 12 o'clock notch (orange) – centered in the band
            run {
                val notchA = Math.toRadians((-90).toDouble()).toFloat()
                val notchLen = 34f
                val rOuter = arcMidR + notchLen * 0.5f
                val rInner = arcMidR - notchLen * 0.5f
                val n0 = Offset(center.x + cos(notchA) * rOuter, center.y + sin(notchA) * rOuter)
                val n1 = Offset(center.x + cos(notchA) * rInner, center.y + sin(notchA) * rInner)
                drawLine(
                    color = goblinOrange.copy(alpha = 0.76f),
                    start = n0,
                    end = n1,
                    strokeWidth = 5.2f,
                    cap = StrokeCap.Round
                )
            }

            // Pointer (orange)
            val pointerLen = innerR * 1.02f
            val pointerAngle = Math.toRadians((rel - 90).toDouble()).toFloat()
            val px = center.x + cos(pointerAngle) * pointerLen
            val py = center.y + sin(pointerAngle) * pointerLen
            drawLine(
                color = goblinOrange.copy(alpha = 0.80f),
                start = center,
                end = Offset(px, py),
                strokeWidth = 7.8f,
                cap = StrokeCap.Round
            )

            // Center hub (dark)
            drawCircle(color = goblinBg.copy(alpha = 0.70f), radius = 34f, center = center)
            drawCircle(color = goblinBrown.copy(alpha = 0.22f), radius = 14f, center = center)

            // Center text: FFT GAIN (lower), dB below. Smaller number.
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas

                val labelPaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.argb(
                        195,
                        (goblinOrange.red * 255).toInt(),
                        (goblinOrange.green * 255).toInt(),
                        (goblinOrange.blue * 255).toInt()
                    )
                    textAlign = Paint.Align.CENTER
                    textSize = (s * 0.055f)
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.06f
                }

                val valuePaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.argb(
                        236,
                        (goblinOrange.red * 255).toInt(),
                        (goblinOrange.green * 255).toInt(),
                        (goblinOrange.blue * 255).toInt()
                    )
                    textAlign = Paint.Align.CENTER
                    textSize = (s * 0.088f)
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }

                val yLabel = center.y + s * 0.157f // +~10px
                val yValue = center.y + s * 0.287f // +~10px

                nc.drawText("FFT GAIN", center.x, yLabel, labelPaint)
                nc.drawText(String.format("%+.1f dB", db), center.x, yValue, valuePaint)
            }
        }
    }
}

/**
 * Backwards compatible alias: if any older code still calls FftGainKnob(...),
 * delegate to the visual variant. (Input moved to swipe gesture.)
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun FftGainKnob(
    value01: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FftGainKnobVisual(value01 = value01, modifier = modifier, pulse = 0f)
}
