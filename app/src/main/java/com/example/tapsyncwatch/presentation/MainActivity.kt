package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.tapsyncwatch.presentation.data.DEFAULT_SETTINGS_STATE
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.action.HapticFeedbackEngine
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.TapScreen
import kotlinx.coroutines.launch
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsStore = SettingsStore(this)

        val oscSender = OscOutputSender(
            host = "127.0.0.1",
            port = 7002
        )

        val clock = Clock(oscSender)

        lifecycleScope.launch {
            settingsStore.settings.collect { s ->
                clock.setEnabled(s.clockEnabled)
                clock.setMode(s.clockMode)
            }
        }

        lifecycleScope.launch {
            settingsStore.activeTarget.collect { target ->
                oscSender.setTarget(target.ip, target.port)
            }
        }

        val oscReceiver = OscInputReceiver(port = 7000)
        // Start OSC receive on a background thread so app launch stays snappy.
        lifecycleScope.launch(Dispatchers.IO) {
            oscReceiver.start()
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

            // Use a non-null initial value so the UI draws immediately.
            // DataStore will update it as soon as the first real emission arrives.
            val settings by settingsStore.settings.collectAsState(initial = DEFAULT_SETTINGS_STATE)
            val clockState by clock.state.collectAsState()

            val s = settings

            if (showSettings) {
                SettingsScreen(
                    settingsStore = settingsStore,
                    onClose = { showSettings = false }
                )
            } else {
                TapScreen(
                    externalClockActivity = oscReceiver.externalBpmActivity,
                    externalTransportIn = oscReceiver.transportIn,
                    showOscDot = s.showOscDot,
                    showBpm = false,
                    bpm = clockState.bpm,

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
