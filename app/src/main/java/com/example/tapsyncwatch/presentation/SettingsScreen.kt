package com.example.tapsyncwatch.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.presentation.data.SettingsState
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val settings by settingsStore.settings.collectAsState(
        initial = SettingsState(
            ip = "192.168.178.24",
            port = 7002,
            showBpm = true
        )
    )

    var ipText by remember(settings.ip) { mutableStateOf(settings.ip) }
    var portText by remember(settings.port) {
        mutableStateOf(settings.port.toString())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ===============================
            // HEADER
            // ===============================
            Text(
                text = "Settings",
                style = MaterialTheme.typography.h5,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ===============================
            // OSC TARGET CARD
            // ===============================
            Card(
                backgroundColor = Color(0xFF1A1A1A),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        text = "OSC Target",
                        style = MaterialTheme.typography.subtitle1,
                        color = Color.White
                    )

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
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save IP")
                    }

                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it.filter(Char::isDigit)
                        },
                        label = { Text("Port") },
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
                }
            }

            // ===============================
            // DISPLAY CARD
            // ===============================
            Card(
                backgroundColor = Color(0xFF1A1A1A),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Column {
                        Text(
                            text = "Show BPM",
                            color = Color.White,
                            style = MaterialTheme.typography.body1
                        )
                        Text(
                            text = "Display tempo on main screen",
                            color = Color.Gray,
                            style = MaterialTheme.typography.caption
                        )
                    }

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

            Spacer(modifier = Modifier.height(8.dp))

            // ===============================
            // BACK BUTTON
            // ===============================
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Back")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
