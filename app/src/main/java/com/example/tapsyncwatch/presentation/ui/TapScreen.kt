@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.domain.clock.ClockVisualState
import com.example.tapsyncwatch.domain.transport.TransportFeedback
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.osc.OscHealth
import com.example.tapsyncwatch.presentation.data.PreflightMode
import com.example.tapsyncwatch.presentation.data.BpmFormat
import com.example.tapsyncwatch.presentation.data.DownbeatStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.interaction.MutableInteractionSource


private fun safe01(v: Float, default: Float = 0f): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else default

private const val STATUS_LAMP_RADIUS_DP = 4f

private fun clampDp(v: Float, default: Float, min: Float = 0.5f, max: Float = 6.0f): Float =
    if (v.isFinite()) v.coerceIn(min, max) else default

@Composable
private fun dpToPx(dp: Float): Float = with(LocalDensity.current) { dp.dp.toPx() }


/* ================= GOBLIN STYLE ================= */

private val GoblinBrown = Color(0xFF8C5A2B)
private val GoblinOrange = Color(0xFFE8802A)
private val GoblinDim = Color(0xFF9A9A9A)
private val GoblinText = Color(0xFFECECEC)
private val GoblinDebugBg = Color(0xAA000000)

private enum class TouchZone {CENTER, LEFT,
    RIGHT_EDGE}
private enum class Voice { LOCAL, REMOTE }

private enum class RippleKind {
    TAP,
    MULTIPLY,
    DIVIDE,
    RESYNC,
    NUDGE_PLUS,
    NUDGE_MINUS
}

@Composable
private fun StatusPill(
    text: String,
    stateColor: Color,
    warnTint: Color? = null,
    rxPulseKey: Long = 0L,
    modifier: Modifier = Modifier
) {
    val borderBase = (warnTint ?: stateColor).copy(alpha = 0.35f)

    // Border blink pulse (no dot)
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(rxPulseKey) {
        if (rxPulseKey <= 0L) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        )
    }

    // Split by CHAR (avoids broken string literals)
    val parts = remember(text) { text.split('\n', limit = 4) }
    val line1 = parts.getOrNull(0).orEmpty()
    val line2 = parts.getOrNull(1).orEmpty()
    val line3 = parts.getOrNull(2).orEmpty()
    val line4 = parts.getOrNull(3).orEmpty()

    val p = pulse.value
    val borderBlink = if (p > 0f) {
        androidx.compose.ui.graphics.lerp(borderBase, Color.White.copy(alpha = 0.85f), p)
    } else borderBase
    val borderW = (1f + 1.2f * p).dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB3000000))
            .border(borderW, borderBlink, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // Avoid widthIn() import issues; keep it watch-friendly instead:
            .fillMaxWidth(0.92f)
    ) {
        if (warnTint != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(warnTint.copy(alpha = 0.10f))
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = line1,
                color = stateColor,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            @Composable
            fun renderDiag(s: String) {
                if (s.isBlank()) return
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = s,
                    color = stateColor.copy(alpha = 0.92f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // render 3 extra lines (I/P/O)
            renderDiag(line2)
            renderDiag(line3)
            renderDiag(line4)
        }
    }
}






@Composable
private fun BottomCloseCircleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "×",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun StatusLamp(
    label: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size((STATUS_LAMP_RADIUS_DP * 2f).dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.92f))
                .border(1.dp, Color.Black.copy(alpha = 0.45f), CircleShape)
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TimelineLane(
    nowMs: Long,
    windowMs: Long,
    events: List<TimelineEvent>,
    showLocal: Boolean,
    showRemote: Boolean,
    showHealth: Boolean,
    importantOnly: Boolean,
    remoteAlpha: Float,
    modifier: Modifier = Modifier
) {
    val isRound = LocalConfiguration.current.isScreenRound
    val edgePad = if (isRound) 18.dp else 12.dp

    val remoteA = safe01(remoteAlpha, 1f)
    val laneSpecs = remember(showLocal, showRemote, showHealth, remoteA) {
        buildList {
            if (showLocal) add(TimelineLaneSpec(TimelineLaneType.LOCAL, 1f))
            if (showRemote) add(TimelineLaneSpec(TimelineLaneType.REMOTE, remoteA))
            if (showHealth) add(TimelineLaneSpec(TimelineLaneType.HEALTH, 1f))
        }
    }

    if (laneSpecs.isEmpty()) return

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
            TimelineKind.LOCAL_NUDGE -> GoblinOrange

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



@Composable
private fun ThinDivider(alpha: Float = 0.12f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = alpha))
    )
}
/* ================= PULSE LIMITER ================= */

private class PulseLimiter(
    private val minIntervalMs: Long
) {
    private var lastMs: Long = 0L

    fun allow(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (nowMs - lastMs < minIntervalMs) return false
        lastMs = nowMs
        return true
    }
}

/* ================= RATE LIMITER ================= */

private class HapticRateLimiter(
    private val minIntervalMs: Long
) {
    private var lastMs: Long = 0L
    fun allow(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - lastMs < minIntervalMs) return false
        lastMs = nowMs
        return true
    }
}

private fun circularDelta(a: Float, b: Float): Float {
    // minimal signed distance on a circle (phase space)
    var d = a - b
    while (d > 0.5f) d -= 1f
    while (d < -0.5f) d += 1f
    return d
}

