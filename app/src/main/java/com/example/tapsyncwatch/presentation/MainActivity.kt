package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.data.SettingsStore
import com.example.tapsyncwatch.presentation.osc.OscSender
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    onTap = {
                        oscScope.launch {
                            OscSender.send(
                                "/composition/tempocontroller/tempotap"
                            )
                        }
                    },
                    onSwipeUp = {
                        oscScope.launch {
                            OscSender.send(
                                "/composition/tempocontroller/tempo/multiply"
                            )
                        }
                    },
                    onSwipeDown = {
                        oscScope.launch {
                            OscSender.send(
                                "/composition/tempocontroller/tempo/divide"
                            )
                        }
                    },
                    onLongPress = {
                        showSettings = true
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscScope.cancel()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TapScreen(
    showBpm: Boolean,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit
) {
    var trigger by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var bpm by remember { mutableStateOf(0f) }
    var glowLevel by remember { mutableStateOf(0f) }

    val alpha = remember { Animatable(0f) }

    val swipeThresholdPx = 60f
    var gestureConsumed by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        alpha.snapTo(0f)

        alpha.animateTo(
            targetValue = min(1f, 0.6f + glowLevel * 0.4f),
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutSlowInEasing
            )
        )

        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = (500 + glowLevel * 600).toInt(),
                easing = LinearOutSlowInEasing
            )
        )

        glowLevel = max(0f, glowLevel - 0.25f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)

            // TAP + LONG PRESS
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!gestureConsumed) {
                            val now = SystemClock.elapsedRealtime()
                            val delta = now - lastTapTime
                            lastTapTime = now

                            if (delta in 180..2000) {
                                bpm = (60_000f / delta).coerceIn(30f, 300f)
                            }

                            glowLevel = if (delta < 250) {
                                min(1f, glowLevel + 0.45f)
                            } else {
                                max(0f, glowLevel - 0.15f)
                            }

                            trigger++
                            onTap()
                        }
                        gestureConsumed = false
                    },
                    onLongPress = {
                        gestureConsumed = true
                        onLongPress()
                    }
                )
            }

            // SWIPE UP / DOWN
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { gestureConsumed = false },
                    onVerticalDrag = { _, dragAmount ->
                        if (!gestureConsumed && abs(dragAmount) > swipeThresholdPx) {
                            gestureConsumed = true
                            if (dragAmount < 0) onSwipeUp()
                            else onSwipeDown()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {

        Image(
            painter = painterResource(id = R.drawable.goblin),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Image(
            painter = painterResource(id = R.drawable.goblin_flash),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value)
        )

        // ✅ BPM ANZEIGE (war der fehlende Teil)
        if (showBpm && bpm > 0f) {
            Text(
                text = bpm.toInt().toString(),
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 100.dp)
            )
        }
    }
}
