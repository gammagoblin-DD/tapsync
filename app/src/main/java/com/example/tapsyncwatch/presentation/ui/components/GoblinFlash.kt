package com.example.tapsyncwatch.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun GoblinFlash(
    phase: Double
) {
    var flashVisible by remember { mutableStateOf(false) }
    var lastPhase by remember { mutableStateOf(phase) }

    LaunchedEffect(phase) {

        // ✅ Beat-Flanke erkennen (Wrap von ~1.0 → 0.0)
        val beatJustHappened = lastPhase > 0.9 && phase < 0.1

        if (beatJustHappened) {
            flashVisible = true
            delay(40)
            flashVisible = false
        }

        lastPhase = phase
    }

    if (flashVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )
    }
}
