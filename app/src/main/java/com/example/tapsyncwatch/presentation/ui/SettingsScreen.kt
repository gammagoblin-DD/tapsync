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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalConfiguration
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

private enum class SettingsPage {
    ROOT, DISPLAY, VISUALS, MOTION, HAPTICS, NETWORK, CLOCK, TARGETS
}

@Composable
private fun SettingsTitleRow(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GoblinCard2)
                    .border(1.dp, GoblinBorder, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = GoblinText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
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
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() },
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = GoblinDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = GoblinText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = GoblinDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = GoblinDim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled) { onToggle(!checked) },
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(22.dp))
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
}

@Composable
private fun SettingsSliderChip(
    title: String,
    subtitle: String? = null,
    value: Float,
    min: Float = 0.30f,
    max: Float = 2.00f,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value.coerceIn(min, max)) }
    val pct = (local * 100f).toInt().coerceIn((min * 100f).toInt(), (max * 100f).toInt())
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)),
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    "${pct}%",
                    color = if (enabled) GoblinAccent else GoblinDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(6.dp))

            Slider(
                value = local,
                onValueChange = { if (enabled) local = it.coerceIn(min, max) },
                onValueChangeFinished = { if (enabled) onValueChange(local.coerceIn(min, max)) },
                valueRange = min..max,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = GoblinAccent,
                    activeTrackColor = GoblinAccent.copy(alpha = 0.55f),
                    inactiveTrackColor = GoblinBorder
                )
            )
        }
    }
}

