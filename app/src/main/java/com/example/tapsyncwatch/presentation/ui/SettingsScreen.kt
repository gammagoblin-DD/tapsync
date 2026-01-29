package com.example.tapsyncwatch.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

/* ================= GOBLIN COLORS ================= */

private val GoblinBg = Color(0xFF0E0B08)
private val GoblinAccent = Color(0xFF8C5A2B)
private val GoblinBorder = Color(0xFF2A1C12)
private val GoblinButton = Color(0xFF1A120C)
private val GoblinText = Color(0xFFE6D3B1)

/* ================= SCREEN ================= */

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    /** 🔑 Pending Preset Commit */
    var pendingCommit by remember {
        mutableStateOf<PendingPresetCommit?>(null)
    }

    /** 🔒 EINZIGE Stelle mit suspend-Aufruf */
    LaunchedEffect(pendingCommit) {
        val commit = pendingCommit ?: return@LaunchedEffect
        settingsStore.updatePreset(commit.index, commit.preset)
        pendingCommit = null
        onClose()
    }

    /** 🔙 Hardware-Back = Close ohne Preset-Commit */
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                settings?.let { s ->

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
                    }

                    /* ---------- PRESETS ---------- */
                    SettingsBlock("Resolume / OSC Presets") {
                        s.presets.forEachIndexed { index, preset ->
                            PresetRow(
                                preset = preset,
                                active = index == s.activePreset,
                                onActivate = {
                                    scope.launch {
                                        settingsStore.setActivePreset(index)
                                    }
                                },
                                onDone = { name, ip, port ->
                                    port.toIntOrNull()?.let { p ->
                                        pendingCommit = PendingPresetCommit(
                                            index,
                                            preset.copy(
                                                name = name,
                                                ip = ip,
                                                port = p
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            /* ---------- SAVE & CLOSE (GLOBAL SETTINGS) ---------- */
            Button(
                onClick = { onClose() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GoblinButton,
                    contentColor = GoblinText
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save & Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ================= DATA ================= */

private data class PendingPresetCommit(
    val index: Int,
    val preset: OscTarget
)

/* ================= PRESET ROW ================= */

@Composable
private fun PresetRow(
    preset: OscTarget,
    active: Boolean,
    onActivate: () -> Unit,
    onDone: (name: String, ip: String, port: String) -> Unit
) {
    var editOpen by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(preset.name) }
    var editIp by remember { mutableStateOf(preset.ip) }
    var editPort by remember { mutableStateOf(preset.port.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                2.dp,
                if (active) GoblinAccent else GoblinBorder,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onActivate() })
            }
            .padding(12.dp)
    ) {

        Text(
            text = preset.name,
            color = GoblinText,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = { editOpen = !editOpen },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = GoblinButton,
                contentColor = GoblinText
            )
        ) {
            Text(if (editOpen) "Close Edit" else "Edit")
        }

        if (editOpen) {
            Spacer(Modifier.height(8.dp))

            GoblinField("Name", editName, KeyboardType.Text) {
                editName = it
            }
            GoblinField("IP", editIp, KeyboardType.Text) {
                editIp = it
            }
            GoblinField("Port", editPort, KeyboardType.Number) {
                editPort = it
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onDone(editName, editIp, editPort) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GoblinAccent,
                    contentColor = GoblinBg
                )
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ================= UI HELPERS ================= */

@Composable
private fun SettingsBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoblinBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            title,
            color = GoblinAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun GoblinField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, color = GoblinText.copy(alpha = 0.7f), fontSize = 12.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            textStyle = LocalTextStyle.current.copy(color = GoblinText),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = GoblinText,
                focusedBorderColor = GoblinAccent,
                unfocusedBorderColor = GoblinBorder,
                cursorColor = GoblinAccent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
