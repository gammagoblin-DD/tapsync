package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.presentation.data.DEFAULT_SETTINGS_STATE
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.SettingsState
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

/* =========================================================
 * Goblin Watch Theme (round-safe, minimal, no neon purple)
 * ========================================================= */

private val GoblinBg = Color(0xFF050403)
private val GoblinCard = Color(0xFF0B0A08)
private val GoblinBrown = Color(0xFFE6D3B1)
private val GoblinDim = GoblinBrown.copy(alpha = 0.72f)
private val GoblinOff = GoblinBrown.copy(alpha = 0.20f)
private val GoblinStroke = GoblinBrown.copy(alpha = 0.18f)
private val GoblinOk = Color(0xFF66D17A)
private val GoblinBad = Color(0xFFFF6B6B)

private val CardShape = RoundedCornerShape(18.dp)
private val PillShape = RoundedCornerShape(999.dp)

/* =========================================================
 * Network helpers
 * ========================================================= */

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
                if (host.contains(":")) continue // skip IPv6
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

/* =========================================================
 * UI building blocks
 * ========================================================= */

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(GoblinCard)
            .border(1.dp, GoblinStroke, CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = GoblinBrown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = GoblinDim, fontSize = 11.sp)
            }
            if (headerTrailing != null) headerTrailing()
        }
        content()
    }
}

@Composable
private fun AccordionCard(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard(
        title = title,
        subtitle = subtitle,
        headerTrailing = {
            val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
            Text(
                "⌄",
                color = GoblinDim,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .graphicsLayer { rotationZ = rot }
            )
        }
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = GoblinBrown, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoblinBrown,
                checkedTrackColor = GoblinBrown.copy(alpha = 0.35f),
                uncheckedThumbColor = GoblinOff,
                uncheckedTrackColor = GoblinOff.copy(alpha = 0.14f),
            )
        )
    }
}

@Composable
private fun SliderDraftRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDraft: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = GoblinBrown, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), color = GoblinDim, fontSize = 11.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onDraft,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = GoblinBrown,
                activeTrackColor = GoblinBrown.copy(alpha = 0.55f),
                inactiveTrackColor = GoblinOff.copy(alpha = 0.16f)
            )
        )
    }
}

@Composable
private fun TinyField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    TextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(placeholder, color = GoblinOff) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        colors = TextFieldDefaults.textFieldColors(
            textColor = GoblinBrown,
            backgroundColor = GoblinCard,
            cursorColor = GoblinBrown,
            focusedIndicatorColor = GoblinBrown.copy(alpha = 0.55f),
            unfocusedIndicatorColor = GoblinStroke
        )
    )
}

@Composable
private fun PresetPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) GoblinBrown.copy(alpha = 0.16f) else Color.Transparent
    val st = if (selected) GoblinBrown.copy(alpha = 0.35f) else GoblinStroke
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .border(1.dp, st, PillShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = GoblinBrown, fontSize = 12.sp)
    }
}

