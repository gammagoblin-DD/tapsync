package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.presentation.data.DEFAULT_SETTINGS_STATE
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

/* ================= THEME ================= */

private val GoblinBg = Color(0xFF0B0B0B)
private val GoblinCard = Color(0xFF131313)
private val GoblinBorder = Color(0xFF2B2B2B)
private val GoblinAccent = Color(0xFFB86CFF)
private val GoblinText = Color(0xFFEDEDED)
private val GoblinDim = Color(0xFF9A9A9A)
private val GoblinOk = Color(0xFF4CAF50)
private val GoblinBad = Color(0xFFE53935)

/* ================= BLOCK ================= */

@Composable
private fun SettingsBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(GoblinCard)
            .border(1.dp, GoblinBorder, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = GoblinAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = GoblinDim,
                fontSize = 12.sp
            )
        }
        content()
    }
}

@Composable
private fun GoblinSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = GoblinText)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoblinAccent,
                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.DarkGray,
                uncheckedTrackColor = GoblinBorder,
                disabledCheckedThumbColor = GoblinAccent.copy(alpha = 0.35f),
                disabledUncheckedThumbColor = Color.DarkGray.copy(alpha = 0.35f)
            )
        )
    }
}

@Composable
private fun GoblinTextField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    TextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(placeholder, color = GoblinDim) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.textFieldColors(
            textColor = GoblinText,
            backgroundColor = GoblinCard,
            cursorColor = GoblinAccent,
            focusedIndicatorColor = GoblinAccent.copy(alpha = 0.7f),
            unfocusedIndicatorColor = GoblinBorder
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/* ================= NET HELPERS ================= */

private fun getLocalIp(): String? {
    return try {
        val ifaces = NetworkInterface.getNetworkInterfaces()
        while (ifaces.hasMoreElements()) {
            val iface = ifaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val a = addrs.nextElement()
                val host = a.hostAddress ?: continue
                if (host.contains(":")) continue // IPv6 skip
                return host
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private suspend fun ping(ip: String, timeoutMs: Int = 350): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        } catch (_: Exception) {
            false
        }
    }
}

/* ================= SCREEN ================= */

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
    val s = settings

    val scope = rememberCoroutineScope()

    BackHandler { onClose() }

    // Network status (active target)
    var localIp by remember { mutableStateOf<String?>(null) }
    var reachable by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        // NetworkInterface enumeration can be slow on some devices; do it off the main thread.
        localIp = withContext(Dispatchers.IO) { getLocalIp() }
    }

    LaunchedEffect(s.activeTarget.ip, s.activeTarget.port) {
        // Let the screen render first, then start network checks.
        delay(300)
        while (isActive) {
            reachable = ping(s.activeTarget.ip)
            delay(2000)
        }
    }

    // Animation details collapsing
    // Default collapsed keeps Settings snappy; user can expand when needed.
    var animDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoblinBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {

            Text(
                text = "SETTINGS",
                color = GoblinText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {

                item {
                    SettingsBlock(
                        title = "Display",
                        subtitle = "UI-only: nothing here changes the clock."
                    ) {
                        GoblinSwitchRow(
                            label = "OSC Statuspunkt",
                            checked = s.showOscDot,
                            onChange = { v -> scope.launch { settingsStore.setShowOscDot(v) } }
                        )

                        GoblinSwitchRow(
                            label = "External BPM Monitor",
                            checked = s.showExternalBpm,
                            onChange = { v -> scope.launch { settingsStore.setShowExternalBpm(v) } }
                        )

                        GoblinSwitchRow(
                            label = "OSC Debug Overlay",
                            checked = s.showOscDebug,
                            onChange = { v -> scope.launch { settingsStore.setShowOscDebug(v) } }
                        )
                    }
                }

                item {
                    SettingsBlock(
                        title = "Phase Visuals",
                        subtitle = "Downbeat marker + phase ring / spiral."
                    ) {
                        GoblinSwitchRow(
                            label = "Phase Visualizer (Ring)",
                            checked = s.phaseVisualizerEnabled,
                            onChange = { v -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } }
                        )
                        GoblinSwitchRow(
                            label = "Phase Spiral (Stability)",
                            checked = s.phaseSpiralEnabled,
                            onChange = { v -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } }
                        )
                    }
                }

                item {
                    SettingsBlock(
                        title = "Animations",
                        subtitle = "Remote ghost mode is visual-only."
                    ) {
                        GoblinSwitchRow(
                            label = "Animations",
                            checked = s.animationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (animDetails) "Details: ON" else "Details: OFF",
                                color = GoblinDim
                            )
                            Switch(
                                checked = animDetails,
                                onCheckedChange = { animDetails = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GoblinAccent,
                                    checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                    uncheckedThumbColor = Color.DarkGray,
                                    uncheckedTrackColor = GoblinBorder
                                )
                            )
                        }

                        AnimatedVisibility(visible = animDetails) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Divider(color = GoblinBorder)

                                GoblinSwitchRow(
                                    label = "Remote Animations",
                                    checked = s.remoteAnimationsEnabled,
                                    enabled = s.animationsEnabled,
                                    onChange = { v -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } }
                                )

                                GoblinSwitchRow(
                                    label = "Remote Ghost Mode",
                                    checked = s.remoteGhostModeEnabled,
                                    enabled = s.animationsEnabled && s.remoteAnimationsEnabled,
                                    onChange = { v -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } }
                                )

                                GoblinSwitchRow(
                                    label = "Goblin Flash",
                                    checked = s.goblinFlashEnabled,
                                    enabled = s.animationsEnabled,
                                    onChange = { v -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } }
                                )

                                GoblinSwitchRow(
                                    label = "Ripples",
                                    checked = s.rippleEnabled,
                                    enabled = s.animationsEnabled,
                                    onChange = { v -> scope.launch { settingsStore.setRippleEnabled(v) } }
                                )

                                GoblinSwitchRow(
                                    label = "OSC Pulse",
                                    checked = s.oscPulseEnabled,
                                    enabled = s.animationsEnabled,
                                    onChange = { v -> scope.launch { settingsStore.setOscPulseEnabled(v) } }
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsBlock(
                        title = "Clock",
                        subtitle = "External BPM stays read-only."
                    ) {
                        GoblinSwitchRow(
                            label = if (s.clockEnabled) "Internal Clock ON" else "Internal Clock OFF",
                            checked = s.clockEnabled,
                            onChange = { v -> scope.launch { settingsStore.setClockEnabled(v) } }
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = s.clockMode == ClockMode.EXTERNAL,
                                    onClick = { scope.launch { settingsStore.setClockMode(ClockMode.EXTERNAL) } },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoblinAccent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("External (OSC)", color = GoblinText)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = s.clockMode == ClockMode.INTERNAL,
                                    onClick = { scope.launch { settingsStore.setClockMode(ClockMode.INTERNAL) } },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoblinAccent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Internal (Watch)", color = GoblinText)
                            }
                        }
                    }
                }

                item {
                    SettingsBlock(
                        title = "Feedback",
                        subtitle = "Haptics only used in INTERNAL mode."
                    ) {
                        GoblinSwitchRow(
                            label = "Haptics",
                            checked = s.hapticsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setHapticsEnabled(v) } }
                        )

                        GoblinSwitchRow(
                            label = "Downbeat Haptic",
                            checked = s.downbeatHapticsEnabled,
                            enabled = s.hapticsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setDownbeatHapticsEnabled(v) } }
                        )

                        GoblinSwitchRow(
                            label = "Transport Haptic",
                            checked = s.transportHapticsEnabled,
                            enabled = s.hapticsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setTransportHapticsEnabled(v) } }
                        )
                    }
                }

                item {
                    SettingsBlock(
                        title = "Network",
                        subtitle = "Quick health check for the active OSC target."
                    ) {
                        Text("Local IP: ${localIp ?: "-"}", color = GoblinText)
                        Text("Target: ${s.activeTarget.ip}:${s.activeTarget.port}", color = GoblinText)

                        val (label, color) = when (reachable) {
                            true -> "Reachable" to GoblinOk
                            false -> "Unreachable" to GoblinBad
                            null -> "Checking…" to GoblinDim
                        }

                        Text("Ping: $label", color = color)
                    }
                }

                item {
                    SettingsBlock(
                        title = "OSC Presets",
                        subtitle = "Pick a target, edit IP/port, save."
                    ) {
                        // Note: keeping presets inside this block to preserve the "card" look,
                        // but using LazyColumn for the whole screen avoids composing everything at once.
                        for (i in s.presets.indices) {
                            val preset = s.presets[i]

                            var name by remember(preset.name) { mutableStateOf(preset.name) }
                            var ip by remember(preset.ip) { mutableStateOf(preset.ip) }
                            var port by remember(preset.port) { mutableStateOf(preset.port.toString()) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, GoblinBorder, MaterialTheme.shapes.medium)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = s.activePreset == i,
                                        onClick = { scope.launch { settingsStore.setActivePreset(i) } },
                                        colors = RadioButtonDefaults.colors(selectedColor = GoblinAccent)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            color = GoblinText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${preset.ip}:${preset.port}",
                                            color = GoblinDim,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                GoblinTextField(
                                    value = name,
                                    onValue = { name = it },
                                    placeholder = "Name",
                                    keyboardType = KeyboardType.Text
                                )

                                GoblinTextField(
                                    value = ip,
                                    onValue = { ip = it },
                                    placeholder = "IP (z.B. 192.168.178.24)",
                                    keyboardType = KeyboardType.Text
                                )

                                GoblinTextField(
                                    value = port,
                                    onValue = { port = it.filter { ch: Char -> ch.isDigit() }.take(5) },
                                    placeholder = "Port (z.B. 7002)",
                                    keyboardType = KeyboardType.Number
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            val p = port.toIntOrNull() ?: preset.port
                                            scope.launch { settingsStore.updatePreset(i, name, ip, p) }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = GoblinAccent,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text("Save", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(Modifier.width(10.dp))

                                    Button(
                                        onClick = { scope.launch { settingsStore.setActivePreset(i) } },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = GoblinBorder,
                                            contentColor = GoblinText
                                        )
                                    ) {
                                        Text("Use")
                                    }
                                }
                            }

                            if (i != s.presets.lastIndex) {
                                Divider(color = GoblinBorder, modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GoblinAccent,
                    contentColor = Color.Black
                )
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}
