@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.data.SettingsStore
import com.example.tapsyncwatch.presentation.network.OscSender
import kotlinx.coroutines.*
import kotlin.math.*

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsStore = SettingsStore(this)

        suspend fun momentaryInt(path: String) {
            OscSender.sendInt(path, 1)
            delay(40)
            OscSender.sendInt(path, 0)
        }

        setContent {
            var showSettings by remember { mutableStateOf(false) }
            val settings by settingsStore.settings.collectAsState(initial = null)
            settings ?: return@setContent

            OscSender.targetIp = settings!!.ip
            OscSender.targetPort = settings!!.port

            if (showSettings) {
                SettingsScreen(store = settingsStore) {
                    showSettings = false
                }
            } else {
                TapScreen(
                    showBpm = settings!!.showBpm,

                    onTap = {
                        oscScope.launch {
                            momentaryInt("/composition/tempocontroller/tempotap")
                        }
                    },

                    onSwipeUp = {
                        oscScope.launch {
                            OscSender.sendInt(
                                "/composition/tempocontroller/tempo/multiply",
                                1
                            )
                        }
                    },

                    onSwipeDown = {
                        oscScope.launch {
                            OscSender.sendInt(
                                "/composition/tempocontroller/tempo/divide",
                                1
                            )
                        }
                    },

                    onResync = {
                        oscScope.launch {
                            momentaryInt("/composition/tempocontroller/resync")
                        }
                    },

                    onNudgePushStart = {
                        oscScope.launch {
                            OscSender.sendInt(
                                "/composition/tempocontroller/tempopush",
                                1
                            )
                        }
                    },
                    onNudgePushEnd = {
                        oscScope.launch {
                            OscSender.sendInt(
                                "/composition/tempocontroller/tempopush",
                                0
                            )
                        }
                    },

                    onNudgePullStart = {
                        oscScope.launch {
                            OscSender.sendInt(
                                "/composition/tempocontroller/tempopull",
                                1
                            )
                        }
                    },
                    onNudgePullEnd = {
                        oscScope.launch {
                            OscSender.sendInt(
                                "/composition/tempocontroller/tempopull",
                                0
                            )
                        }
                    },

                    onLongPress = { showSettings = true }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscScope.cancel()
    }
}

/* ------------------------------------------------------------ */
/* ----------------------- TAP SCREEN -------------------------- */
/* ------------------------------------------------------------ */

private enum class TouchZone { CENTER, RING }
private enum class BezelDir { NONE, CW, CCW }
private enum class BezelState { IDLE, HELD }

@Composable
fun TapScreen(
    showBpm: Boolean,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onResync: () -> Unit,
    onNudgePushStart: () -> Unit,
    onNudgePushEnd: () -> Unit,
    onNudgePullStart: () -> Unit,
    onNudgePullEnd: () -> Unit,
    onLongPress: () -> Unit
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

    LaunchedEffect(tapTrigger) {
        flashAlpha.snapTo(0f)
        flashAlpha.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
        flashAlpha.animateTo(0f, tween(500, easing = LinearOutSlowInEasing))
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

                        /* ---------- BEZEL (RING) – exklusiv ---------- */
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
                                    onNudgePushStart()
                                else
                                    onNudgePullStart()
                            }
                            return@pointerInteropFilter true
                        }

                        /* ---------- SWIPE / RESYNC (CENTER) ---------- */
                        if (!swipeHandled && zone == TouchZone.CENTER) {
                            val dxT = event.x - startX
                            val dyT = event.y - startY

                            if (abs(dyT) > swipeThreshold && abs(dyT) > abs(dxT)) {
                                swipeHandled = true
                                if (dyT < 0) onSwipeUp() else onSwipeDown()
                                return@pointerInteropFilter true
                            } else if (abs(dxT) > swipeThreshold && dxT < 0) {
                                swipeHandled = true
                                onResync()
                                return@pointerInteropFilter true
                            }
                        }

                        return@pointerInteropFilter true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {

                        /* ---------- 1. BEZEL hat Vorrang ---------- */
                        if (zone == TouchZone.RING && bezelState == BezelState.HELD) {
                            if (bezelDir == BezelDir.CW)
                                onNudgePushEnd()
                            else
                                onNudgePullEnd()

                            bezelState = BezelState.IDLE
                            bezelDir = BezelDir.NONE
                            return@pointerInteropFilter true
                        }

                        /* ---------- 2. SWIPE blockiert Tap ---------- */
                        if (swipeHandled) {
                            return@pointerInteropFilter true
                        }

                        /* ---------- 3. CENTER → LongPress oder Tap ---------- */
                        if (zone == TouchZone.CENTER) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - downTime >= longPressMs) {
                                onLongPress()
                            } else {
                                tapTrigger++
                                onTap()
                            }
                        }

                        return@pointerInteropFilter true
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
                    color = Color.Red.copy(alpha = 0.25f),
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
