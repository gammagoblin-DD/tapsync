package com.example.tapsyncwatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsState(initial = null)

    settings ?: return

    var ip by remember { mutableStateOf(settings!!.ip) }
    var portText by remember { mutableStateOf(settings!!.port.toString()) }
    var showBpm by remember { mutableStateOf(settings!!.showBpm) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Settings",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        /* ---------- IP ---------- */
        Text(text = "Resolume IP")

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        /* ---------- PORT ---------- */
        Text(text = "OSC Port (Resolume IN)")

        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter { c -> c.isDigit() } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        /* ---------- SHOW BPM ---------- */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Show BPM")
            Switch(
                checked = showBpm,
                onCheckedChange = { showBpm = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        /* ---------- SAVE ---------- */
        Button(
            onClick = {
                val port = portText.toIntOrNull() ?: return@Button

                scope.launch {
                    if (ip != settings!!.ip) {
                        settingsStore.updateIp(ip)
                    }
                    if (port != settings!!.port) {
                        settingsStore.updatePort(port)
                    }
                    if (showBpm != settings!!.showBpm) {
                        settingsStore.setShowBpm(showBpm)
                    }
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "OSC IN (from Resolume) uses port 7000 automatically.",
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}
