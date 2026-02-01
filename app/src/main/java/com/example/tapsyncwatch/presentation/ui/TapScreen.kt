@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.example.tapsyncwatch.presentation.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.tapsyncwatch.R
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.domain.clock.ClockVisualState
import com.example.tapsyncwatch.domain.transport.TransportFeedback
import com.example.tapsyncwatch.osc.OscHealth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/* ================= GOBLIN STYLE ================= */

private val GoblinBrown = Color(0xFF8C5A2B)

private enum class TouchZone { CENTER, LEFT }
private enum class Voice { LOCAL, REMOTE }

private enum class RippleKind {
    TAP,
    MULTIPLY,
    DIVIDE,
    RESYNC,
    NUDGE_PLUS,
    NUDGE_MINUS
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

@Composable
fun TapScreen(
    showOscDot: Boolean,
    showBpm: Boolean,
    bpm: Double,

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
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val metrics = context.resources.displayMetrics
    val scope = rememberCoroutineScope()
    val oscPulseLimiter = remember { PulseLimiter(minIntervalMs = 120) }
    val remoteTransportLimiter = remember { PulseLimiter(minIntervalMs = 180) }

    fun uiHapticAllowed(): Boolean =
        clockMode == ClockMode.INTERNAL && hapticsEnabled

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

    val longPressMs = 600L
    val swipeMinDist = 70f
    val swipeMaxOffAxis = 30f
    val axisDominanceMargin = 20f
    val swipeArmDist = 12f
    val tapMaxMove = 10f
    val nudgeArmDist = 12f

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
                    // ghost: kein Hold, nur eine ruhige One-shot (Reads: external)
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

    /* ================= CLOCK VISUAL ================= */

    val health by oscHealth.collectAsState()
    val oscDotColor = when (health) {
        is OscHealth.Sending -> Color(0xFF4CAF50)
        is OscHealth.Error -> Color(0xFFE53935)
        OscHealth.Idle -> Color(0xFFB86CFF)
    }

    val visual by clockVisualState.collectAsState()
    val downbeatPulse = remember { Animatable(0f) }

    LaunchedEffect(visual.downbeatId) {
        if (visual.downbeatId != 0L) {
            if (animationsEnabled) {
                downbeatPulse.snapTo(1f)
                downbeatPulse.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            }

            if (hapticsEnabled && downbeatHapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
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
    var nudgeDownMs by remember { mutableStateOf(0L) }

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
                        zone = if (event.x <= leftZoneEdgePx) TouchZone.LEFT else TouchZone.CENTER
                        if (zone == TouchZone.LEFT) {
                            nudgeDownMs = SystemClock.elapsedRealtime()
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        val dxT = event.x - startX
                        val dyT = event.y - startY

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

        Image(
            painter = painterResource(R.drawable.goblin),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Image(
            painter = painterResource(R.drawable.goblin_flash),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(goblinFlashAlpha.value)
        )

        if (showOscDot) {
            val radiusTouch = min(widthPx, heightPx) / 2f
            Canvas(
                modifier = Modifier
                    .size(10.dp)
                    .offset(
                        x = (radiusTouch * 0.36f).dp,
                        y = (radiusTouch * 0.28f).dp
                    )
            ) {
                drawCircle(
                    color = oscDotColor,
                    alpha = 0.18f + oscPulse.value * 0.82f
                )
            }
        }

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
                return if (voice == Voice.LOCAL) base else if (remoteGhostModeEnabled) base * 0.18f else base * 0.35f
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

                val alpha = intensityFor(voice, kind) * fadeLate(p)

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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val safeRadius = min(size.width, size.height) * 0.82f / 2f

            drawCircle(
                color = GoblinBrown.copy(alpha = 0.28f * downbeatPulse.value),
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
