package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockMode
import com.example.tapsyncwatch.domain.clock.ClockState
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import com.example.tapsyncwatch.presentation.ui.TapScreen
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var clock: Clock
    private lateinit var oscReceiver: OscInputReceiver

    private var onOscActivity: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsStore = SettingsStore(this)

        val oscSender = OscOutputSender(
            host = "127.0.0.1",
            port = 7002
        )

        clock = Clock(
            oscSender = oscSender
        )

        /* ================= CLOCK MODE WIRING ================= */

        lifecycleScope.launch {
            settingsStore.settings.collect { s ->
                clock.setMode(s.clockMode)
            }
        }

        /* ================= OSC TARGET ================= */

        lifecycleScope.launch {
            settingsStore.activeTarget.collect { target ->
                oscSender.setTarget(
                    host = target.ip,
                    port = target.port
                )
            }
        }

        /* ================= OSC INPUT ================= */

        oscReceiver = OscInputReceiver(clock)
        oscReceiver.start()

        oscScope.launch {
            oscReceiver.oscActivity.collect {
                onOscActivity?.invoke()
            }
        }

        setContent {
            var showSettings by remember { mutableStateOf(false) }
            val settings by settingsStore.settings.collectAsState(initial = null)

            val actionEngine = remember {
                ActionEngine(
                    scope = oscScope,
                    clock = clock
                )
            }

            val clockState by clock.state.collectAsState(
                initial = ClockState(
                    bpm = 0.0,
                    phase = 0.0,
                    isRunning = false
                )
            )

            MaterialTheme {
                Surface {
                    if (showSettings) {
                        SettingsScreen(
                            settingsStore = settingsStore,
                            onClose = { showSettings = false }
                        )
                    } else {
                        settings?.let { s ->
                            TapScreen(
                                showOscDot = s.showOscDot,
                                showBpm = false,
                                bpm = clockState.bpm,
                                action = actionEngine,
                                oscHealth = oscSender.health,
                                clockVisualState = clock.visualState,
                                clockMode = s.clockMode,            // ✅ FIX
                                hapticsEnabled = s.hapticsEnabled,
                                downbeatHapticsEnabled = s.downbeatHapticsEnabled,
                                onLongPress = { showSettings = true },
                                onOscActivity = { handler ->
                                    onOscActivity = handler
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscReceiver.stop()
        oscScope.cancel()
    }
}
