@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.tapsyncwatch.presentation.ui

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.domain.action.ActionEngine
import kotlin.math.*

/* =========================================================
 * HAPTICS (ABSOLUT CRASH-SAFE)
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
                val duration = if (type == HapticType.TAP) 20L else 35L
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        duration,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
        } catch (_: Throwable) {
            // darf niemals crashen
        }
    }
}

/* =========================================================
 * TAP SCREEN
 * ========================================================= */

private enum class TouchZone { CENTER, RING }
private enum class BezelDir { NONE, CW, CCW }

@Composable
fun TapScreen(
    showBpm: Boolean,
    action: ActionEngine,
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

    var zone by remember { mutableStateOf(TouchZone.CENTER) }
    var bezelDir by remember { mutableStateOf(BezelDir.NONE) }
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

    LaunchedEffect(tapFlash) {
        flash.snapTo(0f)
        flash.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
        flash.animateTo(0f, tween(500, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {

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
                            bezelDir = BezelDir.NONE

                            val dx = event.x - cx
                            val dy = event.y - cy
                            val dist = hypot(dx, dy)
                            val angle = Math.toDegrees(
                                atan2(dy, dx).toDouble()
                            ).toFloat()

                            zone =
                                if (dist in ringInner..ringOuter && isLeftBezel(angle))
                                    TouchZone.RING
                                else
                                    TouchZone.CENTER

                            lastAngle = angle
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {

                            // ---------------- NUDGE (Bezel) ----------------
                            if (zone == TouchZone.RING) {
                                val angle = Math.toDegrees(
                                    atan2(event.y - cy, event.x - cx).toDouble()
                                ).toFloat()

                                val delta = angle - lastAngle
                                lastAngle = angle

                                if (!nudgeActive && abs(delta) > 3f) {
                                    nudgeActive = true
                                    bezelDir =
                                        if (delta > 0) BezelDir.CW else BezelDir.CCW

                                    haptic(HapticType.TAP)

                                    if (bezelDir == BezelDir.CW)
                                        action.nudgePushStart()
                                    else
                                        action.nudgePullStart()
                                }
                                return@pointerInteropFilter true
                            }

                            // ---------------- CENTER SWIPES ----------------
                            if (!swipeHandled && zone == TouchZone.CENTER) {
                                val dx = event.x - startX
                                val dy = event.y - startY

                                // VERTICAL → MULTIPLY / DIVIDE
                                if (abs(dy) > swipeThreshold && abs(dy) > abs(dx)) {
                                    swipeHandled = true
                                    haptic(HapticType.ACTION)

                                    if (dy < 0)
                                        action.multiply()
                                    else
                                        action.divide()

                                    return@pointerInteropFilter true
                                }

                                // HORIZONTAL LEFT → RESYNC
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

                            if (zone == TouchZone.RING && nudgeActive) {
                                haptic(HapticType.TAP)

                                if (bezelDir == BezelDir.CW)
                                    action.nudgePushEnd()
                                else
                                    action.nudgePullEnd()

                                return@pointerInteropFilter true
                            }

                            if (!swipeHandled && zone == TouchZone.CENTER) {
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

        if (showBpm) {
            Text(
                text = "",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = 90.dp)
            )
        }
    }
}