@Composable
private fun SettingsCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(GoblinCard2)
            .border(1.dp, GoblinBorder, CircleShape)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Close", tint = GoblinText, modifier = Modifier.size(20.dp))
    }
}


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
    lastPongMs: kotlinx.coroutines.flow.StateFlow<Long>,
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
    val s = settings
    val lastPong by lastPongMs.collectAsState()
    var nowMs by remember { mutableStateOf(0L) }

    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }

    // Only tick when we actually show time-based network info (pong age).
    LaunchedEffect(page) {
        if (page != SettingsPage.NETWORK) return@LaunchedEffect
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

var presetsSession by remember { mutableStateOf(0) }

    // Hardware back on some watches may fire twice (DOWN/UP or duplicated keycodes).
    // We must prevent a "submenu -> ROOT -> close" within the same physical press.
    var backBlockUntilMs by rememberSaveable { mutableStateOf(0L) }
    var lastBackHandledMs by rememberSaveable { mutableStateOf(0L) }


    BackHandler {
        val now = SystemClock.elapsedRealtime()

        // Debounce ultra-fast duplicates (some devices deliver two back callbacks per physical press)
        if (now - lastBackHandledMs < 140L) return@BackHandler
        lastBackHandledMs = now

        if (page != SettingsPage.ROOT) {
            page = SettingsPage.ROOT
            // Block closing for a short window so a duplicated back doesn't immediately close Settings.
            backBlockUntilMs = now + 420L
            return@BackHandler
        }

        // At ROOT: only close if we're outside the block window.
        if (now < backBlockUntilMs) return@BackHandler
        onClose()
    }

    val isRound = LocalConfiguration.current.isScreenRound
    val edgePad = if (isRound) 18.dp else 12.dp
    val topPad = if (isRound) 18.dp else 12.dp
    val bottomPad = if (isRound) 18.dp else 12.dp

    // Link status
    val pongAge = remember(page, lastPong, nowMs) {
        if (page != SettingsPage.NETWORK) null
        else if (lastPong <= 0L || nowMs <= 0L) null
        else (nowMs - lastPong).coerceAtLeast(0L)
    }
    val linkOk = if (!s.heartbeatEnabled) true else (pongAge != null && pongAge < s.signalGraceMs)
    val linkLabel = when {
        !s.heartbeatEnabled -> "Link Check OFF"
        pongAge == null -> "waiting…"
        linkOk -> "OK"
        else -> "NO SIGNAL"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoblinBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = edgePad,
                end = edgePad,
                top = topPad,
                // keep space for bottom close button
                bottom = bottomPad + 72.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            when (page) {

                SettingsPage.ROOT -> {
                    item { SettingsTitleRow("Einstellungen", showBack = false, onBack = {}) }

                    item { SettingsNavChip(Icons.Filled.Visibility, "Anzeige", "Status, OSC Dot, Monitor") { page = SettingsPage.DISPLAY } }
                    item { SettingsNavChip(Icons.Filled.Palette, "Visuals", "Phase Ring / Spiral") { page = SettingsPage.VISUALS } }
                    item { SettingsNavChip(Icons.Filled.Animation, "Animation", "Remote Ghost, Ripples, Pulse") { page = SettingsPage.MOTION } }
                    item { SettingsNavChip(Icons.Filled.Vibration, "Haptik", "Tap / Downbeat / Transport") { page = SettingsPage.HAPTICS } }
                    item { SettingsNavChip(Icons.Filled.Wifi, "Netzwerk", "Ping/Pong • $linkLabel") { page = SettingsPage.NETWORK } }
                    item { SettingsNavChip(Icons.Filled.Schedule, "Clock / Engine", "Clock enabled + mode") { page = SettingsPage.CLOCK } }
                    item {
                        SettingsNavChip(
                            Icons.Filled.Send,
                            "OSC Targets",
                            "${s.activeTarget.ip}:${s.activeTarget.port}"
                        ) {
                            presetsSession += 1
                            page = SettingsPage.TARGETS
                        }
                    }
                }

                SettingsPage.DISPLAY -> {
                    item { SettingsTitleRow("Anzeige", showBack = true) { page = SettingsPage.ROOT } }

                    item { SettingsToggleChip("Statusbar", "Preset • IP:Port • Link", s.showStatusLine) { v -> scope.launch { settingsStore.setShowStatusLine(v) } } }
                    item { SettingsToggleChip("OSC Dot", "kleiner Puls-Punkt", s.showOscDot) { v -> scope.launch { settingsStore.setShowOscDot(v) } } }
                    item { SettingsToggleChip("External BPM", "zeigt BPM / NO SIGNAL", s.showExternalBpm) { v -> scope.launch { settingsStore.setShowExternalBpm(v) } } }
                    item { SettingsToggleChip("OSC Monitor", "Fullscreen debug overlay", s.showOscDebug) { v -> scope.launch { settingsStore.setShowOscDebug(v) } } }
                }

                SettingsPage.VISUALS -> {
                    item { SettingsTitleRow("Visuals", showBack = true) { page = SettingsPage.ROOT } }

                    item { SettingsToggleChip("Phase Ring", "Downbeat + Phase", s.phaseVisualizerEnabled) { v -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } } }
                    item { SettingsToggleChip("Phase Spiral", "Stability visual", s.phaseSpiralEnabled) { v -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } } }
                    item {
                        SettingsSliderChip(
                            title = "Phase Sichtbarkeit",
                            subtitle = "Ring + Spiral",
                            value = s.phaseAlpha
                        ) { v -> scope.launch { settingsStore.setPhaseAlpha(v) } }
                    }
                    item {
                        SettingsSliderChip(
                            title = "Ghost Sichtbarkeit",
                            subtitle = "Remote overlays",
                            value = s.ghostAlpha,
                            enabled = s.animationsEnabled && s.remoteAnimationsEnabled && s.remoteGhostModeEnabled
                        ) { v -> scope.launch { settingsStore.setGhostAlpha(v) } }
                    }
                    item {
                        SettingsSliderChip(
                            title = "FX Sichtbarkeit",
                            subtitle = "Ripples / Pulse / Downbeat",
                            value = s.fxAlpha,
                            enabled = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setFxAlpha(v) } }
                    }
                }

                SettingsPage.MOTION -> {
                    item { SettingsTitleRow("Animation", showBack = true) { page = SettingsPage.ROOT } }

                    item { SettingsToggleChip("Animationen", "Master switch", s.animationsEnabled) { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } } }
                    item { SettingsToggleChip("Remote Animation", "nur visuell", s.remoteAnimationsEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } } }
                    item { SettingsToggleChip("Remote Ghost", "Remote fühlt sich anders an", s.remoteGhostModeEnabled, enabled = s.animationsEnabled && s.remoteAnimationsEnabled) { v -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } } }
                    item { SettingsToggleChip("Goblin Flash", "Flash bei Events", s.goblinFlashEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } } }
                    item { SettingsToggleChip("Ripples", "Wellen feedback", s.rippleEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setRippleEnabled(v) } } }
                    item { SettingsToggleChip("OSC Pulse", "Pulse animation", s.oscPulseEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setOscPulseEnabled(v) } } }
                }

                SettingsPage.HAPTICS -> {
                    item { SettingsTitleRow("Haptik", showBack = true) { page = SettingsPage.ROOT } }

                    item { SettingsToggleChip("Haptik", "Master switch", s.hapticsEnabled) { v -> scope.launch { settingsStore.setHapticsEnabled(v) } } }
                    item { SettingsToggleChip("Downbeat", "Kick auf 1", s.downbeatHapticsEnabled, enabled = s.hapticsEnabled) { v -> scope.launch { settingsStore.setDownbeatHapticsEnabled(v) } } }
                    item { SettingsToggleChip("Transport", "Nudge / Resync", s.transportHapticsEnabled, enabled = s.hapticsEnabled) { v -> scope.launch { settingsStore.setTransportHapticsEnabled(v) } } }
                }

                SettingsPage.NETWORK -> {
                    item { SettingsTitleRow("Netzwerk", showBack = true) { page = SettingsPage.ROOT } }

                    item { SettingsToggleChip("Link Check", "/tapsync/ping → /tapsync/pong", s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatEnabled(v) } } }
                    item { SettingsToggleChip("Nur Vordergrund", "weniger OSC + Akku", s.heartbeatForegroundOnly, enabled = s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatForegroundOnly(v) } } }
                    item { SettingsToggleChip("Adaptive recovery", "ping schneller wenn down", s.heartbeatAdaptiveEnabled, enabled = s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatAdaptiveEnabled(v) } } }

                    item {
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
                            Text("Status", color = GoblinDim, fontSize = 11.sp)
                            Text(
                                linkLabel,
                                color = if (linkOk) GoblinOk else GoblinBad,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Last pong: " + (pongAge?.let { "${it}ms" } ?: "-"), color = GoblinDim, fontSize = 11.sp)
                            Text("Grace: ${s.signalGraceMs}ms", color = GoblinDim, fontSize = 11.sp)
                        }
                    }
                }

                SettingsPage.CLOCK -> {
                    item { SettingsTitleRow("Clock / Engine", showBack = true) { page = SettingsPage.ROOT } }

                    // FIRST: clock enable/disable
                    item { SettingsToggleChip("Clock Enabled", "Master switch for clock output", s.clockEnabled) { v -> scope.launch { settingsStore.setClockEnabled(v) } } }

                    item {
                        val shape = RoundedCornerShape(22.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(GoblinCard)
                                .border(1.dp, GoblinBorder, shape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Mode", color = GoblinDim, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                 @Composable fun modeChip(mode: ClockMode, label: String) {
                                    val selected = s.clockMode == mode
                                    val bg = if (selected) GoblinAccent.copy(alpha = 0.25f) else GoblinCard2
                                    val br = if (selected) GoblinAccent.copy(alpha = 0.55f) else GoblinBorder
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(bg)
                                            .border(1.dp, br, RoundedCornerShape(16.dp))
                                            .clickable { scope.launch { settingsStore.setClockMode(mode) } }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = GoblinText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                modeChip(ClockMode.EXTERNAL, "EXTERNAL")
                                modeChip(ClockMode.INTERNAL, "INTERNAL")
                            }
                            Text("Hinweis: Mode wirkt nur wenn Clock Enabled aktiv ist.", color = GoblinDim, fontSize = 11.sp)
                        }
                    }
                }

                SettingsPage.TARGETS -> {
                    item { SettingsTitleRow("OSC Targets", showBack = true) { page = SettingsPage.ROOT } }

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
                            sessionId = presetsSession,
                            onUse = { i -> scope.launch { settingsStore.setActivePreset(i) } },
                            onSave = { i, name, ip, port ->
                                scope.launch { settingsStore.updatePreset(i, name, ip, port) }
                            }
                        )
                    }
                }
            }
        }

        SettingsCloseButton(
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPad)
        )
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
    sessionId: Int,
    onUse: (Int) -> Unit,
    onSave: (Int, String, String, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.forEachIndexed { i, preset ->
            PresetCard(
                sessionId = sessionId,
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
    sessionId: Int,
    index: Int,
    preset: OscTarget,
    isActive: Boolean,
    onUse: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var expanded by rememberSaveable("preset_expanded_${sessionId}_$index") { mutableStateOf(false) }
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
            Text("ACTIVE", color = GoblinOk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text("SAVE", fontWeight = FontWeight.Bold)
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
                        Text(if (isActive) "ACTIVE" else "USE")
                    }
                }
            }
        }
    }
}
