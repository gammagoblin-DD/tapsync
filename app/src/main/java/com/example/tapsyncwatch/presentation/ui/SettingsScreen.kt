package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.roundToLong

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



private enum class SettingsPage {
    ROOT,
    DISPLAY,
    VISUALS,
    MOTION,
    HAPTICS,
    NETWORK,
    ENGINE,
    TARGETS
}

@Composable
private fun SettingsTitleRow(
    title: String,
    backLabel: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = backLabel,
            color = GoblinDim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { onBack() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = GoblinText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsNavChip(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GoblinCard)
            .border(1.dp, GoblinBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = GoblinText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = GoblinDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = GoblinDim, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsToggleChip(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GoblinCard)
            .border(1.dp, GoblinBorder, shape)
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) GoblinText else GoblinDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = GoblinDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoblinAccent,
                checkedTrackColor = GoblinAccent.copy(alpha = 0.35f),
                uncheckedThumbColor = Color.DarkGray,
                uncheckedTrackColor = GoblinBorder
            )
        )
    }
}

@Composable
private fun SettingsStatusCard(
    title: String,
    lines: List<String>,
    ok: Boolean
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GoblinCard)
            .border(1.dp, GoblinBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = GoblinDim, fontSize = 11.sp)
        Text(
            text = lines.firstOrNull() ?: "-",
            color = if (ok) GoblinOk else GoblinBad,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        lines.drop(1).forEach { line ->
            Text(line, color = GoblinDim, fontSize = 11.sp)
        }
    }
}

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
    lastPongMs: kotlinx.coroutines.flow.StateFlow<Long>,
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
    val s = settings

    val lastPong by lastPongMs.collectAsState()
    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            delay(250)
        }
    }

    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }

    BackHandler {
        if (page != SettingsPage.ROOT) page = SettingsPage.ROOT else onClose()
    }

    val cfg = LocalConfiguration.current
    val isRound = cfg.isScreenRound
    val edgePad = if (isRound) 18.dp else 12.dp
    val topPad = if (isRound) 22.dp else 12.dp
    val bottomPad = if (isRound) 22.dp else 12.dp

    // Heartbeat-derived link status
    val pongAge = if (lastPong <= 0L) null else (nowMs - lastPong).coerceAtLeast(0L)
    val linkOk = if (!s.heartbeatEnabled) true else (pongAge != null && pongAge < s.signalGraceMs)
    val linkLabel = when {
        !s.heartbeatEnabled -> "Link Check aus"
        pongAge == null -> "warte auf pong…"
        linkOk -> "Link OK"
        else -> "NO SIGNAL"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GoblinBg),
        contentPadding = PaddingValues(
            start = edgePad,
            end = edgePad,
            top = topPad,
            bottom = bottomPad
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        when (page) {

            SettingsPage.ROOT -> {
                item {
                    SettingsTitleRow(
                        title = "Einstellungen",
                        backLabel = "×",
                        onBack = onClose
                    )
                }

                item { SettingsNavChip("Anzeige", "Statuszeile, Dot, Debug, BPM") { page = SettingsPage.DISPLAY } }
                item { SettingsNavChip("Visuals", "Phase Ring / Spiral") { page = SettingsPage.VISUALS } }
                item { SettingsNavChip("Animation", "Remote Ghost, Ripples, Pulse") { page = SettingsPage.MOTION } }
                item { SettingsNavChip("Haptik", "Tap / Downbeat / Transport") { page = SettingsPage.HAPTICS } }
                item { SettingsNavChip("Netzwerk", "$linkLabel • Ping/Pong") { page = SettingsPage.NETWORK } }
                item { SettingsNavChip("Clock / Engine", "External / Internal") { page = SettingsPage.ENGINE } }
                item { SettingsNavChip("OSC Targets", "${s.activeTarget.ip}:${s.activeTarget.port}") { page = SettingsPage.TARGETS } }

                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Long-Press im TapScreen öffnet Settings. Back/Swipe schließt.",
                        color = GoblinDim,
                        fontSize = 11.sp
                    )
                }
            }

            SettingsPage.DISPLAY -> {
                item {
                    SettingsTitleRow("Anzeige", "‹") { page = SettingsPage.ROOT }
                }

                item {
                    SettingsToggleChip(
                        title = "Statuszeile",
                        subtitle = "Preset • IP:Port • Link-Status",
                        checked = s.showStatusLine,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setShowStatusLine(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "OSC Statuspunkt",
                        subtitle = "kleiner Puls-Dot",
                        checked = s.showOscDot,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setShowOscDot(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "External BPM Monitor",
                        subtitle = "zeigt BPM / NO SIGNAL",
                        checked = s.showExternalBpm,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setShowExternalBpm(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "OSC Debug Overlay",
                        subtitle = "Overlay im TapScreen",
                        checked = s.showOscDebug,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setShowOscDebug(v) } }
                    )
                }
            }

            SettingsPage.VISUALS -> {
                item { SettingsTitleRow("Visuals", "‹") { page = SettingsPage.ROOT } }

                item {
                    SettingsToggleChip(
                        title = "Phase Ring",
                        subtitle = "Downbeat Marker + Phase",
                        checked = s.phaseVisualizerEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Phase Spiral",
                        subtitle = "Stability Visual",
                        checked = s.phaseSpiralEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } }
                    )
                }
            }

            SettingsPage.MOTION -> {
                item { SettingsTitleRow("Animation", "‹") { page = SettingsPage.ROOT } }

                item {
                    SettingsToggleChip(
                        title = "Animationen",
                        subtitle = "Master Switch",
                        checked = s.animationsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Remote Animationen",
                        subtitle = "nur visuell",
                        checked = s.remoteAnimationsEnabled,
                        enabled = s.animationsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Remote Ghost Mode",
                        subtitle = "Remote fühlt sich anders an",
                        checked = s.remoteGhostModeEnabled,
                        enabled = s.animationsEnabled && s.remoteAnimationsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Goblin Flash",
                        subtitle = "Flash bei Events",
                        checked = s.goblinFlashEnabled,
                        enabled = s.animationsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Ripples",
                        subtitle = "Wellen-Feedback",
                        checked = s.rippleEnabled,
                        enabled = s.animationsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setRippleEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "OSC Pulse",
                        subtitle = "Pulse auf OSC",
                        checked = s.oscPulseEnabled,
                        enabled = s.animationsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setOscPulseEnabled(v) } }
                    )
                }
            }

            SettingsPage.HAPTICS -> {
                item { SettingsTitleRow("Haptik", "‹") { page = SettingsPage.ROOT } }

                item {
                    SettingsToggleChip(
                        title = "Haptik",
                        subtitle = "Master Switch",
                        checked = s.hapticsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setHapticsEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Downbeat",
                        subtitle = "Kick auf 1",
                        checked = s.downbeatHapticsEnabled,
                        enabled = s.hapticsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setDownbeatHapticsEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Transport",
                        subtitle = "Nudge / Resync",
                        checked = s.transportHapticsEnabled,
                        enabled = s.hapticsEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setTransportHapticsEnabled(v) } }
                    )
                }
            }

            SettingsPage.NETWORK -> {
                item { SettingsTitleRow("Netzwerk", "‹") { page = SettingsPage.ROOT } }

                item {
                    SettingsStatusCard(
                        title = "Status",
                        lines = listOf(
                            linkLabel,
                            "Last pong: " + (pongAge?.let { "${it}ms" } ?: "-"),
                            "Interval: ${s.heartbeatIntervalMs}ms • Grace: ${s.signalGraceMs}ms"
                        ),
                        ok = linkOk
                    )
                }

                item {
                    SettingsToggleChip(
                        title = "Link Check",
                        subtitle = "/tapsync/ping → /tapsync/pong",
                        checked = s.heartbeatEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setHeartbeatEnabled(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Nur Vordergrund",
                        subtitle = "weniger OSC + Akku",
                        checked = s.heartbeatForegroundOnly,
                        enabled = s.heartbeatEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setHeartbeatForegroundOnly(v) } }
                    )
                }
                item {
                    SettingsToggleChip(
                        title = "Adaptive recovery",
                        subtitle = "ping schneller wenn down",
                        checked = s.heartbeatAdaptiveEnabled,
                        enabled = s.heartbeatEnabled,
                        onToggle = { v: Boolean -> scope.launch { settingsStore.setHeartbeatAdaptiveEnabled(v) } }
                    )
                }

                item {
                    val shape = RoundedCornerShape(22.dp)
                    var hbInterval by remember(s.heartbeatIntervalMs) { mutableStateOf(s.heartbeatIntervalMs.toFloat()) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(GoblinCard)
                            .border(1.dp, GoblinBorder, shape)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Heartbeat Interval (${hbInterval.roundToLong()}ms)", color = GoblinDim, fontSize = 12.sp)
                        Slider(
                            value = hbInterval,
                            onValueChange = { hbInterval = it },
                            onValueChangeFinished = {
                                scope.launch { settingsStore.setHeartbeatIntervalMs(hbInterval.roundToLong()) }
                            },
                            valueRange = 250f..5000f,
                            steps = 9,
                            enabled = s.heartbeatEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = GoblinAccent,
                                activeTrackColor = GoblinAccent.copy(alpha = 0.6f),
                                inactiveTrackColor = GoblinBorder
                            )
                        )
                    }
                }

                item {
                    val shape = RoundedCornerShape(22.dp)
                    var grace by remember(s.signalGraceMs) { mutableStateOf(s.signalGraceMs.toFloat()) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(GoblinCard)
                            .border(1.dp, GoblinBorder, shape)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Signal Grace (${grace.roundToLong()}ms)", color = GoblinDim, fontSize = 12.sp)
                        Slider(
                            value = grace,
                            onValueChange = { grace = it },
                            onValueChangeFinished = {
                                scope.launch { settingsStore.setSignalGraceMs(grace.roundToLong()) }
                            },
                            valueRange = 750f..30000f,
                            steps = 10,
                            enabled = s.heartbeatEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = GoblinAccent,
                                activeTrackColor = GoblinAccent.copy(alpha = 0.6f),
                                inactiveTrackColor = GoblinBorder
                            )
                        )
                    }
                }
            }

            SettingsPage.ENGINE -> {
                item { SettingsTitleRow("Clock / Engine", "‹") { page = SettingsPage.ROOT } }

                item {
                    val shape = RoundedCornerShape(22.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(GoblinCard)
                            .border(1.dp, GoblinBorder, shape)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Clock Mode", color = GoblinDim, fontSize = 12.sp)

                        RadioRow(
                            label = "EXTERNAL",
                            selected = s.clockMode == ClockMode.EXTERNAL,
                            onSelect = { scope.launch { settingsStore.setClockMode(ClockMode.EXTERNAL) } }
                        )
                        RadioRow(
                            label = "INTERNAL",
                            selected = s.clockMode == ClockMode.INTERNAL,
                            onSelect = { scope.launch { settingsStore.setClockMode(ClockMode.INTERNAL) } }
                        )

                        Spacer(Modifier.height(4.dp))
                        SettingsToggleChip(
                            title = "Clock Enabled",
                            subtitle = "Legacy / compatibility",
                            checked = s.clockEnabled,
                            onToggle = { v: Boolean -> scope.launch { settingsStore.setClockEnabled(v) } }
                        )
                    }
                }
            }

            SettingsPage.TARGETS -> {
                item { SettingsTitleRow("OSC Targets", "‹") { page = SettingsPage.ROOT } }

                item {
                    ActivePresetBar(
                        presets = s.presets,
                        activeIndex = s.activePreset,
                        onSelect = { i -> scope.launch { settingsStore.setActivePreset(i) } }
                    )
                }

                item {
                    PresetsList(
                        presets = s.presets,
                        activeIndex = s.activePreset,
                        onUse = { i -> scope.launch { settingsStore.setActivePreset(i) } },
                        onSave = { i, name, ip, port ->
                            scope.launch { settingsStore.updatePreset(i, name, ip, port) }
                        }
                    )
                }

                item { Spacer(Modifier.height(6.dp)) }
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
