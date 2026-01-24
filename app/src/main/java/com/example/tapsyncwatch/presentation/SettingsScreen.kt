package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext   // ✅ FIX
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsState
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    osc: OscOutputSender,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val settingsStore = remember { SettingsStore(context) }

    val settings by settingsStore.settings.collectAsState(
        initial = SettingsState(
            ip = "192.168.178.24",
            port = 7002,
            showBpm = true
        )
    )

    val oscStatus by osc.status.collectAsState()

    var ipText by remember(settings.ip) { mutableStateOf(settings.ip) }
    var portText by remember(settings.port) {
        mutableStateOf(settings.port.toString())
    }

    var showSaved by remember { mutableStateOf(false) }

    fun savedFeedbackAndClose() {
        scope.launch {
            showSaved = true
            delay(600)
            showSaved = false
            delay(200)
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Text("Settings", style = MaterialTheme.typography.h5, color = Color.White)

        // ===============================
        // STATUS
        // ===============================
        Card(backgroundColor = Color(0xFF1A1A1A)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (oscStatus.connected) "OSC connected" else "OSC disconnected",
                    color = if (oscStatus.connected) Color(0xFF4CAF50) else Color.Red
                )
                oscStatus.lastSentLabel?.let {
                    Text(
                        text = "Last sent: $it",
                        color = Color.Gray,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        // ===============================
        // OSC TARGET
        // ===============================
        Card(backgroundColor = Color(0xFF1A1A1A)) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        scope.launch {
                            settingsStore.updateIp(ipText)
                            osc.updateTarget(ipText, settings.port)
                            savedFeedbackAndClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save IP")
                }

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: return@Button
                        scope.launch {
                            settingsStore.updatePort(port)
                            osc.updateTarget(settings.ip, port)
                            savedFeedbackAndClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Port")
                }
            }
        }

        // ===============================
        // DISPLAY
        // ===============================
        Card(backgroundColor = Color(0xFF1A1A1A)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show BPM", color = Color.White)
                Switch(
                    checked = settings.showBpm,
                    onCheckedChange = {
                        scope.launch {
                            settingsStore.setShowBpm(it)
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showSaved,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "Saved ✓",
                color = Color(0xFF4CAF50),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
