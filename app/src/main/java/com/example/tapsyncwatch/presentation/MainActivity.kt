package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.example.tapsyncwatch.presentation.data.OscTarget
import com.example.tapsyncwatch.presentation.ui.QuickPingRow
import com.example.tapsyncwatch.presentation.ui.QuickPingKind
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder


private enum class HomePage { TAP, FFT_GAIN, DEBUG }

class MainActivity : ComponentActivity() {

    private val isForeground = MutableStateFlow(false)
    private val heartbeatSuppressed = MutableStateFlow(false)

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

        // Festival-stabil: Heartbeat must always be ON at app start
        val forceHeartbeatOn = true

        // Apply one-time settings migrations early (keep startup smooth + deterministic).
        lifecycleScope.launch(Dispatchers.IO) {
            settingsStore.ensureMigrations()
            if (forceHeartbeatOn) settingsStore.setHeartbeatEnabled(true)
        }


        val oscSender = OscOutputSender(
            host = "127.0.0.1",
            port = 7002
        )

        val clock = Clock(oscSender)

        clock.setEnabled(false)
        clock.setMode(com.example.tapsyncwatch.domain.clock.ClockMode.EXTERNAL)

        lifecycleScope.launch(Dispatchers.IO) {
            settingsStore.activeTarget.collect { target ->
                // DNS / InetAddress.getByName can block on some devices; keep it off the UI thread.
                oscSender.setTarget(target.ip, target.port)
            }
        }

        val oscReceiver = OscInputReceiver(port = 7000)

