package com.example.tapsyncwatch.presentation.ui

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * FFT Gain Screen (round-watch friendly).
 *
 * INPUT: Multiply/Divide style gesture:
 *   - Swipe DOWN  => +   (increase gain)
 *   - Swipe UP    => -   (decrease gain)
 *
 * Compatibility: avoids rememberInfiniteTransition/animateFloat/infiniteRepeatable/RepeatMode.
 *
 * NOTE: onBackToTap exists for backward compatibility with MainActivity call sites,
 * but there is intentionally NO on-screen back button (gesture + hardware back only).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun FftGainScreen(
    value01: Float,
    onSetValue01: (Float) -> Unit,
    onResetToDefault: () -> Unit,
    onBackToTap: (() -> Unit)? = null,
    onPageNext: (() -> Unit)? = null,
    onPagePrev: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // Right-edge paging zone (avoid collisions with center swipe)
    val edgeWidthDp = 26.dp
    val edgeWidthPx = with(density) { edgeWidthDp.toPx() }
    val pageSwipeThresholdPx = with(density) { 34.dp.toPx() }
    val pageVerticalSlopPx = with(density) { 22.dp.toPx() }

    var layoutWidthPx by remember { mutableStateOf(0f) }
    var edgeCandidate by remember { mutableStateOf(false) }
    var edgeStartX by remember { mutableStateOf(0f) }
    var edgeStartY by remember { mutableStateOf(0f) }
    var edgeTriggered by remember { mutableStateOf(false) }

    // TapScreen-ish palette (brown/orange)
    val goblinBg = Color(0xFF0B0B0B)
    val goblinBrown = Color(0xFF8C5A2B)
    val goblinOrange = Color(0xFFFF9A3D)

    // Ambient "breathe" (manual Animatable loop)
    val breatheAnim = remember { Animatable(0.14f) }
    val breathe = breatheAnim.value
    LaunchedEffect(Unit) {
        while (true) {
            breatheAnim.animateTo(0.20f, animationSpec = tween(2200))
            breatheAnim.animateTo(0.10f, animationSpec = tween(2200))
        }
    }

    // Display smoothing (manual Animatable, no animateFloatAsState)
    val displayAnim = remember { Animatable(value01.coerceIn(0f, 1f)) }
    LaunchedEffect(value01) {
        displayAnim.animateTo(value01.coerceIn(0f, 1f), animationSpec = tween(110))
    }
    val displayValue = displayAnim.value

    // Step pulse animation
    var pulseKey by remember { mutableStateOf(0) }
    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(pulseKey) {
        pulseAnim.snapTo(1f)
        pulseAnim.animateTo(0f, animationSpec = tween(220))
    }
    val pulse = pulseAnim.value

    // Reset should require a double-tap to avoid accidental nukes on stage
    var lastResetTapMs by remember { mutableStateOf(0L) }
    val resetDoubleTapWindowMs = 360L

    // Visual 'armed' hint for double-tap reset (no text, just a tiny blink).
    var resetArmPulseKey by remember { mutableStateOf(0) }
    val resetArmPulse = remember { Animatable(0f) }
    LaunchedEffect(resetArmPulseKey) {
        if (resetArmPulseKey == 0) return@LaunchedEffect
        resetArmPulse.snapTo(1f)
        resetArmPulse.animateTo(0f, animationSpec = tween(260))
    }



    // Swipe-to-step gesture (multiply/divide feel)
    val armDistPx = with(density) { 10.dp.toPx() }
    val offAxisMaxPx = with(density) { 26.dp.toPx() }
    val stepPx = with(density) { 18.dp.toPx() }      // distance per step
    val stepValue = 0.01f                             // 0..1 step (~0.48 dB)
    val snapToDefaultEps = 0.008f                     // snap to 0 dB center

    var swipeActive by remember { mutableStateOf(false) }
    var swipeArmed by remember { mutableStateOf(false) }
    var downX by remember { mutableStateOf(0f) }
    var downY by remember { mutableStateOf(0f) }
    var startValue by remember { mutableStateOf(0.5f) }
    var lastSteps by remember { mutableStateOf(0) }

    fun applyValue(vIn: Float, doHaptic: Boolean) {
        var v = vIn.coerceIn(0f, 1f)
        if (abs(v - 0.5f) < snapToDefaultEps) v = 0.5f
        onSetValue01(v)
        if (doHaptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pulseKey += 1
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape),
        color = goblinBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { layoutWidthPx = it.width.toFloat() }
                // Right-edge paging: swallow touch to avoid fighting center swipe.
                .pointerInteropFilter { ev ->
                    val w = layoutWidthPx
                    if (w <= 0f) return@pointerInteropFilter false

                    val inRightEdge = ev.x >= (w - edgeWidthPx)
                    if (!inRightEdge) {
                        edgeCandidate = false
                        edgeTriggered = false
                        return@pointerInteropFilter false
                    }
                    if (onPageNext == null && onPagePrev == null) return@pointerInteropFilter false

                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            edgeCandidate = true
                            edgeTriggered = false
                            edgeStartX = ev.x
                            edgeStartY = ev.y
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!edgeCandidate || edgeTriggered) return@pointerInteropFilter true
                            val dx = ev.x - edgeStartX
                            val dy = ev.y - edgeStartY
                            if (abs(dy) > pageVerticalSlopPx) return@pointerInteropFilter true
                            if (dx <= -pageSwipeThresholdPx) {
                                edgeTriggered = true
                                onPageNext?.invoke()
                                true
                            } else if (dx >= pageSwipeThresholdPx) {
                                edgeTriggered = true
                                onPagePrev?.invoke()
                                true
                            } else true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val was = edgeCandidate
                            edgeCandidate = false
                            edgeTriggered = false
                            was
                        }
                        else -> true
                    }
                }
        ) {
            // Ambient aura (subtle)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            goblinBrown.copy(alpha = breathe),
                            goblinOrange.copy(alpha = breathe * 0.35f),
                            Color.Transparent
                        ),
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }

            // Main interaction zone (swipe + hardware back only)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(8.dp) // symmetric padding keeps the ring centered (a touch more bezel margin)
                    .pointerInteropFilter { ev ->
                        // Don't steal the right edge (paging)
                        val w = layoutWidthPx
                        if (w > 0f && ev.x >= (w - edgeWidthPx)) return@pointerInteropFilter false

                        when (ev.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                swipeActive = true
                                swipeArmed = false
                                downX = ev.x
                                downY = ev.y
                                startValue = value01.coerceIn(0f, 1f)
                                lastSteps = 0
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!swipeActive) return@pointerInteropFilter false
                                val dx = ev.x - downX
                                val dy = ev.y - downY

                                if (!swipeArmed) {
                                    if (abs(dy) >= armDistPx && abs(dx) <= offAxisMaxPx) swipeArmed = true
                                }

                                if (!swipeArmed) return@pointerInteropFilter true
                                if (abs(dx) > offAxisMaxPx) return@pointerInteropFilter true

                                // Steps based on vertical travel:
                                // DOWN => +, UP => -
                                val steps = (dy / stepPx).toInt()
                                if (steps != lastSteps) {
                                    val vNew = startValue + steps * stepValue
                                    val doHaptic = abs(steps - lastSteps) >= 1
                                    applyValue(vNew, doHaptic)
                                    lastSteps = steps
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                val was = swipeActive
                                swipeActive = false
                                swipeArmed = false
                                was
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                FftGainKnobVisual(
                    value01 = displayValue,
                    pulse = pulse,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // RESET at the very bottom edge (visual small, hit big)
            IconButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastResetTapMs <= resetDoubleTapWindowMs) {
                        lastResetTapMs = 0L
                        onResetToDefault()
                        pulseKey += 1
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        lastResetTapMs = now
                        resetArmPulseKey += 1
                        // first tap arms reset; no action to avoid accidents
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp)
                    .offset(y = 18.dp)
                    .size(72.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val a = (resetArmPulse.value * 0.55f).coerceIn(0f, 0.55f)
                        // soft blink ring
                        drawCircle(
                            color = goblinOrange.copy(alpha = a),
                            radius = size.minDimension * 0.48f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.12f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset",
                        tint = goblinOrange.copy(alpha = 0.92f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}