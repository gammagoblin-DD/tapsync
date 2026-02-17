@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.tapsyncwatch.BuildConfig
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.presentation.data.DEFAULT_SETTINGS_STATE
import com.example.tapsyncwatch.presentation.data.BpmFormat
import com.example.tapsyncwatch.presentation.data.DownbeatStyle
import com.example.tapsyncwatch.presentation.data.PreflightMode
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.data.HeartbeatSendTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource

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

// Shared interaction source for simple no-ripple clicks (chips/nav). Specific components may override locally.
private val interaction = MutableInteractionSource()



private enum class PresetPingKind { OK, TIMEOUT, SEND_FAIL }

private data class PresetPingResult(
    val kind: PresetPingKind,
    val rttMs: Long? = null,
    val pongFrom: String? = null
)

private enum class SettingsPage {
    ROOT,

    // Root categories
    DISPLAY,
    VISUALS,
    HAPTICS,
    CONNECTION,
    DEBUG_TOOLS,
    ABOUT,

    // HUD subpages
    STATUSBAR,
    PREFLIGHT,
    TIMELINE,
    OSC_DOT,
    EXTERNAL_BPM,
    DOWNBEAT_INDICATOR,

    // Visuals subpages
    VISUALS_ANIMATIONS,
    VISUALS_EVENT_FX,
    VISUALS_PHASE,
    VISUALS_INSTRUMENT,
    VISUALS_REMOTE_LOOK,
    VISUALS_VISIBILITY,

    // Connection subpages
    ACTIVE_PRESET,
    TARGETS,      // Presets
    PING_TEST,    // Test ALL / RTT
    NETWORK,      // Heartbeat
    OSC_INPUT,
    OSC_OUTPUT,
    ECHO_GUARD,
    CLOCK,
    TARGET_EDIT,

    // Legacy pages (kept for state restore safety)
    PREFLIGHT_TUNING,
    MOODS,
    AURA_PARTICLES,
    SWING,
    GHOST_ECHO,
    MOTION,
    MOTION_REMOTE,
    MOTION_FX,
    NETWORK_DEBUG,

    // Blueprint v2: detail subpages
    REMOTE_GHOST_DETAILS,
    GOBLIN_FLASH_DETAILS,
    RIPPLES_DETAILS,
    RIPPLE_TAP_DETAILS,
    RIPPLE_MULTDIV_DETAILS,
    RIPPLE_RESYNC_DETAILS,
    RIPPLE_NUDGE_DETAILS,
    RIPPLE_GHOST_DETAILS,
    PHASE_RING_DETAILS,
    PHASE_SPIRAL_DETAILS,
    PHASE_AURA_DETAILS,
    PHASE_PARTICLES_DETAILS,
    OSC_PULSE_DETAILS
}

@Composable
private fun SettingsTitleRow(
    title: String
) {
    // Watch-style: centered title only. Back is handled via system back + edge-swipe gesture.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
                        ) {
        Text(
            title,
            color = GoblinText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
                        }
}

@Composable
private fun SettingsSectionHeader(
    title: String
) {
    // Small, watch-friendly section label used inside LazyColumn.
    Text(
        text = title.uppercase(),
        color = GoblinDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp, start = 6.dp, end = 6.dp)
    )
}


@Composable
private fun SettingsNavChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    subtitleMono: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    val titleColor = if (enabled) GoblinText else GoblinDim
    val subtitleColor = if (enabled) GoblinDim else GoblinDim.copy(alpha = 0.7f)
    val iconTint = if (enabled) GoblinDim else GoblinBorder
    val chevronTint = if (enabled) GoblinBorder else GoblinBorder.copy(alpha = 0.6f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = subtitleColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = if (subtitleMono) FontFamily.Monospace else FontFamily.Default
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = chevronTint, modifier = Modifier.size(18.dp))
        }
    }
}




@Composable
private fun SettingsButtonChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    subtitleMono: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    val titleColor = if (enabled) GoblinText else GoblinDim
    val subtitleColor = if (enabled) GoblinDim else GoblinDim.copy(alpha = 0.7f)
    val iconTint = if (enabled) GoblinDim else GoblinBorder

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = subtitleColor,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = if (subtitleMono) FontFamily.Monospace else FontFamily.Default
                )
            }
        }
    }
}

@Composable
private fun SettingsMsSliderChip(
    title: String,
    valueMs: Long,
    minMs: Long,
    maxMs: Long,
    stepMs: Long = 10L,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (Long) -> Unit
) {
    val minF = minMs.toFloat()
    val maxF = maxMs.toFloat().coerceAtLeast(minF + 1f)
    val step = stepMs.coerceAtLeast(1L).toFloat()

    var local by remember(valueMs, minMs, maxMs) {
        mutableStateOf(valueMs.coerceIn(minMs, maxMs).toFloat())
    }

    fun quantize(v: Float): Long {
        val clamped = v.coerceIn(minF, maxF)
        val q = ((clamped - minF) / step).roundToInt() * step + minF
        return q.roundToLong().coerceIn(minMs, maxMs)
    }

    val steps = (((maxMs - minMs) / stepMs).toInt() - 1).coerceIn(0, 200)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = if (enabled) GoblinText else GoblinDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${quantize(local)}ms",
                    color = if (enabled) GoblinAccent else GoblinDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(6.dp))

            Slider(
                value = local.coerceIn(minF, maxF),
                onValueChange = { if (enabled) local = it.coerceIn(minF, maxF) },
                onValueChangeFinished = { if (enabled) onValueChangeFinished(quantize(local)) },
                valueRange = minF..maxF,
                steps = steps,
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
private fun SettingsFoldChip(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    val shape = RoundedCornerShape(26.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .animateContentSize(),
        color = GoblinCard,
        elevation = 0.dp
                        ) {
        Column(
            modifier = Modifier
                .border(1.dp, GoblinBorder, shape)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = { expanded = !expanded }
                    ),
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
                        Text(
                            subtitle,
                            color = GoblinDim,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = if (enabled) GoblinDim else GoblinBorder
                )
            }

            AnimatedVisibility(visible = expanded && enabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
                            }
                        }
}

