package com.example.tapsyncwatch.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {

            Text(
                text = "SETTINGS",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                settings?.let { s ->

                    SettingsBlock("Display") {
                        SettingToggle(
                            label = "OSC Statuspunkt",
                            checked = s.showOscDot
                        ) {
                            scope.launch { settingsStore.setShowOscDot(it) }
                        }
                    }

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
                                onUpdate = { updated ->
                                    scope.launch {
                                        settingsStore.updatePreset(index, updated)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF2A2A2A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save & Close", fontSize = 14.sp)
            }
        }
    }
}

/* ---------------- PRESET ROW ---------------- */

@Composable
private fun PresetRow(
    preset: OscTarget,
    active: Boolean,
    onActivate: () -> Unit,
    onUpdate: (OscTarget) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (active) Color(0xFFFF4DA6) else Color(0xFF333333),
                RoundedCornerShape(10.dp)
            )
            .padding(8.dp)
    ) {

        LabeledField("Name", preset.name) {
            onUpdate(preset.copy(name = it))
        }

        Spacer(Modifier.height(4.dp))

        LabeledField("IP", preset.ip) {
            onUpdate(preset.copy(ip = it))
        }

        Spacer(Modifier.height(4.dp))

        LabeledField("Port", preset.port.toString()) {
            it.toIntOrNull()?.let { p ->
                onUpdate(preset.copy(port = p))
            }
        }

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = onActivate,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (active) Color(0xFFFF4DA6) else Color(0xFF2A2A2A)
            )
        ) {
            Text(if (active) "Active" else "Use Preset", fontSize = 12.sp)
        }
    }
}

/* ---------------- UI HELPERS ---------------- */

@Composable
private fun SettingsBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .padding(8.dp)
        )
    }
}
