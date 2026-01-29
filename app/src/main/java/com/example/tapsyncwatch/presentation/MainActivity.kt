package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.clock.Clock
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsStore = SettingsStore(this)

        clock = Clock(
            oscSender = OscOutputSender(
                host = "192.168.178.24",
                port = 7000
            )
        )

        oscReceiver = OscInputReceiver(clock)
        oscReceiver.start()

        setContent {
            var showSettings by remember { mutableStateOf(false) }
            val settings by settingsStore.settings.collectAsState(initial = null)

            val actionEngine = remember {
                ActionEngine(scope = oscScope)
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
                                showBpm = false, // 🔒 OPTION A: BPM endgültig deaktiviert
                                bpm = clockState.bpm,
                                action = actionEngine,
                                onLongPress = { showSettings = true }
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