/* =========================================================
 * Screen
 * ========================================================= */

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onClose: () -> Unit
) {
    // Back closes WITHOUT saving (explicit "Speichern" button below)
    BackHandler(onBack = onClose)

    val live by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
    val scope = rememberCoroutineScope()

    // Draft state: edits are local until user presses "Speichern".
    var draft by remember(live) { mutableStateOf(live) }
    var dirty by remember { mutableStateOf(false) }

    // Network status (non-blocking, safe)
    var localIp by remember { mutableStateOf<String?>(null) }
    var pingState by remember { mutableStateOf("…") }
    var pingOk by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        localIp = withContext(Dispatchers.IO) { getLocalIp() }
        // optional: delayed ping so opening settings stays snappy
        delay(250)
    }

    // Accordion state
    var netOpen by remember { mutableStateOf(true) }
    var visualsOpen by remember { mutableStateOf(false) }
    var animOpen by remember { mutableStateOf(false) }
    var monitorOpen by remember { mutableStateOf(false) }
    var hapticsOpen by remember { mutableStateOf(false) }
    var clockOpen by remember { mutableStateOf(false) }

    fun markDirty(newState: SettingsState) {
        draft = newState
        dirty = true
    }

    val saveBarH = 58.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoblinBg)
    ) {
        // Round-safe column: keep away from circular edges.
        LazyColumn(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.88f)
                .padding(top = 18.dp, bottom = saveBarH + 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Text(
                    text = "SETTINGS",
                    color = GoblinBrown,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            // ===== 1) NETWORK FIRST =====
            item {
                AccordionCard(
                    title = "Network",
                    subtitle = "Ziel wählen, IP prüfen, Ping",
                    expanded = netOpen,
                    onToggle = { netOpen = !netOpen }
                ) {
                    // Local IP + target
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Local", color = GoblinDim, fontSize = 11.sp, modifier = Modifier.width(52.dp))
                        Text(localIp ?: "—", color = GoblinBrown, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Target", color = GoblinDim, fontSize = 11.sp, modifier = Modifier.width(52.dp))
                        Text("${draft.activeTarget.ip}:${draft.activeTarget.port}", color = GoblinBrown, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .border(1.dp, GoblinStroke, PillShape)
                                .clickable {
                                    scope.launch {
                                        pingState = "…"
                                        pingOk = null
                                        val ok = ping(draft.activeTarget.ip, 350)
                                        pingOk = ok
                                        pingState = if (ok) "OK" else "NO"
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ping", color = GoblinBrown, fontSize = 11.sp)
                        }
                    }
                    if (pingOk != null) {
                        Text(
                            text = "Ping: $pingState",
                            color = if (pingOk == true) GoblinOk else GoblinBad,
                            fontSize = 11.sp
                        )
                    }

                    Divider(color = GoblinStroke)

                    // Preset picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        draft.presets.forEachIndexed { idx, p ->
                            PresetPill(
                                text = p.name.ifBlank { "P${idx+1}" },
                                selected = draft.activePreset == idx,
                                onClick = { markDirty(draft.copy(activePreset = idx)) }
                            )
                        }
                    }

                    // Preset editor (minimal, watch-safe)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        draft.presets.forEachIndexed { idx, p ->
                            val isActive = draft.activePreset == idx
                            val chipBg = if (isActive) GoblinBrown.copy(alpha = 0.10f) else Color.Transparent
                            val chipStroke = if (isActive) GoblinBrown.copy(alpha = 0.28f) else GoblinStroke

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(chipBg)
                                    .border(1.dp, chipStroke, RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Preset ${idx + 1}", color = GoblinDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    if (isActive) Text("ACTIVE", color = GoblinBrown, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                var name by remember(draft.presets[idx].name) { mutableStateOf(draft.presets[idx].name) }
                                var ip by remember(draft.presets[idx].ip) { mutableStateOf(draft.presets[idx].ip) }
                                var port by remember(draft.presets[idx].port) { mutableStateOf(draft.presets[idx].port.toString()) }

                                // keep local fields, write to draft on change
                                fun writeBack() {
                                    val newPort = port.toIntOrNull() ?: p.port
                                    val updated = OscTarget(name.trim(), ip.trim(), newPort.coerceIn(1, 65535))
                                    val newList = draft.presets.toMutableList()
                                    newList[idx] = updated
                                    markDirty(draft.copy(presets = newList))
                                }

                                TinyField(
                                    value = name,
                                    onValue = { name = it; writeBack() },
                                    placeholder = "Name",
                                    keyboardType = KeyboardType.Text
                                )
                                TinyField(
                                    value = ip,
                                    onValue = { ip = it; writeBack() },
                                    placeholder = "IP (z.B. 192.168.178.24)",
                                    keyboardType = KeyboardType.Text
                                )
                                TinyField(
                                    value = port,
                                    onValue = { port = it.filter { ch -> ch.isDigit() }.take(5); writeBack() },
                                    placeholder = "Port (z.B. 7002)",
                                    keyboardType = KeyboardType.Number
                                )
                            }
                        }
                    }
                }
            }

            // ===== 2) IMPORTANT MONITORS (still early) =====
            item {
                AccordionCard(
                    title = "Monitor",
                    subtitle = "BPM / OSC / Phase / Debug",
                    expanded = monitorOpen,
                    onToggle = { monitorOpen = !monitorOpen }
                ) {
                    ToggleRow("External BPM", draft.showExternalBpm) { markDirty(draft.copy(showExternalBpm = it)) }
                    ToggleRow("OSC Dot", draft.showOscDot) { markDirty(draft.copy(showOscDot = it)) }
                    ToggleRow("OSC Debug Overlay", draft.showOscDebug) { markDirty(draft.copy(showOscDebug = it)) }
                    ToggleRow("Phase Visualizer", draft.phaseVisualizerEnabled) { markDirty(draft.copy(phaseVisualizerEnabled = it)) }
                    ToggleRow("Phase Spiral", draft.phaseSpiralEnabled) { markDirty(draft.copy(phaseSpiralEnabled = it)) }
                }
            }

            // ===== 3) VISUALS =====
            item {
                AccordionCard(
                    title = "Visuals",
                    subtitle = "Größe / Transparenz / Ghost",
                    expanded = visualsOpen,
                    onToggle = { visualsOpen = !visualsOpen }
                ) {
                    SliderDraftRow("Ring Größe", draft.ringScale, 0.85f..1.10f) { markDirty(draft.copy(ringScale = it)) }
                    SliderDraftRow("Ring Dicke", draft.ringThickness, 0.70f..1.30f) { markDirty(draft.copy(ringThickness = it)) }
                    SliderDraftRow("Ring Alpha", draft.ringAlpha, 0.20f..1.00f) { markDirty(draft.copy(ringAlpha = it)) }

                    Divider(color = GoblinStroke)

                    SliderDraftRow("Downbeat Größe", draft.downbeatSize, 0.50f..1.50f) { markDirty(draft.copy(downbeatSize = it)) }
                    SliderDraftRow("Downbeat Alpha", draft.downbeatAlpha, 0.20f..1.00f) { markDirty(draft.copy(downbeatAlpha = it)) }

                    Divider(color = GoblinStroke)

                    ToggleRow("Remote Ghost Mode", draft.remoteGhostModeEnabled) { markDirty(draft.copy(remoteGhostModeEnabled = it)) }
                    SliderDraftRow("Ghost Alpha", draft.ghostAlpha, 0.10f..1.00f) { markDirty(draft.copy(ghostAlpha = it)) }
                    SliderDraftRow("Ghost Dicke", draft.ghostThickness, 0.70f..1.30f) { markDirty(draft.copy(ghostThickness = it)) }
                    SliderDraftRow("Lock Glow", draft.lockGlow, 0.00f..1.00f) { markDirty(draft.copy(lockGlow = it)) }

                    Divider(color = GoblinStroke)

                    SliderDraftRow("Spiral Stärke", draft.spiralStrength, 0.00f..1.00f) { markDirty(draft.copy(spiralStrength = it)) }
                    SliderDraftRow("Spiral Alpha", draft.spiralAlpha, 0.05f..0.80f) { markDirty(draft.copy(spiralAlpha = it)) }
                }
            }

            // ===== 4) ANIMATION =====
            item {
                AccordionCard(
                    title = "Animation",
                    subtitle = "Smoothness / Ripple / Pulse",
                    expanded = animOpen,
                    onToggle = { animOpen = !animOpen }
                ) {
                    ToggleRow("Animationen", draft.animationsEnabled) { markDirty(draft.copy(animationsEnabled = it)) }
                    ToggleRow("Remote Animations", draft.remoteAnimationsEnabled) { markDirty(draft.copy(remoteAnimationsEnabled = it)) }
                    ToggleRow("Goblin Flash", draft.goblinFlashEnabled) { markDirty(draft.copy(goblinFlashEnabled = it)) }
                    ToggleRow("Ripple", draft.rippleEnabled) { markDirty(draft.copy(rippleEnabled = it)) }
                    ToggleRow("OSC Pulse", draft.oscPulseEnabled) { markDirty(draft.copy(oscPulseEnabled = it)) }

                    Divider(color = GoblinStroke)

                    SliderDraftRow("Intensity", draft.animationIntensity, 0.20f..1.00f) { markDirty(draft.copy(animationIntensity = it)) }
                    SliderDraftRow("Ripple Stärke", draft.rippleStrength, 0.00f..1.00f) { markDirty(draft.copy(rippleStrength = it)) }
                    SliderDraftRow("Pulse Stärke", draft.pulseStrength, 0.00f..1.00f) { markDirty(draft.copy(pulseStrength = it)) }
                }
            }

            // ===== 5) HAPTICS =====
            item {
                AccordionCard(
                    title = "Haptics",
                    subtitle = "Vibration Feedback",
                    expanded = hapticsOpen,
                    onToggle = { hapticsOpen = !hapticsOpen }
                ) {
                    ToggleRow("Haptics", draft.hapticsEnabled) { markDirty(draft.copy(hapticsEnabled = it)) }
                    ToggleRow("Downbeat", draft.downbeatHapticsEnabled) { markDirty(draft.copy(downbeatHapticsEnabled = it)) }
                    ToggleRow("Transport", draft.transportHapticsEnabled) { markDirty(draft.copy(transportHapticsEnabled = it)) }
                }
            }

            // ===== 6) CLOCK =====
            item {
                AccordionCard(
                    title = "Clock",
                    subtitle = "Internal / External",
                    expanded = clockOpen,
                    onToggle = { clockOpen = !clockOpen }
                ) {
                    ToggleRow("Clock Enabled", draft.clockEnabled) { markDirty(draft.copy(clockEnabled = it)) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, GoblinStroke, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mode", color = GoblinBrown, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PresetPill("EXT", draft.clockMode == ClockMode.EXTERNAL) {
                                markDirty(draft.copy(clockMode = ClockMode.EXTERNAL))
                            }
                            PresetPill("INT", draft.clockMode == ClockMode.INTERNAL) {
                                markDirty(draft.copy(clockMode = ClockMode.INTERNAL))
                            }
                        }
                    }
                }
            }
        }

        // ===== Bottom Save Bar (no top-right close) =====
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            val enabled = dirty
            val bg = if (enabled) GoblinBrown.copy(alpha = 0.18f) else Color.Transparent
            val stroke = if (enabled) GoblinBrown.copy(alpha = 0.35f) else GoblinStroke
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(saveBarH)
                    .clip(PillShape)
                    .background(bg)
                    .border(1.dp, stroke, PillShape)
                    .clickable(enabled = enabled) {
                        scope.launch {
                            settingsStore.saveAll(draft)
                            dirty = false
                            onClose() // close after save
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (enabled) "SPEICHERN" else "OK",
                    color = GoblinBrown,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}
