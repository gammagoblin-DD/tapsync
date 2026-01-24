package com.example.tapsyncwatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.presentation.data.SettingsState
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {

    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    // 🔒 MAXIMAL KOMPATIBEL: kein Lifecycle-Compose, kein KeyboardOptions
    val settings by settingsStore.settings.collectAsState(
        initial = SettingsState(
            ip = "192.168.178.24",
            port = 7002,
            showBpm = true
        )
    )

    var ipText by remember(settings.ip) {
        mutableStateOf(settings.ip)
    }

    var portText by remember(settings.port) {
        mutableStateOf(settings.port.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "OSC Settings",
            style = MaterialTheme.typography.h6
        )

        // ===============================
        // IP ADDRESS
        // ===============================
        OutlinedTextField(
            value = ipText,
            onValueChange = { value ->
                ipText = value
            },
            label = { Text("Target IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                scope.launch {
                    settingsStore.updateIp(ipText)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save IP")
        }

        // ===============================
        // PORT
        // ===============================
        OutlinedTextField(
            value = portText,
            onValueChange = { value ->
                portText = value.filter { it.isDigit() }
            },
            label = { Text("Target Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val port = portText.toIntOrNull() ?: return@Button
                scope.launch {
                    settingsStore.updatePort(port)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Port")
        }

        // ===============================
        // SHOW BPM
        // ===============================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Show BPM")
            Switch(
                checked = settings.showBpm,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsStore.setShowBpm(enabled)
                    }
                }
            )
        }
    }
}
