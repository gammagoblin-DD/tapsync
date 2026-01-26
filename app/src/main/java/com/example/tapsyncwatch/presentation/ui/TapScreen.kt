@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.tapsyncwatch.presentation.ui

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.input.osc.OscOutputSender
import kotlin.math.*

/* =========================================================
 * HAPTICS
 * ========================================================= */

private enum class HapticType { TAP, ACTION }

@Composable
private fun rememberHaptics(): (HapticType) -> Unit {
    val context = LocalContext.current
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    return { type ->
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        if (type == HapticType.TAP) 20L else 35L,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
        } catch (_: Throwable) {}
    }
}

/* =========================================================
 * TAP SCREEN
 * ========================================================= */

@Composable
fun TapScreen(
    bpm: Float,
    showBpm: Boolean,
    action: ActionEngine,
    osc: OscOutputSender,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics

    val cx = metrics.widthPixels / 2f
    val cy = metrics.heightPixels / 2f
    val radius = min(cx, cy)

    val ringOuter = radius * 0.98f
    val ringInner = radius * 0.50f

    fun isLeftBezel(angle: Float): Boolean =
        angle >= 110f || angle <= -110f

    var centerZone by remember { mutableStateOf(true) }
    var nudgeActive by remember { mutableStateOf(false) }

    var downTime by remember { mutableStateOf(0L) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var lastAngle by remember { mutableStateOf(0f) }
    var swipeHandled by remember { mutableStateOf(false) }

    val longPressMs = 600L
    val swipeThreshold = 70f

    val flash = remember { Animatable(0f) }
    var tapFlash by remember { mutableStateOf(0) }

    val haptic = rememberHaptics()

    /* ================= OSC STATUS ================= */

    val oscStatus by osc.status.collectAsState()
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(oscStatus.lastSentAt) {
        oscStatus.lastSentAt ?: return@LaunchedEffect
        pulse.snapTo(1.5f)
        pulse.animateTo(1f, tween(220))
    }

    LaunchedEffect(tapFlash) {
        flash.snapTo(0f)
        flash.animateTo(1f, tween(120))
        flash.animateTo(0f, tween(500))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        /* ================= BACKGROUND ================= */

        Image(
            painter = painterResource(R.drawable.goblin),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Image(
            painter = painterResource(R.drawable.goblin_flash),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(flash.value)
        )

        /* ================= TOUCH LAYER ================= */

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downTime = SystemClock.elapsedRealtime()
                            startX = event.x
                            startY = event.y
                            swipeHandled = false
                            nudgeActive = false

                            val dx = event.x - cx
                            val dy = event.y - cy
                            val dist = hypot(dx, dy)
                            val angle = Math.toDegrees(
                                atan2(dy, dx).toDouble()
                            ).toFloat()

                            centerZone =
                                !(dist in ringInner..ringOuter && isLeftBezel(angle))
                            lastAngle = angle
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {

                            if (!centerZone) {
                                val angle = Math.toDegrees(
                                    atan2(event.y - cy, event.x - cx).toDouble()
                                ).toFloat()

                                val delta = angle - lastAngle
                                lastAngle = angle

                                if (!nudgeActive && abs(delta) > 3f) {
                                    nudgeActive = true
                                    haptic(HapticType.TAP)
                                    if (delta > 0)
                                        action.nudgePushStart()
                                    else
                                        action.nudgePullStart()
                                }
                                return@pointerInteropFilter true
                            }

                            if (!swipeHandled) {
                                val dx = event.x - startX
                                val dy = event.y - startY

                                if (abs(dy) > swipeThreshold && abs(dy) > abs(dx)) {
                                    swipeHandled = true
                                    haptic(HapticType.ACTION)
                                    if (dy < 0) action.multiply() else action.divide()
                                    return@pointerInteropFilter true
                                }

                                if (dx < -swipeThreshold && abs(dx) > abs(dy)) {
                                    swipeHandled = true
                                    haptic(HapticType.ACTION)
                                    action.resync()
                                    return@pointerInteropFilter true
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {

                            if (!centerZone && nudgeActive) {
                                haptic(HapticType.TAP)
                                action.nudgePushEnd()
                                action.nudgePullEnd()
                                return@pointerInteropFilter true
                            }

                            if (!swipeHandled && centerZone) {
                                val dt = SystemClock.elapsedRealtime() - downTime
                                if (dt >= longPressMs) {
                                    onLongPress()
                                } else {
                                    haptic(HapticType.TAP)
                                    tapFlash++
                                    action.tap()
                                }
                            }
                            true
                        }

                        else -> false
                    }
                }
        )

        /* =====================================================
         * OSC STATUS DOT
         * ===================================================== */

        val density = LocalDensity.current.density

        val angleRad = Math.toRadians(325.0)
        val dotRadiusPx = radius * 0.95f

        val offsetXPx = cos(angleRad).toFloat() * dotRadiusPx
        val offsetYPx = -sin(angleRad).toFloat() * dotRadiusPx

        val offsetXDp = (offsetXPx / density).dp
        val offsetYDp = (offsetYPx / density).dp

        val dotColor =
            if (oscStatus.connected) Color(0xFFD98A2B)
            else Color(0xFF6E6A63)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetXDp, y = offsetYDp)
        ) {

            if (oscStatus.lastSentAt != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(1f + (pulse.value - 1f) * 0.4f)
                        .alpha(0.55f)
                        .background(
                            color = lerp(
                                Color(0xFFD98A2B),
                                Color(0xFFE85D9E),
                                (pulse.value - 1f).coerceIn(0f, 1f)
                            ),
                            shape = CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(4.dp)
                    .align(Alignment.Center)
                    .background(dotColor, CircleShape)
            )
        }

        /* ================= BPM DISPLAY ================= */

        if (showBpm) {
            Text(
                text = if (bpm > 0f) "${bpm.toInt()} BPM" else "-- BPM",
                color = Color.White.copy(alpha = if (bpm > 0f) 1f else 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

    }
}
