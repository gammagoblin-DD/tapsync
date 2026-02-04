package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.presentation.data.DEFAULT_SETTINGS_STATE
import com.example.tapsyncwatch.presentation.data.OscTarget
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
private val GoblinCard2 = Color(0xFF161616)
private val GoblinBorder = Color(0xFF2B2B2B)
private val GoblinAccent = Color(0xFFB86CFF)
private val GoblinText = Color(0xFFEDEDED)
private val GoblinDim = Color(0xFF9A9A9A)
private val GoblinOk = Color(0xFF4CAF50)
private val GoblinBad = Color(0xFFE53935)

/* ================= BLOCK ================= */

private val CardShape = RoundedCornerShape(14.dp)

@Composable
private fun Header(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = GoblinText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Avoid icon dependency: watch-like close action.
        Text(
            text = "×",
            color = GoblinDim,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { onClose() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String? = null,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(GoblinCard)
            .border(1.dp, GoblinBorder, CardShape)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = GoblinAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, color = GoblinDim, fontSize = 12.sp)
                }
            }

            Text(
                text = if (expanded) "▾" else "▸",
                color = GoblinDim,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DividerLine() {
    Divider(color = GoblinBorder, thickness = 1.dp)
}

@Composable
private fun SwitchItem(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    description: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = if (enabled) GoblinText else GoblinDim)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = GoblinDim, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoblinAccent,
                checkedTrackColor = GoblinAccent.copy(alpha = 0.35f),
                uncheckedThumbColor = Color.DarkGray,
                uncheckedTrackColor = GoblinBorder,
                disabledCheckedThumbColor = GoblinAccent.copy(alpha = 0.25f),
                disabledUncheckedThumbColor = Color.DarkGray.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
private fun TextFieldItem(
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
            backgroundColor = GoblinCard2,
            cursorColor = GoblinAccent,
            focusedIndicatorColor = GoblinAccent.copy(alpha = 0.7f),
            unfocusedIndicatorColor = GoblinBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    )
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GoblinCard2 else Color.Transparent)
            .clickable(enabled = enabled) { onSelect() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = GoblinAccent)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (enabled) GoblinText else GoblinDim)
    }
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

    // Slider: useful + watch-like, affects only the quick health-check loop.
    var pingIntervalSec by rememberSaveable { mutableStateOf(2f) }
    var pingTimeoutMs by rememberSaveable { mutableStateOf(350f) }

    LaunchedEffect(Unit) {
        localIp = getLocalIp()
    }

    // keep the ping loop responsive to active target + sliders
    LaunchedEffect(s.activeTarget.ip, s.activeTarget.port, pingIntervalSec, pingTimeoutMs) {
        while (isActive) {
            reachable = ping(s.activeTarget.ip, timeoutMs = pingTimeoutMs.toInt())
            delay((pingIntervalSec * 1000f).toLong())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoblinBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = "Einstellungen", onClose = onClose)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                item {
                    Section(
                        title = "Anzeige",
                        subtitle = "Nur UI – ändert nichts am Clock-Engine-Output.",
                        defaultExpanded = true
                    ) {
                        SwitchItem(
                            label = "OSC Statuspunkt",
                            checked = s.showOscDot,
                            onChange = { v -> scope.launch { settingsStore.setShowOscDot(v) } }
                        )
                        DividerLine()
                        SwitchItem(
                            label = "External BPM Monitor",
                            checked = s.showExternalBpm,
                            onChange = { v -> scope.launch { settingsStore.setShowExternalBpm(v) } }
                        )
                        DividerLine()
                        SwitchItem(
                            label = "OSC Debug Overlay",
                            checked = s.showOscDebug,
                            onChange = { v -> scope.launch { settingsStore.setShowOscDebug(v) } }
                        )
                    }
                }

                item {
                    Section(
                        title = "Phase Visuals",
                        subtitle = "Downbeat Marker + Phase Ring/Spiral."
                    ) {
                        SwitchItem(
                            label = "Phase Ring",
                            checked = s.phaseVisualizerEnabled,
                            onChange = { v -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } }
                        )
                        DividerLine()
                        SwitchItem(
                            label = "Phase Spiral (Stability)",
                            checked = s.phaseSpiralEnabled,
                            onChange = { v -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } }
                        )
                    }
                }

                item {
                    Section(
                        title = "Animationen",
                        subtitle = "Remote Ghost ist rein visuell."
                    ) {
                        SwitchItem(
                            label = "Animationen",
                            checked = s.animationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
                        )

                        DividerLine()

                        SwitchItem(
                            label = "Remote Animationen",
                            checked = s.remoteAnimationsEnabled,
                            enabled = s.animationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } }
                        )

                        DividerLine()

                        SwitchItem(
                            label = "Remote Ghost Mode",
                            checked = s.remoteGhostModeEnabled,
                            enabled = s.animationsEnabled && s.remoteAnimationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } }
                        )

                        DividerLine()

                        SwitchItem(
                            label = "Goblin Flash",
                            checked = s.goblinFlashEnabled,
                            enabled = s.animationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } }
                        )

                        DividerLine()

                        SwitchItem(
                            label = "Ripples",
                            checked = s.rippleEnabled,
                            enabled = s.animationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setRippleEnabled(v) } }
                        )

                        DividerLine()

                        SwitchItem(
                            label = "OSC Pulse",
                            checked = s.oscPulseEnabled,
                            enabled = s.animationsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setOscPulseEnabled(v) } }
                        )
                    }
                }

                item {
                    Section(
                        title = "Clock",
                        subtitle = "External BPM bleibt read-only."
                    ) {
                        SwitchItem(
                            label = "Internal Clock",
                            checked = s.clockEnabled,
                            description = if (s.clockEnabled) "An" else "Aus",
                            onChange = { v -> scope.launch { settingsStore.setClockEnabled(v) } }
                        )

                        Spacer(Modifier.height(4.dp))
                        Text("Clock Mode", color = GoblinDim, fontSize = 12.sp)

                        RadioRow(
                            label = "External (OSC)",
                            selected = s.clockMode == ClockMode.EXTERNAL,
                            onSelect = { scope.launch { settingsStore.setClockMode(ClockMode.EXTERNAL) } }
                        )

                        RadioRow(
                            label = "Internal (Watch)",
                            selected = s.clockMode == ClockMode.INTERNAL,
                            onSelect = { scope.launch { settingsStore.setClockMode(ClockMode.INTERNAL) } }
                        )
                    }
                }

                item {
                    Section(
                        title = "Feedback",
                        subtitle = "Haptics nur in INTERNAL sinnvoll."
                    ) {
                        SwitchItem(
                            label = "Haptics",
                            checked = s.hapticsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setHapticsEnabled(v) } }
                        )
                        DividerLine()
                        SwitchItem(
                            label = "Downbeat Haptic",
                            checked = s.downbeatHapticsEnabled,
                            enabled = s.hapticsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setDownbeatHapticsEnabled(v) } }
                        )
                        DividerLine()
                        SwitchItem(
                            label = "Transport Haptic",
                            checked = s.transportHapticsEnabled,
                            enabled = s.hapticsEnabled,
                            onChange = { v -> scope.launch { settingsStore.setTransportHapticsEnabled(v) } }
                        )
                    }
                }

                item {
                    Section(
                        title = "Netzwerk",
                        subtitle = "Health-Check für das aktive OSC Ziel.",
                        defaultExpanded = false
                    ) {
                        Text("Local IP", color = GoblinDim, fontSize = 12.sp)
                        Text(localIp ?: "-", color = GoblinText)

                        Spacer(Modifier.height(6.dp))

                        Text("Aktives Ziel", color = GoblinDim, fontSize = 12.sp)
                        Text("${s.activeTarget.ip}:${s.activeTarget.port}", color = GoblinText)

                        Spacer(Modifier.height(8.dp))

                        val (label, color) = when (reachable) {
                            true -> "Reachable" to GoblinOk
                            false -> "Unreachable" to GoblinBad
                            null -> "Checking…" to GoblinDim
                        }
                        Text("Ping: $label", color = color)

                        Spacer(Modifier.height(10.dp))
                        Text("Ping Intervall (${pingIntervalSec.toInt()}s)", color = GoblinDim, fontSize = 12.sp)
                        Slider(
                            value = pingIntervalSec,
                            onValueChange = { pingIntervalSec = it },
                            valueRange = 1f..5f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = GoblinAccent,
                                activeTrackColor = GoblinAccent.copy(alpha = 0.6f),
                                inactiveTrackColor = GoblinBorder
                            )
                        )

                        Text("Ping Timeout (${pingTimeoutMs.toInt()}ms)", color = GoblinDim, fontSize = 12.sp)
                        Slider(
                            value = pingTimeoutMs,
                            onValueChange = { pingTimeoutMs = it },
                            valueRange = 150f..900f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = GoblinAccent,
                                activeTrackColor = GoblinAccent.copy(alpha = 0.6f),
                                inactiveTrackColor = GoblinBorder
                            )
                        )
                    }
                }

                item {
                    Section(
                        title = "OSC Targets",
                        subtitle = "Presets (Name, IP, Port) – quick switch + edit.",
                        defaultExpanded = true
                    ) {
                        ActivePresetBar(
                            presets = s.presets,
                            activeIndex = s.activePreset,
                            onSelect = { i -> scope.launch { settingsStore.setActivePreset(i) } }
                        )

                        Spacer(Modifier.height(6.dp))

                        PresetsList(
                            presets = s.presets,
                            activeIndex = s.activePreset,
                            onUse = { i -> scope.launch { settingsStore.setActivePreset(i) } },
                            onSave = { i, name, ip, port ->
                                scope.launch { settingsStore.updatePreset(i, name, ip, port) }
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                }
            }

            // keep the close action always reachable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GoblinAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Schließen", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ActivePresetBar(
    presets: List<OscTarget>,
    activeIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GoblinCard2)
            .border(1.dp, GoblinBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("Aktives Preset", color = GoblinDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { i, p ->
                val selected = (i == activeIndex)
                Button(
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (selected) GoblinAccent else GoblinBorder,
                        contentColor = if (selected) Color.Black else GoblinText
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = when (i) {
                            0 -> "A"
                            1 -> "B"
                            2 -> "C"
                            else -> (i + 1).toString()
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val active = presets.getOrNull(activeIndex)
        if (active != null) {
            Text(active.name, color = GoblinText, fontWeight = FontWeight.Bold)
            Text("${active.ip}:${active.port}", color = GoblinDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PresetsList(
    presets: List<OscTarget>,
    activeIndex: Int,
    onUse: (Int) -> Unit,
    onSave: (Int, String, String, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.forEachIndexed { i, preset ->
            PresetCard(
                index = i,
                preset = preset,
                isActive = (i == activeIndex),
                onUse = { onUse(i) },
                onSave = { name, ip, port -> onSave(i, name, ip, port) }
            )
        }
    }
}

@Composable
private fun PresetCard(
    index: Int,
    preset: OscTarget,
    isActive: Boolean,
    onUse: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var expanded by rememberSaveable("preset_expanded_$index") { mutableStateOf(false) }
    var name by remember(preset.name) { mutableStateOf(preset.name) }
    var ip by remember(preset.ip) { mutableStateOf(preset.ip) }
    var port by remember(preset.port) { mutableStateOf(preset.port.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GoblinCard2)
            .border(1.dp, GoblinBorder, RoundedCornerShape(12.dp))
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    color = GoblinText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${preset.ip}:${preset.port}",
                    color = GoblinDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (expanded) "▾" else "▸",
                color = GoblinDim,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isActive) {
            Spacer(Modifier.height(6.dp))
            Text("AKTIV", color = GoblinOk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DividerLine()
                TextFieldItem(
                    value = name,
                    onValue = { name = it },
                    placeholder = "Name",
                    keyboardType = KeyboardType.Text
                )
                TextFieldItem(
                    value = ip,
                    onValue = { ip = it },
                    placeholder = "IP (z.B. 192.168.178.24)",
                    keyboardType = KeyboardType.Text
                )
                TextFieldItem(
                    value = port,
                    onValue = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                    placeholder = "Port (z.B. 7002)",
                    keyboardType = KeyboardType.Number
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val p = port.toIntOrNull() ?: preset.port
                            onSave(name, ip, p)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = GoblinAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Speichern", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onUse,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isActive) GoblinOk.copy(alpha = 0.25f) else GoblinBorder,
                            contentColor = GoblinText
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isActive) "Aktiv" else "Aktivieren")
                    }
                }
            }
        }
    }
}
