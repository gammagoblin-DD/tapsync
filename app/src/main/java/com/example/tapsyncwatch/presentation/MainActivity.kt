package com.example.tapsyncwatch.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import com.example.tapsyncwatch.domain.action.ActionEngine
import com.example.tapsyncwatch.presentation.data.SettingsStore
import com.example.tapsyncwatch.presentation.ui.SettingsScreen
import com.example.tapsyncwatch.presentation.ui.TapScreen
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val oscScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val actionEngine = ActionEngine(
            scope = oscScope
        )

        // SettingsStore EXISTIERT und bleibt
        val settingsStore = SettingsStore(this)

        setContent {
            var showSettings by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface {
                    if (showSettings) {
                        SettingsScreen(
                            settingsStore = settingsStore,
                            onClose = { showSettings = false }
                        )
                    } else {
                        TapScreen(
                            action = actionEngine,
                            showBpm = true,          // ✅ PFLICHTPARAMETER
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
