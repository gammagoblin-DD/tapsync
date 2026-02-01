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
import com.example.tapsyncwatch.presentation.ui.SettingsScreen

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

        val oscReceiver = OscInputReceiver(clock)
        oscReceiver.start()

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
                        onClose = { showSettings = false }
                    )
                } else {
                    TapScreen(
                        externalClockActivity = clock.externalActivity,
                        externalTransportIn = oscReceiver.transportIn,
                        showOscDot = s.showOscDot,
                        showBpm = false,
                        bpm = clockState.bpm,

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
