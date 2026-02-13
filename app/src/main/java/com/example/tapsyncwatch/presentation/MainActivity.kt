package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.action.HapticFeedbackEngine
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.transport.TransportEchoGuard
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.data.HeartbeatSendTo
import com.example.tapsyncwatch.presentation.ui.TapScreen
import com.example.tapsyncwatch.presentation.ui.FftGainScreen
import com.example.tapsyncwatch.presentation.ui.DebugScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.round
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_STEM_2
import androidx.activity.compose.BackHandler


private enum class HomePage { TAP, FFT_GAIN, DEBUG }

class MainActivity : ComponentActivity() {

    private val isForeground = MutableStateFlow(false)

    // Debounce hardware back/stem so it doesn't skip pages (Debug → FFT → Tap)
    private var lastBackKeyMs: Long = 0L
    override fun onResume() {
        super.onResume()
        isForeground.value = true
    }

    override fun onPause() {
        isForeground.value = false
        super.onPause()
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_STEM_1,
            KEYCODE_STEM_2,
            KeyEvent.KEYCODE_STEM_3 -> {
                // Force the bottom hardware key to behave as Back everywhere,
                // but debounce it so we don't skip Debug → FFTGain → Tap.
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackKeyMs < 480L) return true
                lastBackKeyMs = now
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsStore = SettingsStore(this)

        val oscSender = OscOutputSender(
            host = "127.0.0.1",
            port = 7002
        )

        val clock = Clock(oscSender)

        clock.setEnabled(false)
        clock.setMode(com.example.tapsyncwatch.domain.clock.ClockMode.EXTERNAL)

        lifecycleScope.launch {
            settingsStore.activeTarget.collect { target ->
                oscSender.setTarget(target.ip, target.port)
            }
        }

        val oscReceiver = OscInputReceiver(port = 7000)

        // Runtime wiring for Connection settings (Input/Output/Echo Guard)
        lifecycleScope.launch {
            settingsStore.settings.collectLatest { s ->
                // OSC Output
                oscSender.setSendEnabled(s.oscOutputEnabled)
                oscSender.setThrottleMs(s.oscOutputThrottleMs)

                // OSC Input
                oscReceiver.setPort(s.oscInputPort)
                oscReceiver.setEnabled(s.oscInputEnabled)

                // Transport Echo Guard (suppress watch→host→watch echo spikes)
                TransportEchoGuard.configure(
                    enabled = s.echoGuardEnabled,
                    windowMs = s.echoGuardWindowMs,
                    ruleTap = s.echoGuardRuleTap,
                    ruleResync = s.echoGuardRuleResync,
                    ruleMultiplyDivide = s.echoGuardRuleMultDiv,
                    ruleNudge = s.echoGuardRuleNudge
                )
            }
        }

        // Heartbeat: Watch → /tapsync/ping  (Resolume Wire answers /tapsync/pong)
        var heartbeatJob: Job? = null
        lifecycleScope.launch {
            settingsStore.settings.collectLatest { s ->
                heartbeatJob?.cancel()
                if (!s.heartbeatEnabled) return@collectLatest

                val baseIntervalMs = s.heartbeatIntervalMs.coerceIn(500L, 5000L)
                val graceMs = s.signalGraceMs.coerceIn(1000L, 30000L)
                val adaptive = s.heartbeatAdaptiveEnabled

                heartbeatJob = launch {
                    var nonce = 0
                    while (isActive) {
                        if (s.heartbeatForegroundOnly && !isForeground.value) {
                            delay(750L)
                            continue
                        }

                        nonce = (nonce + 1) % 999
                        val v = (nonce + 1) / 1000f
                        if (s.heartbeatSendTo == HeartbeatSendTo.ALL) {
                            // Broadcast ping to all presets (useful for multi-host setups)
                            s.presets.forEach { t ->
                                oscSender.sendFloatTo(t.ip, t.port, "/tapsync/ping", v)
                            }
                        } else {
                            oscSender.sendFloat("/tapsync/ping", v)
                        }

                        val nextDelayMs = if (!adaptive) {
                            baseIntervalMs
                        } else {
                            val lastPong = oscReceiver.lastPongMs.value
                            val now = SystemClock.elapsedRealtime()
                            val age =
                                if (lastPong <= 0L) Long.MAX_VALUE else (now - lastPong).coerceAtLeast(
                                    0L
                                )
                            val linkOk = age < graceMs
                            if (linkOk) {
                                baseIntervalMs
                            } else {
                                if (age > 15_000L) baseIntervalMs else minOf(500L, baseIntervalMs)
                            }
                        }
                        delay(nextDelayMs)
                    }
                }
            }
        }

        setContent {

            var showSettings by remember { mutableStateOf(false) }
            var homePage by remember { mutableStateOf(HomePage.TAP) }

            BackHandler(enabled = !showSettings && homePage != HomePage.TAP) {
                homePage = when (homePage) {
                    HomePage.DEBUG -> HomePage.FFT_GAIN
                    HomePage.FFT_GAIN -> HomePage.TAP
                    HomePage.TAP -> HomePage.TAP
                }
            }

            // On TapScreen: Back should do nothing (stay in-app).
            BackHandler(enabled = !showSettings && homePage == HomePage.TAP) {
                // swallow
            }



            // FFT Input Gain (0..1). Prefer incoming Resolume value if available.
            var localFftGain01 by remember { mutableStateOf(0.5f) }
            val remoteFftGain01 by oscReceiver.fftInputGain01.collectAsState(initial = null)
            val fftGain01 = (remoteFftGain01 ?: localFftGain01).coerceIn(0f, 1f)

            fun goNextPage() {
                homePage = when (homePage) {
                    HomePage.TAP -> HomePage.FFT_GAIN
                    HomePage.FFT_GAIN -> HomePage.DEBUG
                    HomePage.DEBUG -> HomePage.TAP
                }
            }

            fun goPrevPage() {
                homePage = when (homePage) {
                    HomePage.TAP -> HomePage.DEBUG
                    HomePage.FFT_GAIN -> HomePage.TAP
                    HomePage.DEBUG -> HomePage.FFT_GAIN
                }
            }

            val uiScope = rememberCoroutineScope()

            val vibrator = getSystemService(Vibrator::class.java)

            val actionEngine = remember {
                ActionEngine(
                    scope = lifecycleScope,
                    clock = clock,
                    haptics = HapticFeedbackEngine(vibrator)
                )
            }

            val settings by settingsStore.settings.collectAsState(initial = null)
            val clockState by clock.state.collectAsState()

            settings?.let { s ->

                if (showSettings) {
                    SettingsScreen(
                        settingsStore = settingsStore,
                        lastPongMs = oscReceiver.lastPongMs,
                        lastPongFrom = oscReceiver.lastPongFrom,
                        lastAnyRxMs = oscReceiver.lastAnyRxMs,
                        lastAnyFrom = oscReceiver.lastAnyFrom,
                        onClose = {
                            // In settings root, Back should always bring us back to the TapScreen.
                            showSettings = false
                            homePage = HomePage.TAP
                        }
                    )
                } else {
                    when (homePage) {
                        HomePage.TAP -> TapScreen(
                            externalClockActivity = oscReceiver.externalBpmActivity,
                            externalTransportIn = oscReceiver.transportIn,
                            showOscDot = s.showOscDot,
                            oscDotOpacity = s.oscDotOpacity,
                            oscDotFadeMs = s.oscDotFadeMs,
                            showStatusLine = s.showStatusLine,
                            statusLineAlpha = s.statusLineAlpha,
                            preflightAlpha = s.preflightAlpha,
                            timelineAlpha = s.timelineAlpha,

                            showPreflight = s.showPreflight,
                            preflightMode = s.preflightMode,
                            statusbarAutoDimWarn = s.statusbarAutoDimWarn,
                            showTimeline = s.showTimeline,
                            timelineWindowMs = s.timelineWindowMs,
                            timelineShowLocal = s.timelineShowLocal,
                            timelineShowRemote = s.timelineShowRemote,
                            timelineShowHealth = s.timelineShowHealth,
                            timelineImportantOnly = s.timelineImportantOnly,
                            timelineRemoteAlpha = s.timelineRemoteAlpha,
                            remoteEventMinIntervalMs = s.remoteEventMinIntervalMs,

                            preflightPhaseOkMs = s.preflightPhaseOkMs,
                            preflightDownbeatOkMs = s.preflightDownbeatOkMs,
                            preflightOutOkMs = s.preflightOutOkMs,

                            activePresetName = s.activeTarget.name,
                            activeTargetIp = s.activeTarget.ip,
                            activeTargetPort = s.activeTarget.port,

                            showDownbeatIndicator = s.showDownbeatIndicator,
                            downbeatStyle = s.downbeatStyle,
                            downbeatOpacity = s.downbeatOpacity,
                            showBpm = false,
                            bpm = clockState.bpm,

                            lastPongMs = oscReceiver.lastPongMs,
                            lastAnyRxMs = oscReceiver.lastAnyRxMs,
                            lastPhaseRxMs = oscReceiver.lastPhaseRxMs,
                            lastDownbeatRxMs = oscReceiver.lastDownbeatRxMs,
                            heartbeatEnabled = s.heartbeatEnabled,
                            signalGraceMs = s.signalGraceMs,

                            anyRxFallbackEnabled = s.oscInputAnyRxFallbackEnabled,

                            anyRxTimeoutMs = s.oscInputAnyRxTimeoutMs,

                            showExternalBpm = s.showExternalBpm,
                            bpmOpacity = s.bpmOpacity,
                            bpmFormat = s.bpmFormat,
                            externalBpm = oscReceiver.externalBpm,
                            externalConfidence = oscReceiver.externalConfidence,
                            showOscDebug = false, // legacy fullscreen OSC overlay disabled (use DebugScreen OSC monitor)
                            oscDebugState = oscReceiver.debugState,
                            remoteGhost = oscReceiver.remoteGhost,
                            phaseVisualizerEnabled = s.phaseVisualizerEnabled,
                            phaseSpiralEnabled = s.phaseSpiralEnabled,
                             fxAlpha = s.fxAlpha,
                             phaseAlpha = s.phaseAlpha,
                             ghostAlpha = s.ghostAlpha,
                            moodsEnabled = s.moodsEnabled,
                            moodIntensity = s.moodIntensity,
                            phaseAuraEnabled = s.phaseAuraEnabled,
                            microParticlesEnabled = s.microParticlesEnabled,
                            visualSwing = s.visualSwing,
                            ghostEchoEnabled = s.ghostEchoEnabled,
                            ghostEchoStrength = s.ghostEchoStrength,
                            animationsEnabled = s.animationsEnabled,
                            remoteAnimationsEnabled = s.remoteAnimationsEnabled,
                            remoteGhostModeEnabled = s.remoteGhostModeEnabled,
                            goblinFlashEnabled = s.goblinFlashEnabled,
                            rippleEnabled = s.rippleEnabled,
                            oscPulseEnabled = s.oscPulseEnabled,

                            action = actionEngine,
                            oscHealth = oscSender.health,
                            clockVisualState = clock.visualState,
                            externalActivity = clock.externalActivity,
                            clockMode = s.clockMode,
                            hapticsEnabled = s.hapticsEnabled,
                            downbeatHapticsEnabled = s.downbeatHapticsEnabled,
                            transportHapticsEnabled = s.transportHapticsEnabled,

                            // Right-edge paging
                            onPageNext = ::goNextPage,
                            onPagePrev = ::goPrevPage,

                            onLongPress = { showSettings = true },
                            onCloseOscMonitor = {
                                uiScope.launch {
                                    settingsStore.setShowOscDebug(
                                        false
                                    )
                                }
                            }
                        )

                        HomePage.FFT_GAIN -> {
                            val (uiGainState, setUiTarget) = rememberOscGainSmoother(
                                initial = fftGain01,
                                send = { v -> oscSender.sendFftInputGain01(v) },
                                frameMs = 33L,
                                smoothMs = 90,
                                sendEpsilon = 0.002f,
                                quantizeStep = 0.001f,
                            )

                            FftGainScreen(
                                value01 = uiGainState.value,
                                onBackToTap = { homePage = HomePage.TAP },
                                onSetValue01 = { v ->
                                    localFftGain01 = v
                                    setUiTarget(v)
                                },
                                onResetToDefault = {
                                    localFftGain01 = 0.5f
                                    setUiTarget(0.5f)
                                    oscSender.resetFftInputGainToDefault()
                                },
                                onPageNext = ::goNextPage,
                                onPagePrev = ::goPrevPage
                            )
                        }
                        HomePage.DEBUG -> DebugScreen(
                            lastPongMs = oscReceiver.lastPongMs,
                            lastAnyRxMs = oscReceiver.lastAnyRxMs,
                            lastPhaseRxMs = oscReceiver.lastPhaseRxMs,
                            lastDownbeatRxMs = oscReceiver.lastDownbeatRxMs,
                            signalGraceMs = s.signalGraceMs,
                            heartbeatEnabled = s.heartbeatEnabled,

                            oscHealth = oscSender.health,

                            // OSC debug (summary + last packet)
                            showOscMonitor = s.showOscDebug,
                            oscDebugState = oscReceiver.debugState,

                            // Timeline / transport
                            showTimeline = s.showTimeline,
                            timelineWindowMs = s.timelineWindowMs,
                            timelineShowLocal = s.timelineShowLocal,
                            timelineShowRemote = s.timelineShowRemote,
                            timelineShowHealth = s.timelineShowHealth,
                            timelineImportantOnly = s.timelineImportantOnly,
                            timelineRemoteAlpha = s.timelineRemoteAlpha,
                            remoteEventMinIntervalMs = s.remoteEventMinIntervalMs,
                            transportIn = oscReceiver.transportIn,

                            // Preflight visibility + alpha
                            showPreflight = s.showPreflight,
                            preflightAlpha = s.preflightAlpha,
                            preflightMode = s.preflightMode,
                            preflightPhaseOkMs = s.preflightPhaseOkMs,
                            preflightDownbeatOkMs = s.preflightDownbeatOkMs,
                            preflightOutOkMs = s.preflightOutOkMs,

                            // Targets (presets)
                            presets = s.presets,
                            activePreset = s.activePreset,

                            onPageNext = ::goNextPage,
                            onPagePrev = ::goPrevPage,
                        )

                    }
                }
            }
        }
    }


