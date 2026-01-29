@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.domain.clock.ClockVisualState
import com.example.tapsyncwatch.osc.OscHealth
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

/* ================= GOBLIN STYLE ================= */

private val GoblinBrown = Color(0xFF8C5A2B)

private enum class TouchZone { CENTER, RING }
private enum class BezelDir { NONE, CW, CCW }
private enum class BezelState { IDLE, HELD }

@Composable
fun TapScreen(
    showOscDot: Boolean,
    showBpm: Boolean,
    bpm: Double,
    action: ActionEngine,
    oscHealth: StateFlow<OscHealth>,
    clockVisualState: StateFlow<ClockVisualState>,
    clockMode: ClockMode,                 // ✅ NEU
    hapticsEnabled: Boolean,               // Settings: ON / OFF
    downbeatHapticsEnabled: Boolean,
    onLongPress: () -> Unit,
    onOscActivity: ((() -> Unit)) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val metrics = context.resources.displayMetrics

    /* ========= UI HAPTIC GUARD (FINAL) ========= */

    fun uiHapticAllowed(): Boolean =
        clockMode == ClockMode.INTERNAL && hapticsEnabled

    fun lightHaptic() {
        if (uiHapticAllowed()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun strongHaptic() {
        if (uiHapticAllowed()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /* ===== Touch-Geometrie ===== */

    val width = metrics.widthPixels.toFloat()
    val height = metrics.heightPixels.toFloat()
    val cxTouch = width / 2f
    val cyTouch = height / 2f
    val radiusTouch = min(width, height) / 2f

    val ringOuter = radiusTouch * 0.98f
    val ringInner = radiusTouch * 0.50f

    fun isInLeftBezel(angle: Float): Boolean =
        angle >= 110f || angle <= -110f

    var zone by remember { mutableStateOf(TouchZone.CENTER) }
    var bezelDir by remember { mutableStateOf(BezelDir.NONE) }
    var bezelState by remember { mutableStateOf(BezelState.IDLE) }
    var nudgeStarted by remember { mutableStateOf(false) }
    var lastAngle by remember { mutableStateOf(0f) }

    var downTime by remember { mutableStateOf(0L) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var swipeHandled by remember { mutableStateOf(false) }

    val swipeThreshold = 70f
    val longPressMs = 600L

    val flashAlpha = remember { Animatable(0f) }
    var tapTrigger by remember { mutableStateOf(0) }

    /* ================= OSC ================= */

    val oscPulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun pulseOsc() {
        scope.launch {
            oscPulse.snapTo(1f)
            oscPulse.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(Unit) {
        onOscActivity { pulseOsc() }
    }

    LaunchedEffect(tapTrigger) {
        flashAlpha.snapTo(0f)
        flashAlpha.animateTo(1f, tween(120))
        flashAlpha.animateTo(0f, tween(500))
        pulseOsc()
    }

    val health by oscHealth.collectAsState()
    val oscDotColor = when (health) {
        is OscHealth.Sending -> Color(0xFF4CAF50)
        is OscHealth.Error -> Color(0xFFE53935)
        OscHealth.Idle -> Color(0xFFB86CFF)
    }

    /* ================= CLOCK VISUAL ================= */

    val visual by clockVisualState.collectAsState()
    val downbeatPulse = remember { Animatable(0f) }

    LaunchedEffect(visual.downbeatId) {
        if (visual.downbeatId != 0L) {
            downbeatPulse.snapTo(1f)
            downbeatPulse.animateTo(0f, tween(260))

            // ❗ Downbeat-Haptik bleibt bewusst unabhängig vom ClockMode
            if (hapticsEnabled && downbeatHapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    fun overlayAlpha(eventMs: Long?): Float {
        if (eventMs == null) return 0f
        val dt = System.currentTimeMillis() - eventMs
        return when {
            dt < 200 -> 1f
            dt < 800 -> 1f - (dt - 200) / 600f
            else -> 0f
        }
    }

    /* ================= UI ================= */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInteropFilter { event ->

                val dx = event.x - cxTouch
                val dy = event.y - cyTouch
                val dist = hypot(dx, dy)

                when (event.actionMasked) {

                    MotionEvent.ACTION_DOWN -> {
                        downTime = SystemClock.elapsedRealtime()
                        startX = event.x
                        startY = event.y
                        swipeHandled = false
                        nudgeStarted = false

                        val angle =
                            Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

                        zone =
                            if (dist in ringInner..ringOuter && isInLeftBezel(angle))
                                TouchZone.RING
                            else
                                TouchZone.CENTER

                        lastAngle = angle
                        bezelDir = BezelDir.NONE
                        bezelState = BezelState.IDLE
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        if (zone == TouchZone.RING) {
                            val angle = Math.toDegrees(
                                atan2(event.y - cyTouch, event.x - cxTouch).toDouble()
                            ).toFloat()

                            var delta = angle - lastAngle
                            lastAngle = angle

                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f

                            if (!nudgeStarted && abs(delta) > 3f) {
                                nudgeStarted = true
                                bezelDir =
                                    if (delta > 0) BezelDir.CW else BezelDir.CCW
                                bezelState = BezelState.HELD

                                if (bezelDir == BezelDir.CW)
                                    action.nudgePushStart()
                                else
                                    action.nudgePullStart()

                                lightHaptic()
                                tapTrigger++
                            }
                            return@pointerInteropFilter true
                        }

                        if (!swipeHandled && zone == TouchZone.CENTER) {
                            val dxT = event.x - startX
                            val dyT = event.y - startY

                            if (abs(dyT) > swipeThreshold && abs(dyT) > abs(dxT)) {
                                swipeHandled = true
                                if (dyT < 0) action.multiply() else action.divide()
                                lightHaptic()
                                tapTrigger++
                                return@pointerInteropFilter true
                            } else if (abs(dxT) > swipeThreshold && dxT < 0) {
                                swipeHandled = true
                                action.resync()
                                strongHaptic()
                                tapTrigger++
                                return@pointerInteropFilter true
                            }
                        }

                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {

                        if (zone == TouchZone.RING && bezelState == BezelState.HELD) {
                            if (bezelDir == BezelDir.CW)
                                action.nudgePushEnd()
                            else
                                action.nudgePullEnd()

                            bezelState = BezelState.IDLE
                            bezelDir = BezelDir.NONE
                            tapTrigger++
                            return@pointerInteropFilter true
                        }

                        if (!swipeHandled && zone == TouchZone.CENTER) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - downTime >= longPressMs)
                                onLongPress()
                            else {
                                tapTrigger++
                                action.tap()
                                lightHaptic()
                            }
                        }
                        true
                    }

                    else -> true
                }
            },
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
                .alpha(flashAlpha.value)
        )

        /* OSC DOT */
        if (showOscDot) {
            Canvas(
                modifier = Modifier
                    .size(10.dp)
                    .offset(
                        x = (radiusTouch * 0.30f).dp,
                        y = (radiusTouch * 0.22f).dp
                    )
            ) {
                drawCircle(
                    color = oscDotColor,
                    alpha = 0.35f + oscPulse.value * 0.65f
                )
            }
        }

        /* ===== DOWNBEAT RING (WEAR SAFE) ===== */
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val safeRadius = min(size.width, size.height) * 0.82f / 2f

            drawCircle(
                color = GoblinBrown.copy(alpha = 0.28f * downbeatPulse.value),
                radius = safeRadius * (1.0f + downbeatPulse.value * 0.06f),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = Stroke(width = 14f)
            )
        }

        /* MULTIPLY / DIVIDE */
        val multiplyAlpha = overlayAlpha(visual.lastMultiplyMs)
        val divideAlpha = overlayAlpha(visual.lastDivideMs)

        if (multiplyAlpha > 0f || divideAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (radiusTouch * 0.25f).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (multiplyAlpha > divideAlpha) "×2" else "÷2",
                    color = GoblinBrown,
                    modifier = Modifier.alpha(max(multiplyAlpha, divideAlpha))
                )
            }
        }

        /* NUDGE */
        if (visual.nudgeActive) {
            Canvas(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.Center)
            ) {
                drawCircle(
                    color = GoblinBrown.copy(alpha = 0.15f),
                    style = Stroke(width = 6f)
                )
            }
        }
    }
}