@Composable
fun TapScreen(
    showOscDot: Boolean,
    oscDotOpacity: Float = 1.0f,
    oscDotFadeMs: Long = 180L,
    showStatusLine: Boolean,
    statusLineAlpha: Float = 1.0f,
    preflightAlpha: Float = 1.0f,
    timelineAlpha: Float = 1.0f,
    showPreflight: Boolean,
    preflightMode: PreflightMode,
    statusbarAutoDimWarn: Boolean,
    showTimeline: Boolean,
    timelineWindowMs: Long,
    timelineShowLocal: Boolean,
    timelineShowRemote: Boolean,
    timelineShowHealth: Boolean,
    timelineImportantOnly: Boolean,
    timelineRemoteAlpha: Float,
    remoteEventMinIntervalMs: Long,
    preflightPhaseOkMs: Long,
    preflightDownbeatOkMs: Long,
    preflightOutOkMs: Long,
    activePresetName: String,
    activeTargetIp: String,
    activeTargetPort: Int,

    oscInputPort: Int,
    lastPongFrom: StateFlow<String?>,


    // Downbeat HUD (visual "kick on 1")
    showDownbeatIndicator: Boolean = true,
    downbeatStyle: DownbeatStyle = DownbeatStyle.PULSE,
    downbeatOpacity: Float = 1.0f,

    showBpm: Boolean,
    bpm: Double,

    // Link health (Heartbeat Pong)
    lastPongMs: StateFlow<Long>,
    lastAnyRxMs: StateFlow<Long>,
    lastPhaseRxMs: StateFlow<Long>,
    lastDownbeatRxMs: StateFlow<Long>,
    heartbeatEnabled: Boolean,
    heartbeatSuppressed: Boolean,
    signalGraceMs: Long,
    anyRxFallbackEnabled: Boolean,
    anyRxTimeoutMs: Long,

    // UI-only monitors
    showExternalBpm: Boolean,
    bpmOpacity: Float = 1.0f,
    bpmFormat: BpmFormat = BpmFormat.BPM,
    externalBpm: StateFlow<Double?>,
    externalConfidence: StateFlow<Float?>,
    showOscDebug: Boolean,
    oscDebugState: StateFlow<OscInputReceiver.OscDebugState>,
    remoteGhost: StateFlow<OscInputReceiver.RemoteGhostSnapshot?>,
    phaseVisualizerEnabled: Boolean,
    phaseSpiralEnabled: Boolean,
    phaseRingOpacity: Float,
    phaseRingThicknessDp: Float,
    phaseSpiralOpacity: Float,
    phaseSpiralThicknessDp: Float,
    phaseAuraOpacity: Float,
    phaseAuraThicknessDp: Float,
    microParticlesOpacity: Float,
    fxAlpha: Float = 1.0f,
    phaseAlpha: Float = 1.0f,
    ghostAlpha: Float = 1.0f,

    // Phase 5: Goblin Instrument visuals
    moodsEnabled: Boolean,
    moodIntensity: Float,
    phaseAuraEnabled: Boolean,
    microParticlesEnabled: Boolean,
    visualSwing: Float,
    ghostEchoEnabled: Boolean,
    ghostEchoStrength: Float,
    ghostEchoOpacity: Float,
    ghostEchoThicknessDp: Float,

    // UI motion
    animationsEnabled: Boolean,
    localVisualsEnabled: Boolean,
    remoteAnimationsEnabled: Boolean,
    remoteGhostModeEnabled: Boolean,
    remoteGhostOpacity: Float,
    goblinFlashEnabled: Boolean,
    goblinFlashOpacity: Float,
    goblinFlashFadeMs: Long,
    rippleEnabled: Boolean,
    rippleTapEnabled: Boolean,
    rippleTapOpacity: Float,
    rippleTapThicknessDp: Float,
    rippleMultDivEnabled: Boolean,
    rippleMultDivOpacity: Float,
    rippleMultDivThicknessDp: Float,
    rippleResyncEnabled: Boolean,
    rippleResyncOpacity: Float,
    rippleResyncThicknessDp: Float,
    rippleNudgeEnabled: Boolean,
    rippleNudgeOpacity: Float,
    rippleNudgeThicknessDp: Float,
    oscPulseEnabled: Boolean,
    oscPulseOpacity: Float,
    oscPulseThicknessDp: Float,

    action: ActionEngine,
    oscHealth: StateFlow<OscHealth>,
    externalClockActivity: Flow<Unit>,
    externalTransportIn: Flow<TransportFeedback>,
    @Suppress("UNUSED_PARAMETER") clockVisualState: StateFlow<ClockVisualState>,
    externalActivity: Flow<Unit>,
    @Suppress("UNUSED_PARAMETER") clockMode: ClockMode,
    hapticsEnabled: Boolean,
    downbeatHapticsEnabled: Boolean,
    transportHapticsEnabled: Boolean,
    onPageNext: (() -> Unit)? = null,
    onPagePrev: (() -> Unit)? = null,
    onCloseOscMonitor: (() -> Unit)? = null,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val metrics = context.resources.displayMetrics
    val scope = rememberCoroutineScope()
    val oscPulseLimiter = remember { PulseLimiter(minIntervalMs = 120) }

    // TapScreen is an instrument: no Debug HUD overlays here (Status/Preflight/Timeline live only in DebugScreen).
    val tapHudEnabled = false
    val showStatusLineTap = tapHudEnabled && showStatusLine
    val showPreflightTap = tapHudEnabled && showPreflight
    val showTimelineTap = tapHudEnabled && showTimeline


    // Startup warmup: keep the first seconds light-weight so the watch can settle.
    var startupWarmup by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(12_000L)
        startupWarmup = false
    }

    // GPU warmup: pre-draw key Canvas paths/rings at near-zero alpha so shaders compile before first live events.
    var gpuWarmup by remember { mutableStateOf(true) }
    val gpuWarmupAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // A handful of quick pulses triggers a few frames and warms stroke/path pipelines.
        repeat(4) {
            gpuWarmupAnim.snapTo(0f)
            gpuWarmupAnim.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            gpuWarmupAnim.snapTo(0f)
            delay(60L)
        }
        gpuWarmup = false
    }
    // Legacy visual multipliers are kept for compatibility but intentionally ignored.
    val fxMul = 1.0f
    val phaseMul = 1.0f
    val ghostMul = 1.0f

    // Phase 5: Goblin Instrument (visual-only)
    val moodIntensitySafe = (if (moodIntensity.isFinite()) moodIntensity else 0f).coerceIn(0f, 0.20f)
    val moodT = (moodIntensitySafe / 0.20f).coerceIn(0f, 1f)
    val swingAmount = (if (visualSwing.isFinite()) visualSwing else 0f).coerceIn(0f, 0.25f)
    val ghostEchoStrengthSafe = (if (ghostEchoStrength.isFinite()) ghostEchoStrength else 0f).coerceIn(0f, 1f)
    val ghostEchoOn = animationsEnabled && rippleEnabled && ghostEchoEnabled && !startupWarmup && ghostEchoStrengthSafe > 0f

    val remoteGhostOpacitySafe = safe01(remoteGhostOpacity, 1f)

    val goblinFlashOpacitySafe = safe01(goblinFlashOpacity, 1f)

    val rippleTapOpacitySafe = safe01(rippleTapOpacity, 1f)
    val rippleTapThicknessDpSafe = clampDp(rippleTapThicknessDp, 5.0f)
    val rippleMultDivOpacitySafe = safe01(rippleMultDivOpacity, 1f)
    val rippleMultDivThicknessDpSafe = clampDp(rippleMultDivThicknessDp, 5.0f)
    val rippleResyncOpacitySafe = safe01(rippleResyncOpacity, 1f)
    val rippleResyncThicknessDpSafe = clampDp(rippleResyncThicknessDp, 5.0f)
    val rippleNudgeOpacitySafe = safe01(rippleNudgeOpacity, 1f)
    val rippleNudgeThicknessDpSafe = clampDp(rippleNudgeThicknessDp, 5.0f)

    val ghostEchoOpacitySafe = safe01(ghostEchoOpacity, 1f)
    val ghostEchoThicknessDpSafe = clampDp(ghostEchoThicknessDp, 3.0f)

    val phaseRingOpacitySafe = safe01(phaseRingOpacity, 1f)
    val phaseRingThicknessPx = dpToPx(clampDp(phaseRingThicknessDp, 4.0f))
    val phaseSpiralOpacitySafe = safe01(phaseSpiralOpacity, 1f)
    val phaseSpiralThicknessPx = dpToPx(clampDp(phaseSpiralThicknessDp, 3.0f))
    val phaseAuraOpacitySafe = safe01(phaseAuraOpacity, 1f)
    val phaseAuraThicknessPx = dpToPx(clampDp(phaseAuraThicknessDp, 4.0f))
    val microParticlesOpacitySafe = safe01(microParticlesOpacity, 1f)

    val oscPulseOpacitySafe = safe01(oscPulseOpacity, 1f)
    val oscPulseRadiusDpSafe = clampDp(oscPulseThicknessDp, 5.0f)


// Safety helpers: avoid NaN/Infinity bricking Canvas (coerceIn does NOT fix NaN)
fun safeAlpha(a: Float): Float = if (a.isFinite()) a.coerceIn(0f, 1f) else 0f
fun safe01(v: Float, default: Float = 0f): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else default
fun clampDp(v: Float, default: Float): Float = if (v.isFinite()) v.coerceIn(0.5f, 6.0f) else default
fun dpToPx(dp: Float): Float = dp * metrics.density
fun safePhase(v: Float, default: Float = 0f): Float {
    if (!v.isFinite()) return default
    // wrap to [0..1)
    val m = v % 1f
    val w = if (m < 0f) m + 1f else m
    return w.coerceIn(0f, 1f)
}



