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
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.ui.TapScreen
import com.example.tapsyncwatch.service.TapSyncForegroundService
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        setContent {
            var showSettings by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface {
                    if (showSettings) {
                        SettingsScreen(
                            onClose = { showSettings = false }
                        )
                    } else {
                        TapScreen(
                            showBpm = true,
                            action = actionEngine,
                            onLongPress = { showSettings = true }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscScope.cancel()
    }
}