        // Runtime wiring for Connection settings (Input/Output/Echo Guard)
        lifecycleScope.launch(Dispatchers.Default) {
            settingsStore.settings.collectLatest { s ->
                // OSC Output
                oscSender.setSendEnabled(s.oscOutputEnabled)
                oscSender.setThrottleMs(s.oscOutputThrottleMs)

                // OSC Input
                // Socket bind/close can block; keep it off the UI thread.
                withContext(Dispatchers.IO) {
                    oscReceiver.setPort(s.oscInputPort)
                    oscReceiver.setEnabled(s.oscInputEnabled)
                }

                // Debug bookkeeping (avoid per-packet allocations unless enabled)
                oscReceiver.setDebugEnabled(s.showOscDebug)

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
        lifecycleScope.launch(Dispatchers.Default) {
            settingsStore.settings.collectLatest { s ->
                heartbeatJob?.cancel()
                val heartbeatEnabledEffective = s.heartbeatEnabled || forceHeartbeatOn
                if (!heartbeatEnabledEffective) return@collectLatest

                val baseIntervalMs = s.heartbeatIntervalMs.coerceIn(500L, 5000L)
                val graceMs = s.signalGraceMs.coerceIn(1000L, 30000L)
                val adaptive = s.heartbeatAdaptiveEnabled

                heartbeatJob = launch(Dispatchers.Default) {
                    var nonce = 0
                    var downSinceMs: Long? = null
                    var hardDownSinceMs: Long? = null
                    var wasSuppressed = false
                    var recoveryBurstLeft = 0
                    while (isActive) {
                        if (s.heartbeatForegroundOnly && !isForeground.value) {
                            delay(750L)
                            continue
                        }

                        if (heartbeatSuppressed.value) {
                            if (!wasSuppressed) {
                                downSinceMs = null
                                hardDownSinceMs = null
                                recoveryBurstLeft = 0
                            }
                            wasSuppressed = true
                            delay(250L)
                            continue
                        } else if (wasSuppressed) {
                            wasSuppressed = false
                            downSinceMs = null
                            hardDownSinceMs = null
                            recoveryBurstLeft = 0
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

                        // Heartbeat state machine:
                        // - UP:      ping at baseInterval
                        // - DOWN:    short burst of faster pings (quick recovery)
                        // - HARD-DOWN: backoff (avoid spamming/battery drain when the link is truly dead)

                        val now = SystemClock.elapsedRealtime()
                        val lastPong = oscReceiver.lastPongMs.value
                        val pongAge = if (lastPong <= 0L) Long.MAX_VALUE else (now - lastPong).coerceAtLeast(0L)
                        val linkUp = pongAge < graceMs

                        // Enter hard-down after a longer outage (scaled by graceMs, but bounded).
                        val hardDownAfterMs = maxOf(2L * graceMs, 10_000L).coerceIn(6_000L, 60_000L)

                        fun hardDownBackoffDelayMs(baseMs: Long, hardElapsedMs: Long): Long {
                            // Step ladder (watch-friendly). Capped, but never below base interval.
                            val steps = longArrayOf(1_200L, 2_000L, 3_500L, 6_000L, 10_000L, 16_000L, 25_000L, 30_000L)
                            val step = (hardElapsedMs / 6_500L).toInt().coerceIn(0, steps.lastIndex)
                            return maxOf(baseMs, steps[step]).coerceIn(1_200L, 30_000L)
                        }

                        val nextDelayMs = if (!adaptive) {
                            // Adaptive off → steady ping.
                            downSinceMs = null
                            hardDownSinceMs = null
                            recoveryBurstLeft = 0
                            baseIntervalMs
                        } else if (linkUp) {
                            val wasDown = (downSinceMs != null) || (hardDownSinceMs != null)
                            downSinceMs = null
                            hardDownSinceMs = null

                            // Recovery burst: a few faster pings right after link comes back.
                            if (wasDown) recoveryBurstLeft = 3
                            if (recoveryBurstLeft > 0) {
                                recoveryBurstLeft -= 1
                                minOf(350L, baseIntervalMs)
                            } else {
                                baseIntervalMs
                            }
                        } else {
                            if (downSinceMs == null) downSinceMs = now
                            val downElapsedMs = (now - (downSinceMs ?: now)).coerceAtLeast(0L)

                            if (downElapsedMs < hardDownAfterMs) {
                                // Soft-down: try fast recovery for a short window.
                                hardDownSinceMs = null
                                minOf(450L, baseIntervalMs)
                            } else {
                                // Hard-down: backoff.
                                if (hardDownSinceMs == null) hardDownSinceMs = now
                                val hardElapsedMs = (now - (hardDownSinceMs ?: now)).coerceAtLeast(0L)
                                hardDownBackoffDelayMs(baseIntervalMs, hardElapsedMs)
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

            // TapScreen Quick Actions: Test ALL results
            var quickTestBusy by remember { mutableStateOf(false) }
            var quickTestRunningIndex by remember { mutableStateOf(-1) }
            var quickTestRunningLabel by remember { mutableStateOf<String?>(null) }
            var quickTestJob by remember { mutableStateOf<Job?>(null) }
            val quickTestRows = remember { mutableStateListOf<QuickPingRow>() }


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
            val heartbeatSuppressedUi by heartbeatSuppressed.collectAsState()

            suspend fun pingPresetOnce(index: Int, target: OscTarget): QuickPingRow {
                val host = target.ip.trim()
                val port = target.port.coerceIn(1, 65535)
                if (host.isEmpty()) return QuickPingRow(index, target.name, QuickPingKind.SEND_FAIL)

                val started = SystemClock.elapsedRealtime()
                val nonce = (((started % 1000L) + 1L).toFloat() / 1000f)

                val sentOk = try {
                    sendOscPingFloat(host, port, nonce)
                    true
                } catch (_: Exception) {
                    false
                }

                if (!sentOk) return QuickPingRow(index, target.name, QuickPingKind.SEND_FAIL)

                val pongTs = withTimeoutOrNull(900L) {
                    oscReceiver.lastPongMs.filter { it >= started }.first()
                }

                if (pongTs == null) return QuickPingRow(index, target.name, QuickPingKind.TIMEOUT)

                val rtt = (pongTs - started).coerceAtLeast(0L)
                var from = oscReceiver.lastPongFrom.value
                if (from == null) {
                    delay(10L)
                    from = oscReceiver.lastPongFrom.value
                }

                return QuickPingRow(index, target.name, QuickPingKind.OK, rttMs = rtt, pongFrom = from)
            }


            settings?.let { s ->

            val heartbeatEnabledEffectiveUi = s.heartbeatEnabled || forceHeartbeatOn
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
                        },
                        setHeartbeatSuppressed = { heartbeatSuppressed.value = it }
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

                            oscInputPort = s.oscInputPort,
                            lastPongFrom = oscReceiver.lastPongFrom,

                            showDownbeatIndicator = s.showDownbeatIndicator,
                            downbeatStyle = s.downbeatStyle,
                            downbeatOpacity = s.downbeatOpacity,
                            showBpm = false,
                            bpm = clockState.bpm,

                            lastPongMs = oscReceiver.lastPongMs,
                            lastAnyRxMs = oscReceiver.lastAnyRxMs,
                            lastPhaseRxMs = oscReceiver.lastPhaseRxMs,
                            lastDownbeatRxMs = oscReceiver.lastDownbeatRxMs,
                            heartbeatEnabled = heartbeatEnabledEffectiveUi,
                            heartbeatSuppressed = heartbeatSuppressedUi,
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
                            phaseRingOpacity = s.phaseRingOpacity,
                            phaseRingThicknessDp = s.phaseRingThicknessDp,
                            phaseSpiralOpacity = s.phaseSpiralOpacity,
                            phaseSpiralThicknessDp = s.phaseSpiralThicknessDp,
                            phaseAuraOpacity = s.phaseAuraOpacity,
                            phaseAuraThicknessDp = s.phaseAuraThicknessDp,
                            microParticlesOpacity = s.microParticlesOpacity,
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
                            ghostEchoOpacity = s.ghostEchoOpacity,
                            ghostEchoThicknessDp = s.ghostEchoThicknessDp,
                            animationsEnabled = s.animationsEnabled,
                            localVisualsEnabled = s.localVisualsEnabled,
                            remoteAnimationsEnabled = s.remoteAnimationsEnabled,
                            remoteGhostModeEnabled = s.remoteGhostModeEnabled,
                            remoteGhostOpacity = s.remoteGhostOpacity,
                            goblinFlashEnabled = s.goblinFlashEnabled,
                            goblinFlashOpacity = s.goblinFlashOpacity,
                            goblinFlashFadeMs = s.goblinFlashFadeMs,
                            rippleEnabled = s.rippleEnabled,
                            rippleTapEnabled = s.rippleTapEnabled,
                            rippleTapOpacity = s.rippleTapOpacity,
                            rippleTapThicknessDp = s.rippleTapThicknessDp,
                            rippleMultDivEnabled = s.rippleMultDivEnabled,
                            rippleMultDivOpacity = s.rippleMultDivOpacity,
                            rippleMultDivThicknessDp = s.rippleMultDivThicknessDp,
                            rippleResyncEnabled = s.rippleResyncEnabled,
                            rippleResyncOpacity = s.rippleResyncOpacity,
                            rippleResyncThicknessDp = s.rippleResyncThicknessDp,
                            rippleNudgeEnabled = s.rippleNudgeEnabled,
                            rippleNudgeOpacity = s.rippleNudgeOpacity,
                            rippleNudgeThicknessDp = s.rippleNudgeThicknessDp,
                            oscPulseEnabled = s.oscPulseEnabled,
                            oscPulseOpacity = s.oscPulseOpacity,
                            oscPulseThicknessDp = s.oscPulseThicknessDp,

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
                            heartbeatEnabled = heartbeatEnabledEffectiveUi,
                            heartbeatSuppressed = heartbeatSuppressedUi,
                            anyRxFallbackEnabled = s.oscInputAnyRxFallbackEnabled,
                            anyRxTimeoutMs = s.oscInputAnyRxTimeoutMs,

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


                            // Quick Actions (moved from TapScreen -> DebugScreen)
                            echoGuardEnabled = s.echoGuardEnabled,
                            quickTestBusy = quickTestBusy,
                            quickTestRunningIndex = quickTestRunningIndex,
                            quickTestRunningLabel = quickTestRunningLabel,
                            quickTestRows = quickTestRows,
                            onQuickTestAll = {
                                if (quickTestBusy) return@DebugScreen
                                quickTestJob?.cancel()
                                quickTestJob = uiScope.launch {
                                    quickTestBusy = true
                                    quickTestRunningIndex = -1
                                    quickTestRunningLabel = null
                                    quickTestRows.clear()
                                    heartbeatSuppressed.value = true
                                    try {
                                        val presetsSnapshot = s.presets.toList()
                                        presetsSnapshot.forEachIndexed { idx, t ->
                                            if (!isActive) return@forEachIndexed
                                            quickTestRunningIndex = idx
                                            quickTestRunningLabel = t.name.ifBlank { "Preset ${idx + 1}" }
                                            quickTestRows.add(pingPresetOnce(idx, t))
                                            delay(120L)
                                        }
                                    } finally {
                                        heartbeatSuppressed.value = false
                                        quickTestBusy = false
                                        quickTestRunningIndex = -1
                                        quickTestRunningLabel = null
                                        quickTestJob = null
                                    }
                                }
                            },
                            onQuickTestCancel = {
                                quickTestJob?.cancel()
                            },
                            onQuickTestClear = {
                                quickTestRows.clear()
                            },
                            onPresetPrev = {
                                uiScope.launch {
                                    val n = s.presets.size.coerceAtLeast(1)
                                    val next = ((s.activePreset - 1 + n) % n)
                                    settingsStore.setActivePreset(next)
                                }
                            },
                            onPresetNext = {
                                uiScope.launch {
                                    val n = s.presets.size.coerceAtLeast(1)
                                    val next = ((s.activePreset + 1) % n)
                                    settingsStore.setActivePreset(next)
                                }
                            },
                            onToggleEchoGuard = {
                                uiScope.launch { settingsStore.setEchoGuardEnabled(!s.echoGuardEnabled) }
                            },
                            onToggleOscMonitor = {
                                uiScope.launch { settingsStore.setShowOscDebug(!s.showOscDebug) }
                            },

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

    /* ================= OSC UTIL (Quick Actions: Test ALL) ================= */

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

}