@Composable
private fun SettingsToggleChip(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {

    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onToggle(!checked) },
        color = GoblinCard,
        elevation = 0.dp
                        ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
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
                    Text(
                        subtitle,
                        color = GoblinDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                    )
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
private fun SettingsToggleNavChip(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    openEnabled: Boolean = enabled,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = openEnabled,
                        onClick = { onOpen() }
                    )
            ) {
                Text(
                    title,
                    color = if (enabled) GoblinText else GoblinDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = GoblinDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (openEnabled) GoblinBorder else GoblinBorder.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(8.dp))

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
private fun SettingsToggleGroupChip(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    advanced: (@Composable ColumnScope.() -> Unit)? = null
) {

    val interaction = remember { MutableInteractionSource() }

    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    LaunchedEffect(checked, enabled) {
        if (!checked || !enabled) expanded = false
                        }

    val shape = RoundedCornerShape(26.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .animateContentSize(),
        color = GoblinCard,
        elevation = 0.dp
                        ) {
        Column(
            modifier = Modifier
                .border(1.dp, GoblinBorder, shape)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled && checked && (advanced != null),
                        onClick = { expanded = !expanded }
                    ),
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
                        Text(
                            subtitle,
                            color = GoblinDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                        )
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

                if (advanced != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = if (enabled && checked) GoblinDim else GoblinBorder
                    )
                }
            }

            AnimatedVisibility(visible = expanded && enabled && checked && advanced != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = advanced!!
                )
            }
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
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {

    var local by remember(value) { mutableStateOf(value.coerceIn(min, max)) }
    val pct = (local * 100f).toInt().coerceIn((min * 100f).toInt(), (max * 100f).toInt())
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        color = GoblinCard,
        elevation = 0.dp
                        ) {
        Column(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
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
                        Text(
                        subtitle,
                        color = GoblinDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
private fun SettingsSegmentChip(
    title: String,
    subtitle: String? = null,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {

    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        color = GoblinCard,
        elevation = 0.dp
                        ) {
        Column(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                title,
                color = if (enabled) GoblinText else GoblinDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                        subtitle,
                        color = GoblinDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { idx, label ->
                    val sel = idx == selectedIndex
                    val bg = when {
                        sel && enabled -> GoblinAccent.copy(alpha = 0.55f)
                        sel && !enabled -> GoblinBorder
                        else -> GoblinCard2
                    }
                    val border = if (sel) GoblinAccent.copy(alpha = 0.75f) else GoblinBorder

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(bg)
                            .border(1.dp, border, shape)
                            .clickable(enabled = enabled) { onSelect(idx) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (enabled) (if (sel) GoblinText else GoblinDim) else GoblinDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
                            }
                        }
}


@Composable
private fun SettingsCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(GoblinCard2)
            .border(1.dp, GoblinBorder, CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClose() },
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
    val interaction = remember { MutableInteractionSource() }

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
                .clickable(interactionSource = interaction, indication = null) { onClose() }
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
    val interaction = remember { MutableInteractionSource() }

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
                .clickable(interactionSource = interaction, indication = null) { expanded = !expanded }
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
    lastPongFrom: kotlinx.coroutines.flow.StateFlow<String?>,
    lastAnyRxMs: kotlinx.coroutines.flow.StateFlow<Long>,
    lastAnyFrom: kotlinx.coroutines.flow.StateFlow<String?>,
    onClose: () -> Unit,
    // MainActivity may implement a lightweight heartbeat suppression gate (without mutating persisted settings).
    setHeartbeatSuppressed: (Boolean) -> Unit = {}
) {
    val settings by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
    val s = settings

    // Performance: avoid collectAsState() on high-frequency OSC flows while scrolling.
    // We sample StateFlow.value on our low-frequency ticker (nowMs) so the whole screen doesn't recompose on every packet.
    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    val scope = rememberCoroutineScope()
    val pageStack = rememberSaveable(saver = listSaver(
    save = { it.map { p -> p.name } },
    restore = { names -> names.map { SettingsPage.valueOf(it) }.toMutableStateList() }
)) { mutableStateListOf(SettingsPage.ROOT) }
val page = pageStack.last()
fun push(p: SettingsPage) { pageStack.add(p) }
fun pop() { if (pageStack.size > 1) pageStack.removeAt(pageStack.lastIndex) }

    // Tick while settings are open (used for link ages).
    // Disable while editing text fields to keep typing silky.
    val tickerEnabled = page != SettingsPage.TARGET_EDIT
    LaunchedEffect(tickerEnabled) {
        if (!tickerEnabled) return@LaunchedEffect
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            delay(500L)
        }
    }

    var editPresetIndex by rememberSaveable { mutableStateOf(0) }



// Targets page: "Test ALL" runner state
val targetsTestResults = remember { mutableStateMapOf<Int, PresetPingResult>() }
var targetsTestBusy by remember { mutableStateOf(false) }
var targetsTestJob by remember { mutableStateOf<Job?>(null) }

var targetsTestRunningIndex by remember { mutableStateOf(-1) }
var targetsTestSnapshot by remember { mutableStateOf<List<OscTarget>>(emptyList()) }
	var targetsTestRestoreHeartbeat by remember { mutableStateOf(false) }


LaunchedEffect(page) {
    if (page != SettingsPage.TARGETS) {
        targetsTestJob?.cancel()
        targetsTestJob = null
        targetsTestBusy = false
    }
}


suspend fun pingPresetOnce(target: OscTarget): PresetPingResult {
    val host = target.ip.trim()
    val port = target.port.coerceIn(1, 65535)
    if (host.isEmpty()) return PresetPingResult(PresetPingKind.SEND_FAIL)

    val started = SystemClock.elapsedRealtime()
    val nonce = ((started % 1000L) + 1L).toFloat() / 1000f

    val sentOk = try {
        sendOscPingFloat(host, port, nonce)
        true
    } catch (_: Exception) {
        false
    }
    if (!sentOk) return PresetPingResult(PresetPingKind.SEND_FAIL)

    val pongTs = withTimeoutOrNull(900L) {
        lastPongMs.filter { it >= started }.first()
    }

    if (pongTs == null) return PresetPingResult(PresetPingKind.TIMEOUT)

    val rtt = (pongTs - started).coerceAtLeast(0L)
    var from = lastPongFrom.value
    if (from == null) {
        delay(10L) // allow OscInputReceiver to update lastPongFrom after lastPongMs
        from = lastPongFrom.value
    }

    return PresetPingResult(PresetPingKind.OK, rttMs = rtt, pongFrom = from)
}

    // Single-level Back/Swipe guard: avoid double-pop and accidental close.
    var lastPopMs by rememberSaveable { mutableStateOf(0L) }

    fun popOneLevel() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPopMs < 250L) return
        lastPopMs = now
        if (pageStack.size > 1) pop() else onClose()
    }

    BackHandler { popOneLevel() }

    val isRound = LocalConfiguration.current.isScreenRound
    val edgePad = if (isRound) 18.dp else 12.dp
    val topPad = if (isRound) 18.dp else 12.dp
    val bottomPad = if (isRound) 18.dp else 12.dp

    // Edge-swipe back (like watch settings): swipe from left edge to go back one level.
    val density = LocalDensity.current
    val edgePx = with(density) { 22.dp.toPx() }
    val triggerPx = with(density) { 42.dp.toPx() }


    // Link status (Ping/Pong + optional Any-RX fallback)
    val lastPongSnapshot = lastPongMs.value
    val lastAnyRxSnapshot = lastAnyRxMs.value
    val pongAge = remember(lastPongSnapshot, nowMs) {
        if (lastPongSnapshot <= 0L || nowMs <= 0L) null else (nowMs - lastPongSnapshot).coerceAtLeast(0L)
    }
    val anyRxAge = remember(lastAnyRxSnapshot, nowMs) {
        if (lastAnyRxSnapshot <= 0L || nowMs <= 0L) null else (nowMs - lastAnyRxSnapshot).coerceAtLeast(0L)
    }

    val pongOk = s.heartbeatEnabled && pongAge != null && pongAge < s.signalGraceMs
    val anyOk = s.oscInputAnyRxFallbackEnabled && anyRxAge != null && anyRxAge < s.oscInputAnyRxTimeoutMs

    val linkOk = when {
        pongOk -> true
        anyOk -> true
        !s.heartbeatEnabled && !s.oscInputAnyRxFallbackEnabled -> true // neutral: link check off
        else -> false
                        }

    val linkLabel = when {
        pongOk -> "PONG ${pongAge ?: 0L}ms"
        !s.heartbeatEnabled && s.oscInputAnyRxFallbackEnabled -> when {
            anyRxAge == null -> "waiting…"
            anyOk -> "RX ${anyRxAge ?: 0L}ms"
            else -> "NO RX"
                            }
        !s.heartbeatEnabled -> "OFF"
        s.heartbeatEnabled && anyOk -> "RX ${anyRxAge ?: 0L}ms"
        pongAge == null && (!s.oscInputAnyRxFallbackEnabled || anyRxAge == null) -> "waiting…"
        else -> "NO SIGNAL"
                        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoblinBg)
            .pointerInput(pageStack.size) {
                var started = false
                var dx = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        started = offset.x <= edgePx
                        dx = 0f
                    },
                    onDrag = { _, dragAmount ->
                        if (started) dx += dragAmount.x
                    },
                    onDragEnd = {
                        if (!started) return@detectDragGestures
                        if (dx > triggerPx) {
                            popOneLevel()
                        }
                        started = false
                        dx = 0f
                    },
                    onDragCancel = { started = false; dx = 0f }
                )
            }
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
                    val active = s.activeTarget

                    // Health-rich subtitles (festival-friendly): show what's alive without opening pages.
                    val grace = s.signalGraceMs.coerceAtLeast(0L)
                    val hardDownMs = (maxOf(grace * 2L, 10_000L)).coerceIn(6_000L, 60_000L)
                    val isHardDown = s.heartbeatEnabled && pongAge != null && pongAge >= hardDownMs && !pongOk && !anyOk

                    val connHealth = if (isHardDown) "HARD DOWN" else linkLabel
                    val connSubtitle = "${active.name} • $connHealth"

                    val displaySubtitle = if (!s.animationsEnabled) {
                        "Anim OFF"
                    } else {
                        "Local ${if (s.localVisualsEnabled) "ON" else "OFF"} • Remote ${if (s.remoteAnimationsEnabled) "ON" else "OFF"}"
                    }

                    val hapSubtitle = if (!s.hapticsEnabled) {
                        "OFF"
                    } else {
                        "ON • DB ${if (s.downbeatHapticsEnabled) "ON" else "OFF"} • TR ${if (s.transportHapticsEnabled) "ON" else "OFF"}"
                    }

                    val hbStatus = when {
                        !s.heartbeatEnabled -> "OFF"
                        isHardDown -> "HARD"
                        linkOk -> "UP"
                        else -> "DOWN"
                    }

                    val dbgCount = listOf(s.showOscDebug, s.showPreflight, s.showTimeline, s.heartbeatEnabled).count { it }
                    val dbgSubtitle = if (dbgCount == 0) {
                        "alles aus"
                    } else {
                        buildString {
                            if (s.showOscDebug) append("OSC ")
                            if (s.showPreflight) append("Preflight ")
                            if (s.showTimeline) append("Timeline ")
                            if (s.heartbeatEnabled) append("HB $hbStatus ")
                        }.trim()
                    }

                    val aboutSubtitle = "v${BuildConfig.VERSION_NAME}"

                    item { SettingsTitleRow("Einstellungen") }

                    item { SettingsNavChip(Icons.Filled.Visibility, "Anzeige", displaySubtitle) { push(SettingsPage.DISPLAY) } }
                    item { SettingsNavChip(Icons.Filled.Wifi, "Verbindungen", connSubtitle, subtitleMono = true) { push(SettingsPage.CONNECTION) } }
                    item { SettingsNavChip(Icons.Filled.Vibration, "Haptik", hapSubtitle) { push(SettingsPage.HAPTICS) } }
                    item { SettingsNavChip(Icons.Filled.BugReport, "Debug", dbgSubtitle) { push(SettingsPage.DEBUG_TOOLS) } }
                    item { SettingsNavChip(Icons.Filled.Info, "Über", aboutSubtitle) { push(SettingsPage.ABOUT) } }
}

                SettingsPage.DISPLAY -> {
    val enabled = s.animationsEnabled

    val fadeLabel = when (s.oscDotFadeMs) {
        120L -> "Fast"
        180L -> "Normal"
        260L -> "Smooth"
        400L -> "Slow"
        650L -> "Cinema"
        else -> "${s.oscDotFadeMs}ms"
    }
    val oscDotSubtitle = if (s.showOscDot) "ON • $fadeLabel" else "OFF"

    val bpmFmtLabel = when (s.bpmFormat) {
        BpmFormat.BPM -> "BPM"
        BpmFormat.BPM_PHASE -> "BPM+Phase"
        BpmFormat.BPM_BAR -> "BPM+Bar"
    }
    val bpmSubtitle = if (s.showExternalBpm) "ON • $bpmFmtLabel" else "OFF"

    val phaseAny = s.phaseVisualizerEnabled || s.phaseSpiralEnabled || s.phaseAuraEnabled || s.microParticlesEnabled
    val moodsSubtitle = if (s.moodsEnabled) "ON • ${(s.moodIntensity * 100).toInt()}%" else "OFF"

    item { SettingsTitleRow("Anzeige") }

    item {
        SettingsToggleChip(
            title = "Animationen",
            subtitle = "Master switch",
            checked = s.animationsEnabled
        ) { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
    }

    item {
        SettingsToggleChip(
            title = "Local Visuals",
            subtitle = "eigene Events",
            checked = s.localVisualsEnabled,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setLocalVisualsEnabled(v) } }
    }

    item {
        SettingsToggleNavChip(
            title = "Remote Visuals",
            subtitle = if (s.remoteAnimationsEnabled) "ON" else "OFF",
            checked = s.remoteAnimationsEnabled,
            enabled = enabled,
            openEnabled = enabled && s.remoteAnimationsEnabled,
            onToggle = { v -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } },
            onOpen = { push(SettingsPage.VISUALS_REMOTE_LOOK) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "OSC Dot",
            subtitle = oscDotSubtitle,
            checked = s.showOscDot,
            openEnabled = s.showOscDot,
            onToggle = { v -> scope.launch { settingsStore.setShowOscDot(v) } },
            onOpen = { push(SettingsPage.OSC_DOT) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "External BPM",
            subtitle = bpmSubtitle,
            checked = s.showExternalBpm,
            openEnabled = s.showExternalBpm,
            onToggle = { v -> scope.launch { settingsStore.setShowExternalBpm(v) } },
            onOpen = { push(SettingsPage.EXTERNAL_BPM) }
        )
    }

    item { SettingsNavChip(Icons.Filled.Tune, "Events", "Downbeat • Goblin Flash • Ripples", enabled = enabled) { push(SettingsPage.VISUALS_EVENT_FX) } }

    item {
        SettingsToggleNavChip(
            title = "Phase",
            subtitle = if (phaseAny) "ON" else "OFF",
            checked = phaseAny,
            enabled = enabled,
            openEnabled = enabled && phaseAny,
            onToggle = { v ->
                scope.launch {
                    settingsStore.setPhaseVisualizerEnabled(v)
                    settingsStore.setPhaseSpiralEnabled(v)
                    settingsStore.setPhaseAuraEnabled(v)
                    settingsStore.setMicroParticlesEnabled(v)
                }
            },
            onOpen = { push(SettingsPage.VISUALS_PHASE) }
        )
    }


    item {
        SettingsToggleNavChip(
            title = "OSC Pulse",
            subtitle = if (s.oscPulseEnabled) "ON" else "OFF",
            checked = s.oscPulseEnabled,
            enabled = enabled,
            openEnabled = enabled && s.oscPulseEnabled,
            onToggle = { v -> scope.launch { settingsStore.setOscPulseEnabled(v) } },
            onOpen = { push(SettingsPage.OSC_PULSE_DETAILS) }
        )
    }
    item {
        SettingsToggleNavChip(
            title = "Moods",
            subtitle = moodsSubtitle,
            checked = s.moodsEnabled,
            enabled = enabled,
            openEnabled = enabled && s.moodsEnabled,
            onToggle = { v -> scope.launch { settingsStore.setMoodsEnabled(v) } },
            onOpen = { push(SettingsPage.MOODS) }
        )
    }

    item {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Debug-Overlays (Preflight/Timeline/Heartbeat/OSC) findest du unter Debug.",
            color = GoblinDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

                SettingsPage.STATUSBAR -> {
                    item { SettingsTitleRow("Statusbar (Deprecated)") }

                    item {
                        Text(
                            "TapScreen-HUD ist entfernt. Diese Einstellung wird ignoriert und existiert nur für alte Saves.",
                            color = GoblinDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    item {
                        SettingsButtonChip(
                            icon = Icons.Filled.Clear,
                            title = "Clear legacy flag",
                            subtitle = if (s.showStatusLine) "Currently ON (no effect)" else "Currently OFF",
                            enabled = s.showStatusLine
                        ) {
                            scope.launch { settingsStore.setShowStatusLine(false) }
                        }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.BugReport,
                            title = "Open Debug",
                            subtitle = "Preflight • Timeline • Heartbeat • OSC Monitor"
                        ) { push(SettingsPage.DEBUG_TOOLS) }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.Visibility,
                            title = "Open Anzeige",
                            subtitle = "Visual settings"
                        ) { push(SettingsPage.DISPLAY) }
                    }
                }


                SettingsPage.OSC_DOT -> {
                    item { SettingsTitleRow("OSC Dot") }

                    item {
                        SettingsToggleChip(
                            title = "OSC Dot",
                            subtitle = "Aktivitäts-Punkt",
                            checked = s.showOscDot
                        ) { v -> scope.launch { settingsStore.setShowOscDot(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.oscDotOpacity * 100f).roundToLong()}%",
                            value = s.oscDotOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = s.showOscDot
                        ) { v -> scope.launch { settingsStore.setOscDotOpacity(v) } }
                    }

                    item {
                        val fadeOptions = listOf(120L, 180L, 260L, 400L, 650L)
                        val fadeLabels = listOf("Fast", "Normal", "Smooth", "Slow", "Cinema")
                        val fadeIndex = fadeOptions.indexOfFirst { it == s.oscDotFadeMs }.let { if (it < 0) 1 else it }
                        SettingsSegmentChip(
                            title = "Fade",
                            subtitle = "${s.oscDotFadeMs}ms",
                            options = fadeLabels,
                            selectedIndex = fadeIndex,
                            enabled = s.showOscDot
                        ) { idx -> scope.launch { settingsStore.setOscDotFadeMs(fadeOptions[idx]) } }
                    }
                }

                SettingsPage.EXTERNAL_BPM -> {
                    item { SettingsTitleRow("External BPM") }

                    item {
                        SettingsToggleChip(
                            title = "External BPM",
                            subtitle = "Resolume → Watch",
                            checked = s.showExternalBpm
                        ) { v -> scope.launch { settingsStore.setShowExternalBpm(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.bpmOpacity * 100f).roundToLong()}%",
                            value = s.bpmOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = s.showExternalBpm
                        ) { v -> scope.launch { settingsStore.setBpmOpacity(v) } }
                    }

                    item {
                        val bpmFmtLabels = listOf("BPM", "BPM+Phase", "BPM+Bar")
                        val bpmFmtIndex = when (s.bpmFormat) {
                            BpmFormat.BPM -> 0
                            BpmFormat.BPM_PHASE -> 1
                            BpmFormat.BPM_BAR -> 2
                        }
                        SettingsSegmentChip(
                            title = "Format",
                            subtitle = bpmFmtLabels[bpmFmtIndex],
                            options = bpmFmtLabels,
                            selectedIndex = bpmFmtIndex,
                            enabled = s.showExternalBpm
                        ) { i ->
                            val v = when (i) {
                                1 -> BpmFormat.BPM_PHASE
                                2 -> BpmFormat.BPM_BAR
                                else -> BpmFormat.BPM
                            }
                            scope.launch { settingsStore.setBpmFormat(v) }
                        }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Hide 'BPM'",
                            subtitle = "Show only the number",
                            checked = s.hideBpmUnit,
                            enabled = s.showExternalBpm
                        ) { v -> scope.launch { settingsStore.setHideBpmUnit(v) } }
                    }
                }

                SettingsPage.DOWNBEAT_INDICATOR -> {
                    item { SettingsTitleRow("Downbeat") }

                    item {
                        SettingsToggleChip(
                            title = "Downbeat Indicator",
                            subtitle = "Kick auf 1 (visuell)",
                            checked = s.showDownbeatIndicator
                        ) { v -> scope.launch { settingsStore.setShowDownbeatIndicator(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.downbeatOpacity * 100f).roundToLong()}%",
                            value = s.downbeatOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = s.showDownbeatIndicator
                        ) { v -> scope.launch { settingsStore.setDownbeatOpacity(v) } }
                    }

                    item {
                        val labels = listOf("Dot", "Tick", "Pulse")
                        val idx = when (s.downbeatStyle) {
                            DownbeatStyle.DOT -> 0
                            DownbeatStyle.TICK -> 1
                            DownbeatStyle.PULSE -> 2
                        }
                        SettingsSegmentChip(
                            title = "Style",
                            subtitle = labels[idx],
                            options = labels,
                            selectedIndex = idx,
                            enabled = s.showDownbeatIndicator
                        ) { i ->
                            val v = when (i) {
                                1 -> DownbeatStyle.TICK
                                2 -> DownbeatStyle.PULSE
                                else -> DownbeatStyle.DOT
                            }
                            scope.launch { settingsStore.setDownbeatStyle(v) }
                        }
                    }
                }

                SettingsPage.PREFLIGHT -> {
                    item { SettingsTitleRow("Preflight") }

                    item {
                        SettingsToggleChip(
                            title = "Preflight",
                            subtitle = "P / IN / OUT",
                            checked = s.showPreflight
                        ) { v -> scope.launch { settingsStore.setShowPreflight(v) } }
                    }

                    item {
                        val modeIdx = if (s.preflightMode == PreflightMode.MINIMAL) 1 else 0
                        SettingsSegmentChip(
                            title = "Mode",
                            subtitle = "FULL = labels, MIN = dots",
                            options = listOf("FULL", "MIN"),
                            selectedIndex = modeIdx,
                            enabled = s.showPreflight
                        ) { i ->
                            val v = if (i == 1) PreflightMode.MINIMAL else PreflightMode.FULL
                            scope.launch { settingsStore.setPreflightMode(v) }
                        }
                    }

                    item { DividerLine() }

                    item {
                        val phaseOpts = listOf(140L, 220L, 320L, 480L)
                        val phaseLabels = listOf("140", "220", "320", "480")
                        val phaseIdx = phaseOpts.indexOfFirst { it == s.preflightPhaseOkMs }.let { if (it >= 0) it else 1 }
                        SettingsSegmentChip(
                            title = "IN Phase OK",
                            subtitle = "grün wenn ≤ ${s.preflightPhaseOkMs}ms",
                            options = phaseLabels,
                            selectedIndex = phaseIdx,
                            enabled = s.showPreflight
                        ) { i -> scope.launch { settingsStore.setPreflightPhaseOkMs(phaseOpts[i]) } }
                    }

                    item {
                        val downbeatOpts = listOf(60L, 90L, 120L, 160L)
                        val downbeatLabels = listOf("60", "90", "120", "160")
                        val downbeatIdx = downbeatOpts.indexOfFirst { it == s.preflightDownbeatOkMs }.let { if (it >= 0) it else 1 }
                        SettingsSegmentChip(
                            title = "IN Downbeat OK",
                            subtitle = "grün wenn ≤ ${s.preflightDownbeatOkMs}ms",
                            options = downbeatLabels,
                            selectedIndex = downbeatIdx,
                            enabled = s.showPreflight
                        ) { i -> scope.launch { settingsStore.setPreflightDownbeatOkMs(downbeatOpts[i]) } }
                    }

                    item {
                        val outOpts = listOf(2000L, 6000L, 10000L)
                        val outLabels = listOf("2s", "6s", "10s")
                        val outIdx = outOpts.indexOfFirst { it == s.preflightOutOkMs }.let { if (it >= 0) it else 1 }
                        SettingsSegmentChip(
                            title = "OUT OK",
                            subtitle = "grün wenn ≤ ${s.preflightOutOkMs}ms",
                            options = outLabels,
                            selectedIndex = outIdx,
                            enabled = s.showPreflight
                        ) { i -> scope.launch { settingsStore.setPreflightOutOkMs(outOpts[i]) } }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tuning: Schwellenwerte für IN/OUT – live ruhig halten.",
                            color = GoblinDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                SettingsPage.PREFLIGHT_TUNING -> {
                    item { SettingsTitleRow("Preflight Tuning (Deprecated)") }

                    item {
                        Text(
                            "Moved: Debug → Preflight (Mode + thresholds)",
                            color = GoblinDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.BugReport,
                            title = "Open Preflight",
                            subtitle = "Mode + thresholds"
                        ) { push(SettingsPage.PREFLIGHT) }
                    }
                }


                SettingsPage.TIMELINE -> {
                    item { SettingsTitleRow("Timeline") }

                    item {
                        SettingsToggleChip(
                            title = "Timeline",
                            subtitle = "Events als Liste",
                            checked = s.showTimeline
                        ) { v -> scope.launch { settingsStore.setShowTimeline(v) } }
                    }

                    item {
                        val winOpts = listOf(3000L, 5000L, 8000L, 12000L)
                        val winLabels = listOf("3s", "5s", "8s", "12s")
                        val winIdx = winOpts.indexOfFirst { it == s.timelineWindowMs }.let { if (it < 0) 1 else it }
                        SettingsSegmentChip(
                            title = "Window",
                            subtitle = "${s.timelineWindowMs}ms",
                            options = winLabels,
                            selectedIndex = winIdx,
                            enabled = s.showTimeline
                        ) { i -> scope.launch { settingsStore.setTimelineWindowMs(winOpts[i]) } }
                    }

                    item { DividerLine() }

                    item {
                        SettingsToggleChip(
                            title = "Local",
                            subtitle = "Show local events",
                            checked = s.timelineShowLocal,
                            enabled = s.showTimeline
                        ) { v -> scope.launch { settingsStore.setTimelineShowLocal(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Remote",
                            subtitle = "Show remote events",
                            checked = s.timelineShowRemote,
                            enabled = s.showTimeline
                        ) { v -> scope.launch { settingsStore.setTimelineShowRemote(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Health",
                            subtitle = "Errors + Pong",
                            checked = s.timelineShowHealth,
                            enabled = s.showTimeline
                        ) { v -> scope.launch { settingsStore.setTimelineShowHealth(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Important only",
                            subtitle = "Tap/Resync/Nudge + Errors + Pong",
                            checked = s.timelineImportantOnly,
                            enabled = s.showTimeline
                        ) { v -> scope.launch { settingsStore.setTimelineImportantOnly(v) } }
                    }
                }

SettingsPage.VISUALS -> {
    val animSubtitle = if (s.animationsEnabled) "ON" else "OFF"
    val remoteSubtitle = when {
        !s.animationsEnabled -> "Animationen aus"
        s.remoteAnimationsEnabled && s.remoteGhostModeEnabled -> "Remote + Ghost"
        s.remoteAnimationsEnabled -> "Remote"
        else -> "OFF"
    }

    item { SettingsTitleRow("Visuals") }

    item { SettingsNavChip(Icons.Filled.Palette, "Animationen", animSubtitle) { push(SettingsPage.VISUALS_ANIMATIONS) } }
    item { SettingsNavChip(Icons.Filled.Tune, "Event FX", "Goblin Flash • Ripples • OSC Pulse") { push(SettingsPage.VISUALS_EVENT_FX) } }
    item { SettingsNavChip(Icons.Filled.SyncAlt, "Phase", "Ring • Spiral") { push(SettingsPage.VISUALS_PHASE) } }
    item { SettingsNavChip(Icons.Filled.MoreHoriz, "Instrument", "Moods • Aura • Particles • Swing • Echo") { push(SettingsPage.VISUALS_INSTRUMENT) } }
    item { SettingsNavChip(Icons.Filled.Visibility, "Remote Look", remoteSubtitle) { push(SettingsPage.VISUALS_REMOTE_LOOK) } }
    item { SettingsNavChip(Icons.Filled.Tune, "Sichtbarkeit", "FX • Phase • Ghost Opacity") { push(SettingsPage.VISUALS_VISIBILITY) } }
}

                SettingsPage.VISUALS_ANIMATIONS -> {
    item { SettingsTitleRow("Animationen") }

    item {
        SettingsToggleChip(
            title = "Animationen",
            subtitle = "Master switch",
            checked = s.animationsEnabled
        ) { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
    }

    item {
        Spacer(Modifier.height(6.dp))
        Text(
            "Wenn Animationen aus sind, sind alle Visual-Seiten deaktiviert.",
            color = GoblinDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

SettingsPage.VISUALS_EVENT_FX -> {
    val enabled = s.animationsEnabled

    item { SettingsTitleRow("Events") }

    if (!enabled) {
        item {
            Text(
                "Animationen sind aus — FX sind deaktiviert.",
                color = GoblinDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    item {
        SettingsToggleNavChip(
            title = "Downbeat Indicator",
            subtitle = if (s.showDownbeatIndicator) "ON" else "OFF",
            checked = s.showDownbeatIndicator,
            enabled = enabled,
            openEnabled = enabled && s.showDownbeatIndicator,
            onToggle = { v -> scope.launch { settingsStore.setShowDownbeatIndicator(v) } },
            onOpen = { push(SettingsPage.DOWNBEAT_INDICATOR) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Goblin Flash",
            subtitle = if (s.goblinFlashEnabled) "ON" else "OFF",
            checked = s.goblinFlashEnabled,
            enabled = enabled,
            openEnabled = enabled && s.goblinFlashEnabled,
            onToggle = { v -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } },
            onOpen = { push(SettingsPage.GOBLIN_FLASH_DETAILS) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Ripples",
            subtitle = if (s.rippleEnabled) "ON" else "OFF",
            checked = s.rippleEnabled,
            enabled = enabled,
            openEnabled = enabled && s.rippleEnabled,
            onToggle = { v -> scope.launch { settingsStore.setRippleEnabled(v) } },
            onOpen = { push(SettingsPage.RIPPLES_DETAILS) }
        )
    }

}

SettingsPage.VISUALS_PHASE -> {
    val enabled = s.animationsEnabled

    item { SettingsTitleRow("Phase") }

    if (!enabled) {
        item {
            Text(
                "Animationen sind aus — Phase Visuals sind deaktiviert.",
                color = GoblinDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    item {
        SettingsToggleNavChip(
            title = "Phase Ring",
            subtitle = if (s.phaseVisualizerEnabled) "ON" else "OFF",
            checked = s.phaseVisualizerEnabled,
            enabled = enabled,
            openEnabled = enabled && s.phaseVisualizerEnabled,
            onToggle = { v -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } },
            onOpen = { push(SettingsPage.PHASE_RING_DETAILS) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Phase Spiral",
            subtitle = if (s.phaseSpiralEnabled) "ON" else "OFF",
            checked = s.phaseSpiralEnabled,
            enabled = enabled,
            openEnabled = enabled && s.phaseSpiralEnabled,
            onToggle = { v -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } },
            onOpen = { push(SettingsPage.PHASE_SPIRAL_DETAILS) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Phase Aura",
            subtitle = if (s.phaseAuraEnabled) "ON" else "OFF",
            checked = s.phaseAuraEnabled,
            enabled = enabled,
            openEnabled = enabled && s.phaseAuraEnabled,
            onToggle = { v -> scope.launch { settingsStore.setPhaseAuraEnabled(v) } },
            onOpen = { push(SettingsPage.PHASE_AURA_DETAILS) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Micro Particles",
            subtitle = if (s.microParticlesEnabled) "ON" else "OFF",
            checked = s.microParticlesEnabled,
            enabled = enabled,
            openEnabled = enabled && s.microParticlesEnabled,
            onToggle = { v -> scope.launch { settingsStore.setMicroParticlesEnabled(v) } },
            onOpen = { push(SettingsPage.PHASE_PARTICLES_DETAILS) }
        )
    }

    item { DividerLine() }

    item {
        SettingsSliderChip(
            title = "Swing Amount",
            subtitle = "${(s.visualSwing * 100f).roundToLong()}%",
            value = s.visualSwing,
            min = 0f,
            max = 0.25f,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setVisualSwing(v) } }
    }
}

SettingsPage.VISUALS_REMOTE_LOOK -> {
    val enabled = s.animationsEnabled

    item { SettingsTitleRow("Remote Visuals") }

    if (!enabled) {
        item {
            Text(
                "Animationen sind aus — Remote Visuals sind deaktiviert.",
                color = GoblinDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    item {
        SettingsToggleNavChip(
            title = "Remote Ghost",
            subtitle = if (s.remoteGhostModeEnabled) "ON" else "OFF",
            checked = s.remoteGhostModeEnabled,
            enabled = enabled && s.remoteAnimationsEnabled,
            openEnabled = enabled && s.remoteAnimationsEnabled && s.remoteGhostModeEnabled,
            onToggle = { v -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } },
            onOpen = { push(SettingsPage.REMOTE_GHOST_DETAILS) }
        )
    }

    item {
        SettingsSliderChip(
            title = "Debounce",
            subtitle = "${s.remoteEventMinIntervalMs}ms min interval",
            value = s.remoteEventMinIntervalMs.toFloat(),
            min = 0f,
            max = 500f,
            enabled = enabled && s.remoteAnimationsEnabled
        ) { v -> scope.launch { settingsStore.setRemoteEventMinIntervalMs(v.roundToLong()) } }
    }


    item {
        Spacer(Modifier.height(6.dp))
        Text(
            "Debounce glättet Remote-Spam (nur Visual-Events).",
            color = GoblinDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

                

                SettingsPage.VISUALS_VISIBILITY -> {
    val enabled = s.animationsEnabled
    val ghostEnabled = enabled && s.remoteAnimationsEnabled && s.remoteGhostModeEnabled

    item { SettingsTitleRow("Sichtbarkeit") }

    if (!enabled) {
        item {
            Text(
                "Animationen sind aus — Opacity ist deaktiviert.",
                color = GoblinDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    item {
        SettingsSliderChip(
            title = "FX Opacity",
            subtitle = "${(s.fxAlpha * 100f).roundToLong()}%",
            value = s.fxAlpha,
            min = 0.30f,
            max = 2.00f,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setFxAlpha(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Phase Opacity",
            subtitle = "${(s.phaseAlpha * 100f).roundToLong()}%",
            value = s.phaseAlpha,
            min = 0.30f,
            max = 2.00f,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setPhaseAlpha(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Ghost Opacity",
            subtitle = "${(s.ghostAlpha * 100f).roundToLong()}%",
            value = s.ghostAlpha,
            min = 0.30f,
            max = 2.00f,
            enabled = ghostEnabled
        ) { v -> scope.launch { settingsStore.setGhostAlpha(v) } }
    }

    if (!ghostEnabled) {
        item {
            Text(
                "Ghost Opacity ist nur aktiv wenn Remote Ghost aktiv ist.",
                color = GoblinDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}



                SettingsPage.VISUALS_INSTRUMENT -> {
    val enabled = s.animationsEnabled

    item { SettingsTitleRow("Instrument") }

    if (!enabled) {
        item {
            Text(
                "Animationen sind aus — Instrument-Layer sind deaktiviert.",
                color = GoblinDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    item {
        SettingsToggleChip(
            title = "Moods",
            subtitle = "Color mood shifts",
            checked = s.moodsEnabled,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setMoodsEnabled(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Mood Intensity",
            subtitle = "${(s.moodIntensity * 100).toInt()}%",
            value = s.moodIntensity,
            min = 0f,
            max = 0.20f,
            enabled = enabled && s.moodsEnabled
        ) { v -> scope.launch { settingsStore.setMoodIntensity(v) } }
    }

    item { DividerLine() }

    item {
        SettingsToggleChip(
            title = "Phase Aura",
            subtitle = "only when stability is high",
            checked = s.phaseAuraEnabled,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setPhaseAuraEnabled(v) } }
    }

    item {
        SettingsToggleChip(
            title = "Micro Particles",
            subtitle = "grainy sparkle layer",
            checked = s.microParticlesEnabled,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setMicroParticlesEnabled(v) } }
    }

    item {
        SettingsNavChip(
            icon = Icons.Filled.Tune,
            title = "Swing",
            subtitle = "${(s.visualSwing * 100f).roundToLong()}%",
            enabled = enabled
        ) { push(SettingsPage.SWING) }
    }
item { DividerLine() }

    item {
        SettingsToggleChip(
            title = "Ghost Echo",
            subtitle = "adds a trailing ring",
            checked = s.ghostEchoEnabled,
            enabled = enabled
        ) { v -> scope.launch { settingsStore.setGhostEchoEnabled(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Echo Strength",
            subtitle = "${(s.ghostEchoStrength * 100).toInt()}%",
            value = s.ghostEchoStrength,
            min = 0f,
            max = 1.0f,
            enabled = enabled && s.ghostEchoEnabled
        ) { v -> scope.launch { settingsStore.setGhostEchoStrength(v) } }
    }
}

                SettingsPage.MOODS -> {
                    item { SettingsTitleRow("Goblin Moods") }

                    item {
                        SettingsToggleChip(
                            title = "Moods",
                            subtitle = "subtle state personality",
                            checked = s.moodsEnabled
                        ) { v -> scope.launch { settingsStore.setMoodsEnabled(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Intensity",
                            subtitle = "${(s.moodIntensity * 100).toInt()}%",
                            value = s.moodIntensity,
                            min = 0f,
                            max = 0.20f,
                            enabled = s.moodsEnabled
                        ) { v -> scope.launch { settingsStore.setMoodIntensity(v) } }
                    }
                }

                SettingsPage.AURA_PARTICLES -> {
                    item { SettingsTitleRow("Aura + Particles") }

                    item {
                        SettingsToggleChip(
                            title = "Phase Aura",
                            subtitle = "only when stability is high",
                            checked = s.phaseAuraEnabled
                        ) { v -> scope.launch { settingsStore.setPhaseAuraEnabled(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Micro Particles",
                            subtitle = "alive sparkle (stable only)",
                            checked = s.microParticlesEnabled
                        ) { v -> scope.launch { settingsStore.setMicroParticlesEnabled(v) } }
                    }
                }

                SettingsPage.SWING -> {
                    item { SettingsTitleRow("Swing") }

                    item {
                        SettingsSliderChip(
                            title = "Amount",
                            subtitle = "visual only",
                            value = s.visualSwing,
                            min = 0f,
                            max = 0.25f
                        ) { v -> scope.launch { settingsStore.setVisualSwing(v) } }
                    }
                }

                SettingsPage.GHOST_ECHO -> {
                    item { SettingsTitleRow("Ghost Echo") }

                    item {
                        SettingsToggleChip(
                            title = "Echo",
                            subtitle = "adds a trailing ring",
                            checked = s.ghostEchoEnabled
                        ) { v -> scope.launch { settingsStore.setGhostEchoEnabled(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Strength",
                            subtitle = "${(s.ghostEchoStrength * 100).toInt()}%",
                            value = s.ghostEchoStrength,
                            min = 0f,
                            max = 1.0f,
                            enabled = s.ghostEchoEnabled
                        ) { v -> scope.launch { settingsStore.setGhostEchoStrength(v) } }
                    }
                }
                SettingsPage.MOTION -> {
                    item { SettingsTitleRow("Animation") }

                    item {
                        SettingsToggleChip(
                            title = "Animationen",
                            subtitle = "Master switch",
                            checked = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.AutoMirrored.Filled.Input,
                            title = "Remote",
                            subtitle = "Input visuals (Ghost etc.)"
                        ) { push(SettingsPage.MOTION_REMOTE) }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.AutoAwesome,
                            title = "FX",
                            subtitle = "Ripples / Pulse / Flash"
                        ) { push(SettingsPage.MOTION_FX) }
                    }
                }

                

                SettingsPage.MOTION_REMOTE -> {
                    item { SettingsTitleRow("Remote") }

                    item {
                        SettingsToggleChip(
                            title = "Remote Animation",
                            subtitle = "nur visuell",
                            checked = s.remoteAnimationsEnabled,
                            enabled = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Remote Ghost",
                            subtitle = "Remote fühlt sich anders an",
                            checked = s.remoteGhostModeEnabled,
                            enabled = s.animationsEnabled && s.remoteAnimationsEnabled
                        ) { v -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } }
                    }
                }

                SettingsPage.MOTION_FX -> {
                    item { SettingsTitleRow("FX") }

                    item {
                        SettingsToggleChip(
                            title = "Goblin Flash",
                            subtitle = "Flash bei Events",
                            checked = s.goblinFlashEnabled,
                            enabled = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Ripples",
                            subtitle = "Wellen feedback",
                            checked = s.rippleEnabled,
                            enabled = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setRippleEnabled(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "OSC Pulse",
                            subtitle = "Pulse animation",
                            checked = s.oscPulseEnabled,
                            enabled = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setOscPulseEnabled(v) } }
                    }
                }

                SettingsPage.HAPTICS -> {
                    item { SettingsTitleRow("Haptik") }

                    item { SettingsToggleChip("Haptik", "Master switch", s.hapticsEnabled) { v -> scope.launch { settingsStore.setHapticsEnabled(v) } } }
                    item { SettingsToggleChip("Downbeat", "Kick auf 1", s.downbeatHapticsEnabled, enabled = s.hapticsEnabled) { v -> scope.launch { settingsStore.setDownbeatHapticsEnabled(v) } } }
                    item { SettingsToggleChip("Transport", "Nudge / Resync", s.transportHapticsEnabled, enabled = s.hapticsEnabled) { v -> scope.launch { settingsStore.setTransportHapticsEnabled(v) } } }
                }

                SettingsPage.CONNECTION -> {
    val active = s.activeTarget
    val connSubtitle = "${active.name} • ${active.ip}:${active.port}"

    val presetsSubtitle = "${s.presets.size} Presets • aktiv: ${active.name}"
    val oscInSubtitle = if (s.oscInputEnabled) "ON • Port ${s.oscInputPort}" else "OFF"
    val oscOutSubtitle = if (s.oscOutputEnabled) "ON • ${s.oscOutputThrottleMs}ms" else "OFF"
    val echoSubtitle = if (s.echoGuardEnabled) "ON • ${s.echoGuardWindowMs}ms" else "OFF"

    item { SettingsTitleRow("Verbindungen") }

    item { SettingsNavChip(Icons.Filled.Star, "Aktives Preset", connSubtitle, subtitleMono = true) { push(SettingsPage.ACTIVE_PRESET) } }
    item { SettingsNavChip(Icons.AutoMirrored.Filled.Send, "Presets", presetsSubtitle, subtitleMono = true) { push(SettingsPage.TARGETS) } }
    item { SettingsNavChip(Icons.AutoMirrored.Filled.CallReceived, "OSC Input", oscInSubtitle, subtitleMono = true) { push(SettingsPage.OSC_INPUT) } }
    item { SettingsNavChip(Icons.AutoMirrored.Filled.CallMade, "OSC Output", oscOutSubtitle) { push(SettingsPage.OSC_OUTPUT) } }
    item { SettingsNavChip(Icons.Filled.SyncAlt, "Echo Guard", echoSubtitle) { push(SettingsPage.ECHO_GUARD) } }
}

                SettingsPage.OSC_INPUT -> {
                    item { SettingsTitleRow("OSC Input") }

                    item {
                        SettingsToggleChip(
                            title = "Input",
                            subtitle = "UDP listen",
                            checked = s.oscInputEnabled
                        ) { v -> scope.launch { settingsStore.setOscInputEnabled(v) } }
                    }

                    item {
                        val shape = RoundedCornerShape(26.dp)
                        var portStr by remember(s.oscInputPort) { mutableStateOf(s.oscInputPort.toString()) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(GoblinCard)
                                .border(1.dp, GoblinBorder, shape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Listen Port", color = GoblinDim, fontSize = 11.sp)

                            TextFieldItem(
                                value = portStr,
                                onValue = { portStr = it.filter { c -> c.isDigit() }.take(5) },
                                placeholder = "7000",
                                keyboardType = KeyboardType.Number
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val p = portStr.toIntOrNull()?.coerceIn(1, 65535) ?: s.oscInputPort
                                        scope.launch { settingsStore.setOscInputPort(p) }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = GoblinBorder,
                                        contentColor = GoblinText
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    enabled = s.oscInputEnabled
                                ) { Text("Apply") }

                                Button(
                                    onClick = { portStr = s.oscInputPort.toString() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = GoblinCard2,
                                        contentColor = GoblinText
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) { Text("Reset") }
                            }

                            Text("Hinweis: Port-Change startet den Listener neu.", color = GoblinDim, fontSize = 11.sp)
                        }
                    }

                    item { DividerLine() }

                    item { SettingsSectionHeader("Fallback") }

                    item {
                        SettingsToggleChip(
                            title = "Any-RX fallback",
                            subtitle = "any OSC = signal",
                            checked = s.oscInputAnyRxFallbackEnabled,
                            enabled = s.oscInputEnabled
                        ) { v -> scope.launch { settingsStore.setOscInputAnyRxFallbackEnabled(v) } }
                    }

                    item {
                        val opts = listOf(500L, 1000L, 1500L, 2500L, 5000L)
                        val labels = listOf("500", "1000", "1500", "2500", "5000")
                        val idx = opts.indexOfFirst { it == s.oscInputAnyRxTimeoutMs }.let { if (it < 0) 2 else it }
                        SettingsSegmentChip(
                            title = "Any-RX timeout",
                            subtitle = "${s.oscInputAnyRxTimeoutMs}ms",
                            options = labels,
                            selectedIndex = idx,
                            enabled = s.oscInputEnabled && s.oscInputAnyRxFallbackEnabled
                        ) { i -> scope.launch { settingsStore.setOscInputAnyRxTimeoutMs(opts[i]) } }
                    }

                    item {
                        val shape = RoundedCornerShape(26.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(GoblinCard)
                                .border(1.dp, GoblinBorder, shape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Last Any-RX", color = GoblinDim, fontSize = 11.sp)
                            Text(
                                anyRxAge?.let { "${it}ms" } ?: "-",
                                color = if (anyOk) GoblinOk else GoblinDim,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val anyFromSnapshot = lastAnyFrom.value
                            Text("From: " + (anyFromSnapshot ?: "-"), color = GoblinDim, fontSize = 11.sp)
                        }
                    }
                }

                SettingsPage.OSC_OUTPUT -> {
                    item { SettingsTitleRow("OSC Output") }

                    item {
                        SettingsToggleChip(
                            title = "Send",
                            subtitle = "master output",
                            checked = s.oscOutputEnabled
                        ) { v -> scope.launch { settingsStore.setOscOutputEnabled(v) } }
                    }

                    item {
                        val opts = listOf(0L, 20L, 40L, 80L, 120L, 200L, 400L, 800L)
                        val labels = opts.map { it.toString() }
                        val idx = opts.indexOfFirst { it == s.oscOutputThrottleMs }.let { if (it < 0) 0 else it }
                        SettingsSegmentChip(
                            title = "Throttle",
                            subtitle = "${s.oscOutputThrottleMs}ms",
                            options = labels,
                            selectedIndex = idx,
                            enabled = s.oscOutputEnabled
                        ) { i -> scope.launch { settingsStore.setOscOutputThrottleMs(opts[i]) } }
                    }

                    item {
                        Text(
                            "Throttle betrifft nur non-critical Messages. Transport + /tapsync/ping sind exempt.",
                            color = GoblinDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                                SettingsPage.ECHO_GUARD -> {
                    item { SettingsTitleRow("Echo Guard") }

                    // Blueprint: one-tap Resolume preset + manual controls
                    val resolumeWindow = 320L
                    val isResolumePreset = s.echoGuardEnabled &&
                            s.echoGuardWindowMs == resolumeWindow &&
                            s.echoGuardRuleTap &&
                            s.echoGuardRuleResync &&
                            s.echoGuardRuleMultDiv &&
                            s.echoGuardRuleNudge

                    item {
                        SettingsButtonChip(
                            icon = Icons.Filled.SyncAlt,
                            title = "Resolume Preset",
                            subtitle = if (isResolumePreset) "Applied • ${resolumeWindow}ms" else "Apply known-good settings",
                            enabled = true
                        ) {
                            scope.launch {
                                settingsStore.setEchoGuardEnabled(true)
                                settingsStore.setEchoGuardWindowMs(resolumeWindow)
                                settingsStore.setEchoGuardRuleTap(true)
                                settingsStore.setEchoGuardRuleMultDiv(true)
                                settingsStore.setEchoGuardRuleResync(true)
                                settingsStore.setEchoGuardRuleNudge(true)
                            }
                        }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Enabled",
                            subtitle = "suppress watch→host→watch",
                            checked = s.echoGuardEnabled
                        ) { v -> scope.launch { settingsStore.setEchoGuardEnabled(v) } }
                    }

                    item {
                        SettingsMsSliderChip(
                            title = "Window",
                            valueMs = s.echoGuardWindowMs,
                            minMs = 0L,
                            maxMs = 750L,
                            stepMs = 10L,
                            enabled = s.echoGuardEnabled
                        ) { ms -> scope.launch { settingsStore.setEchoGuardWindowMs(ms) } }
                    }

                    item { SettingsSectionHeader("Rules") }

                    item {
                        SettingsToggleChip(
                            title = "Tap",
                            subtitle = "suppress echo tap",
                            checked = s.echoGuardRuleTap,
                            enabled = s.echoGuardEnabled
                        ) { v -> scope.launch { settingsStore.setEchoGuardRuleTap(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Resync",
                            subtitle = "suppress echo resync",
                            checked = s.echoGuardRuleResync,
                            enabled = s.echoGuardEnabled
                        ) { v -> scope.launch { settingsStore.setEchoGuardRuleResync(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Multiply/Divide",
                            subtitle = "suppress echo mult/div",
                            checked = s.echoGuardRuleMultDiv,
                            enabled = s.echoGuardEnabled
                        ) { v -> scope.launch { settingsStore.setEchoGuardRuleMultDiv(v) } }
                    }

                    item {
                        SettingsToggleChip(
                            title = "Nudge",
                            subtitle = "suppress echo push/pull",
                            checked = s.echoGuardRuleNudge,
                            enabled = s.echoGuardEnabled
                        ) { v -> scope.launch { settingsStore.setEchoGuardRuleNudge(v) } }
                    }

                    item {
                        Text(
                            "Tipp: Window ~250–400ms fühlt sich live meist am besten an.",
                            color = GoblinDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

SettingsPage.DEBUG_TOOLS -> {
    item { SettingsTitleRow("Debug") }

    item {
        SettingsToggleNavChip(
            title = "Preflight",
            subtitle = if (s.showPreflight) "ON" else "OFF",
            checked = s.showPreflight,
            openEnabled = s.showPreflight,
            onToggle = { v -> scope.launch { settingsStore.setShowPreflight(v) } },
            onOpen = { push(SettingsPage.PREFLIGHT) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Timeline",
            subtitle = if (s.showTimeline) "ON" else "OFF",
            checked = s.showTimeline,
            openEnabled = s.showTimeline,
            onToggle = { v -> scope.launch { settingsStore.setShowTimeline(v) } },
            onOpen = { push(SettingsPage.TIMELINE) }
        )
    }

    item {
        SettingsToggleNavChip(
            title = "Heartbeat",
            subtitle = linkLabel,
            checked = s.heartbeatEnabled,
            openEnabled = s.heartbeatEnabled,
            onToggle = { v -> scope.launch { settingsStore.setHeartbeatEnabled(v) } },
            onOpen = { push(SettingsPage.NETWORK) }
        )
    }

    item {
        SettingsToggleChip(
            title = "OSC Monitor",
            subtitle = "Input/Output Overlay",
            checked = s.showOscDebug
        ) { v -> scope.launch { settingsStore.setShowOscDebug(v) } }
    }

    item {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Diese Module erscheinen im Debug-Screen (nicht im Tap-Screen).",
            color = GoblinDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

                // --- Blueprint v2 detail pages ---

                SettingsPage.REMOTE_GHOST_DETAILS -> {
                    val enabled = s.animationsEnabled && s.remoteAnimationsEnabled && s.remoteGhostModeEnabled

                    item { SettingsTitleRow("Remote Ghost") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.remoteGhostOpacity * 100f).roundToLong()}%",
                            value = s.remoteGhostOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRemoteGhostOpacity(v) } }
                    }

                    if (!enabled) {
                        item {
                            Text(
                                "Aktiviere Animationen → Remote Visuals → Remote Ghost, um das hier zu ändern.",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                SettingsPage.GOBLIN_FLASH_DETAILS -> {
                    val enabled = s.animationsEnabled && s.goblinFlashEnabled

                    item { SettingsTitleRow("Goblin Flash") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.goblinFlashOpacity * 100f).roundToLong()}%",
                            value = s.goblinFlashOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setGoblinFlashOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Fade (ms)",
                            subtitle = "${s.goblinFlashFadeMs}ms",
                            value = s.goblinFlashFadeMs.toFloat(),
                            min = 50f,
                            max = 2000f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setGoblinFlashFadeMs(v.roundToLong()) } }
                    }

                    if (!enabled) {
                        item {
                            Text(
                                "Goblin Flash ist aus (oder Animationen aus).",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                SettingsPage.RIPPLES_DETAILS -> {
                    val enabled = s.animationsEnabled
                    val rippleMaster = enabled && s.rippleEnabled

                    item { SettingsTitleRow("Ripples") }

                    if (!enabled) {
                        item {
                            Text(
                                "Animationen sind aus — Ripples sind deaktiviert.",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    item {
                        SettingsToggleNavChip(
                            title = "Tap",
                            subtitle = if (s.rippleTapEnabled) "ON" else "OFF",
                            checked = s.rippleTapEnabled,
                            enabled = rippleMaster,
                            openEnabled = rippleMaster && s.rippleTapEnabled,
                            onToggle = { v -> scope.launch { settingsStore.setRippleTapEnabled(v) } },
                            onOpen = { push(SettingsPage.RIPPLE_TAP_DETAILS) }
                        )
                    }

                    item {
                        SettingsToggleNavChip(
                            title = "Multiply/Divide",
                            subtitle = if (s.rippleMultDivEnabled) "ON" else "OFF",
                            checked = s.rippleMultDivEnabled,
                            enabled = rippleMaster,
                            openEnabled = rippleMaster && s.rippleMultDivEnabled,
                            onToggle = { v -> scope.launch { settingsStore.setRippleMultDivEnabled(v) } },
                            onOpen = { push(SettingsPage.RIPPLE_MULTDIV_DETAILS) }
                        )
                    }

                    item {
                        SettingsToggleNavChip(
                            title = "Resync",
                            subtitle = if (s.rippleResyncEnabled) "ON" else "OFF",
                            checked = s.rippleResyncEnabled,
                            enabled = rippleMaster,
                            openEnabled = rippleMaster && s.rippleResyncEnabled,
                            onToggle = { v -> scope.launch { settingsStore.setRippleResyncEnabled(v) } },
                            onOpen = { push(SettingsPage.RIPPLE_RESYNC_DETAILS) }
                        )
                    }

                    item {
                        SettingsToggleNavChip(
                            title = "Nudge",
                            subtitle = if (s.rippleNudgeEnabled) "ON" else "OFF",
                            checked = s.rippleNudgeEnabled,
                            enabled = rippleMaster,
                            openEnabled = rippleMaster && s.rippleNudgeEnabled,
                            onToggle = { v -> scope.launch { settingsStore.setRippleNudgeEnabled(v) } },
                            onOpen = { push(SettingsPage.RIPPLE_NUDGE_DETAILS) }
                        )
                    }

                    item {
                        SettingsToggleNavChip(
                            title = "Ghost Echo",
                            subtitle = if (s.ghostEchoEnabled) "ON" else "OFF",
                            checked = s.ghostEchoEnabled,
                            enabled = rippleMaster,
                            openEnabled = rippleMaster && s.ghostEchoEnabled,
                            onToggle = { v -> scope.launch { settingsStore.setGhostEchoEnabled(v) } },
                            onOpen = { push(SettingsPage.RIPPLE_GHOST_DETAILS) }
                        )
                    }

                    if (!rippleMaster) {
                        item {
                            Text(
                                "Aktiviere Ripples (Master) in Events, um die Details zu bearbeiten.",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                SettingsPage.RIPPLE_TAP_DETAILS -> {
                    val enabled = s.animationsEnabled && s.rippleEnabled && s.rippleTapEnabled

                    item { SettingsTitleRow("Tap Ripple") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.rippleTapOpacity * 100f).roundToLong()}%",
                            value = s.rippleTapOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleTapOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.rippleTapThicknessDp)}dp",
                            value = s.rippleTapThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleTapThicknessDp(v) } }
                    }
                }

                SettingsPage.RIPPLE_MULTDIV_DETAILS -> {
                    val enabled = s.animationsEnabled && s.rippleEnabled && s.rippleMultDivEnabled

                    item { SettingsTitleRow("Mult/Div Ripple") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.rippleMultDivOpacity * 100f).roundToLong()}%",
                            value = s.rippleMultDivOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleMultDivOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.rippleMultDivThicknessDp)}dp",
                            value = s.rippleMultDivThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleMultDivThicknessDp(v) } }
                    }
                }

                SettingsPage.RIPPLE_RESYNC_DETAILS -> {
                    val enabled = s.animationsEnabled && s.rippleEnabled && s.rippleResyncEnabled

                    item { SettingsTitleRow("Resync Ripple") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.rippleResyncOpacity * 100f).roundToLong()}%",
                            value = s.rippleResyncOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleResyncOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.rippleResyncThicknessDp)}dp",
                            value = s.rippleResyncThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleResyncThicknessDp(v) } }
                    }
                }

                SettingsPage.RIPPLE_NUDGE_DETAILS -> {
                    val enabled = s.animationsEnabled && s.rippleEnabled && s.rippleNudgeEnabled

                    item { SettingsTitleRow("Nudge Ripple") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.rippleNudgeOpacity * 100f).roundToLong()}%",
                            value = s.rippleNudgeOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleNudgeOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.rippleNudgeThicknessDp)}dp",
                            value = s.rippleNudgeThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setRippleNudgeThicknessDp(v) } }
                    }
                }

                SettingsPage.RIPPLE_GHOST_DETAILS -> {
                    val enabled = s.animationsEnabled && s.rippleEnabled && s.ghostEchoEnabled

                    item { SettingsTitleRow("Ghost Echo") }

                    item {
                        SettingsSliderChip(
                            title = "Ghost Opacity",
                            subtitle = "${(s.ghostEchoOpacity * 100f).roundToLong()}%",
                            value = s.ghostEchoOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setGhostEchoOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Echo Strength",
                            subtitle = "${(s.ghostEchoStrength * 100f).roundToLong()}%",
                            value = s.ghostEchoStrength,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setGhostEchoStrength(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.ghostEchoThicknessDp)}dp",
                            value = s.ghostEchoThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setGhostEchoThicknessDp(v) } }
                    }

                    if (!enabled) {
                        item {
                            Text(
                                "Ghost Echo ist aus (oder Ripples/Animationen aus).",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                SettingsPage.PHASE_RING_DETAILS -> {
                    val enabled = s.animationsEnabled && s.phaseVisualizerEnabled

                    item { SettingsTitleRow("Phase Ring") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.phaseRingOpacity * 100f).roundToLong()}%",
                            value = s.phaseRingOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setPhaseRingOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.phaseRingThicknessDp)}dp",
                            value = s.phaseRingThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setPhaseRingThicknessDp(v) } }
                    }
                }

                SettingsPage.PHASE_SPIRAL_DETAILS -> {
                    val enabled = s.animationsEnabled && s.phaseSpiralEnabled

                    item { SettingsTitleRow("Phase Spiral") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.phaseSpiralOpacity * 100f).roundToLong()}%",
                            value = s.phaseSpiralOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setPhaseSpiralOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.phaseSpiralThicknessDp)}dp",
                            value = s.phaseSpiralThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setPhaseSpiralThicknessDp(v) } }
                    }
                }

                SettingsPage.PHASE_AURA_DETAILS -> {
                    val enabled = s.animationsEnabled && s.phaseAuraEnabled

                    item { SettingsTitleRow("Phase Aura") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.phaseAuraOpacity * 100f).roundToLong()}%",
                            value = s.phaseAuraOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setPhaseAuraOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.phaseAuraThicknessDp)}dp",
                            value = s.phaseAuraThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setPhaseAuraThicknessDp(v) } }
                    }
                }

                SettingsPage.PHASE_PARTICLES_DETAILS -> {
                    val enabled = s.animationsEnabled && s.microParticlesEnabled

                    item { SettingsTitleRow("Micro Particles") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.microParticlesOpacity * 100f).roundToLong()}%",
                            value = s.microParticlesOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setMicroParticlesOpacity(v) } }
                    }
                }

                SettingsPage.OSC_PULSE_DETAILS -> {
                    val enabled = s.animationsEnabled && s.oscPulseEnabled

                    item { SettingsTitleRow("OSC Pulse") }

                    item {
                        SettingsSliderChip(
                            title = "Opacity",
                            subtitle = "${(s.oscPulseOpacity * 100f).roundToLong()}%",
                            value = s.oscPulseOpacity,
                            min = 0f,
                            max = 1f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setOscPulseOpacity(v) } }
                    }

                    item {
                        SettingsSliderChip(
                            title = "Thickness",
                            subtitle = "${String.format("%.1f", s.oscPulseThicknessDp)}dp",
                            value = s.oscPulseThicknessDp,
                            min = 0.5f,
                            max = 6.0f,
                            enabled = enabled
                        ) { v -> scope.launch { settingsStore.setOscPulseThicknessDp(v) } }
                    }

                    if (!enabled) {
                        item {
                            Text(
                                "OSC Pulse ist aus (oder Animationen aus).",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }


                SettingsPage.ABOUT -> {
                    item { SettingsTitleRow("Über") }

                    item {
                        val shape = RoundedCornerShape(26.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(GoblinCard)
                                .border(1.dp, GoblinBorder, shape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("TapSyncWatch", color = GoblinText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = GoblinDim, fontSize = 12.sp)
                            Text("Build: ${BuildConfig.BUILD_TYPE}", color = GoblinDim, fontSize = 12.sp)
                        }
                    }

                    item {
                        Text(
                            "OSC Tempo/Transport sync für Live-Visuals.\n" +
                                "Projekt: Uhr APP – TapSyncWatch",
                            color = GoblinDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                SettingsPage.NETWORK -> {
                    item { SettingsTitleRow("Heartbeat") }

                    item { SettingsToggleChip("Link Check", "/tapsync/ping → /tapsync/pong", s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatEnabled(v) } } }
                    item { SettingsToggleChip("Nur Vordergrund", "weniger OSC + Akku", s.heartbeatForegroundOnly, enabled = s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatForegroundOnly(v) } } }
                    item { SettingsToggleChip("Adaptive recovery", "ping schneller wenn down", s.heartbeatAdaptiveEnabled, enabled = s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatAdaptiveEnabled(v) } } }

                    item {
                        val idx = if (s.heartbeatSendTo == HeartbeatSendTo.ALL) 1 else 0
                        SettingsSegmentChip(
                            title = "Send To",
                            subtitle = if (s.heartbeatSendTo == HeartbeatSendTo.ALL) "All presets" else "Active preset",
                            options = listOf("Active", "All"),
                            selectedIndex = idx,
                            enabled = s.heartbeatEnabled
                        ) { i ->
                            val v = if (i == 1) HeartbeatSendTo.ALL else HeartbeatSendTo.ACTIVE
                            scope.launch { settingsStore.setHeartbeatSendTo(v) }
                        }
                    }

                    item {
                        val shape = RoundedCornerShape(26.dp)
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
                            val pongFromSnapshot = lastPongFrom.value
            Text("Last pong from: " + (pongFromSnapshot ?: "-"), color = GoblinDim, fontSize = 11.sp)
                            Text("Grace: ${s.signalGraceMs}ms", color = GoblinDim, fontSize = 11.sp)
                            if (s.oscInputAnyRxFallbackEnabled) {
                                Text("Last any: " + (anyRxAge?.let { "${it}ms" } ?: "-"), color = GoblinDim, fontSize = 11.sp)
                                Text("Last any from: " + (lastAnyFrom.value ?: "-"), color = GoblinDim, fontSize = 11.sp)
                            }
                        }
                    }
                }

                SettingsPage.NETWORK_DEBUG -> {
                    item { SettingsTitleRow("Debug (Deprecated)") }

                    item {
                        Text(
                            "Moved: Root → Debug. Diese Seite bleibt nur für alte Saves da.",
                            color = GoblinDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.BugReport,
                            title = "Open Debug",
                            subtitle = "Preflight • Timeline • Heartbeat • OSC Monitor"
                        ) { push(SettingsPage.DEBUG_TOOLS) }
                    }
                }


                SettingsPage.CLOCK -> {
                    item { SettingsTitleRow("Clock / Engine") }

                    // FIRST: clock enable/disable
                    item { SettingsToggleChip("Clock Enabled", "Master switch for clock output", s.clockEnabled) { v -> scope.launch { settingsStore.setClockEnabled(v) } } }

                    item {
                        val shape = RoundedCornerShape(26.dp)
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
                                    val interaction = remember { MutableInteractionSource() }
                                    val selected = s.clockMode == mode
                                    val bg = if (selected) GoblinAccent.copy(alpha = 0.25f) else GoblinCard2
                                    val br = if (selected) GoblinAccent.copy(alpha = 0.55f) else GoblinBorder
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(bg)
                                            .border(1.dp, br, RoundedCornerShape(16.dp))
                                            .clickable(interactionSource = interaction, indication = null) { scope.launch { settingsStore.setClockMode(mode) } }
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

                
                
                SettingsPage.ACTIVE_PRESET -> {
    val active = s.activeTarget
    val presetLine = "${active.name} • ${active.ip}:${active.port}"
    val hbLine = "Heartbeat: ${if (s.heartbeatEnabled) "ON" else "OFF"} • Link: $linkLabel"
    val oscLine = "OSC In: ${if (s.oscInputEnabled) "ON" else "OFF"}:${s.oscInputPort} • OSC Out: ${if (s.oscOutputEnabled) "ON" else "OFF"}"
    val echoLine = "Echo Guard: ${if (s.echoGuardEnabled) "ON" else "OFF"} • ${s.echoGuardWindowMs}ms"

    item { SettingsTitleRow("Aktives Preset") }

    item {
        Card(
            backgroundColor = GoblinCard,
            shape = RoundedCornerShape(14.dp),
            elevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(presetLine, color = GoblinText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(hbLine, color = if (linkOk) GoblinOk else GoblinDim, fontSize = 12.sp)
                Text(oscLine, color = GoblinDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(echoLine, color = GoblinDim, fontSize = 12.sp)
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Hinweis: Link-Status basiert auf PONG (Heartbeat) oder Any-RX-Fallback.",
            color = GoblinDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}
SettingsPage.TARGETS -> {
    item { SettingsTitleRow("Presets") }

    item {
        ActivePresetBar(
            presets = s.presets,
            activeIndex = s.activePreset,
            onSelect = { i -> scope.launch { settingsStore.setActivePreset(i) } }
        )
    }

    item {
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(GoblinCard)
                .border(1.dp, GoblinBorder, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Test ALL", color = GoblinDim, fontSize = 11.sp)

            // Progress line
            val total = s.presets.size
            val done = targetsTestResults.size
            val progressText = when {
                targetsTestBusy -> {
                    val idx = targetsTestRunningIndex.coerceIn(0, (targetsTestSnapshot.size - 1).coerceAtLeast(0))
                    val name = targetsTestSnapshot.getOrNull(idx)?.name ?: "…"
                    "Running ${idx + 1}/$total • $name"
                }
                done == 0 -> "Ready"
                else -> "Done $done/$total"
            }
            Text(progressText, color = GoblinDim, fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (targetsTestBusy) return@Button
                        targetsTestResults.clear()
                        targetsTestSnapshot = s.presets.toList()
                        targetsTestRunningIndex = -1

                        // Suppress heartbeat while testing to avoid pong collisions.
                        targetsTestRestoreHeartbeat = s.heartbeatEnabled

                        targetsTestBusy = true
                        targetsTestJob?.cancel()
                        targetsTestJob = scope.launch {
                            try {
                                if (targetsTestRestoreHeartbeat) setHeartbeatSuppressed(true)

                                val snapshot = targetsTestSnapshot
                                snapshot.forEachIndexed { idx, t ->
                                    targetsTestRunningIndex = idx
                                    val res = pingPresetOnce(t)
                                    targetsTestResults[idx] = res
                                    if (idx != snapshot.lastIndex) delay(120L)
                                }
                            } finally {
                                targetsTestRunningIndex = -1
                                targetsTestBusy = false
                                targetsTestJob = null
                                if (targetsTestRestoreHeartbeat) setHeartbeatSuppressed(false)
                            }
                        }
                    },
                    enabled = !targetsTestBusy
                ) { Text(if (targetsTestBusy) "Running…" else "Test ALL") }

                OutlinedButton(
                    onClick = {
                        targetsTestJob?.cancel()
                        targetsTestJob = null
                        targetsTestBusy = false
                        targetsTestRunningIndex = -1
                        if (targetsTestRestoreHeartbeat) {
								 setHeartbeatSuppressed(false)
                        }
                    },
                    enabled = targetsTestBusy
                ) { Text("Cancel") }

                OutlinedButton(
                    onClick = {
                        targetsTestJob?.cancel()
                        targetsTestJob = null
                        targetsTestBusy = false
                        targetsTestRunningIndex = -1
                        targetsTestResults.clear()
                        if (targetsTestRestoreHeartbeat) {
								 setHeartbeatSuppressed(false)
                        }
                        targetsTestRestoreHeartbeat = false
                    }
                ) { Text("Clear") }
            }

            Text(
                "timeout 900ms • spacing 120ms",
                color = GoblinDim,
                fontSize = 11.sp
            )
        }
    }


    s.presets.forEachIndexed { i, p ->
        val label = when (i) { 0 -> "A"; 1 -> "B"; 2 -> "C"; else -> (i + 1).toString() }
        val res = targetsTestResults[i]

        val suffix = when (res?.kind) {
            null -> ""
            PresetPingKind.OK -> {
                val from = res.pongFrom?.let { " ($it)" } ?: ""
                " • OK ${res.rttMs}ms$from"
            }
            PresetPingKind.TIMEOUT -> " • TIMEOUT"
            PresetPingKind.SEND_FAIL -> " • SEND FAIL"
        }

        item {
            val isActive = (i == s.activePreset)
            val tick = if (isActive) " ✓" else ""
            SettingsNavChip(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "$label$tick • ${p.name}",
                subtitle = "${p.ip}:${p.port}$suffix",
                subtitleMono = true
            ) {
                editPresetIndex = i
                push(SettingsPage.TARGET_EDIT)
            }
        }
    }

    item {
        Spacer(Modifier.height(6.dp))
        Text(
            "Tip: Preset öffnen zum Editieren von IP/Port.",
            color = GoblinDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

                                SettingsPage.PING_TEST -> {
                    item { SettingsTitleRow("Ping Test") }
                    item {
                        Text(
                            "Moved: Presets → Test ALL",
                            color = GoblinDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        SettingsNavChip(
                            icon = Icons.AutoMirrored.Filled.Send,
                            title = "Open Presets",
                            subtitle = "Test ALL + results"
                        ) { push(SettingsPage.TARGETS) }
                    }
                }

SettingsPage.TARGET_EDIT -> {
                    val i = editPresetIndex.coerceIn(0, s.presets.lastIndex)
                    val preset = s.presets[i]
                    val letter = when (i) { 0 -> "A"; 1 -> "B"; 2 -> "C"; else -> (i + 1).toString() }

                    item { SettingsTitleRow("Preset $letter") }

                    item {
                        val shape = RoundedCornerShape(30.dp)

                        var name by remember(preset.name) { mutableStateOf(preset.name) }
                        var ip by remember(preset.ip) { mutableStateOf(preset.ip) }
                        var portStr by remember(preset.port) { mutableStateOf(preset.port.toString()) }

                        var testBusy by rememberSaveable(i) { mutableStateOf(false) }
                        var testResultOk by rememberSaveable(i) { mutableStateOf<Boolean?>(null) }
                        var testLine by rememberSaveable(i) { mutableStateOf("") }

                        LaunchedEffect(testBusy, testLine) {
                            if (!testBusy && testLine.isNotEmpty() && !testLine.contains("sending")) {
                                val snap = testLine
                                delay(2500L)
                                if (!testBusy && testLine == snap) testLine = ""
                            }
                        }

                        val host = ip.trim()
                        val parsedPort = portStr.toIntOrNull()
                        val port = (parsedPort ?: preset.port).coerceIn(1, 65535)
                        val isActive = (i == s.activePreset)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(GoblinCard)
                                .border(1.dp, GoblinBorder, shape)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Preview (fast readability on watch)
                            Text(
                                text = name.trim().ifEmpty { preset.name },
                                color = GoblinText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${host.ifEmpty { preset.ip }}:$port",
                                color = GoblinDim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Fields (compact)
                            Text("Name", color = GoblinDim, fontSize = 11.sp)
                            TextFieldItem(
                                value = name,
                                onValue = { name = it },
                                placeholder = "Preset name",
                                keyboardType = KeyboardType.Text
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(modifier = Modifier.weight(2f)) {
                                    Text("IP", color = GoblinDim, fontSize = 11.sp)
                                    TextFieldItem(
                                        value = ip,
                                        onValue = { ip = it },
                                        placeholder = "192.168.0.10",
                                        keyboardType = KeyboardType.Text
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Port", color = GoblinDim, fontSize = 11.sp)
                                    TextFieldItem(
                                        value = portStr,
                                        onValue = { portStr = it.filter { c -> c.isDigit() }.take(5) },
                                        placeholder = "7000",
                                        keyboardType = KeyboardType.Number
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                                Button(
                                    onClick = {
                                        if (testBusy) return@Button
                                        val h = host
                                        val p = portStr.toIntOrNull()
                                        if (h.isEmpty()) {
                                            testResultOk = false
                                            testLine = "Test: IP missing"
                                            return@Button
                                        }
                                        if (p == null) {
                                            testResultOk = false
                                            testLine = "Test: Port invalid"
                                            return@Button
                                        }
                                        scope.launch {
                                            val clickAt = SystemClock.elapsedRealtime()
                                            val minBusyMs = 1000L

                                            testBusy = true
                                            testResultOk = null
                                            testLine = "Test: sending…"

                                            try {
                                                val started = SystemClock.elapsedRealtime()
                                                val nonce = ((started % 1000L) + 1L).toFloat() / 1000f

                                                val sentOk = try {
                                                    sendOscPingFloat(h, p.coerceIn(1, 65535), nonce)
                                                    true
                                                } catch (_: Exception) {
                                                    false
                                                }

                                                if (!sentOk) {
                                                    testResultOk = false
                                                    testLine = "Test: bad host"
                                                    return@launch
                                                }

                                                val pongTs = withTimeoutOrNull(900L) {
                                                    lastPongMs.filter { it >= started }.first()
                                                }

                                                val ok = pongTs != null
                                                testResultOk = ok

                                                testLine = if (ok) {
                                                    val rtt = (pongTs!! - started).coerceAtLeast(0L)
                                                    var from = lastPongFrom.value
                                                    if (from == null) {
                                                        delay(10L)
                                                        from = lastPongFrom.value
                                                    }
                                                    "Test: OK • ${rtt}ms • ${from ?: "-"}"
                                                } else {
                                                    "Test: timeout"
                                                }
                                            } finally {
                                                val remaining = (clickAt + minBusyMs) - SystemClock.elapsedRealtime()
                                                if (remaining > 0L) delay(remaining)
                                                testBusy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = GoblinBorder,
                                        contentColor = GoblinText
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) { Text(if (testBusy) "…" else "Test") }

                                if (!isActive) {
                                    Button(
                                        onClick = { scope.launch { settingsStore.setActivePreset(i) }; pop() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = GoblinOk.copy(alpha = 0.25f),
                                            contentColor = GoblinText
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) { Text("Use") }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }

                            if (testLine.isNotEmpty()) {
                                Text(
                                    testLine,
                                    color = when (testResultOk) {
                                        true -> GoblinOk
                                        false -> GoblinBad
                                        null -> GoblinDim
                                    },
                                    fontSize = 12.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                                Button(
                                    onClick = { pop() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = GoblinBorder,
                                        contentColor = GoblinText
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) { Text("Cancel") }

                                Button(
                                    onClick = {
                                        val h = host
                                        val p = portStr.toIntOrNull()
                                        if (h.isEmpty()) {
                                            testResultOk = false
                                            testLine = "Save: IP missing"
                                            return@Button
                                        }
                                        if (p == null) {
                                            testResultOk = false
                                            testLine = "Save: Port invalid"
                                            return@Button
                                        }

                                        scope.launch {
                                            settingsStore.updatePreset(
                                                i,
                                                name.trim().ifEmpty { preset.name },
                                                h,
                                                p.coerceIn(1, 65535)
                                            )
                                            pop()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = GoblinAccent,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) { Text("Save", fontWeight = FontWeight.Bold) }
                            }
                        }
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
            presets.forEachIndexed { i, _ ->
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

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GoblinCard2)
            .border(1.dp, GoblinBorder, RoundedCornerShape(12.dp))
            .animateContentSize()
            .clickable(interactionSource = interaction, indication = null) { expanded = !expanded }
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
/* ================= OSC UTIL ================= */

/**
 * Minimal OSC sender used by the Target/Preset "Test" button.
 *
 * Sends /tapsync/ping as a float (0..1). Resolume (or other peer) should answer /tapsync/pong.
 */
private suspend fun sendOscPingFloat(host: String, port: Int, value: Float) {
    withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(host)
        val data = buildOscFloatMessage("/tapsync/ping", value)
        DatagramSocket().use { socket ->
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)
                            }
                        }
}

private fun buildOscFloatMessage(path: String, value: Float): ByteArray {
    val bb = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN)
    writeOscString(bb, path)
    writeOscString(bb, ",f")
    bb.putFloat(value)
    return bb.array().copyOf(bb.position())
}

private fun writeOscString(bb: ByteBuffer, s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)
    bb.put(bytes)
    bb.put(0)
    while (bb.position() % 4 != 0) bb.put(0)
}