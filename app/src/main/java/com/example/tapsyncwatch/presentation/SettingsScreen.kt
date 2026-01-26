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
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    osc: OscOutputSender,
    onClose: () -> Unit
) {
    BackHandler { /* bewusst leer → nur Save & Close */ }

    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val settings by store.settings.collectAsState(initial = null)

    // ---------- Lokaler Edit-State ----------
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var showBpm by remember { mutableStateOf(true) }
    var showOscDot by remember { mutableStateOf(true) }

    // Initialwerte übernehmen
    LaunchedEffect(settings) {
        settings?.let {
            ip = it.ip
            port = it.port.toString()
            showBpm = it.showBpm
            showOscDot = it.showOscDot
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        /* ================= HEADER ================= */

        Text(
            text = "Settings",
            color = Color.White,
            style = MaterialTheme.typography.h5,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        /* ================= RESOLUME STATUS ================= */

        SettingsBlock {
            val status by osc.status.collectAsState()
            Text(
                text = if (status.connected)
                    "Resolume connected"
                else
                    "Resolume not connected",
                color = if (status.connected)
                    Color(0xFF4CAF50)
                else
                    Color(0xFFFF5252)
            )
        }

        /* ================= OSC TARGET ================= */

        SettingsBlock {

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
        }

        /* ================= UI OPTIONS ================= */

        SettingsBlock {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show BPM", color = Color.White)
                Switch(
                    checked = showBpm,
                    onCheckedChange = { showBpm = it }
                )
            }
        }

        SettingsBlock {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OSC Dot", color = Color.White)
                Switch(
                    checked = showOscDot,
                    onCheckedChange = { showOscDot = it }
                )
            }
        }

        /* ================= SAVE & CLOSE ================= */

        Button(
            onClick = {
                val p = port.toIntOrNull() ?: return@Button
                scope.launch {
                    store.updateIp(ip)
                    store.updatePort(p)
                    store.setShowBpm(showBpm)
                    store.setShowOscDot(showOscDot)

                    // 🔥 WICHTIG: sofort anwenden
                    osc.updateTarget(ip, p)

                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = grayButtonColors(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Save & Close")
        }
    }
}

/* ========================================================= */

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
