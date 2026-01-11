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
import com.example.tapsyncwatch.presentation.osc.OscSender
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
                SettingsScreen(
                    store = settingsStore,
                    onClose = { showSettings = false }
                )
            } else {
                TapScreen(
                    showBpm = settings!!.showBpm,
                    onTap = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempotap") } },
                    onSwipeUp = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempo/multiply") } },
                    onSwipeDown = { oscScope.launch { OscSender.send("/composition/tempocontroller/tempo/divide") } },
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

@Composable
fun TapScreen(
    showBpm: Boolean,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onNudgePush: () -> Unit,
    onNudgePull: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.toFloat()
    val height = metrics.heightPixels.toFloat()

    var lastTapTime by remember { mutableStateOf(0L) }
    var bpm by remember { mutableStateOf(0f) }

    var tapTrigger by remember { mutableStateOf(0) }
    val flashAlpha = remember { Animatable(0f) }

    var lastAngle by remember { mutableStateOf<Float?>(null) }
    var lastNudgeTime by remember { mutableStateOf(0L) }
    var longPressStart by remember { mutableStateOf(0L) }

    val edge = min(width, height) * 0.28f
    val angleThreshold = 15f
    val nudgeCooldown = 80L
    val swipeThreshold = 70f
    val longPressMs = 600L

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
                val cx = width / 2f
                val cy = height / 2f

                when (event.actionMasked) {

                    MotionEvent.ACTION_DOWN -> {
                        lastAngle = null
                        longPressStart = SystemClock.elapsedRealtime()
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.y - cy
                        if (abs(dy) > swipeThreshold) {
                            if (dy < 0) onSwipeUp() else onSwipeDown()
                            return@pointerInteropFilter true
                        }

                        if (event.x > edge && event.x < width - edge &&
                            event.y > edge && event.y < height - edge
                        ) return@pointerInteropFilter false

                        val angle = Math.toDegrees(
                            atan2(event.y - cy, event.x - cx).toDouble()
                        ).toFloat()

                        val prev = lastAngle
                        lastAngle = angle
                        if (prev == null) return@pointerInteropFilter true

                        var delta = angle - prev
                        if (delta > 180) delta -= 360f
                        if (delta < -180) delta += 360f

                        if (abs(delta) < angleThreshold) return@pointerInteropFilter true

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNudgeTime < nudgeCooldown) return@pointerInteropFilter true
                        lastNudgeTime = now

                        if (delta > 0) onNudgePush() else onNudgePull()
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val now = SystemClock.elapsedRealtime()

                        if (now - longPressStart > longPressMs) {
                            onLongPress()
                            return@pointerInteropFilter true
                        }

                        val delta = now - lastTapTime
                        lastTapTime = now

                        if (delta in 180..2000) {
                            bpm = (60_000f / delta).coerceIn(30f, 300f)
                        }

                        tapTrigger++
                        onTap()
                        true
                    }

                    else -> false
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

        if (showBpm && bpm > 0f) {
            Text(
                text = bpm.toInt().toString(),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = 90.dp)
            )
        }
    }
}
