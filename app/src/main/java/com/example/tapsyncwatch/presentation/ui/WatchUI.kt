package com.example.tapsyncwatch.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun WatchUI(
    bpm: Float,
    onTap: () -> Unit,
    onMultiply: () -> Unit,
    onDivide: () -> Unit,
    onResync: () -> Unit
) {
    var dragAccumulated by remember { mutableStateOf(0f) }
    val swipeThreshold = 60f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .clickable { onTap() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccumulated < -swipeThreshold) {
                            onResync()
                        }
                        dragAccumulated = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulated += dragAmount
                    }
                )
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${bpm.toInt()} BPM",
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.h4
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Text(
                    text = "÷2",
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onDivide() }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "×2",
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onMultiply() }
                )
            }
        }
    }
}
