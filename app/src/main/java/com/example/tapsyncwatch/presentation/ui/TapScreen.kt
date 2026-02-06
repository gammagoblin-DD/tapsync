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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB3000000))
            .border(1.dp, stateColor.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = stateColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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
    showStatusLine: Boolean,
    activePresetName: String,
    activeTargetIp: String,
    activeTargetPort: Int,
    showBpm: Boolean,
    bpm: Double,

    // Link health (Heartbeat Pong)
    lastPongMs: StateFlow<Long>,
    heartbeatEnabled: Boolean,
    signalGraceMs: Long,

    // UI-only monitors
    showExternalBpm: Boolean,
    externalBpm: StateFlow<Double?>,
    externalConfidence: StateFlow<Float?>,
    showOscDebug: Boolean,
    oscDebugState: StateFlow<OscInputReceiver.OscDebugState>,
    remoteGhost: StateFlow<OscInputReceiver.RemoteGhostSnapshot?>,
    phaseVisualizerEnabled: Boolean,
    phaseSpiralEnabled: Boolean,
    fxAlpha: Float = 1.0f,
    phaseAlpha: Float = 1.0f,
    ghostAlpha: Float = 1.0f,

    // UI motion
    animationsEnabled: Boolean,
    remoteAnimationsEnabled: Boolean,
    remoteGhostModeEnabled: Boolean,
    goblinFlashEnabled: Boolean,
    rippleEnabled: Boolean,
    oscPulseEnabled: Boolean,

    action: ActionEngine,
    oscHealth: StateFlow<OscHealth>,
    externalClockActivity: Flow<Unit>,
    externalTransportIn: Flow<TransportFeedback>,
    clockVisualState: StateFlow<ClockVisualState>,
    externalActivity: Flow<Unit>,
    clockMode: ClockMode,
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
    val fxMul = (if (fxAlpha.isFinite()) fxAlpha else 1.0f).coerceIn(0.30f, 2.00f)
    val phaseMul = (if (phaseAlpha.isFinite()) phaseAlpha else 1.0f).coerceIn(0.30f, 2.00f)
    val ghostMul = (if (ghostAlpha.isFinite()) ghostAlpha else 1.0f).coerceIn(0.30f, 2.00f)


// Safety helpers: avoid NaN/Infinity bricking Canvas (coerceIn does NOT fix NaN)
fun safeAlpha(a: Float): Float = if (a.isFinite()) a.coerceIn(0f, 1f) else 0f
fun safe01(v: Float, default: Float = 0f): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else default
fun safePhase(v: Float, default: Float = 0f): Float {
    if (!v.isFinite()) return default
    // wrap to [0..1)
    val m = v % 1f
    val w = if (m < 0f) m + 1f else m
    return w.coerceIn(0f, 1f)
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

    val extBpm by externalBpm.collectAsState()
    val extConf by externalConfidence.collectAsState()
    val dbg by oscDebugState.collectAsState()
    val ghost by remoteGhost.collectAsState()

    /* ================= OSC pulse dot ================= */

    val oscPulse = remember { Animatable(0f) }

    fun pulseOsc() {
        if (!animationsEnabled) return
        if (!oscPulseEnabled) return
        if (!oscPulseLimiter.allow()) return

        scope.launch {
            oscPulse.snapTo(1f)
            oscPulse.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
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
        goblinFlashAlpha.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
        goblinFlashAlpha.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
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
        if (voice == Voice.REMOTE && !remoteAnimationsEnabled) return

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

    // Remote transport in (Resolume -> Watch): NO goblin flash
    LaunchedEffect(remoteAnimationsEnabled, animationsEnabled, rippleEnabled, remoteGhostModeEnabled) {
        externalTransportIn.collect { fb ->
            pulseOsc()
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

// Heartbeat-based link state (do NOT depend on BPM updates)
val lastPong by lastPongMs.collectAsState()
var signalNowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

// Keep signal freshness updated at low frequency (avoid 30Hz recomposition in Settings/idle cases)
LaunchedEffect(heartbeatEnabled) {
    if (!heartbeatEnabled) return@LaunchedEffect
    while (isActive) {
        signalNowMs = SystemClock.elapsedRealtime()
        delay(500L)
    }
}

// Phase ticker cadence (watch-friendly). Only fast when phase visuals are on.
val phaseTickMs: Long = remember(phaseVisualizerEnabled, phaseSpiralEnabled, downbeatHapticsEnabled) {
    when {
        phaseVisualizerEnabled || phaseSpiralEnabled -> 33L   // ~30Hz
        downbeatHapticsEnabled -> 60L                         // keep wrap detection reliable
        else -> 120L
    }
}

val hasSignal = remember(heartbeatEnabled, lastPong, signalNowMs, signalGraceMs) {
    if (!heartbeatEnabled) true
    else lastPong > 0L && (signalNowMs - lastPong) <= signalGraceMs
}

    val goblinBaseAlphaTarget = if (hasSignal) 1f else 0.35f
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
    var lastDownbeatTriggerMs by remember { mutableStateOf(0L) }

    LaunchedEffect(hasSignal, extBpm, ghost, extConf) {
        if (!hasSignal) {
            extPhase = 0f
            stability = 1f
            prevPhase = 0f
            return@LaunchedEffect
        }

        val bpmForPhase = (ghost?.bpm ?: extBpm) ?: return@LaunchedEffect
        val bpmF = bpmForPhase.toFloat().coerceIn(1f, 999f)

        while (isActive) {
            val now = SystemClock.elapsedRealtime()
            val anchorMs = (ghost?.lastSeenMs ?: now).coerceAtMost(now)
            val anchorPhase = safePhase(ghost?.phase ?: extPhase, default = 0f)

            val dtSec = (now - anchorMs).coerceAtLeast(0).toFloat() / 1000f
            val beats = dtSec * (bpmF / 60f)
            val p = ((anchorPhase + beats) % 1f)
            val pSafe = safePhase(p, default = 0f)


            // Downbeat: detect phase wrap
            val wrapped = (prevPhase > 0.80f && pSafe < 0.20f)
            if (wrapped && (now - lastDownbeatTriggerMs) > 250L) {
                lastDownbeatTriggerMs = now

                if (animationsEnabled) {
                    // animate pulse without blocking the ticker
                    scope.launch {
                        downbeatPulse.snapTo(1f)
                        downbeatPulse.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                    }
                }

                if (hapticsEnabled && downbeatHapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            prevPhase = pSafe
            extPhase = pSafe

            val conf = safe01((ghost?.confidence ?: extConf ?: 1f), default = 1f)
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
val statusText = remember(showStatusLine, activePresetName, activeTargetIp, activeTargetPort, hasSignal, heartbeatEnabled) {
    if (!showStatusLine) "" else {
        val link = when {
            !heartbeatEnabled -> "HB OFF"
            hasSignal -> "OK"
            else -> "NO SIGNAL"
        }
        "$activePresetName  $activeTargetIp:$activeTargetPort  $link"
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
                                    startRipple(Voice.LOCAL, RippleKind.MULTIPLY, hold = false)
                                } else {
                                    action.divide()
                                    lightHaptic()
                                    startRipple(Voice.LOCAL, RippleKind.DIVIDE, hold = false)
                                }
                                return@pointerInteropFilter true
                            }

                            if (isHorizontalSwipe(dxT, dyT) && absX > swipeMinDist && dxT < 0 && absY < swipeMaxOffAxis) {
                                swipeHandled = true
                                action.resync()
                                strongHaptic()
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
                                if (animationsEnabled && goblinFlashEnabled) goblinFlashTrigger++
                                action.tap()
                                lightHaptic()
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

// ===== Status line (live HUD) — mouth-safe pill =====
if (showStatusLine && statusText.isNotEmpty()) {
    val cfg = LocalConfiguration.current
    val isRound = cfg.isScreenRound
    val minDp = min(cfg.screenWidthDp, cfg.screenHeightDp).dp
    val edgePad = if (isRound) 18.dp else 12.dp
    // Mouth-ish anchor: center + down a bit (round screens need a bigger safe offset)
    val y = if (isRound) (minDp * 0.18f) else 64.dp

    val c = when {
        !heartbeatEnabled -> GoblinDim
        hasSignal -> Color(0xFF6DFF8F)
        else -> Color(0xFFFF6D6D)
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
        StatusPill(text = statusText, stateColor = c)
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
                .alpha(goblinFlashAlpha.value * goblinBaseAlpha)
        )

        /* ================= UI overlays (text) ================= */

                // External BPM monitor (Resolume -> Watch):
        // Show ONLY the integer BPM, centered under the goblin "chin", with a smooth value animation.
        val extBpmTarget = (extBpm ?: 0.0).toFloat()
        val extBpmAnimated by animateFloatAsState(
            targetValue = extBpmTarget,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "extBpmAnim"
        )

        AnimatedVisibility(
            visible = showExternalBpm && (extBpm != null || !hasSignal),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 101.dp)
                .zIndex(20f)
        ) {
            Text(
                text = if (!hasSignal) "NO SIGNAL" else extBpmAnimated.roundToInt().toString(),
                color = GoblinBrown,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // OSC Monitor: fullscreen, scrollable, close (X) bottom-center
        if (showOscDebug) {
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


        if (showOscDot) {
            val radiusTouch = min(widthPx, heightPx) / 2f
            Canvas(
                modifier = Modifier
                    .size(10.dp)
                    .offset(
                        x = (radiusTouch * 0.39f).dp,
                        y = (radiusTouch * 0.26f).dp
                    )
                    .zIndex(18f)
            ) {
                drawCircle(
                    color = oscDotColor,
                    alpha = ((0.18f + (if (oscPulse.value.isFinite()) oscPulse.value else 0f) * 0.82f) * fxMul).coerceIn(0f, 1f)
                )
            }
        }

        /* ================= Remote Ghost Mode (clarified coding) ================= */

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(7f)
        ) {
            if (!remoteGhostModeEnabled) return@Canvas
            if (!hasSignal) return@Canvas
            val g = ghost ?: return@Canvas

            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(size.width, size.height) * 0.70f / 2f

            val nowMs = SystemClock.elapsedRealtime()
            val stale = (nowMs - g.lastSeenMs) > 1500L
            val locked = !stale

            val baseAlpha = (if (stale) 0.06f else if (locked) 0.18f else 0.12f) * ghostMul
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
                    color = GoblinBrown.copy(alpha = safeAlpha(0.35f * ghostMul)),
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
            val cx = size.width / 2f
            val cy = size.height / 2f

            val maxRadius = min(size.width, size.height) * 0.90f / 2f
            val overscan = maxRadius * 1.12f
            val stroke = 14f

            fun intensityFor(voice: Voice, kind: RippleKind): Float {
                val base = when (kind) {
                    RippleKind.TAP -> 0.35f
                    RippleKind.MULTIPLY, RippleKind.DIVIDE -> 0.55f
                    RippleKind.RESYNC -> 0.60f
                    RippleKind.NUDGE_PLUS, RippleKind.NUDGE_MINUS -> 0.45f
                }
                return if (voice == Voice.LOCAL) {
                    base * fxMul
                } else {
                    val remoteBase = if (remoteGhostModeEnabled) base * 0.18f else base * 0.35f
                    remoteBase * fxMul * ghostMul
                }
            }

            fun drawRipple(voice: Voice, kind: RippleKind?, p: Float) {
                if (kind == null || p <= 0f) return

                val invert = (voice == Voice.REMOTE)

                fun isGrowLocal(k: RippleKind): Boolean = when (k) {
                    RippleKind.TAP, RippleKind.MULTIPLY, RippleKind.NUDGE_PLUS, RippleKind.RESYNC -> true
                    RippleKind.DIVIDE, RippleKind.NUDGE_MINUS -> false
                }

                val allowInvert = kind != RippleKind.NUDGE_PLUS && kind != RippleKind.NUDGE_MINUS
                val grow = if (invert && allowInvert) !isGrowLocal(kind) else isGrowLocal(kind)

                fun fadeLate(p: Float): Float {
                    val t = ((p - 0.80f) / 0.20f).coerceIn(0f, 1f)
                    return 1f - (t * t)
                }

                val alpha = (intensityFor(voice, kind) * fadeLate(p)).coerceIn(0f, 1f)

                val strokeW = if (voice == Voice.REMOTE && remoteGhostModeEnabled) 10f else stroke

                if (kind == RippleKind.RESYNC) {

                    // LOCAL = 3 rings. REMOTE ghost = 1 ring (calmer, reads as "external")
                    val offsets = if (voice == Voice.REMOTE && remoteGhostModeEnabled)
                        listOf(0.0f)
                    else
                        listOf(0.0f, 0.14f, 0.28f)

                    val intens = if (voice == Voice.REMOTE && remoteGhostModeEnabled)
                        listOf(1.0f)
                    else
                        listOf(1.0f, 0.72f, 0.52f)

                    for (i in offsets.indices) {
                        val pi = (p - offsets[i]).coerceIn(0f, 1f)
                        if (pi <= 0f) continue

                        val a = alpha * intens[i]
                        val r = if (grow) maxRadius * pi else overscan * (1f - pi)

                        drawCircle(
                            color = GoblinBrown.copy(alpha = a),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = strokeW)
                        )
                    }
                    return
                }

                val pr = (p * p * (3f - 2f * p))
                val radius = if (grow) maxRadius * pr else overscan * (1f - pr)

                drawCircle(
                    color = GoblinBrown.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeW)
                )
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
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseRadius = min(size.width, size.height) * 0.78f / 2f

            if (!hasSignal) return@Canvas

            if (phaseVisualizerEnabled) {
                val strokeW = 10f

                // base ring (very subtle)
                drawCircle(
                    color = GoblinBrown.copy(alpha = safeAlpha(0.16f * phaseMul)),
                    radius = baseRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeW)
                )

                // downbeat marker (12 o'clock)
                val markerLen = 22f
                drawLine(
                    color = GoblinOrange.copy(alpha = safeAlpha(0.58f * phaseMul)),
                    start = Offset(cx, cy - baseRadius - markerLen),
                    end = Offset(cx, cy - baseRadius + markerLen),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )

                // phase dot
                val a = (extPhase.toDouble() * 2.0 * PI) - (PI / 2.0)
                val px = cx + cos(a).toFloat() * baseRadius
                val py = cy + sin(a).toFloat() * baseRadius
                drawCircle(
                    color = GoblinOrange.copy(alpha = safeAlpha(0.72f * phaseMul)),
                    radius = 12f,
                    center = Offset(px, py)
                )
            }

if (phaseSpiralEnabled) {
    // A real spiral (Archimedean). It "breathes" with stability and rotates with phase.
    val turns = 2.35f + (1f - stability) * 0.55f
    val r0 = baseRadius * 0.22f
    val r1 = baseRadius * 0.98f
    val wobble = (1f - stability) * (baseRadius * 0.07f)

    val a0 = (extPhase.toDouble() * 2.0 * PI) - (PI / 2.0)
    val steps = 320
    val path = Path()

    for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val theta = a0 + (t * turns * 2.0 * PI)
        val wave = sin(theta * 3.0 + a0).toFloat()
        val r = (r0 + (r1 - r0) * t) + wobble * wave
        val x = cx + cos(theta).toFloat() * r
        val y = cy + sin(theta).toFloat() * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    val aBase = ((0.22f + (1f - stability) * 0.22f) * phaseMul).coerceIn(0f, 1f)
val aHi = ((0.10f + (1f - stability) * 0.10f) * phaseMul).coerceIn(0f, 1f)

// Body (darker) - thinner so it reads as spiral, not ring
drawPath(
    path = path,
    color = GoblinBrown.copy(alpha = aBase),
    style = Stroke(width = 7f, cap = StrokeCap.Round)
)
// Highlight (orange) - very subtle edge
drawPath(
    path = path,
    color = GoblinOrange.copy(alpha = aHi),
    style = Stroke(width = 3f, cap = StrokeCap.Round)
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

            drawCircle(
                color = GoblinBrown.copy(alpha = safeAlpha(0.28f * downbeatPulse.value * fxMul)),
                radius = safeRadius * (1.0f + downbeatPulse.value * 0.06f),
                center = Offset(cx, cy),
                style = Stroke(width = 14f)
            )
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