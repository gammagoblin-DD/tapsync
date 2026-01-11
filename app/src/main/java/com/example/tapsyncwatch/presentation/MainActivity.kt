package com.example.tapsyncwatch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.osc.OscSender
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    // 🔒 Eigener IO-Scope für OSC (crash-sicher)
    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TapScreen(
                onTap = { sendOscTapSafe() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscScope.cancel()
    }

    private fun sendOscTapSafe() {
        oscScope.launch {
            try {
                OscSender.sendTap()
            } catch (_: Exception) {
                // niemals crashen lassen
            }
        }
    }
}

@Composable
fun TapScreen(onTap: () -> Unit) {

    var trigger by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var glowLevel by remember { mutableStateOf(0f) }

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        alpha.snapTo(0f)

        // 🌱 Weiches Einfaden
        alpha.animateTo(
            targetValue = min(1f, 0.6f + glowLevel * 0.4f),
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutSlowInEasing
            )
        )

        // 🌊 Träger Fade-Out (Afterglow)
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
            .clickable {
                val now = System.currentTimeMillis()
                val delta = now - lastTapTime
                lastTapTime = now

                if (delta < 250) {
                    glowLevel = min(1f, glowLevel + 0.45f)
                } else {
                    glowLevel = max(0f, glowLevel - 0.15f)
                }

                trigger++
                onTap()
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
    }
}
