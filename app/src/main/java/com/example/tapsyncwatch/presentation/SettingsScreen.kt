package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsState
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    osc: OscOutputSender,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val settings by store.settings.collectAsState(
        initial = SettingsState(
            ip = "192.168.178.24",
            port = 7002,
            showBpm = true
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Settings",
                color = Color.White,
                style = MaterialTheme.typography.h5,
                textAlign = TextAlign.Center
            )
        }

        SettingsBlock {
            val status by osc.status.collectAsState()
            Text(
                text = if (status.connected) "OSC connected" else "OSC disconnected",
                color = if (status.connected) Color(0xFF4CAF50) else Color.Red
            )
        }

        SettingsBlock {
            var ip by remember(settings.ip) { mutableStateOf(settings.ip) }
            var port by remember(settings.port) { mutableStateOf(settings.port.toString()) }

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )

            Button(
                onClick = {
                    scope.launch {
                        store.updateIp(ip)
                        store.updatePort(port.toIntOrNull() ?: settings.port)
                        osc.updateTarget(ip, port.toIntOrNull() ?: settings.port)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = grayButtonColors()
            ) {
                Text("Save OSC Target")
            }
        }

        SettingsBlock {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show BPM", color = Color.White)
                Switch(
                    checked = settings.showBpm,
                    onCheckedChange = {
                        scope.launch { store.setShowBpm(it) }
                    }
                )
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = grayButtonColors(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Save & Close")
        }
    }
}

@Composable
private fun SettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun textFieldColors() =
    TextFieldDefaults.outlinedTextFieldColors(
        textColor = Color.White,
        cursorColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.Gray,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.Gray
    )

@Composable
private fun grayButtonColors() =
    ButtonDefaults.buttonColors(
        backgroundColor = Color(0xFF2A2A2A),
        contentColor = Color.White
    )
