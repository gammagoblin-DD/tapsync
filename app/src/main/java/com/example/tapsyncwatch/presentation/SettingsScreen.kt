package com.example.tapsyncwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.data.SettingsStore
import com.example.tapsyncwatch.osc.OscListener
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    store: SettingsStore,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings by store.settings.collectAsState(initial = null)
    settings ?: return

    var discoveryStatus by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ---------- TITLE ----------
        Text(
            text = "Settings",
            color = Color.White,
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Divider(color = Color.White.copy(alpha = 0.3f))

        // ---------- IP ----------
        Text(
            text = "Resolume IP",
            color = Color.LightGray
        )
        TextField(
            value = settings!!.ip,
            onValueChange = { scope.launch { store.updateIp(it) } },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                textColor = Color.White,
                backgroundColor = Color.Transparent,
                cursorColor = Color.White,
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Color.Gray
            )
        )

        // ---------- PORT ----------
        Text(
            text = "OSC Port (Resolume IN)",
            color = Color.LightGray
        )
        TextField(
            value = settings!!.port.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { p ->
                    scope.launch { store.updatePort(p) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.textFieldColors(
                textColor = Color.White,
                backgroundColor = Color.Transparent,
                cursorColor = Color.White,
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Color.Gray
            )
        )

        Divider(color = Color.White.copy(alpha = 0.3f))

        // ---------- LISTEN FOR RESOLUME (BROADCAST OSC OUT = 7000) ----------
        Button(
            onClick = {
                if (!listening) {
                    listening = true
                    discoveryStatus =
                        "Waiting for Resolume…\nTap or Resync in Resolume"

                    OscListener.listen(
                        context = context,
                        listenPort = 7000   // ✅ Resolume OSC OUT
                    ) { ip, _ ->
                        scope.launch {
                            store.updateIp(ip)
                            store.updatePort(7002) // ✅ Resolume OSC IN
                            discoveryStatus = "Found Resolume:\n$ip"
                            listening = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (listening) Color.Gray else Color.DarkGray,
                contentColor = Color.White
            )
        ) {
            Text(
                if (listening) "Listening…" else "Listen for Resolume OSC"
            )
        }

        if (discoveryStatus != null) {
            Text(
                text = discoveryStatus!!,
                color = Color.LightGray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Divider(color = Color.White.copy(alpha = 0.3f))

        // ---------- BPM TOGGLE ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show BPM",
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = settings!!.showBpm,
                onCheckedChange = {
                    scope.launch { store.setShowBpm(it) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.LightGray
                )
            )
        }

        Divider(color = Color.White.copy(alpha = 0.3f))

        // ---------- BACK ----------
        Button(
            onClick = {
                OscListener.stop()
                listening = false
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.DarkGray,
                contentColor = Color.White
            )
        ) {
            Text("Back")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