fun applySwingWarp(phase: Float, swing: Float): Float {
    val p = safePhase(phase, default = 0f)
    val s = if (swing.isFinite()) swing.coerceIn(0f, 0.25f) else 0f
    if (s <= 0f) return p
    // Swing = non-linear mapping: first half slower, second half faster (offbeat is delayed)
    val pivot = (0.5f + s * 0.4f).coerceIn(0.5f, 0.75f)
    return if (p < pivot) {
        ((p / pivot) * 0.5f).coerceIn(0f, 1f)
    } else {
        (0.5f + ((p - pivot) / (1f - pivot)) * 0.5f).coerceIn(0f, 1f)
    }
}

    val remoteTransportLimiter = remember { PulseLimiter(minIntervalMs = 180) }

    fun uiHapticAllowed(): Boolean =
        hapticsEnabled

    fun lightHaptic() {
        if (uiHapticAllowed()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun strongHaptic() {
        if (uiHapticAllowed()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val widthPx = metrics.widthPixels.toFloat()
    val heightPx = metrics.heightPixels.toFloat()
    val leftZoneEdgePx = widthPx * 0.25f
    val rightZoneEdgePx = widthPx * 0.86f // ~14% right edge for paging

    val longPressMs = 600L
    val swipeMinDist = 70f
    val swipeMaxOffAxis = 30f
    val axisDominanceMargin = 20f
    val swipeArmDist = 12f
    val tapMaxMove = 10f
    val nudgeArmDist = 12f

    /* ================= External BPM + Debug ================= */

    // Avoid recomposition storms: sample high-frequency OSC flows at a low rate for UI.
    var extBpmUi by remember { mutableStateOf<Double?>(null) }
    var extConfUi by remember { mutableStateOf<Float?>(null) }
    var ghostUi by remember { mutableStateOf<OscInputReceiver.RemoteGhostSnapshot?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            extBpmUi = externalBpm.value
            extConfUi = externalConfidence.value
            ghostUi = remoteGhost.value
            delay(if (startupWarmup) 500L else 250L)
        }
    }

/* ================= OSC pulse dot ================= */

    val oscPulse = remember { Animatable(0f) }

    fun pulseOsc() {
        if (!animationsEnabled) return
        if (!oscPulseEnabled) return
        if (!oscPulseLimiter.allow()) return

        scope.launch {
            val fade = oscDotFadeMs.coerceIn(80L, 900L).toInt()
            oscPulse.snapTo(1f)
            oscPulse.animateTo(0f, tween(fade, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(Unit) { externalActivity.collect { pulseOsc() } }
    LaunchedEffect(Unit) { externalClockActivity.collect { pulseOsc() } }

    /* ================= Transport feedback (OUTGOING) ================= */

    val transportLimiter = remember { HapticRateLimiter(minIntervalMs = 140) }

    LaunchedEffect(Unit) {
        action.oscSender.transportFeedback.collect { feedback ->
            pulseOsc()

            if (!transportHapticsEnabled) return@collect
            if (!uiHapticAllowed()) return@collect
            if (!transportLimiter.allow()) return@collect

            when (feedback) {
                TransportFeedback.Resync -> strongHaptic()
                TransportFeedback.Tap,
                TransportFeedback.Multiply,
                TransportFeedback.Divide,
                TransportFeedback.NudgeStart -> lightHaptic()
                TransportFeedback.NudgeStop -> Unit
            }
        }
    }

    /* ================= Goblin Flash (LOCAL TAP only) ================= */

    val goblinFlashAlpha = remember { Animatable(0f) }
    var goblinFlashTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(goblinFlashTrigger) {
        if (goblinFlashTrigger == 0) return@LaunchedEffect
        goblinFlashAlpha.snapTo(0f)
        val fade = goblinFlashFadeMs.coerceIn(80L, 2500L).toInt()
        goblinFlashAlpha.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
        goblinFlashAlpha.animateTo(0f, tween(fade, easing = FastOutSlowInEasing))
    }

    /* ================= Ripple controller (LOCAL + REMOTE) ================= */

    val localRipple = remember { Animatable(0f) }
    val remoteRipple = remember { Animatable(0f) }
    var localKind by remember { mutableStateOf<RippleKind?>(null) }
    var remoteKind by remember { mutableStateOf<RippleKind?>(null) }
    var localHold by remember { mutableStateOf(false) }
    var remoteHold by remember { mutableStateOf(false) }
    var localNudgePlus by remember { mutableStateOf(true) }

    var localRippleJob by remember { mutableStateOf<Job?>(null) }
    var remoteRippleJob by remember { mutableStateOf<Job?>(null) }
    var localHoldArmJob by remember { mutableStateOf<Job?>(null) }

    fun startRipple(
        voice: Voice,
        kind: RippleKind,
        hold: Boolean,
        nudgePlus: Boolean = true
    ) {
        if (!animationsEnabled) return
        if (!rippleEnabled) return
        if (voice == Voice.LOCAL && !localVisualsEnabled) return
        if (voice == Voice.REMOTE && !remoteAnimationsEnabled) return

        val kindAllowed = when (kind) {
            RippleKind.TAP -> rippleTapEnabled
            RippleKind.MULTIPLY, RippleKind.DIVIDE -> rippleMultDivEnabled
            RippleKind.RESYNC -> rippleResyncEnabled
            RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> rippleNudgeEnabled
        }
        if (!kindAllowed) return

        val anim = if (voice == Voice.LOCAL) localRipple else remoteRipple

        if (voice == Voice.LOCAL) {
            localRippleJob?.cancel(); localRippleJob = null
        } else {
            remoteRippleJob?.cancel(); remoteRippleJob = null
        }

        if (voice == Voice.LOCAL) {
            localKind = kind
            localHold = hold
            if (kind == RippleKind.NUDGE_PLUS || kind == RippleKind.NUDGE_MINUS) {
                localNudgePlus = nudgePlus
            }
        } else {
            remoteKind = kind
            remoteHold = hold
        }

        val newJob = scope.launch {
            anim.snapTo(0f)

            if (hold) {
                val loopMs = 900
                while (isActive && (if (voice == Voice.LOCAL) localHold else remoteHold)) {
                    anim.snapTo(0f)
                    anim.animateTo(1f, tween(loopMs, easing = FastOutSlowInEasing))
                }
                anim.snapTo(0f)
            } else {
                if (kind == RippleKind.MULTIPLY || kind == RippleKind.DIVIDE) {

                    // REMOTE: ghosty "breath" (avoid noisy double-triggers)
                    if (voice == Voice.REMOTE && remoteGhostModeEnabled) {
                        val breathMs = 820
                        anim.snapTo(0f)
                        anim.animateTo(1f, tween(breathMs, easing = FastOutSlowInEasing))
                        anim.snapTo(0f)
                    } else {
                        val pulseMs = 520
                        val gapMs = 140L

                        anim.snapTo(0f)
                        anim.animateTo(1f, tween(pulseMs, easing = FastOutSlowInEasing))
                        anim.snapTo(0f)
                        delay(gapMs)
                        anim.animateTo(1f, tween(pulseMs, easing = FastOutSlowInEasing))
                        anim.snapTo(0f)
                    }
                } else {
                    val ms = when (kind) {
                        RippleKind.TAP -> 520
                        RippleKind.RESYNC -> 900
                        RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> 560
                        RippleKind.MULTIPLY, RippleKind.DIVIDE -> 760
                    }
                    anim.snapTo(0f)
                    anim.animateTo(1f, tween(ms, easing = FastOutSlowInEasing))
                    anim.snapTo(0f)
                }
            }
        }

        if (voice == Voice.LOCAL) localRippleJob = newJob else remoteRippleJob = newJob
    }

    fun stopHoldRipple(voice: Voice) {
        if (voice == Voice.LOCAL) {
            localHold = false
            localRippleJob?.cancel(); localRippleJob = null
            scope.launch { localRipple.snapTo(0f) }
        } else {
            remoteHold = false
            remoteRippleJob?.cancel(); remoteRippleJob = null
            scope.launch { remoteRipple.snapTo(0f) }
        }
    }

    fun mapTransportToRippleKind(feedback: TransportFeedback, nudgePlus: Boolean?): RippleKind? {
        return when (feedback) {
            TransportFeedback.Tap -> RippleKind.TAP
            TransportFeedback.Multiply -> RippleKind.MULTIPLY
            TransportFeedback.Divide -> RippleKind.DIVIDE
            TransportFeedback.Resync -> RippleKind.RESYNC
            TransportFeedback.NudgeStart -> {
                if (nudgePlus == true) RippleKind.NUDGE_PLUS else RippleKind.NUDGE_MINUS
            }
            TransportFeedback.NudgeStop -> null
        }
    }


// ===== Timeline (last N seconds) =====
val timelineWindowMsSafe = timelineWindowMs.coerceIn(2000L, 15000L)
var timelineNowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
val timelineEvents = remember { androidx.compose.runtime.mutableStateListOf<TimelineEvent>() }

fun pushTimeline(kind: TimelineKind) {
    if (!showStatusLineTap || !showTimelineTap) return
    val t = SystemClock.elapsedRealtime()
    timelineEvents.add(TimelineEvent(atMs = t, kind = kind))
    // hard cap to avoid unbounded growth even if prune loop stalls
    if (timelineEvents.size > 96) {
        timelineEvents.subList(0, timelineEvents.size - 96).clear()
    }
}

// Remote spam guard for timeline (Resolume can flood)
val remoteDebounceMs = remoteEventMinIntervalMs.coerceIn(0L, 2000L)
var lastRemoteTimelineMs by remember { mutableStateOf(0L) }

fun pushTimelineRemote(kind: TimelineKind) {
    val now = SystemClock.elapsedRealtime()
    // Debounce only the noisy kinds (keep TAP/RESYNC crisp)
    val shouldDebounce = kind == TimelineKind.REMOTE_MULTIPLY ||
        kind == TimelineKind.REMOTE_DIVIDE ||
        kind == TimelineKind.REMOTE_NUDGE
    if (remoteDebounceMs > 0L && shouldDebounce) {

        if (now - lastRemoteTimelineMs < remoteDebounceMs) return
        lastRemoteTimelineMs = now
    }
    pushTimeline(kind)
}

// Drive "now" + prune old events. Only when status HUD is shown.
LaunchedEffect(showStatusLineTap, showTimelineTap, timelineWindowMsSafe) {
    if (!showStatusLineTap || !showTimelineTap) return@LaunchedEffect
    while (isActive) {
        val now = SystemClock.elapsedRealtime()
        timelineNowMs = now
        val cut = now - timelineWindowMsSafe
        while (timelineEvents.isNotEmpty() && timelineEvents.first().atMs < cut) {
            timelineEvents.removeAt(0)
        }
        delay(66L)
    }
}

    // Remote transport in (Resolume -> Watch): NO goblin flash
    LaunchedEffect(remoteAnimationsEnabled, animationsEnabled, rippleEnabled, remoteGhostModeEnabled) {
        externalTransportIn.collect { fb ->
            pulseOsc()
            when (fb) {
                TransportFeedback.Tap -> pushTimelineRemote(TimelineKind.REMOTE_TAP)
                TransportFeedback.Multiply -> pushTimelineRemote(TimelineKind.REMOTE_MULTIPLY)
                TransportFeedback.Divide -> pushTimelineRemote(TimelineKind.REMOTE_DIVIDE)
                TransportFeedback.Resync -> pushTimelineRemote(TimelineKind.REMOTE_RESYNC)
                TransportFeedback.NudgeStart, TransportFeedback.NudgeStop -> pushTimelineRemote(TimelineKind.REMOTE_NUDGE)
            }
            if (!animationsEnabled || !remoteAnimationsEnabled || !rippleEnabled) return@collect
            if (!remoteTransportLimiter.allow()) return@collect

            when (fb) {
                TransportFeedback.NudgeStop -> stopHoldRipple(Voice.REMOTE)
                TransportFeedback.NudgeStart -> {
                    // ghost: no hold, calm one-shot (reads as external)
                    startRipple(
                        Voice.REMOTE,
                        RippleKind.NUDGE_MINUS,
                        hold = !remoteGhostModeEnabled
                    )
                }
                else -> {
                    val kind = mapTransportToRippleKind(fb, nudgePlus = null) ?: return@collect
                    startRipple(Voice.REMOTE, kind, hold = false)
                }
            }
        }
    }

    /* ================= CLOCK VISUAL (RESOLUME-DRIVEN) ================= */

    val health by oscHealth.collectAsState()
    val oscDotColor = when (health) {
        is OscHealth.Sending -> Color(0xFF4CAF50)
        is OscHealth.Error -> Color(0xFFE53935)
        OscHealth.Idle -> Color(0xFFB86CFF)
    }



// Track last outbound OSC send moment (used for "TX-only" state).
var lastTxMs by remember { mutableStateOf(0L) }

LaunchedEffect(health) {
    when (val h = health) {
        is OscHealth.Sending -> {
            lastTxMs = h.atMs
            pushTimeline(TimelineKind.OSC_OUT)
        }
        is OscHealth.Error -> pushTimeline(TimelineKind.OSC_ERR)
        else -> Unit
    }
}

// Heartbeat-based link state (do NOT depend on BPM updates)
val lastPong by lastPongMs.collectAsState()
val lastPongFromV by lastPongFrom.collectAsState()
val lastAnyRx by lastAnyRxMs.collectAsState()
val lastPhaseRx by lastPhaseRxMs.collectAsState()
val lastDownbeatRx by lastDownbeatRxMs.collectAsState()
var signalNowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

// Keep freshness updated at low frequency (avoid 30Hz recomposition).
LaunchedEffect(Unit) {
    while (isActive) {
        signalNowMs = SystemClock.elapsedRealtime()
        delay(500L)
    }
}

// Phase ticker cadence (watch-friendly). Only fast when phase visuals are on.
val phaseTickMs: Long = remember(startupWarmup, phaseVisualizerEnabled, phaseSpiralEnabled, phaseAuraEnabled, microParticlesEnabled, visualSwing, downbeatHapticsEnabled) {
    when {
        startupWarmup -> if (downbeatHapticsEnabled) 60L else 120L
        phaseVisualizerEnabled || phaseSpiralEnabled || phaseAuraEnabled || microParticlesEnabled || (visualSwing.isFinite() && visualSwing > 0f) -> 33L   // ~30Hz
        downbeatHapticsEnabled -> 60L                         // keep wrap detection reliable
        else -> 120L
    }
}

val pongOk = remember(heartbeatEnabled, heartbeatSuppressed, lastPong, signalNowMs, signalGraceMs) {
    heartbeatEnabled && !heartbeatSuppressed &&
        lastPong > 0L && (signalNowMs - lastPong) <= signalGraceMs
}

// Any inbound OSC traffic (always tracked). Used as "activity" even when fallback is disabled.
val rxOk = remember(lastAnyRx, signalNowMs, anyRxTimeoutMs) {
    lastAnyRx > 0L && (signalNowMs - lastAnyRx) <= anyRxTimeoutMs.coerceIn(400L, 30_000L)
}

// Optional: allow "any RX" to count as remoteOk (gating remote visuals) if enabled.
val anyOk = remember(anyRxFallbackEnabled, rxOk) { anyRxFallbackEnabled && rxOk }

// Heartbeat-based "remote ok" for driving remote visuals / ghost.
val remoteOk = pongOk || anyOk

// Track last outbound OSC send moment (used for "TX-only" activity state).
val txAge = remember(lastTxMs, signalNowMs) {
    if (lastTxMs <= 0L) null else (signalNowMs - lastTxMs).coerceAtLeast(0L)
}
val txSignalMs = remember(anyRxTimeoutMs) {
    // TX activity is naturally bursty (taps, nudges). Keep it alive longer than the RX timeout.
    maxOf(anyRxTimeoutMs, 2_500L).coerceIn(800L, 15_000L)
}
val txOk = remember(txAge, txSignalMs) {
    txAge != null && txAge <= txSignalMs
}

// "Signal" = any recent activity (pong / any-rx / tx). This is independent of fallback.
val hasSignal = pongOk || rxOk || txOk

// If heartbeat is enabled but there is outbound activity and no pong, show a softer warning.
// If we are receiving OSC already (rxOk), do NOT warn about missing pong.
val noPong = remember(heartbeatEnabled, heartbeatSuppressed, pongOk, rxOk, txOk) {
    heartbeatEnabled && !heartbeatSuppressed && !pongOk && txOk && !rxOk
}

// Hard-down is a stronger offline state (longer outage). Base it on last *activity*, not just last pong.
val lastActivityMs = remember(lastTxMs, lastAnyRx, lastPong) {
    maxOf(lastTxMs, lastAnyRx, lastPong)
}

val hardDown = remember(heartbeatEnabled, heartbeatSuppressed, hasSignal, lastActivityMs, signalNowMs, signalGraceMs) {
    if (!heartbeatEnabled || heartbeatSuppressed) {
        false
    } else if (hasSignal) {
        false
    } else if (lastActivityMs <= 0L) {
        // Never saw any activity yet → treat as normal "down".
        false
    } else {
        val age = (signalNowMs - lastActivityMs).coerceAtLeast(0L)
        val hardDownAfterMs = maxOf(2L * signalGraceMs, 10_000L).coerceIn(6_000L, 60_000L)
        age >= hardDownAfterMs
    }
}

// Timeline: record each pong as a blip
LaunchedEffect(lastPong) {
    if (lastPong > 0L) pushTimeline(TimelineKind.PONG)
}

    // TapScreen stays bright even when there's no link; we only show a subtle hint.
    val goblinBaseAlphaTarget = 1f
    val goblinBaseAlpha by animateFloatAsState(
        targetValue = goblinBaseAlphaTarget,
        animationSpec = tween(
            durationMillis = if (animationsEnabled) 220 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "signalAlpha"
    )

val downbeatPulse = remember { Animatable(0f) }

    // Extrapolated external phase (smooth) + stability (confidence)
    var extPhase by remember { mutableStateOf(0f) }
    var stability by remember { mutableStateOf(1f) }
    var prevPhase by remember { mutableStateOf(0f) }
    var barCount by remember { mutableStateOf(0) }
    var lastDownbeatTriggerMs by remember { mutableStateOf(0L) }
    // Avoid effect restart storms: keep phase ticker stable and read latest values inside the loop.
    val animationsEnabledV by rememberUpdatedState(animationsEnabled)
    val hapticsEnabledV by rememberUpdatedState(hapticsEnabled)
    val downbeatHapticsEnabledV by rememberUpdatedState(downbeatHapticsEnabled)

    LaunchedEffect(remoteOk, phaseTickMs) {
        if (!remoteOk) {
            extPhase = 0f
            stability = 1f
            prevPhase = 0f
            barCount = 0
            return@LaunchedEffect
        }

        while (isActive) {
            val g = remoteGhost.value
            val bpmForPhase = (g?.bpm ?: externalBpm.value)

            if (bpmForPhase == null) {
                // No tempo yet: keep idle but yield.
                extPhase = 0f
                stability = 1f
                prevPhase = 0f
                delay(phaseTickMs)
                continue
            }

            val bpmF = bpmForPhase.toFloat().coerceIn(1f, 999f)

            val now = SystemClock.elapsedRealtime()
            val anchorMs = (g?.lastSeenMs ?: now).coerceAtMost(now)
            val anchorPhase = safePhase(g?.phase ?: extPhase, default = 0f)

            val dtSec = (now - anchorMs).coerceAtLeast(0).toFloat() / 1000f
            val beats = dtSec * (bpmF / 60f)
            val p = ((anchorPhase + beats) % 1f)
            val pSafe = safePhase(p, default = 0f)

            // Downbeat: detect phase wrap
            val wrapped = (prevPhase > 0.80f && pSafe < 0.20f)
            if (wrapped && (now - lastDownbeatTriggerMs) > 250L) {
                lastDownbeatTriggerMs = now
                barCount += 1
                barCount += 1

                if (animationsEnabledV) {
                    // animate pulse without blocking the ticker
                    scope.launch {
                        downbeatPulse.snapTo(1f)
                        downbeatPulse.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                    }
                }

                if (hapticsEnabledV && downbeatHapticsEnabledV) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            prevPhase = pSafe
            extPhase = pSafe

            val conf = safe01((g?.confidence ?: externalConfidence.value ?: 1f), default = 1f)
            stability = conf

            delay(phaseTickMs)
        }
    }



/* ================= Swipe helpers ================= */

    fun isHorizontalSwipe(dx: Float, dy: Float): Boolean =
        (abs(dx) - abs(dy)) >= axisDominanceMargin

    fun isVerticalSwipe(dx: Float, dy: Float): Boolean =
        (abs(dy) - abs(dx)) >= axisDominanceMargin

    /* ================= UI / INPUT ================= */

    var zone by remember { mutableStateOf(TouchZone.CENTER) }
    var downTime by remember { mutableStateOf(0L) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var swipeHandled by remember { mutableStateOf(false) }
    var tapCanceled by remember { mutableStateOf(false) }

    var nudgeStarted by remember { mutableStateOf(false) }
    var nudgePlus by remember { mutableStateOf(true) }

// ===== Status line (live HUD) =====
val statusText = remember(
    showStatusLineTap,
    activePresetName,
    activeTargetIp,
    activeTargetPort,
    oscInputPort,
    lastPongFromV,
    pongOk,
    rxOk,
    txOk,
    noPong,
    hardDown,
    heartbeatEnabled,
    heartbeatSuppressed
) {
    if (!showStatusLineTap) "" else {
        val link = when {
            !heartbeatEnabled -> when {
                rxOk -> "RX"
                txOk -> "TX"
                else -> "IDLE"
            }
            heartbeatSuppressed -> "HB PAUSED"
            pongOk -> "OK"
            rxOk -> "RX"
            noPong -> "NO PONG"
            hardDown -> "HARD DOWN"
            else -> "NO SIGNAL"
        }

        fun hostPort(host: String, port: Int): String {
            val h = host.trim()
            return if (h.contains(":") && !h.startsWith("[")) "[$h]:$port" else "$h:$port"
        }

        val header = "$activePresetName  $link"
        val lineI = "I:" + hostPort("0.0.0.0", oscInputPort)
        val lineP = "P:" + (lastPongFromV ?: "-")
        val lineO = "O:" + hostPort(activeTargetIp, activeTargetPort)

        buildString {
            append(header)
            append('\n'); append(lineI)
            append('\n'); append(lineP)
            append('\n'); append(lineO)
        }
    }
}

Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {

                    MotionEvent.ACTION_DOWN -> {
                        downTime = SystemClock.elapsedRealtime()
                        startX = event.x
                        startY = event.y
                        swipeHandled = false
                        tapCanceled = false
                        nudgeStarted = false
                        zone = if (event.x > rightZoneEdgePx) TouchZone.RIGHT_EDGE else if (event.x <= leftZoneEdgePx) TouchZone.LEFT else TouchZone.CENTER
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        val dxT = event.x - startX
                        val dyT = event.y - startY

// Right edge paging: swipe horizontally to switch pages (keeps center gestures safe)
if (!swipeHandled && zone == TouchZone.RIGHT_EDGE) {
    val absX = abs(dxT)
    val absY = abs(dyT)
    if (isHorizontalSwipe(dxT, dyT) && absX > swipeMinDist && absY < swipeMaxOffAxis) {
        swipeHandled = true
        if (dxT < 0) onPageNext?.invoke() else onPagePrev?.invoke()
        return@pointerInteropFilter true
    }
    // swallow vertical / small movements on the paging edge
    return@pointerInteropFilter true
}


                        if (!tapCanceled && (abs(dxT) > swipeArmDist || abs(dyT) > swipeArmDist)) {
                            tapCanceled = true
                        }

                        if (zone == TouchZone.LEFT) {
                            if (!nudgeStarted && abs(dyT) > nudgeArmDist) {
                                nudgeStarted = true
                                nudgePlus = (dyT < 0)

                                if (nudgePlus) action.nudgeRightStart() else action.nudgeLeftStart()
                                lightHaptic()
                                pushTimeline(TimelineKind.LOCAL_NUDGE)

                                val kind = if (nudgePlus) RippleKind.NUDGE_PLUS else RippleKind.NUDGE_MINUS

                                startRipple(
                                    voice = Voice.LOCAL,
                                    kind = kind,
                                    hold = false,
                                    nudgePlus = nudgePlus
                                )

                                localHoldArmJob?.cancel()
                                localHoldArmJob = scope.launch {
                                    delay(260L)
                                    if (nudgeStarted) {
                                        startRipple(
                                            voice = Voice.LOCAL,
                                            kind = kind,
                                            hold = true,
                                            nudgePlus = nudgePlus
                                        )
                                    }
                                }
                            }
                            return@pointerInteropFilter true
                        }

                        if (!swipeHandled && zone == TouchZone.CENTER) {
                            val absX = abs(dxT)
                            val absY = abs(dyT)

                            if (isVerticalSwipe(dxT, dyT) && absY > swipeMinDist && absX < swipeMaxOffAxis) {
                                swipeHandled = true
                                if (dyT < 0) {
                                    action.multiply()
                                    lightHaptic()
                                    pushTimeline(TimelineKind.LOCAL_MULTIPLY)
                                    startRipple(Voice.LOCAL, RippleKind.MULTIPLY, hold = false)
                                } else {
                                    action.divide()
                                    lightHaptic()
                                    pushTimeline(TimelineKind.LOCAL_DIVIDE)
                                    startRipple(Voice.LOCAL, RippleKind.DIVIDE, hold = false)
                                }
                                return@pointerInteropFilter true
                            }

                            if (isHorizontalSwipe(dxT, dyT) && absX > swipeMinDist && dxT < 0 && absY < swipeMaxOffAxis) {
                                swipeHandled = true
                                action.resync()
                                strongHaptic()
                                pushTimeline(TimelineKind.LOCAL_RESYNC)
                                startRipple(Voice.LOCAL, RippleKind.RESYNC, hold = false)
                                return@pointerInteropFilter true
                            }
                        }

                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {

                        if (zone == TouchZone.LEFT) {
                            if (nudgeStarted) {
                                action.nudgeStop()
                                pushTimeline(TimelineKind.LOCAL_NUDGE)
                            }
                            nudgeStarted = false

                            localHoldArmJob?.cancel()
                            localHoldArmJob = null

                            stopHoldRipple(Voice.LOCAL)
                            return@pointerInteropFilter true
                        }

                        val now = SystemClock.elapsedRealtime()
                        val heldLong = (now - downTime) >= longPressMs

                        if (zone == TouchZone.CENTER && !swipeHandled) {
                            val dxT = event.x - startX
                            val dyT = event.y - startY
                            val movedTooMuch = abs(dxT) > tapMaxMove || abs(dyT) > tapMaxMove

                            if (heldLong && !movedTooMuch) {
                                onLongPress()
                            } else if (!heldLong && !movedTooMuch) {
                                if (animationsEnabled && localVisualsEnabled && goblinFlashEnabled) goblinFlashTrigger++
                                action.tap()
                                lightHaptic()
                                pushTimeline(TimelineKind.LOCAL_TAP)
                                startRipple(Voice.LOCAL, RippleKind.TAP, hold = false)
                            }
                        }
                        true
                    }

                    else -> true
                }
            },
        contentAlignment = Alignment.Center
    ) {

        // GPU warmup layer (invisible): primes shaders for rings/ripples so first live events don't stutter.
        if (gpuWarmup) {
            GpuWarmupLayer(progress = gpuWarmupAnim.value)
        }

        // Mood overlay: super subtle background tint (online grin / offline grumble)
        if (moodsEnabled && moodT > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val a = (if (remoteOk) 0.028f else 0.018f) * moodT
                val c = if (remoteOk) GoblinOrange else GoblinDim
                drawRect(color = c.copy(alpha = safeAlpha(a)))
            }
        }

// NO SIGNAL hint (TapScreen only; no dimming)
        AnimatedVisibility(
            visible = hardDown || noPong || !hasSignal,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .zIndex(28f)
        ) {
            Text(
                text = when {
                    hardDown -> "HARD DOWN"
                    noPong -> "NO PONG"
                    else -> "NO SIGNAL"
                },
                color = GoblinDim.copy(alpha = 0.60f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

// ===== Status line (live HUD) — mouth-safe pill =====
if (showStatusLineTap && statusText.isNotEmpty()) {
    val cfg = LocalConfiguration.current
    val isRound = cfg.isScreenRound
    val minDp = min(cfg.screenWidthDp, cfg.screenHeightDp).dp
    val edgePad = if (isRound) 18.dp else 12.dp
    // Mouth-ish anchor: center + down a bit (round screens need a bigger safe offset)
    val y = if (isRound) (minDp * 0.18f) else 64.dp

    // Preflight lamps (live sanity): Pong / Inbound phase+downbeat / OSC out
    val nowHud = timelineNowMs
    val phaseOk = (lastPhaseRx > 0L && (nowHud - lastPhaseRx) <= preflightPhaseOkMs) ||
        (lastDownbeatRx > 0L && (nowHud - lastDownbeatRx) <= preflightDownbeatOkMs)

    val inboundColor = when {
        !heartbeatEnabled -> GoblinDim
        // If we see any inbound activity (pong or generic RX), don't paint it as dead.
        (pongOk || rxOk) && phaseOk -> Color(0xFF6DFF8F)
        (pongOk || rxOk) -> Color(0xFFFFD36D)
        else -> Color(0xFFFF6D6D)
    }

    val outColor = when (health) {
        is OscHealth.Error -> Color(0xFFFF6D6D)
        is OscHealth.Sending -> {
            val age = nowHud - (health as OscHealth.Sending).atMs
            if (age <= preflightOutOkMs) Color(0xFF6DFF8F) else GoblinDim
        }
        OscHealth.Idle -> GoblinDim
    }

    val c = when {
        !heartbeatEnabled -> GoblinDim
        pongOk -> Color(0xFF6DFF8F)
        rxOk -> Color(0xFFFFD36D)
        noPong -> Color(0xFFFFD36D)
        else -> Color(0xFFFF6D6D)
    }

    val warnTint = if (!statusbarAutoDimWarn) null else when {
        !heartbeatEnabled -> null
        hardDown -> Color(0xFFFF6D6D)
        noPong -> Color(0xFFFFD36D)
        inboundColor == Color(0xFFFF6D6D) -> Color(0xFFFF6D6D)
        inboundColor == Color(0xFFFFD36D) -> Color(0xFFFFD36D)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .offset(y = y)
            .padding(horizontal = edgePad)
            .zIndex(30f),
        contentAlignment = Alignment.Center
    ) {
Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
) {

        StatusPill(
            text = statusText,
            stateColor = c,
            warnTint = warnTint,
            rxPulseKey = maxOf(lastAnyRx, lastPong),
            modifier = Modifier.alpha(safe01(statusLineAlpha, 1f))
        )

            Spacer(modifier = Modifier.height(10.dp))

            if (showPreflightTap) {
            val minimal = (preflightMode == PreflightMode.MINIMAL)
            Row(
                modifier = Modifier
                    .padding(top = if (minimal) 6.dp else 8.dp)
                    .fillMaxWidth()
                    .alpha(safe01(preflightAlpha, 1f)),
                horizontalArrangement = Arrangement.Center
            ) {
                StatusLamp(
                    label = if (minimal) null else "P",
                    color = when {
                        !heartbeatEnabled -> GoblinDim
                        remoteOk -> Color(0xFF6DFF8F)
                        noPong -> Color(0xFFFFD36D)
                        else -> Color(0xFFFF6D6D)
                    }
                )
                Spacer(modifier = Modifier.width(if (minimal) 8.dp else 10.dp))
                StatusLamp(label = if (minimal) null else "IN", color = inboundColor)
                Spacer(modifier = Modifier.width(if (minimal) 8.dp else 10.dp))
                StatusLamp(label = if (minimal) null else "OUT", color = outColor)
            }
        }
    
        }
}
}

        Image(
            painter = painterResource(R.drawable.goblin),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(goblinBaseAlpha)
        )

        Image(
            painter = painterResource(R.drawable.goblin_flash),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(goblinFlashAlpha.value * goblinBaseAlpha * safe01(goblinFlashOpacity, 1f))
        )

        /* ================= UI overlays (text) ================= */

                // External BPM monitor (Resolume -> Watch):
        // Show ONLY the integer BPM, centered under the goblin "chin", with a smooth value animation.
        val extBpmTarget = (extBpmUi ?: 0.0).toFloat()
        val extBpmAnimated by animateFloatAsState(
            targetValue = extBpmTarget,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "extBpmAnim"
        )

        val bpmText = when {
            !remoteOk -> when {
                hardDown -> "HARD DOWN"
                noPong -> "NO PONG"
                else -> "NO SIGNAL"
            }
            extBpmUi != null -> {
                val bpmInt = extBpmAnimated.roundToInt()
                when (bpmFormat) {
                    BpmFormat.BPM_PHASE -> "$bpmInt BPM · ${(extPhase * 100f).roundToInt()}%"
                    BpmFormat.BPM_BAR -> "$bpmInt BPM · Bar $barCount"
                    else -> "$bpmInt BPM"
                }
            }
            else -> "–"
        }

        AnimatedVisibility(
            visible = showExternalBpm && (extBpmUi != null || !remoteOk || noPong || hardDown),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 101.dp)
                .zIndex(20f)
        ) {
            Text(
                text = bpmText,
                color = GoblinBrown.copy(alpha = (if (bpmOpacity.isFinite()) bpmOpacity else 1f).coerceIn(0f, 1f) * goblinBaseAlpha),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }


// Timeline lane (last few seconds): blips for transport / pong / osc out
if (showStatusLineTap && showTimelineTap) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = (-10).dp)
            .zIndex(25f)
            .alpha(safe01(timelineAlpha, 1f))
    ) {
        TimelineLane(
            nowMs = timelineNowMs,
            windowMs = timelineWindowMsSafe,
            events = timelineEvents,
            showLocal = timelineShowLocal,
            showRemote = timelineShowRemote,
            showHealth = timelineShowHealth,
            importantOnly = timelineImportantOnly,
            remoteAlpha = timelineRemoteAlpha
        )
    }
}

        // OSC Monitor: fullscreen, scrollable, close (X) bottom-center
        if (false && showOscDebug) { // disabled: legacy fullscreen OSC overlay blocks gestures (use DebugScreen OSC monitor)
            val dbg by oscDebugState.collectAsState(initial = OscInputReceiver.OscDebugState())
            val scroll = rememberScrollState()
            val isRound = LocalConfiguration.current.isScreenRound
            val edgePad = if (isRound) 18.dp else 12.dp
            val topPad = if (isRound) 14.dp else 12.dp
            val bottomPad = if (isRound) 16.dp else 12.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(60f)
                    // swallow touches so tap gestures don't leak through
                    .pointerInteropFilter { true }
                    .background(Color(0xF0000000))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = edgePad)
                        .padding(top = topPad, bottom = bottomPad + 72.dp)
                        .verticalScroll(scroll),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "OSC Monitor",
                        color = GoblinText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    @Composable
                    fun row(label: String, value: String) {
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(Color(0x22000000))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(label, color = GoblinDim, fontSize = 11.sp)
                            Text(
                                value,
                                color = GoblinText,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    row("Packets", dbg.packetsTotal.toString())
                    row("Last address", dbg.lastAddress ?: "-")
                    row("Args", dbg.lastArgs ?: "-")
                    row("External BPM", dbg.externalBpm?.let { "%.2f".format(it) } ?: "-")
                    row("Tempo raw", dbg.externalTempoRaw?.let { "%.4f".format(it) } ?: "-")
                    row("Confidence", dbg.externalConfidence?.let { "%.2f".format(it) } ?: "-")
                    row("Phase", dbg.externalPhase?.let { "%.3f".format(it) } ?: "-")

                    val age = dbg.externalDownbeatMs?.let { ms ->
                        val a = (SystemClock.elapsedRealtime() - ms).coerceAtLeast(0L)
                        "${a}ms"
                    } ?: "-"
                    row("Downbeat age", age)
                }

                BottomCloseCircleButton(
                    onClick = { onCloseOscMonitor?.invoke() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPad)
                )
            }
        }

        /* ================= OSC pulse dot ================= */


        if (animationsEnabled && showOscDot) {
            val radiusTouch = min(widthPx, heightPx) / 2f
            Canvas(
                modifier = Modifier
                    .size((oscPulseRadiusDpSafe * 2f).dp)
                    .offset(
                        x = (radiusTouch * 0.39f).dp,
                        y = (radiusTouch * 0.26f).dp
                    )
                    .zIndex(18f)
            ) {
                drawCircle(
                    color = oscDotColor,
                    alpha = (((0.18f + (if (oscPulse.value.isFinite()) oscPulse.value else 0f) * 0.82f) )
                        * oscPulseOpacitySafe * (if (oscDotOpacity.isFinite()) oscDotOpacity else 1f)).coerceIn(0f, 1f)
                )
            }
        }

        /* ================= Remote Ghost Mode (clarified coding) ================= */

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(7f)
        ) {
            if (!animationsEnabled) return@Canvas
            if (!remoteAnimationsEnabled) return@Canvas
            if (!remoteGhostModeEnabled) return@Canvas
            if (!hasSignal) return@Canvas
            val g = ghostUi ?: return@Canvas

            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(size.width, size.height) * 0.70f / 2f

            val nowMs = SystemClock.elapsedRealtime()
            val stale = (nowMs - g.lastSeenMs) > 1500L
            val locked = !stale

            val baseAlpha = safeAlpha((if (stale) 0.06f else if (locked) 0.18f else 0.12f) * remoteGhostOpacitySafe)
            val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)

            drawCircle(
                color = GoblinBrown.copy(alpha = baseAlpha),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 8f, pathEffect = dash, cap = StrokeCap.Round)
            )

            // lock marker at 12 o'clock
            if (locked) {
                val mx = cx
                val my = cy - radius
                drawCircle(
                    color = GoblinBrown.copy(alpha = safeAlpha(0.35f * remoteGhostOpacitySafe)),
                    radius = 10f,
                    center = Offset(mx, my)
                )
            }
        }

        /* ================= Ripples (local + remote) ================= */

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(8f)
        ) {
            if (!animationsEnabled) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f

            val maxRadius = min(size.width, size.height) * 0.90f / 2f
            val overscan = maxRadius * 1.12f

            fun thicknessPxFor(kind: RippleKind?): Float {
                val dp = when (kind) {
                    RippleKind.TAP -> rippleTapThicknessDpSafe
                    RippleKind.MULTIPLY, RippleKind.DIVIDE -> rippleMultDivThicknessDpSafe
                    RippleKind.RESYNC -> rippleResyncThicknessDpSafe
                    RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> rippleNudgeThicknessDpSafe
                    null -> rippleTapThicknessDpSafe
                }
                return dpToPx(dp).coerceAtLeast(1f)
            }

            val ghostEchoThicknessPx = dpToPx(ghostEchoThicknessDpSafe).coerceAtLeast(1f)

            fun kindEnabled(kind: RippleKind): Boolean = when (kind) {
                RippleKind.TAP -> rippleTapEnabled
                RippleKind.MULTIPLY, RippleKind.DIVIDE -> rippleMultDivEnabled
                RippleKind.RESYNC -> rippleResyncEnabled
                RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> rippleNudgeEnabled
            }

            fun kindOpacity(kind: RippleKind): Float = when (kind) {
                RippleKind.TAP -> rippleTapOpacitySafe
                RippleKind.MULTIPLY, RippleKind.DIVIDE -> rippleMultDivOpacitySafe
                RippleKind.RESYNC -> rippleResyncOpacitySafe
                RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> rippleNudgeOpacitySafe
            }

            fun intensityFor(voice: Voice, kind: RippleKind): Float {
                if (!kindEnabled(kind)) return 0f

                val base = when (voice) {
                    Voice.LOCAL -> 0.35f
                    Voice.REMOTE -> if (remoteGhostModeEnabled) 0.18f else 0.36f
                }

                val voiceMul = when (voice) {
                    Voice.LOCAL -> 1.0f
                    Voice.REMOTE -> if (remoteGhostModeEnabled) remoteGhostOpacitySafe else 1.0f
                }

                val mult = when (kind) {
                    RippleKind.TAP -> 1.00f
                    RippleKind.MULTIPLY -> 0.85f
                    RippleKind.DIVIDE -> 0.80f
                    RippleKind.RESYNC -> 1.20f
                    RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> 0.75f
                }

                return base * voiceMul * mult * kindOpacity(kind)
            }

            fun fadeLate(p: Float): Float {
                val t = ((p - 0.80f) / 0.20f).coerceIn(0f, 1f)
                return 1f - (t * t)
            }

            fun smooth(p: Float): Float = (p * p * (3f - 2f * p))

            fun drawRipple(voice: Voice, kind: RippleKind?, p: Float) {
                if (kind == null || p <= 0f) return
                if (!kindEnabled(kind)) return

                // Direction rules:
                // LOCAL:
                //   TAP/MULTIPLY/NUDGE+ = center -> edge (grow)
                //   DIVIDE/NUDGE- = outside -> center (shrink)
                //   RESYNC = multi ripple center -> edge
                //
                // REMOTE: invert direction (except for nudges)
                val invert = (voice == Voice.REMOTE)

                fun isGrowLocal(k: RippleKind): Boolean = when (k) {
                    RippleKind.TAP, RippleKind.MULTIPLY, RippleKind.NUDGE_PLUS, RippleKind.RESYNC -> true
                    RippleKind.DIVIDE, RippleKind.NUDGE_MINUS -> false
                }

                val allowInvert = kind != RippleKind.NUDGE_PLUS && kind != RippleKind.NUDGE_MINUS
                val grow = if (invert && allowInvert) !isGrowLocal(kind) else isGrowLocal(kind)

                val strokeW = thicknessPxFor(kind)
                val alpha = safeAlpha(intensityFor(voice, kind) * fadeLate(p))

                // RESYNC: triple ripple for local, single for remote ghost
                if (kind == RippleKind.RESYNC) {
                    val offsets = if (voice == Voice.REMOTE && remoteGhostModeEnabled) listOf(0.0f) else listOf(0.0f, 0.14f, 0.28f)
                    val intens = if (voice == Voice.REMOTE && remoteGhostModeEnabled) listOf(1.0f) else listOf(1.0f, 0.72f, 0.52f)

                    for (i in offsets.indices) {
                        val pi = (p - offsets[i]).coerceIn(0f, 1f)
                        if (pi <= 0f) continue
                        val a = (alpha * intens[i]).coerceIn(0f, 1f)
                        val r = if (grow) maxRadius * pi else overscan * (1f - pi)
                        drawCircle(
                            color = GoblinBrown.copy(alpha = a),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                    return
                }

                val pr = smooth(p)
                val radius = if (grow) maxRadius * pr else overscan * (1f - pr)

                drawCircle(
                    color = GoblinBrown.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Ghost Echo: secondary, delayed ripple trail (visual-only)
                if (ghostEchoOn) {
                    val pe = (p - 0.22f).coerceIn(0f, 1f)
                    if (pe > 0f) {
                        val pr2 = smooth(pe)
                        val r2 = if (grow) maxRadius * pr2 else overscan * (1f - pr2)
                        val a2 = safeAlpha(alpha * ghostEchoOpacitySafe * ghostEchoStrengthSafe * 0.55f * fadeLate(pe))
                        drawCircle(
                            color = GoblinBrown.copy(alpha = a2),
                            radius = r2,
                            center = Offset(cx, cy),
                            style = Stroke(width = ghostEchoThicknessPx, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            drawRipple(Voice.LOCAL, localKind, localRipple.value)
            drawRipple(Voice.REMOTE, remoteKind, remoteRipple.value)
        }

        /* ================= Phase Visualizer + Spiral ================= */

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(9f)
        ) {
            if (!animationsEnabled) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseRadius = min(size.width, size.height) * 0.78f / 2f

            if (!hasSignal) return@Canvas

            val phaseDraw = applySwingWarp(extPhase, swingAmount)
            val stableT = ((stability - 0.80f) / 0.20f).coerceIn(0f, 1f)

            // Mood: tiny center glow
            if (moodsEnabled && moodT > 0f) {
                val a = safeAlpha(0.045f * moodT)
                if (a > 0f) {
                    drawCircle(
                        color = GoblinOrange.copy(alpha = a),
                        radius = baseRadius * 0.55f,
                        center = Offset(cx, cy)
                    )
                }
            }

            // Aura: only when stable
            if (phaseAuraEnabled && !startupWarmup && stableT > 0f) {
                val a = safeAlpha(0.075f * stableT * phaseAuraOpacitySafe * (1f + 0.30f * moodT))
                val w = phaseAuraThicknessPx
                drawCircle(
                    color = GoblinOrange.copy(alpha = a),
                    radius = baseRadius * 1.02f,
                    center = Offset(cx, cy),
                    style = Stroke(width = w)
                )
            }

            // Micro particles: subtle sparkle on the ring (stable only)
            if (microParticlesEnabled && !startupWarmup && stableT > 0f) {
                val n = (6 + (6 * stableT)).toInt().coerceIn(6, 12)
                val baseA = safeAlpha(0.14f * stableT * microParticlesOpacitySafe)
                val baseR = baseRadius * 0.96f
                val t = phaseDraw
                for (i in 0 until n) {
                    val theta = (t.toDouble() * 2.0 * PI) + (i.toDouble() * 2.0 * PI / n.toDouble()) + (sin((t * 2f * PI.toFloat()) * (i + 1) * 0.7f).toDouble() * 0.22)
                    val wob = sin((t * 2f * PI.toFloat()) * (i + 1) * 1.3f).toFloat()
                    val r = baseR + wob * (baseRadius * 0.03f)
                    val x = cx + cos(theta).toFloat() * r
                    val y = cy + sin(theta).toFloat() * r
                    drawCircle(
                        color = GoblinOrange.copy(alpha = baseA * 0.55f),
                        radius = 2.0f + 1.6f * stableT,
                        center = Offset(x, y)
                    )
                }
            }
            if (phaseVisualizerEnabled && !startupWarmup) {
                val strokeW = phaseRingThicknessPx.coerceAtLeast(1f)
                val ringT = (0.55f + 0.45f * stableT).coerceIn(0f, 1f)

                // base ring (subtle)
                drawCircle(
                    color = GoblinBrown.copy(alpha = safeAlpha(0.16f * ringT * phaseRingOpacitySafe)),
                    radius = baseRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeW)
                )

                // downbeat marker (12 o'clock)
                val markerLen = 22f
                drawLine(
                    color = GoblinOrange.copy(alpha = safeAlpha(0.58f * ringT * phaseRingOpacitySafe)),
                    start = Offset(cx, cy - baseRadius - markerLen),
                    end = Offset(cx, cy - baseRadius + markerLen),
                    strokeWidth = (strokeW * 0.70f).coerceAtLeast(2f),
                    cap = StrokeCap.Round
                )

                // phase dot
                val a = (phaseDraw.toDouble() * 2.0 * PI) - (PI / 2.0)
                val px = cx + cos(a).toFloat() * baseRadius
                val py = cy + sin(a).toFloat() * baseRadius
                drawCircle(
                    color = GoblinOrange.copy(alpha = safeAlpha(0.72f * ringT * phaseRingOpacitySafe)),
                    radius = (strokeW * 1.15f).coerceIn(6f, 14f),
                    center = Offset(px, py)
                )
            }

            if (phaseSpiralEnabled && !startupWarmup && stableT > 0f) {
                // A real spiral (Archimedean). It breathes with stability and rotates with phase.
                val turns = 2.35f + (1f - stability) * 0.55f
                val r0 = baseRadius * 0.22f
                val r1 = baseRadius * 0.98f
                val wobble = (1f - stability) * (baseRadius * 0.07f)

                val a0 = (phaseDraw.toDouble() * 2.0 * PI) - (PI / 2.0)
                val steps = 320
                val path = Path()

                for (i in 0..steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val theta = a0 + (t * turns * 2.0 * PI)
                    val wave = sin(theta * 3.0 + a0).toFloat()
                    val r = (r0 + (r1 - r0) * t) + wave * wobble
                    val x = cx + cos(theta).toFloat() * r
                    val y = cy + sin(theta).toFloat() * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                val aBase = safeAlpha(0.12f * stableT * phaseSpiralOpacitySafe)
                val aHi = safeAlpha(0.20f * stableT * phaseSpiralOpacitySafe)
                val wBase = (phaseSpiralThicknessPx * 1.2f).coerceAtLeast(1f)
                val wHi = (phaseSpiralThicknessPx * 0.55f).coerceAtLeast(1f)

                drawPath(
                    path = path,
                    color = GoblinBrown.copy(alpha = aBase),
                    style = Stroke(width = wBase, cap = StrokeCap.Round)
                )

                drawPath(
                    path = path,
                    color = GoblinOrange.copy(alpha = aHi),
                    style = Stroke(width = wHi, cap = StrokeCap.Round)
                )
            }

        }

        /* ================= Downbeat pulse ring ================= */

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val safeRadius = min(size.width, size.height) * 0.82f / 2f

            if (!hasSignal) return@Canvas
            if (!animationsEnabled) return@Canvas
            if (!showDownbeatIndicator) return@Canvas

            val dbOp = (if (downbeatOpacity.isFinite()) downbeatOpacity else 1f).coerceIn(0f, 1f)
            val a = safeAlpha(0.28f * downbeatPulse.value * dbOp)

            when (downbeatStyle) {
                DownbeatStyle.DOT -> {
                    drawCircle(
                        color = GoblinBrown.copy(alpha = (a * 1.2f).coerceIn(0f, 1f)),
                        radius = 10f + downbeatPulse.value * 6f,
                        center = Offset(cx, cy - safeRadius + 22f)
                    )
                }
                DownbeatStyle.TICK -> {
                    val y = cy - safeRadius + 18f
                    drawLine(
                        color = GoblinBrown.copy(alpha = (a * 1.2f).coerceIn(0f, 1f)),
                        start = Offset(cx - 22f, y),
                        end = Offset(cx + 22f, y),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
                DownbeatStyle.PULSE -> {
                    drawCircle(
                        color = GoblinBrown.copy(alpha = a),
                        radius = safeRadius * (1.0f + downbeatPulse.value * 0.06f),
                        center = Offset(cx, cy),
                        style = Stroke(width = 14f)
                    )
                }
            }
        }

        if (showBpm) {
            Text(
                text = "%.1f".format(bpm),
                color = GoblinBrown,
                modifier = Modifier.zIndex(11f)
            )
        }
    }
}

@Composable
private fun GpuWarmupLayer(progress: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.0012f) // must be > 0 so it actually draws (shader warmup)
            .zIndex(-1f)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.82f / 2f

        val strokeThin = 2.5.dp.toPx()
        val strokeMid = 6.dp.toPx()
        val strokeThick = 14.dp.toPx()

        val p = progress.coerceIn(0f, 1f)

        // Touch both fill + stroke pipelines (rect + circles + line + path).
        drawRect(color = GoblinDim.copy(alpha = 0.02f))

        // Ring-ish stroke (matches Downbeat PULSE thickness).
        drawCircle(
            color = GoblinBrown.copy(alpha = 0.03f),
            radius = r * (0.88f + 0.04f * p),
            center = Offset(cx, cy),
            style = Stroke(width = strokeThick, cap = StrokeCap.Round)
        )

        // Ripple-ish strokes (thin + mid).
        drawCircle(
            color = GoblinOrange.copy(alpha = 0.028f),
            radius = r * (0.55f + 0.30f * p),
            center = Offset(cx, cy),
            style = Stroke(width = strokeMid, cap = StrokeCap.Round)
        )
        drawCircle(
            color = GoblinOrange.copy(alpha = 0.018f),
            radius = r * (0.30f + 0.35f * (1f - p)),
            center = Offset(cx, cy),
            style = Stroke(width = strokeThin, cap = StrokeCap.Round)
        )

        // A line to warm cap rendering (DownbeatStyle.TICK).
        val y = cy - r * 0.78f
        drawLine(
            color = GoblinBrown.copy(alpha = 0.02f),
            start = Offset(cx - r * 0.18f, y),
            end = Offset(cx + r * 0.18f, y),
            strokeWidth = strokeMid,
            cap = StrokeCap.Round
        )

        // A small path to warm path rendering (spiral/highlights later).
        val path = Path().apply {
            val px0 = cx - r * 0.35f
            val py0 = cy + r * 0.05f
            moveTo(px0, py0)
            cubicTo(
                cx - r * 0.10f, cy - r * 0.25f,
                cx + r * 0.20f, cy + r * 0.25f,
                cx + r * 0.35f, cy - r * 0.05f
            )
        }
        drawPath(
            path = path,
            color = GoblinBrown.copy(alpha = 0.02f),
            style = Stroke(width = strokeThin, cap = StrokeCap.Round)
        )
    }
}