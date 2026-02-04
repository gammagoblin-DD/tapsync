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
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.TapScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val isForeground = MutableStateFlow(false)

    override fun onResume() {
        super.onResume()
        isForeground.value = true
    }

    override fun onPause() {
        isForeground.value = false
        super.onPause()
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
        oscReceiver.start()

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
                        oscSender.sendFloat("/tapsync/ping", v)

                        val nextDelayMs = if (!adaptive) {
                            baseIntervalMs
                        } else {
                            val lastPong = oscReceiver.lastPongMs.value
                            val now = SystemClock.elapsedRealtime()
                            val age = if (lastPong <= 0L) Long.MAX_VALUE else (now - lastPong).coerceAtLeast(0L)
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
                        onClose = { showSettings = false }
                    )
                } else {
                    TapScreen(
                        externalClockActivity = oscReceiver.externalBpmActivity,
                        externalTransportIn = oscReceiver.transportIn,
                        showOscDot = s.showOscDot,
                        showStatusLine = s.showStatusLine,
                        activePresetName = s.presets.getOrNull(s.activePreset)?.name ?: "Preset",
                        activeTargetIp = s.presets.getOrNull(s.activePreset)?.ip ?: "-",
                        activeTargetPort = s.presets.getOrNull(s.activePreset)?.port ?: 0,
                        showBpm = false,
                        bpm = clockState.bpm,

                        lastPongMs = oscReceiver.lastPongMs,
                        heartbeatEnabled = s.heartbeatEnabled,
                        signalGraceMs = s.signalGraceMs,


                        showExternalBpm = s.showExternalBpm,
                        externalBpm = oscReceiver.externalBpm,
                        externalConfidence = oscReceiver.externalConfidence,
                        showOscDebug = s.showOscDebug,
                        oscDebugState = oscReceiver.debugState,
                        remoteGhost = oscReceiver.remoteGhost,
                        phaseVisualizerEnabled = s.phaseVisualizerEnabled,
                        phaseSpiralEnabled = s.phaseSpiralEnabled,

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
                        onLongPress = { showSettings = true },
                    )
                }
            }
        }
    }
}
