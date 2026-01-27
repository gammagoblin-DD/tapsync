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
import com.example.tapsyncwatch.input.osc.OscUdpInputReceiver
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import com.example.tapsyncwatch.presentation.ui.TapScreen
import com.example.tapsyncwatch.service.TapSyncForegroundService
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var oscInputReceiver: OscUdpInputReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startForegroundService(
            Intent(this, TapSyncForegroundService::class.java)
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initiale Dummy-Werte – echtes Target kommt aus Presets
        val oscOut = OscOutputSender(
            host = "127.0.0.1",
            port = 7002
        )

        val actionEngine = ActionEngine(
            scope = oscScope,
            osc = oscOut
        )

        val clock = Clock(
            oscSender = oscOut,
            onBpmChanged = { bpm ->
                // ✅ FIX: Float → Double
                actionEngine.setExternalBpm(bpm.toDouble())
            }
        )

        oscInputReceiver = OscUdpInputReceiver(
            clock = clock,
            port = 7000
        ).also { it.start() }

        val settingsStore = SettingsStore(this)

        setContent {
            val settings by settingsStore.settings.collectAsState(initial = null)
            val bpm by actionEngine.bpm.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

            settings?.let { s ->

                // 🔑 Aktives Preset bestimmt OSC OUT
                LaunchedEffect(s.activePreset) {
                    val target = s.activeTarget
                    oscOut.updateTarget(target.ip, target.port)
                }

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
                                showBpm = s.showBpm,
                                showOscDot = s.showOscDot,
                                action = actionEngine,
                                osc = oscOut,
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
        oscInputReceiver?.stop()
        oscInputReceiver = null
        oscScope.cancel()
    }
}
