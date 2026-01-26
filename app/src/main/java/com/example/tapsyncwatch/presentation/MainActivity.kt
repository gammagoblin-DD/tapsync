package com.example.tapsyncwatch.presentation

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.data.SettingsState
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import com.example.tapsyncwatch.presentation.ui.TapScreen
import com.example.tapsyncwatch.service.TapSyncForegroundService
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var oscInputReceiver:
            com.example.tapsyncwatch.input.osc.OscUdpInputReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startForegroundService(
            Intent(this, TapSyncForegroundService::class.java)
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val oscOut = OscOutputSender(
            host = "192.168.178.24",
            port = 7002
        )

        val actionEngine = ActionEngine(
            scope = oscScope,
            osc = oscOut
        )

        val clock = Clock(
            oscSender = oscOut,
            onBpmChanged = { bpm ->
                actionEngine.setExternalBpm(bpm.toDouble())
            }
        )

        oscInputReceiver =
            com.example.tapsyncwatch.input.osc.OscUdpInputReceiver(
                clock = clock,
                port = 7000
            ).also { it.start() }

        val settingsStore = SettingsStore(this)

        setContent {
            val settings by settingsStore.settings.collectAsState(
                initial = SettingsState(
                    ip = "192.168.178.24",
                    port = 7002,
                    showBpm = true,
                    showOscDot = true   // ✅ FIX
                )
            )

            val bpm by actionEngine.bpm.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface {
                    if (showSettings) {
                        SettingsScreen(
                            osc = oscOut,
                            onClose = { showSettings = false }
                        )
                    } else {
                        TapScreen(
                            bpm = bpm,
                            showBpm = settings.showBpm,
                            showOscDot = settings.showOscDot,
                            action = actionEngine,
                            osc = oscOut,
                            onLongPress = { showSettings = true }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscInputReceiver?.stop()
        oscInputReceiver = null
        oscScope.cancel()
    }
}
