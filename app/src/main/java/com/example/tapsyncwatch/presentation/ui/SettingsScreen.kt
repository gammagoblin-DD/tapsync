package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.presentation.data.DEFAULT_SETTINGS_STATE
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

/* ================= THEME ================= */

private val GoblinBg = Color(0xFF0B0B0B)
private val GoblinCard = Color(0xFF131313)
private val GoblinBorder = Color(0xFF2B2B2B)
private val GoblinAccent = Color(0xFFB86CFF)
private val GoblinText = Color(0xFFEDEDED)
private val GoblinDim = Color(0xFF9A9A9A)

/* ================= BLOCK COMPONENT ================= */

@Composable
private fun SettingsBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(GoblinCard)
            .border(1.dp, GoblinBorder, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Text(
                text = title,
                color = GoblinAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    )
}

/* ================= PRESET EDIT MODEL ================= */

private data class PendingPresetCommit(
    val index: Int,
    val preset: OscTarget
)

/* ================= SETTINGS SCREEN ================= */

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(
        initial = DEFAULT_SETTINGS_STATE
    )
    val s = settings

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    /** 🔑 Pending Preset Commit */
    var pendingCommit by remember {
        mutableStateOf<PendingPresetCommit?>(null)
    }

    /** 🔒 EINZIGE Stelle mit suspend-Aufruf */
    LaunchedEffect(pendingCommit) {
        val commit = pendingCommit ?: return@LaunchedEffect
        val index = commit.index
        val target = commit.preset
        settingsStore.updatePreset(
            index = index,
            name = target.name,
            ip = target.ip,
            port = target.port
        )

        pendingCommit = null
        onClose()
    }

    BackHandler { onClose() }

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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                /* ---------- DISPLAY ---------- */
                SettingsBlock("Display") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OSC Statuspunkt", color = GoblinText)
                        Switch(
                            checked = s.showOscDot,
                            onCheckedChange = {
                                scope.launch {
                                    settingsStore.setShowOscDot(it)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }
                }

                /* ---------- ANIMATIONS ---------- */
                SettingsBlock("Animations") {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Animations", color = GoblinText)
                        Switch(
                            checked = s.animationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setAnimationsEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Remote Animations", color = GoblinText)
                        Switch(
                            checked = s.remoteAnimationsEnabled,
                            enabled = s.animationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setRemoteAnimationsEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Remote Ghost Mode", color = GoblinText)
                        Switch(
                            checked = s.remoteGhostModeEnabled,
                            enabled = s.animationsEnabled && s.remoteAnimationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setRemoteGhostModeEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Goblin Flash", color = GoblinText)
                        Switch(
                            checked = s.goblinFlashEnabled,
                            enabled = s.animationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setGoblinFlashEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ripples", color = GoblinText)
                        Switch(
                            checked = s.rippleEnabled,
                            enabled = s.animationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setRippleEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OSC Pulse", color = GoblinText)
                        Switch(
                            checked = s.oscPulseEnabled,
                            enabled = s.animationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setOscPulseEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }
                }

                SettingsBlock("Internal Clock") {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (s.clockEnabled)
                                "Internal Clock ON"
                            else
                                "Internal Clock OFF",
                            color = GoblinText
                        )
                        Switch(
                            checked = s.clockEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    settingsStore.setClockEnabled(it)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = s.clockMode == ClockMode.EXTERNAL,
                                onClick = {
                                    scope.launch {
                                        settingsStore.setClockMode(ClockMode.EXTERNAL)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = GoblinAccent
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("External (OSC)", color = GoblinText)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = s.clockMode == ClockMode.INTERNAL,
                                onClick = {
                                    scope.launch {
                                        settingsStore.setClockMode(ClockMode.INTERNAL)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = GoblinAccent
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Internal (Watch)", color = GoblinText)
                        }
                    }
                }

                /* ---------- FEEDBACK ---------- */
                SettingsBlock("Feedback") {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptics", color = GoblinText)
                        Switch(
                            checked = s.hapticsEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    settingsStore.setHapticsEnabled(it)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Downbeat Haptic", color = GoblinText)
                        Switch(
                            checked = s.downbeatHapticsEnabled,
                            enabled = s.hapticsEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    settingsStore.setDownbeatHapticsEnabled(it)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transport Haptic", color = GoblinText)
                        Switch(
                            checked = s.transportHapticsEnabled,
                            enabled = s.hapticsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setTransportHapticsEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoblinAccent,
                                checkedTrackColor = GoblinAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = GoblinBorder
                            )
                        )
                    }
                }

                SettingsBlock("OSC Presets") {
                    // … dein bestehender Preset-UI-Block bleibt wie er ist …
                    // (ich lasse den Rest unverändert, weil du ihn schon drin hast)
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { onClose() },
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
