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
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    osc: OscOutputSender,
    onClose: () -> Unit
) {
    BackHandler { }

    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val settings by store.settings.collectAsState(initial = null)

    var activePreset by remember { mutableStateOf(0) }
    var presets by remember { mutableStateOf(listOf<OscTarget>()) }
    var showBpm by remember { mutableStateOf(true) }
    var showOscDot by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        settings?.let { s ->
            if (!initialized) {
                activePreset = s.activePreset
                presets = s.presets
                showBpm = s.showBpm
                showOscDot = s.showOscDot
                initialized = true
            }
        }
    }

    val current = presets.getOrNull(activePreset)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Settings",
            color = Color.White,
            style = MaterialTheme.typography.h5,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        /* ================= PRESETS ================= */

        SettingsBlock {
            Text("OSC Target Preset", color = Color.White)

            presets.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.name, color = Color.White)
                    RadioButton(
                        selected = activePreset == i,
                        onClick = { activePreset = i }
                    )
                }
            }
        }

        /* ================= EDIT PRESET ================= */

        current?.let { preset ->
            SettingsBlock {

                OutlinedTextField(
                    value = preset.name,
                    onValueChange = { newName ->
                        presets = presets.toMutableList().also { list ->
                            list[activePreset] = preset.copy(name = newName)
                        }
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )

                OutlinedTextField(
                    value = preset.ip,
                    onValueChange = { newIp ->
                        presets = presets.toMutableList().also { list ->
                            list[activePreset] = preset.copy(ip = newIp)
                        }
                    },
                    label = { Text("IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )

                OutlinedTextField(
                    value = preset.port.toString(),
                    onValueChange = { v ->
                        val p = v.filter(Char::isDigit).toIntOrNull() ?: preset.port
                        presets = presets.toMutableList().also { list ->
                            list[activePreset] = preset.copy(port = p)
                        }
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
            }
        }

        /* ================= UI OPTIONS ================= */

        SettingsBlock {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show BPM", color = Color.White)
                Switch(showBpm, { showBpm = it })
            }
        }

        SettingsBlock {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OSC Dot", color = Color.White)
                Switch(showOscDot, { showOscDot = it })
            }
        }

        Text(
            text = "Changes apply on close",
            color = Color.Gray,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        /* ================= SAVE & CLOSE ================= */

        Button(
            onClick = {
                scope.launch {

                    presets.forEachIndexed { i, p ->
                        store.updatePreset(i, p)
                    }

                    store.setActivePreset(activePreset)
                    store.setShowBpm(showBpm)
                    store.setShowOscDot(showOscDot)

                    current?.let {
                        osc.updateTarget(it.ip, it.port)
                    }

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