    @Composable
    private fun rememberOscGainSmoother(
        initial: Float,
        send: (Float) -> Unit,
        frameMs: Long = 33L,          // ~30 Hz
        smoothMs: Int = 90,           // 60–120ms feels good on stage
        sendEpsilon: Float = 0.002f,  // ~0.1 dB steps (48 dB range)
        quantizeStep: Float = 0.001f, // optional raster to fight jitter
    ): Pair<State<Float>, (Float) -> Unit> {
        var uiTarget by remember { mutableStateOf(initial.coerceIn(0f, 1f)) }
        val anim = remember { Animatable(uiTarget) }

        val uiValueState: State<Float> = derivedStateOf { uiTarget }

        LaunchedEffect(uiTarget) {
            anim.animateTo(uiTarget, animationSpec = tween(durationMillis = smoothMs))
        }

        LaunchedEffect(Unit) {
            var lastSent = Float.NaN
            while (true) {
                val v = anim.value
                val q = if (quantizeStep > 0f) {
                    (round(v / quantizeStep) * quantizeStep).coerceIn(0f, 1f)
                } else v

                if (lastSent.isNaN() || abs(q - lastSent) >= sendEpsilon) {
                    send(q)
                    lastSent = q
                }
                delay(frameMs)
            }
        }

        val setTarget: (Float) -> Unit = { v ->
            uiTarget = v.coerceIn(0f, 1f)
        }

        return uiValueState to setTarget
    }
}