@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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

        setContent {
            var showSettings by remember { mutableStateOf(false) }
            val settings by settingsStore.settings.collectAsState(initial = null)
            settings ?: return@setContent

            OscSender.targetIp = settings!!.ip
            OscSender.targetPort = settings!!.port

            if (showSettings) {
                SettingsScreen(store = settingsStore) { showSettings = false }
            } else {
                TapScreen(
                    showBpm = settings!!.showBpm,
                    onTap = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempotap") } },
                    onSwipeUp = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempo/multiply") } },
                    onSwipeDown = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempo/divide") } },
                    onResync = { oscScope.launch { OscSender.send("/composition/tempocontroller/resync") } },
                    onNudgePush = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempopush") } },
                    onNudgePull = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempopull") } },
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

private enum class TouchZone { CENTER, RING }
private enum class SwipeDir { NONE, UP, DOWN, LEFT }

@Composable
fun TapScreen(
    showBpm: Boolean,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onResync: () -> Unit,
    onNudgePush: () -> Unit,
    onNudgePull: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics

    val width = metrics.widthPixels.toFloat()
    val height = metrics.heightPixels.toFloat()
    val cx = width / 2f
    val cy = height / 2f
    val radius = min(width, height) / 2f

    val ringOuter = radius
    val ringInner = radius * 0.72f

    var zone by remember { mutableStateOf(TouchZone.CENTER) }

    var downTime by remember { mutableStateOf(0L) }
    var swipeDir by remember { mutableStateOf(SwipeDir.NONE) }
    var gestureConsumed by remember { mutableStateOf(false) }

    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }

    var lastAngle by remember { mutableStateOf(0f) }
    var rotationSum by remember { mutableStateOf(0f) }
    var lastNudgeTime by remember { mutableStateOf(0L) }

    val swipeThreshold = 70f
    val rotationThreshold = 25f
    val nudgeCooldown = 80L
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
                        swipeDir = SwipeDir.NONE
                        gestureConsumed = false

                        rotationSum = 0f
                        lastAngle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

                        zone = if (dist in ringInner..ringOuter)
                            TouchZone.RING
                        else
                            TouchZone.CENTER
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        if (gestureConsumed) return@pointerInteropFilter true

                        if (zone == TouchZone.RING) {
                            val angle = Math.toDegrees(
                                atan2(event.y - cy, event.x - cx).toDouble()
                            ).toFloat()

                            var delta = angle - lastAngle
                            lastAngle = angle
                            if (delta > 180) delta -= 360f
                            if (delta < -180) delta += 360f

                            rotationSum += delta

                            val now = SystemClock.elapsedRealtime()
                            if (abs(rotationSum) >= rotationThreshold &&
                                now - lastNudgeTime >= nudgeCooldown
                            ) {
                                lastNudgeTime = now
                                gestureConsumed = true
                                if (rotationSum > 0) onNudgePush() else onNudgePull()
                                rotationSum = 0f
                            }
                            return@pointerInteropFilter true
                        }

                        // CENTER – Swipe once
                        if (zone == TouchZone.CENTER && swipeDir == SwipeDir.NONE) {
                            val dxTotal = event.x - startX
                            val dyTotal = event.y - startY

                            if (abs(dyTotal) > swipeThreshold && abs(dyTotal) > abs(dxTotal)) {
                                swipeDir = if (dyTotal < 0) SwipeDir.UP else SwipeDir.DOWN
                                gestureConsumed = true
                                if (swipeDir == SwipeDir.UP) onSwipeUp() else onSwipeDown()
                            } else if (abs(dxTotal) > swipeThreshold && abs(dxTotal) > abs(dyTotal)) {
                                if (dxTotal < 0) {
                                    swipeDir = SwipeDir.LEFT
                                    gestureConsumed = true
                                    onResync()
                                }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {

                        if (gestureConsumed || zone == TouchZone.RING) {
                            return@pointerInteropFilter true
                        }

                        val now = SystemClock.elapsedRealtime()
                        if (now - downTime >= longPressMs) {
                            onLongPress()
                        } else {
                            gestureConsumed = true
                            tapTrigger++
                            onTap()
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
            modifier = Modifier.fillMaxSize().alpha(flashAlpha.value)
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
