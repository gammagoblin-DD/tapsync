package com.example.tapsyncwatch.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.presentation.ui.components.GoblinFlash
import kotlinx.coroutines.delay
import com.example.tapsyncwatch.presentation.ui.components.ResyncFlash


@Composable
fun WatchUI(
    clock: Clock,
    onTap: () -> Unit,
    onMultiply: () -> Unit,
    onDivide: () -> Unit,
    onResync: () -> Unit
) {

    // =========================
    // 1️⃣ Lokaler UI-Refresh
    // =========================

    var bpm by remember { mutableStateOf(clock.getBpm()) }
    var phase by remember { mutableStateOf(clock.getPhase()) }
    var resyncFlashTrigger by remember { mutableStateOf(false) }
    var phaseOverride by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            bpm = clock.getBpm()
            phase = phaseOverride ?: clock.getPhase()
            if (phaseOverride != null) {
                delay(100)
                phaseOverride = null
            }

            delay(16L) // ca. 60 FPS
        }
    }

    // =========================
    // 2️⃣ Layout-Grundfläche
    // =========================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = {
                        resyncFlashTrigger = true
                        phaseOverride = 0.0
                        onResync()
                    }

                )
            },
        contentAlignment = Alignment.Center
    ) {

        ResyncFlash(
            trigger = resyncFlashTrigger,
            onConsumed = { resyncFlashTrigger = false }
        )

        // 👹 Goblin Flash (Hintergrund)
        GoblinFlash(phase = phase)

        // 📊 UI (Vordergrund)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = bpm.toInt().toString(),
                fontSize = 42.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Phase: ${"%.2f".format(phase)}",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "×2",
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { onMultiply() }
                    }
                )

                Text(
                    text = "÷2",
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { onDivide() }
                    }
                )
            }
        }
    }
}
