package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tapsyncwatch.domain.transport.TransportFeedback
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.osc.OscHealth
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.data.PreflightMode
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Debug Hub Screen (single scrollable page).
 * Reached via swipe paging: Tap -> FFT -> Debug.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DebugScreen(
    // Health / signal freshness
    lastPongMs: StateFlow<Long>,
    lastPhaseRxMs: StateFlow<Long>,
    lastDownbeatRxMs: StateFlow<Long>,
    lastAnyRxMs: StateFlow<Long>,
    signalGraceMs: Long,
    heartbeatEnabled: Boolean,

    // OSC health (sending/error)
    oscHealth: StateFlow<OscHealth>,

    // OSC debug (summary)
    showOscMonitor: Boolean,
    oscDebugState: StateFlow<OscInputReceiver.OscDebugState>,

    // Timeline / transport
    showTimeline: Boolean,
    timelineWindowMs: Long,
    timelineShowLocal: Boolean,
    timelineShowRemote: Boolean,
    timelineShowHealth: Boolean,
    timelineImportantOnly: Boolean,
    timelineRemoteAlpha: Float,
    remoteEventMinIntervalMs: Long,
    transportIn: SharedFlow<TransportFeedback>,

    // Preflight
    showPreflight: Boolean,
    preflightAlpha: Float,
    preflightMode: PreflightMode,
    preflightPhaseOkMs: Long,
    preflightDownbeatOkMs: Long,
    preflightOutOkMs: Long,

    // Targets
    presets: List<OscTarget>,
    activePreset: Int,

    onPageNext: (() -> Unit)? = null,
    onPagePrev: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // --- Edge swipe paging ---
    val edgeWidthPx = with(density) { 26.dp.toPx() }
    val swipeThresholdPx = with(density) { 34.dp.toPx() }
    val verticalSlopPx = with(density) { 22.dp.toPx() }

    var layoutWidthPx by remember { mutableStateOf(0f) }
    var candidateNext by remember { mutableStateOf(false) }
    var candidatePrev by remember { mutableStateOf(false) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    // Goblin palette
    val goblinBg = Color(0xFF0B0B0B)
    val goblinBrown = Color(0xFF8C5A2B)
    val goblinOrange = Color(0xFFFF9A3D)
    val goblinGreen = Color(0xFF41D17E)
    val goblinRed = Color(0xFFFF5A5F)
    val goblinGrey = Color(0xFFB9B0A6)

    // Drive a cheap "now" for age readouts.
    var uiNowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            uiNowMs = SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(66L)
        }
    }

    // Health ages
    val pong by lastPongMs.collectAsState(initial = 0L)
    val phaseRx by lastPhaseRxMs.collectAsState(initial = 0L)
    val downbeatRx by lastDownbeatRxMs.collectAsState(initial = 0L)
    val anyRx by lastAnyRxMs.collectAsState(initial = 0L)

    fun ageMs(ts: Long): Long = if (ts <= 0L) Long.MAX_VALUE else (uiNowMs - ts).coerceAtLeast(0L)
    val pongAge = ageMs(pong)
    val phaseAge = ageMs(phaseRx)
    val downbeatAge = ageMs(downbeatRx)
    val anyAge = ageMs(anyRx)

    fun ok(age: Long): Boolean = age < signalGraceMs

    // OSC debug summary
    val dbg by oscDebugState.collectAsState(initial = OscInputReceiver.OscDebugState())

    // ===== Timeline (3 lanes, smooth) =====
    val windowMsSafe = timelineWindowMs.coerceIn(2000L, 15_000L)
    var timelineNowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val timelineEvents = remember { mutableStateListOf<TimelineEvent>() }

    fun pushTimeline(kind: TimelineKind, atMs: Long = SystemClock.elapsedRealtime()) {
        timelineEvents.add(TimelineEvent(atMs = atMs, kind = kind))
        if (timelineEvents.size > 120) {
            timelineEvents.subList(0, timelineEvents.size - 120).clear()
        }
    }

    // Remote spam guard (Resolume can flood)
    val remoteDebounceMs = remoteEventMinIntervalMs.coerceIn(0L, 2000L)
    var lastRemoteTimelineMs by remember { mutableStateOf(0L) }

    fun pushTimelineRemote(kind: TimelineKind) {
        val now = SystemClock.elapsedRealtime()
        val debouncedKinds = setOf(
            TimelineKind.REMOTE_MULTIPLY,
            TimelineKind.REMOTE_DIVIDE,
            TimelineKind.REMOTE_NUDGE
        )
        if (remoteDebounceMs > 0L && kind in debouncedKinds) {
            if (now - lastRemoteTimelineMs < remoteDebounceMs) return
            lastRemoteTimelineMs = now
        }
        pushTimeline(kind, atMs = now)
    }

    // Collect remote transport events
    LaunchedEffect(showTimeline) {
        if (!showTimeline) return@LaunchedEffect
        transportIn.collect { fb ->
            when (fb) {
                TransportFeedback.Tap -> pushTimelineRemote(TimelineKind.REMOTE_TAP)
                TransportFeedback.Multiply -> pushTimelineRemote(TimelineKind.REMOTE_MULTIPLY)
                TransportFeedback.Divide -> pushTimelineRemote(TimelineKind.REMOTE_DIVIDE)
                TransportFeedback.Resync -> pushTimelineRemote(TimelineKind.REMOTE_RESYNC)
                TransportFeedback.NudgeStart, TransportFeedback.NudgeStop -> pushTimelineRemote(TimelineKind.REMOTE_NUDGE)
            }
        }
    }

    // Heartbeat PONG as health timeline event
    LaunchedEffect(showTimeline, timelineShowHealth) {
        if (!showTimeline || !timelineShowHealth) return@LaunchedEffect
        lastPongMs.collect { ts ->
            if (ts > 0L) pushTimeline(TimelineKind.PONG, atMs = ts)
        }
    }

    // OSC output health events
    LaunchedEffect(showTimeline, timelineShowHealth) {
        if (!showTimeline || !timelineShowHealth) return@LaunchedEffect
        oscHealth.collect { h ->
            when (h) {
                is OscHealth.Sending -> pushTimeline(TimelineKind.OSC_OUT, atMs = h.atMs)
                is OscHealth.Error -> pushTimeline(TimelineKind.OSC_ERR)
                OscHealth.Idle -> Unit
            }
        }
    }

    // Drive "now" + prune old events
    LaunchedEffect(showTimeline, windowMsSafe) {
        if (!showTimeline) return@LaunchedEffect
        while (isActive) {
            val now = SystemClock.elapsedRealtime()
            timelineNowMs = now
            val cut = now - windowMsSafe
            while (timelineEvents.isNotEmpty() && timelineEvents.first().atMs < cut) {
                timelineEvents.removeAt(0)
            }
            kotlinx.coroutines.delay(66L)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(goblinBg)
            .onSizeChanged { layoutWidthPx = it.width.toFloat() }
            .pointerInteropFilter { e ->
                if (layoutWidthPx <= 0f) return@pointerInteropFilter false

                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        triggered = false
                        candidateNext = (onPageNext != null) && (e.x >= layoutWidthPx - edgeWidthPx)
                        candidatePrev = (onPagePrev != null) && (e.x <= edgeWidthPx)
                        startX = e.x
                        startY = e.y
                        candidateNext || candidatePrev
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (triggered) return@pointerInteropFilter true
                        val dx = e.x - startX
                        val dy = e.y - startY
                        if (abs(dy) > verticalSlopPx) return@pointerInteropFilter candidateNext || candidatePrev

                        if (candidateNext && dx < -swipeThresholdPx) {
                            triggered = true
                            onPageNext?.invoke()
                            return@pointerInteropFilter true
                        }
                        if (candidatePrev && dx > swipeThresholdPx) {
                            triggered = true
                            onPagePrev?.invoke()
                            return@pointerInteropFilter true
                        }
                        candidateNext || candidatePrev
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        candidateNext = false
                        candidatePrev = false
                        false
                    }

                    else -> false
                }
            },
        color = goblinBg
    ) {
        val isRound = LocalConfiguration.current.isScreenRound
        val pad = if (isRound) 16.dp else 12.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = pad, vertical = pad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            item {
                Text(
                    text = "DEBUG",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.h6,
                    color = goblinOrange,
                    textAlign = TextAlign.Center
                )
            }

            // --- Preflight / Link health ---
            if (showPreflight || heartbeatEnabled) {
                item {
                    val health by oscHealth.collectAsState(initial = OscHealth.Idle)

                    // Mirror TapScreen semantics, but fall back to 'any RX' when phase/downbeat are not available.
                    val hasSignal = heartbeatEnabled && pongAge < signalGraceMs

                    val phaseOk = phaseAge < preflightPhaseOkMs
                    val downbeatOk = downbeatAge < preflightDownbeatOkMs

                    val useAnyFallback = (phaseRx <= 0L && downbeatRx <= 0L)
                    val anyOk = anyAge < signalGraceMs
                    val inOk = if (useAnyFallback) anyOk else (phaseOk || downbeatOk)

                    val outAge = when (val h = health) {
                        is OscHealth.Sending -> ageMs(h.atMs)
                        else -> Long.MAX_VALUE
                    }
                    val outOk = outAge < preflightOutOkMs

                    val inColor = when {
                        !heartbeatEnabled -> goblinGrey.copy(alpha = 0.35f)
                        !hasSignal -> goblinRed
                        inOk -> goblinGreen
                        else -> goblinOrange
                    }

                    val outColor = when (health) {
                        is OscHealth.Error -> goblinRed
                        is OscHealth.Sending -> if (outOk) goblinGreen else goblinOrange
                        OscHealth.Idle -> goblinGrey.copy(alpha = 0.35f)
                    }

                    DebugSection(
                        title = "Preflight",
                        subtitle = if (useAnyFallback)
                            "Link / Inbound freshness (any-RX fallback)"
                        else
                            "Link / Phase / Downbeat freshness",
                        accent = goblinBrown,
                    ) {
                        val minimal = preflightMode == PreflightMode.MINIMAL

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DebugDot(
                                label = if (minimal) "" else "P",
                                ok = hasSignal,
                                colorOk = goblinGreen,
                                colorBad = goblinRed
                            )
                            DebugDot(
                                label = if (minimal) "" else "IN",
                                ok = hasSignal && inOk,
                                colorOk = inColor,
                                colorBad = goblinRed
                            )
                            DebugDot(
                                label = if (minimal) "" else "OUT",
                                ok = outOk,
                                colorOk = outColor,
                                colorBad = goblinRed
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        DebugKeyValue("P age", fmtAge(pongAge), goblinGrey, alpha = preflightAlpha)

                        val inAgeShown = if (useAnyFallback) anyAge else minOf(phaseAge, downbeatAge)
                        val inLabel = if (useAnyFallback) "IN age (any)" else "IN age"
                        DebugKeyValue(inLabel, fmtAge(inAgeShown), goblinGrey, alpha = preflightAlpha)

                        // Diagnostics (will show --- when those signals are absent)
                        DebugKeyValue("Phase age", fmtAge(phaseAge), goblinGrey, alpha = preflightAlpha)
                        DebugKeyValue("Downbeat age", fmtAge(downbeatAge), goblinGrey, alpha = preflightAlpha)
                        DebugKeyValue("Any RX age", fmtAge(anyAge), goblinGrey, alpha = preflightAlpha)

                        DebugKeyValue("grace", "${signalGraceMs}ms", goblinGrey, alpha = preflightAlpha)
                    }
                }
            }


            // --- OSC Monitor (summary) ---
            if (showOscMonitor) {
                item {
                    DebugSection(
                        title = "OSC Monitor",
                        subtitle = "Last packet + counters",
                        accent = goblinOrange,
                    ) {
                        DebugKeyValue("packets", dbg.packetsTotal.toString(), goblinGrey)

                        DebugKeyMultiline(
                            key = "last addr",
                            value = softBreakAddress(dbg.lastAddress ?: "-"),
                            valueColor = goblinGrey,
                            mono = true,
                            maxLines = 4
                        )

                        DebugKeyValue("types", dbg.lastTypeTags ?: "-", goblinGrey, mono = true)
                        DebugKeyValue("args", dbg.lastArgs ?: "-", goblinGrey, mono = true)
                        DebugKeyValue("last rx", fmtAge(ageMs(dbg.lastSeenMs)), goblinGrey)
                    }
                }
            }

            // --- Timeline Inspector ---
            if (showTimeline) {
                item {
                    DebugSection(
                        title = "Timeline",
                        subtitle = "Window ${windowMsSafe}ms",
                        accent = goblinBrown,
                    ) {
                        DebugTimelineLane(
                            nowMs = timelineNowMs,
                            windowMs = windowMsSafe,
                            events = timelineEvents,
                            showLocal = timelineShowLocal,
                            showRemote = timelineShowRemote,
                            showHealth = timelineShowHealth,
                            importantOnly = timelineImportantOnly,
                            remoteAlpha = timelineRemoteAlpha,
                            goblinOrange = goblinOrange
                        )
                    }
                }
            }

            // --- Targets ---
            item {
                DebugSection(
                    title = "Targets",
                    subtitle = "Active preset + endpoints",
                    accent = goblinOrange,
                ) {
                    val idx = activePreset.coerceIn(0, (presets.size - 1).coerceAtLeast(0))
                    val active = presets.getOrNull(idx)
                    DebugKeyValue("active", active?.name ?: "Preset ${idx + 1}", goblinGrey)
                    if (active != null) {
                        DebugKeyValue("primary", "${active.ip}:${active.port}", goblinGrey, mono = true)
                    }
                    if (presets.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        presets.forEachIndexed { i, t ->
                            val isActive = i == idx
                            TargetRow(
                                name = t.name.ifBlank { "Preset ${i + 1}" },
                                endpoint = "${t.ip}:${t.port}",
                                active = isActive,
                                colorActive = goblinGreen,
                                colorIdle = goblinGrey
                            )
                        }
                    }
                }
            }

            // --- Empty state ---
            if (!showOscMonitor && !showTimeline && !(showPreflight || heartbeatEnabled)) {
                item {
                    DebugSection(
                        title = "Nothing armed",
                        subtitle = "Enable OSC monitor or Timeline in Settings",
                        accent = goblinBrown,
                    ) {
                        Text(
                            text = "Tip: Settings → Network/Debug or HUD toggles.",
                            color = goblinGrey,
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

// ===== UI building blocks =====

@Composable
private fun DebugSection(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = Color(0xFF101010)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(bg, Color(0xFF0D0D0D))
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp)) { drawCircle(color = accent) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = title, color = Color(0xFFF1E6D8), style = MaterialTheme.typography.subtitle1)
                Text(
                    text = subtitle,
                    color = Color(0xFFB9B0A6),
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun DebugKeyValue(
    key: String,
    value: String,
    valueColor: Color,
    alpha: Float = 1f,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = key, color = Color(0xFFB9B0A6), style = MaterialTheme.typography.caption)
        Text(
            text = value,
            color = valueColor.copy(alpha = alpha.coerceIn(0f, 1f)),
            style = MaterialTheme.typography.caption,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 2,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun DebugKeyMultiline(
    key: String,
    value: String,
    valueColor: Color,
    mono: Boolean = false,
    alpha: Float = 1f,
    maxLines: Int = 4
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = key, color = Color(0xFFB9B0A6), style = MaterialTheme.typography.caption)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor.copy(alpha = alpha.coerceIn(0f, 1f)),
            style = MaterialTheme.typography.caption,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = maxLines,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun DebugDot(label: String, ok: Boolean, colorOk: Color, colorBad: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(18.dp)) { drawCircle(color = if (ok) colorOk else colorBad) }
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color(0xFFB9B0A6), style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun TargetRow(name: String, endpoint: String, active: Boolean, colorActive: Color, colorIdle: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF143221) else Color(0xFF151515))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (active) colorActive else colorIdle,
            style = MaterialTheme.typography.subtitle2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = softBreakAddress(endpoint),
            color = colorIdle,
            style = MaterialTheme.typography.caption,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Clip
        )
    }
}

private fun fmtAge(ageMs: Long): String {
    if (ageMs == Long.MAX_VALUE) return "—"
    if (ageMs < 1000) return "${ageMs}ms"
    val s = ageMs / 1000.0
    return String.format("%.1fs", s)
}

private fun softBreakAddress(s: String): String {
    // Add zero-width break opportunities after common OSC/IP separators.
    // This enables multi-line wrapping even for long "word-like" addresses.
    val zwsp = "\u200B"
    val seps = listOf("/", ".", ":", "_", "-", "?", "&", "=", "#")
    var out = s
    for (sep in seps) {
        out = out.replace(sep, sep + zwsp)
    }
    return out
}

// ===== Timeline drawing (3-lane canvas, no legend) =====

@Composable
private fun DebugTimelineLane(
    nowMs: Long,
    windowMs: Long,
    events: List<TimelineEvent>,
    showLocal: Boolean,
    showRemote: Boolean,
    showHealth: Boolean,
    importantOnly: Boolean,
    remoteAlpha: Float,
    goblinOrange: Color,
    modifier: Modifier = Modifier
) {
    val isRound = LocalConfiguration.current.isScreenRound
    val edgePad = if (isRound) 14.dp else 8.dp

    val remoteA = safe01(remoteAlpha, 1f)
    val laneSpecs = remember(showLocal, showRemote, showHealth, remoteA) {
        buildList {
            if (showLocal) add(TimelineLaneSpec(TimelineLaneType.LOCAL, 1f))
            if (showRemote) add(TimelineLaneSpec(TimelineLaneType.REMOTE, remoteA))
            if (showHealth) add(TimelineLaneSpec(TimelineLaneType.HEALTH, 1f))
        }
    }

    if (laneSpecs.isEmpty()) {
        Text(
            text = "All lanes hidden (check Settings).",
            color = Color(0xFFB9B0A6),
            style = MaterialTheme.typography.body2
        )
        return
    }

    val lanes = laneSpecs.size
    val heightDp = when (lanes) {
        1 -> 20.dp
        2 -> 30.dp
        else -> 40.dp
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .padding(horizontal = edgePad)
    ) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        val baseAlpha = 0.18f

        val laneH = (h / lanes.toFloat()).coerceAtLeast(1f)

        fun laneType(k: TimelineKind): TimelineLaneType = when (k) {
            TimelineKind.LOCAL_TAP,
            TimelineKind.LOCAL_MULTIPLY,
            TimelineKind.LOCAL_DIVIDE,
            TimelineKind.LOCAL_RESYNC,
            TimelineKind.LOCAL_NUDGE -> TimelineLaneType.LOCAL

            TimelineKind.REMOTE_TAP,
            TimelineKind.REMOTE_MULTIPLY,
            TimelineKind.REMOTE_DIVIDE,
            TimelineKind.REMOTE_RESYNC,
            TimelineKind.REMOTE_NUDGE -> TimelineLaneType.REMOTE

            TimelineKind.PONG,
            TimelineKind.OSC_OUT,
            TimelineKind.OSC_ERR -> TimelineLaneType.HEALTH
        }

        fun laneIndex(k: TimelineKind): Int? {
            val t = laneType(k)
            val idx = laneSpecs.indexOfFirst { it.type == t }
            return if (idx >= 0) idx else null
        }

        fun laneMidY(i: Int): Float = (i + 0.5f) * laneH

        // baselines
        for (i in 0 until lanes) {
            val a = baseAlpha * laneSpecs[i].alpha
            if (a <= 0.01f) continue
            drawLine(
                color = Color.White.copy(alpha = a),
                start = Offset(0f, laneMidY(i)),
                end = Offset(w, laneMidY(i)),
                strokeWidth = stroke
            )
        }

        fun isImportant(k: TimelineKind): Boolean = when (k) {
            TimelineKind.LOCAL_TAP,
            TimelineKind.LOCAL_RESYNC,
            TimelineKind.LOCAL_NUDGE,

            TimelineKind.REMOTE_TAP,
            TimelineKind.REMOTE_RESYNC,
            TimelineKind.REMOTE_NUDGE -> true

            TimelineKind.OSC_ERR,
            TimelineKind.PONG -> true

            else -> false
        }

        fun colorFor(k: TimelineKind): Color = when (k) {
            TimelineKind.PONG -> Color(0xFF6DFF8F)
            TimelineKind.OSC_OUT -> Color(0xFFB86CFF)
            TimelineKind.OSC_ERR -> Color(0xFFFF6D6D)

            TimelineKind.LOCAL_MULTIPLY -> Color(0xFF6DFF8F)
            TimelineKind.LOCAL_DIVIDE -> Color(0xFFFFD36D)
            TimelineKind.LOCAL_RESYNC -> Color(0xFFFF6D6D)
            TimelineKind.LOCAL_TAP,
            TimelineKind.LOCAL_NUDGE -> goblinOrange

            // remote
            else -> Color(0xFF6D9BFF)
        }

        fun heightFor(k: TimelineKind): Float {
            val lh = laneH
            return when (k) {
                TimelineKind.LOCAL_RESYNC -> lh * 0.95f
                TimelineKind.LOCAL_MULTIPLY, TimelineKind.LOCAL_DIVIDE -> lh * 0.85f
                TimelineKind.LOCAL_TAP -> lh * 0.75f
                TimelineKind.LOCAL_NUDGE -> lh * 0.65f

                TimelineKind.OSC_ERR -> lh * 0.95f
                TimelineKind.PONG -> lh * 0.75f
                TimelineKind.OSC_OUT -> lh * 0.55f

                else -> lh * 0.60f
            }.coerceIn(2f, lh)
        }

        for (e in events) {
            if (importantOnly && !isImportant(e.kind)) continue

            val age = (nowMs - e.atMs).coerceAtLeast(0L)
            if (age > windowMs) continue

            val t = age.toFloat() / windowMs.toFloat()
            val x = w * (1f - t)

            val li = laneIndex(e.kind) ?: continue
            val laneA = laneSpecs[li].alpha
            if (laneA <= 0.01f) continue

            val mid = laneMidY(li)
            val hh = heightFor(e.kind)
            val y0 = (mid - hh * 0.5f).coerceAtLeast(li * laneH)
            val y1 = (mid + hh * 0.5f).coerceAtMost((li + 1) * laneH)

            val fade = (1f - t * 0.65f).coerceIn(0.25f, 1f)
            val a = 0.92f * laneA * fade

            drawLine(
                color = colorFor(e.kind).copy(alpha = a),
                start = Offset(x, y0),
                end = Offset(x, y1),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        // "now" marker
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(w, 0f),
            end = Offset(w, h),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun safe01(v: Float, default: Float = 0f): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else default
