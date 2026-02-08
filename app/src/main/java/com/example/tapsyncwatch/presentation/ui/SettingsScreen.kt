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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

private enum class SettingsPage {
    ROOT,
    DISPLAY,
    STATUSBAR,
    PREFLIGHT,
    PREFLIGHT_TUNING,
    TIMELINE,
    VISUALS,
    VISUALS_VISIBILITY,
    VISUALS_INSTRUMENT,
    MOODS,
    AURA_PARTICLES,
    SWING,
    GHOST_ECHO,
    MOTION,
    MOTION_REMOTE,
    MOTION_FX,
    HAPTICS,
    NETWORK,
    NETWORK_DEBUG,
    CLOCK,
    TARGETS,
    TARGET_EDIT
}

@Composable
private fun SettingsTitleRow(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        color = GoblinCard,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, GoblinBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = GoblinDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = GoblinText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                        enabled = enabled,
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
    onClose: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
    val s = settings
    val lastPong by lastPongMs.collectAsState()
    var nowMs by remember { mutableStateOf(0L) }

    val scope = rememberCoroutineScope()
    val pageStack = rememberSaveable(saver = listSaver(
    save = { it.map { p -> p.name } },
    restore = { names -> names.map { SettingsPage.valueOf(it) }.toMutableStateList() }
)) { mutableStateListOf(SettingsPage.ROOT) }
val page = pageStack.last()
fun push(p: SettingsPage) { pageStack.add(p) }
fun pop() { if (pageStack.size > 1) pageStack.removeAt(pageStack.lastIndex) }

    // Only tick when we actually show time-based network info (pong age).
    LaunchedEffect(page) {
        if (page != SettingsPage.NETWORK) return@LaunchedEffect
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

var presetsSession by remember { mutableStateOf(0) }
    var editPresetIndex by rememberSaveable { mutableStateOf(0) }

    // Hardware back on some watches may fire twice (DOWN/UP or duplicated keycodes).
    // We must prevent a "submenu -> ROOT -> close" within the same physical press.
    var backBlockUntilMs by rememberSaveable { mutableStateOf(0L) }
    var lastBackHandledMs by rememberSaveable { mutableStateOf(0L) }


    
BackHandler {
    val now = SystemClock.elapsedRealtime()

    // Debounce ultra-fast duplicates (some devices deliver two back callbacks per physical press)
    if (now - lastBackHandledMs < 140L) return@BackHandler
    lastBackHandledMs = now

    if (pageStack.size > 1) {
        pop()
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

    // Edge-swipe back (like watch settings): swipe from left edge to go back one level.
    val density = LocalDensity.current
    val edgePx = with(density) { 22.dp.toPx() }
    val triggerPx = with(density) { 42.dp.toPx() }

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
                            // Mirror BackHandler behavior: one level back, then debounce close.
                            val now = SystemClock.elapsedRealtime()
                            if (pageStack.size > 1) {
                                pop()
                                backBlockUntilMs = now + 420L
                            } else {
                                if (now >= backBlockUntilMs) onClose()
                            }
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
                    item { SettingsTitleRow("Settings", showBack = false, onBack = {}) }

                    item { SettingsNavChip(Icons.Filled.Visibility, "Anzeige", "Status, OSC Dot, Monitor") { push(SettingsPage.DISPLAY) } }
                    item { SettingsNavChip(Icons.Filled.Palette, "Visuals", "Phase Ring / Spiral") { push(SettingsPage.VISUALS) } }
                    item { SettingsNavChip(Icons.Filled.Animation, "Animation", "Remote Ghost, Ripples, Pulse") { push(SettingsPage.MOTION) } }
                    item { SettingsNavChip(Icons.Filled.Vibration, "Haptik", "Tap / Downbeat / Transport") { push(SettingsPage.HAPTICS) } }
                    item { SettingsNavChip(Icons.Filled.Wifi, "Netzwerk", "Ping/Pong • $linkLabel") { push(SettingsPage.NETWORK) } }
                    item { SettingsNavChip(Icons.Filled.Schedule, "Clock / Engine", "Clock enabled + mode") { push(SettingsPage.CLOCK) } }
                    item {
                        SettingsNavChip(
                            Icons.Filled.Send,
                            "OSC Targets",
                            "${s.activeTarget.ip}:${s.activeTarget.port}"
                        ) {
                            presetsSession += 1
                            push(SettingsPage.TARGETS)
                        }
                    }
                }

                
                SettingsPage.DISPLAY -> {
    item { SettingsTitleRow("Anzeige", showBack = true) { pop() } }

    item {
        SettingsToggleNavChip(
            title = "Statusbar",
            subtitle = "Preset • IP:Port • Link",
            checked = s.showStatusLine,
            onToggle = { v -> scope.launch { settingsStore.setShowStatusLine(v) } },
            onOpen = { push(SettingsPage.STATUSBAR) }
        )
    }

    // (Optional) Add further display-related root toggles here.
}

                SettingsPage.STATUSBAR -> {
    item { SettingsTitleRow("Statusbar", showBack = true) { pop() } }

    item {
        SettingsToggleChip(
            title = "Statusbar",
            subtitle = "Master toggle",
            checked = s.showStatusLine
        ) { v -> scope.launch { settingsStore.setShowStatusLine(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Opacity",
            subtitle = "Pill transparency",
            value = s.statusLineAlpha,
            min = 0.15f,
            max = 1.0f,
            enabled = s.showStatusLine
        ) { v -> scope.launch { settingsStore.setStatusLineAlpha(v) } }
    }

    item {
        SettingsToggleChip(
            title = "Auto-Dim Warn",
            subtitle = "Warny when IN bad",
            checked = s.statusbarAutoDimWarn,
            enabled = s.showStatusLine
        ) { v -> scope.launch { settingsStore.setStatusbarAutoDimWarn(v) } }
    }

    item {
        SettingsNavChip(
            icon = Icons.Filled.FactCheck,
            title = "Preflight",
            subtitle = "P / IN / OUT"
        ) { push(SettingsPage.PREFLIGHT) }
    }

    item {
        SettingsNavChip(
            icon = Icons.Filled.Schedule,
            title = "Timeline",
            subtitle = "Letzte Events"
        ) { push(SettingsPage.TIMELINE) }
    }
}

                SettingsPage.PREFLIGHT -> {
    item { SettingsTitleRow("Preflight", showBack = true) { pop() } }

    item {
        SettingsToggleChip(
            title = "Preflight",
            subtitle = "P / IN / OUT",
            checked = s.showPreflight,
            enabled = s.showStatusLine
        ) { v -> scope.launch { settingsStore.setShowPreflight(v) } }
    }

    item {
        val modeOptions = listOf(
            com.example.tapsyncwatch.presentation.data.PreflightMode.FULL,
            com.example.tapsyncwatch.presentation.data.PreflightMode.MINIMAL
        )
        val modeIdx = if (s.preflightMode == com.example.tapsyncwatch.presentation.data.PreflightMode.MINIMAL) 1 else 0
        SettingsSegmentChip(
            title = "Mode",
            subtitle = "FULL = labels, MIN = dots",
            options = listOf("FULL", "MIN"),
            selectedIndex = modeIdx,
            enabled = s.showStatusLine && s.showPreflight
        ) { i -> scope.launch { settingsStore.setPreflightMode(modeOptions[i]) } }
    }

    item {
        SettingsSliderChip(
            title = "Opacity",
            subtitle = "Lamp row transparency",
            value = s.preflightAlpha,
            min = 0.15f,
            max = 1.0f,
            enabled = s.showStatusLine && s.showPreflight
        ) { v -> scope.launch { settingsStore.setPreflightAlpha(v) } }
    }

    item {
        SettingsNavChip(
            icon = Icons.Filled.Tune,
            title = "Tuning",
            subtitle = "Zeitfenster / Thresholds"
        ) { push(SettingsPage.PREFLIGHT_TUNING) }
    }
}


                SettingsPage.PREFLIGHT_TUNING -> {
                    item { SettingsTitleRow("Tuning", showBack = true) { pop() } }

                    item {
                        val phaseOpts = listOf(800L, 1500L, 3000L)
                        val phaseIdx = phaseOpts.indexOfFirst { it == s.preflightPhaseOkMs }.let { if (it >= 0) it else 1 }
                        SettingsSegmentChip(
                            title = "IN Phase OK",
                            subtitle = "grün wenn ≤ ${s.preflightPhaseOkMs}ms",
                            options = listOf("0.8s", "1.5s", "3s"),
                            selectedIndex = phaseIdx,
                            enabled = s.showStatusLine && s.showPreflight
                        ) { i -> scope.launch { settingsStore.setPreflightPhaseOkMs(phaseOpts[i]) } }
                    }

                    item {
                        val downbeatOpts = listOf(3000L, 6000L, 10000L)
                        val downbeatIdx = downbeatOpts.indexOfFirst { it == s.preflightDownbeatOkMs }.let { if (it >= 0) it else 1 }
                        SettingsSegmentChip(
                            title = "IN Downbeat OK",
                            subtitle = "grün wenn ≤ ${s.preflightDownbeatOkMs}ms",
                            options = listOf("3s", "6s", "10s"),
                            selectedIndex = downbeatIdx,
                            enabled = s.showStatusLine && s.showPreflight
                        ) { i -> scope.launch { settingsStore.setPreflightDownbeatOkMs(downbeatOpts[i]) } }
                    }

                    item {
                        val outOpts = listOf(2000L, 6000L, 10000L)
                        val outIdx = outOpts.indexOfFirst { it == s.preflightOutOkMs }.let { if (it >= 0) it else 1 }
                        SettingsSegmentChip(
                            title = "OUT OK",
                            subtitle = "grün wenn ≤ ${s.preflightOutOkMs}ms",
                            options = listOf("2s", "6s", "10s"),
                            selectedIndex = outIdx,
                            enabled = s.showStatusLine && s.showPreflight
                        ) { i -> scope.launch { settingsStore.setPreflightOutOkMs(outOpts[i]) } }
                    }
                }


                SettingsPage.TIMELINE -> {
    item { SettingsTitleRow("Timeline", showBack = true) { pop() } }

    item {
        SettingsToggleChip(
            title = "Timeline",
            subtitle = "Letzte Events",
            checked = s.showTimeline,
            enabled = s.showStatusLine
        ) { v -> scope.launch { settingsStore.setShowTimeline(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Opacity",
            subtitle = "Lane transparency",
            value = s.timelineAlpha,
            min = 0.15f,
            max = 1.0f,
            enabled = s.showStatusLine && s.showTimeline
        ) { v -> scope.launch { settingsStore.setTimelineAlpha(v) } }
    }

    item { DividerLine() }

    item {
        SettingsToggleChip(
            title = "Local",
            subtitle = "Show local events",
            checked = s.timelineShowLocal,
            enabled = s.showStatusLine && s.showTimeline
        ) { v -> scope.launch { settingsStore.setTimelineShowLocal(v) } }
    }

    item {
        SettingsToggleChip(
            title = "Remote",
            subtitle = "Show remote events",
            checked = s.timelineShowRemote,
            enabled = s.showStatusLine && s.showTimeline
        ) { v -> scope.launch { settingsStore.setTimelineShowRemote(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Remote Opacity",
            subtitle = "Remote lane alpha",
            value = s.timelineRemoteAlpha,
            min = 0.15f,
            max = 1.0f,
            enabled = s.showStatusLine && s.showTimeline && s.timelineShowRemote
        ) { v -> scope.launch { settingsStore.setTimelineRemoteAlpha(v) } }
    }

    item {
        SettingsToggleChip(
            title = "Health",
            subtitle = "Errors + Pong",
            checked = s.timelineShowHealth,
            enabled = s.showStatusLine && s.showTimeline
        ) { v -> scope.launch { settingsStore.setTimelineShowHealth(v) } }
    }

    item {
        SettingsToggleChip(
            title = "Important only",
            subtitle = "Tap/Resync/Nudge + Errors + Pong",
            checked = s.timelineImportantOnly,
            enabled = s.showStatusLine && s.showTimeline
        ) { v -> scope.launch { settingsStore.setTimelineImportantOnly(v) } }
    }

    item {
        SettingsSliderChip(
            title = "Remote Debounce",
            subtitle = "${s.remoteEventMinIntervalMs}ms min interval",
            value = s.remoteEventMinIntervalMs.toFloat(),
            min = 0f,
            max = 250f,
            enabled = s.showStatusLine && s.showTimeline && s.timelineShowRemote
        ) { v -> scope.launch { settingsStore.setRemoteEventMinIntervalMs(v.toLong()) } }
    }
}

                SettingsPage.VISUALS -> {
                    item { SettingsTitleRow("Visuals", showBack = true) { pop() } }

                    item { SettingsToggleChip("Phase Ring", "Downbeat + Phase", s.phaseVisualizerEnabled) { v -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } } }
                    item { SettingsToggleChip("Phase Spiral", "Stability visual", s.phaseSpiralEnabled) { v -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } } }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Instrument",
                            subtitle = "Moods / Aura / Swing / Echo"
                        ) { push(SettingsPage.VISUALS_INSTRUMENT) }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.Visibility,
                            title = "Visibility",
                            subtitle = "Alpha sliders"
                        ) { push(SettingsPage.VISUALS_VISIBILITY) }
                    }
                }

                

                SettingsPage.VISUALS_VISIBILITY -> {
                    item { SettingsTitleRow("Visibility", showBack = true) { pop() } }

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



                SettingsPage.VISUALS_INSTRUMENT -> {
                    item { SettingsTitleRow("Instrument", showBack = true) { pop() } }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.Star,
                            title = "Goblin Moods",
                            subtitle = "online grin / offline grumble"
                        ) { push(SettingsPage.MOODS) }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Aura + Particles",
                            subtitle = "only when stable"
                        ) { push(SettingsPage.AURA_PARTICLES) }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.Schedule,
                            title = "Swing",
                            subtitle = "visual groove (no tempo change)"
                        ) { push(SettingsPage.SWING) }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.Visibility,
                            title = "Ghost Echo",
                            subtitle = "afterglow trail"
                        ) { push(SettingsPage.GHOST_ECHO) }
                    }
                }

                SettingsPage.MOODS -> {
                    item { SettingsTitleRow("Goblin Moods", showBack = true) { pop() } }

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
                    item { SettingsTitleRow("Aura + Particles", showBack = true) { pop() } }

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
                    item { SettingsTitleRow("Swing", showBack = true) { pop() } }

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
                    item { SettingsTitleRow("Ghost Echo", showBack = true) { pop() } }

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
                    item { SettingsTitleRow("Animation", showBack = true) { pop() } }

                    item {
                        SettingsToggleChip(
                            title = "Animationen",
                            subtitle = "Master switch",
                            checked = s.animationsEnabled
                        ) { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } }
                    }

                    item {
                        SettingsNavChip(
                            icon = Icons.Filled.Input,
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
                    item { SettingsTitleRow("Remote", showBack = true) { pop() } }

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
                    item { SettingsTitleRow("FX", showBack = true) { pop() } }

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
                    item { SettingsTitleRow("Haptik", showBack = true) { pop() } }

                    item { SettingsToggleChip("Haptik", "Master switch", s.hapticsEnabled) { v -> scope.launch { settingsStore.setHapticsEnabled(v) } } }
                    item { SettingsToggleChip("Downbeat", "Kick auf 1", s.downbeatHapticsEnabled, enabled = s.hapticsEnabled) { v -> scope.launch { settingsStore.setDownbeatHapticsEnabled(v) } } }
                    item { SettingsToggleChip("Transport", "Nudge / Resync", s.transportHapticsEnabled, enabled = s.hapticsEnabled) { v -> scope.launch { settingsStore.setTransportHapticsEnabled(v) } } }
                }

                SettingsPage.NETWORK -> {
                    item { SettingsTitleRow("Netzwerk", showBack = true) { pop() } }

                    item { SettingsToggleChip("Link Check", "/tapsync/ping → /tapsync/pong", s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatEnabled(v) } } }
                    item { SettingsToggleChip("Nur Vordergrund", "weniger OSC + Akku", s.heartbeatForegroundOnly, enabled = s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatForegroundOnly(v) } } }
                    item { SettingsToggleChip("Adaptive recovery", "ping schneller wenn down", s.heartbeatAdaptiveEnabled, enabled = s.heartbeatEnabled) { v -> scope.launch { settingsStore.setHeartbeatAdaptiveEnabled(v) } } }

                    item { SettingsNavChip(Icons.Filled.BugReport, "Debug", "OSC, Timeline, Preflight, FX") { push(SettingsPage.NETWORK_DEBUG) } }

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
                            Text("Grace: ${s.signalGraceMs}ms", color = GoblinDim, fontSize = 11.sp)
                        }
                    }
                }


                SettingsPage.NETWORK_DEBUG -> {
                    item { SettingsTitleRow("Debug", showBack = true) { pop() } }

                    item { SettingsSectionHeader("OSC") }
                    item { SettingsToggleChip("OSC Dot", "kleiner Aktivitäts-Punkt", s.showOscDot) { v -> scope.launch { settingsStore.setShowOscDot(v) } } }
                    item { SettingsToggleChip("OSC Pulse", "kurzer Pulse bei OSC", s.oscPulseEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setOscPulseEnabled(v) } } }
                    item { SettingsToggleChip("OSC Monitor", "Input/Output Debug Overlay", s.showOscDebug) { v -> scope.launch { settingsStore.setShowOscDebug(v) } } }

                    item { SettingsSectionHeader("HUD") }
                    item { SettingsToggleChip("Statusbar", "Status oben", s.showStatusLine) { v -> scope.launch { settingsStore.setShowStatusLine(v) } } }
                    item { SettingsSliderChip("Statusbar Opacity", "${(s.statusLineAlpha * 100f).roundToLong()}%", s.statusLineAlpha, 0f, 1f, enabled = s.showStatusLine) { v -> scope.launch { settingsStore.setStatusLineAlpha(v) } } }

                    item { SettingsToggleChip("Preflight", "Health dots", s.showPreflight, enabled = s.showStatusLine) { v -> scope.launch { settingsStore.setShowPreflight(v) } } }
                    item { SettingsNavChip(Icons.Filled.Tune, "Preflight Tuning", "Thresholds & Mode") { push(SettingsPage.PREFLIGHT_TUNING) } }

                    item { SettingsToggleChip("Timeline", "Events overlay", s.showTimeline, enabled = s.showStatusLine) { v -> scope.launch { settingsStore.setShowTimeline(v) } } }
                    item { SettingsSliderChip("Timeline Opacity", "${(s.timelineAlpha * 100f).roundToLong()}%", s.timelineAlpha, 0f, 1f, enabled = s.showTimeline && s.showStatusLine) { v -> scope.launch { settingsStore.setTimelineAlpha(v) } } }

                    item { SettingsSectionHeader("Timeline Filter") }
                    item { SettingsToggleChip("Local", "eigene Aktionen", s.timelineShowLocal, enabled = s.showTimeline) { v -> scope.launch { settingsStore.setTimelineShowLocal(v) } } }
                    item { SettingsToggleChip("Remote", "Resolume/OSC", s.timelineShowRemote, enabled = s.showTimeline) { v -> scope.launch { settingsStore.setTimelineShowRemote(v) } } }
                    item { SettingsToggleChip("Health", "Ping/Errors", s.timelineShowHealth, enabled = s.showTimeline) { v -> scope.launch { settingsStore.setTimelineShowHealth(v) } } }
                    item { SettingsToggleChip("Important only", "Tap/Resync/Nudge + Errors", s.timelineImportantOnly, enabled = s.showTimeline) { v -> scope.launch { settingsStore.setTimelineImportantOnly(v) } } }
                    item { SettingsSliderChip("Remote Alpha", "${(s.timelineRemoteAlpha * 100f).roundToLong()}%", s.timelineRemoteAlpha, 0.1f, 1f, enabled = s.showTimeline && s.timelineShowRemote) { v -> scope.launch { settingsStore.setTimelineRemoteAlpha(v) } } }
                    item { SettingsSliderChip("Remote Debounce", "${s.remoteEventMinIntervalMs}ms", s.remoteEventMinIntervalMs.toFloat(), 0f, 500f, enabled = s.showTimeline && s.timelineShowRemote) { v -> scope.launch { settingsStore.setRemoteEventMinIntervalMs(v.roundToLong()) } } }

                    item { SettingsSectionHeader("Visuals / Motion") }
                    item { SettingsToggleChip("Animations", "Master switch", s.animationsEnabled) { v -> scope.launch { settingsStore.setAnimationsEnabled(v) } } }
                    item { SettingsToggleChip("Ripples", "Transport rings", s.rippleEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setRippleEnabled(v) } } }
                    item { SettingsToggleChip("Phase Visualizer", "Ring phase dot", s.phaseVisualizerEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setPhaseVisualizerEnabled(v) } } }
                    item { SettingsToggleChip("Phase Spiral", "Spiral visual", s.phaseSpiralEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setPhaseSpiralEnabled(v) } } }
                    item { SettingsToggleChip("Remote Ghost", "remote visuals", s.remoteGhostModeEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setRemoteGhostModeEnabled(v) } } }
                    item { SettingsToggleChip("Remote Animations", "remote motion", s.remoteAnimationsEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setRemoteAnimationsEnabled(v) } } }
                    item { SettingsToggleChip("Goblin Flash", "accent flashes", s.goblinFlashEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setGoblinFlashEnabled(v) } } }

                    item { SettingsSectionHeader("Instrument") }
                    item { SettingsToggleChip("Moods", "online grin / offline grummeln", s.moodsEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setMoodsEnabled(v) } } }
                    item { SettingsSliderChip("Mood Intensity", "${(s.moodIntensity * 100f).roundToLong()}%", s.moodIntensity, 0f, 0.2f, enabled = s.animationsEnabled && s.moodsEnabled) { v -> scope.launch { settingsStore.setMoodIntensity(v) } } }
                    item { SettingsToggleChip("Aura", "nur wenn stable", s.phaseAuraEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setPhaseAuraEnabled(v) } } }
                    item { SettingsToggleChip("Micro Particles", "nur wenn stable", s.microParticlesEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setMicroParticlesEnabled(v) } } }
                    item { SettingsSliderChip("Swing", "${(s.visualSwing * 100f).roundToLong()}%", s.visualSwing, 0f, 0.3f, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setVisualSwing(v) } } }
                    item { SettingsToggleChip("Ghost Echo", "afterglow trail", s.ghostEchoEnabled, enabled = s.animationsEnabled) { v -> scope.launch { settingsStore.setGhostEchoEnabled(v) } } }
                    item { SettingsSliderChip("Echo Strength", "${(s.ghostEchoStrength * 100f).roundToLong()}%", s.ghostEchoStrength, 0f, 1f, enabled = s.animationsEnabled && s.ghostEchoEnabled) { v -> scope.launch { settingsStore.setGhostEchoStrength(v) } } }
                }

                SettingsPage.CLOCK -> {
                    item { SettingsTitleRow("Clock / Engine", showBack = true) { pop() } }

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

                
                SettingsPage.TARGETS -> {
                    item { SettingsTitleRow("OSC Targets", showBack = true) { pop() } }

                    item {
                        ActivePresetBar(
                            presets = s.presets,
                            activeIndex = s.activePreset,
                            onSelect = { i -> scope.launch { settingsStore.setActivePreset(i) } }
                        )
                    }

                    // Presets as real subpages (no expand-on-card)
                    s.presets.forEachIndexed { i, p ->
                        val label = when (i) { 0 -> "A"; 1 -> "B"; 2 -> "C"; else -> (i + 1).toString() }
                        item {
                            SettingsNavChip(
                                icon = Icons.Filled.Send,
                                title = "$label • ${p.name}",
                                subtitle = "${p.ip}:${p.port}" + if (i == s.activePreset) " • active" else ""
                            ) {
                                editPresetIndex = i
                                push(SettingsPage.TARGET_EDIT)
                            }
                        }
                    }
                }

                SettingsPage.TARGET_EDIT -> {
                    val i = editPresetIndex.coerceIn(0, s.presets.lastIndex)
                    val preset = s.presets[i]
                    val letter = when (i) { 0 -> "A"; 1 -> "B"; 2 -> "C"; else -> (i + 1).toString() }

                    item { SettingsTitleRow("Preset $letter", showBack = true) { pop() } }

                    item {
                        val shape = RoundedCornerShape(30.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(GoblinCard)
                                .border(1.dp, GoblinBorder, shape)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Name", color = GoblinDim, fontSize = 11.sp)
                            var name by remember(preset.name) { mutableStateOf(preset.name) }
                            TextFieldItem(
                                value = name,
                                onValue = { name = it },
                                placeholder = "Preset name",
                                keyboardType = KeyboardType.Text
                            )

                            Text("IP", color = GoblinDim, fontSize = 11.sp)
                            var ip by remember(preset.ip) { mutableStateOf(preset.ip) }
                            TextFieldItem(
                                value = ip,
                                onValue = { ip = it },
                                placeholder = "192.168.0.10",
                                keyboardType = KeyboardType.Text
                            )

                            Text("Port", color = GoblinDim, fontSize = 11.sp)
                            var portStr by remember(preset.port) { mutableStateOf(preset.port.toString()) }
                            TextFieldItem(
                                value = portStr,
                                onValue = { portStr = it.filter { c -> c.isDigit() }.take(5) },
                                placeholder = "7000",
                                keyboardType = KeyboardType.Number
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val isActive = (i == s.activePreset)
                                Button(
                                    onClick = { scope.launch { settingsStore.setActivePreset(i) } },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (isActive) GoblinAccent.copy(alpha = 0.35f) else GoblinBorder,
                                        contentColor = GoblinText
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) { Text(if (isActive) "Active" else "Use") }

                                Button(
                                    onClick = {
                                        val port = portStr.toIntOrNull() ?: preset.port
                                        scope.launch {
                                            settingsStore.updatePreset(
                                                i,
                                                name.trim().ifEmpty { preset.name },
                                                ip.trim().ifEmpty { preset.ip },
                                                port
                                            )
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