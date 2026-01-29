@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.domain.action.ActionEngine
import kotlinx.coroutines.launch
import kotlin.math.*
import androidx.compose.ui.unit.IntOffset


private enum class TouchZone { CENTER, RING }
private enum class BezelDir { NONE, CW, CCW }
private enum class BezelState { IDLE, HELD }

@Composable
fun TapScreen(
    showOscDot: Boolean,
    showBpm: Boolean,     // API-Stabilität (OPTION A)
    bpm: Double,          // bleibt intern, nie angezeigt
    action: ActionEngine,
    onLongPress: () -> Unit,
    onOscActivity: ((() -> Unit)) -> Unit
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics

    val width = metrics.widthPixels.toFloat()
    val height = metrics.heightPixels.toFloat()
    val cx = width / 2f
    val cy = height / 2f
    val radius = min(width, height) / 2f

    val ringOuter = radius * 0.98f
    val ringInner = radius * 0.50f

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

    /* =====================================================
     * OSC DOT PULSE (visual only)
     * ===================================================== */

    val oscPulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun pulseOsc() {
        scope.launch {
            oscPulse.snapTo(1f)
            oscPulse.animateTo(
                0f,
                tween(220, easing = FastOutSlowInEasing)
            )
        }
    }

    // Register callback ONCE
    LaunchedEffect(Unit) {
        onOscActivity {
            pulseOsc()
        }
    }

    // Watch → Resolume actions also pulse
    LaunchedEffect(tapTrigger) {
        flashAlpha.snapTo(0f)
        flashAlpha.animateTo(1f, tween(120))
        flashAlpha.animateTo(0f, tween(500))
        pulseOsc()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInteropFilter { event ->

                val dx = event.x - cx
                val dy = event.y - cy
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
                                atan2(event.y - cy, event.x - cx).toDouble()
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
                                tapTrigger++
                                return@pointerInteropFilter true
                            } else if (abs(dxT) > swipeThreshold && dxT < 0) {
                                swipeHandled = true
                                action.resync()
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

        if (zone == TouchZone.RING) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFFB86CFF).copy(alpha = 0.25f),
                    startAngle = 110f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        cx - ringOuter,
                        cy - ringOuter
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        ringOuter * 2,
                        ringOuter * 2
                    ),
                    style = Stroke(width = ringOuter - ringInner)
                )
            }
        }

        /* =================================================
         * OSC STATUS DOT – legacy ring position (FINAL)
         * ================================================= */

        if (showOscDot) {

            // relative position inside ring (matches legacy pink marker)
            val dotDx = radius * 0.30f
            val dotDy = radius * 0.22f

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(
                            x = dotDx.dp,
                            y = dotDy.dp
                        )
                ) {
                    drawCircle(
                        color = Color(0xFFB86CFF), // grafiknah
                        alpha = 0.35f + oscPulse.value * 0.65f
                    )
                }
            }
        }






        // BPM intentionally never rendered (OPTION A)
    }
}
