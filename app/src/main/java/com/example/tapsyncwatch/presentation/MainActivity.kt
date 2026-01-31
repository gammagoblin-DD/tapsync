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
import com.example.tapsyncwatch.domain.settings.TransportRingIntensity
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import com.example.tapsyncwatch.presentation.ui.TapScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /* ---------- Android / Domain ---------- */

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

        /* ---------- Compose ---------- */

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

            /* 🔑 Transport Ring als echtes StateFlow */
            val transportRingIntensity =
                settingsStore.settings
                    .map { it.transportRingIntensity }
                    .stateIn(
                        scope = lifecycleScope,
                        started = SharingStarted.Eagerly,
                        initialValue = TransportRingIntensity.LOW
                    )

            settings?.let { s ->

                if (showSettings) {
                    SettingsScreen(
                        settingsStore = settingsStore,
                        onClose = { showSettings = false }
                    )
                } else {
                    TapScreen(
                        externalClockActivity = clock.externalActivity,
                        showOscDot = s.showOscDot,
                        showBpm = false,
                        bpm = clockState.bpm,
                        action = actionEngine,
                        oscHealth = oscSender.health,
                        clockVisualState = clock.visualState,
                        externalActivity = clock.externalActivity,
                        clockMode = s.clockMode,
                        hapticsEnabled = s.hapticsEnabled,
                        downbeatHapticsEnabled = s.downbeatHapticsEnabled,
                        transportHapticsEnabled = s.transportHapticsEnabled,
                        transportRingIntensity = transportRingIntensity,
                        onLongPress = { showSettings = true }
                    )
                }
            }
        }
    }
}
