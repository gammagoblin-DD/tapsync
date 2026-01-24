package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.presentation.network.OscSender
import com.example.tapsyncwatch.presentation.ui.TapScreen
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    // exakt wie im ALT-Code
    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val actionEngine = ActionEngine(
            scope = oscScope
        )

        setContent {
            var showSettings by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface {
                    if (showSettings) {

                        // ✅ KORREKT: parameterlose SettingsScreen
                        SettingsScreen()

                    } else {

                        TapScreen(
                            showBpm = true, // wird intern geregelt wie im Alt-Code
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
