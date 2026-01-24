package com.example.tapsyncwatch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.tapsyncwatch.input.osc.OscOutputSender
import com.example.tapsyncwatch.presentation.ui.WatchUI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var oscSender: OscOutputSender

    // UI-State (nur Anzeige, keine Clock)
    private var bpm by mutableStateOf(120f)

    private var blockTempoSend = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        oscSender = OscOutputSender(
            host = "192.168.178.24", // Resolume Rechner
            port = 7002              // Resolume OSC INPUT
        )

        setContent {
            MaterialTheme {
                Surface {
                    WatchUI(
                        bpm = bpm,

                        // ---------- TAP ----------
                        onTap = {
                            lifecycleScope.launch {
                                oscSender.sendInt(
                                    "/composition/tempocontroller/tempotap",
                                    1
                                )
                                delay(40)
                                oscSender.sendInt(
                                    "/composition/tempocontroller/tempotap",
                                    0
                                )
                            }
                        },

                        // ---------- MULTIPLY ×2 ----------
                        // ❗ nur OSC-Impuls, keine BPM-Sends
                        onMultiply = {
                            lifecycleScope.launch {
                                blockTempoSend = true
                                oscSender.sendInt(
                                    "/composition/tempocontroller/tempo/multiply",
                                    1
                                )
                                delay(40)
                                blockTempoSend = false
                            }
                        },

                        // ---------- DIVIDE ÷2 ----------
                        onDivide = {
                            lifecycleScope.launch {
                                blockTempoSend = true
                                oscSender.sendInt(
                                    "/composition/tempocontroller/tempo/divide",
                                    1
                                )
                                delay(40)
                                blockTempoSend = false
                            }
                        },

                        // ---------- RESYNC ----------
                        // wird durch Swipe R→L in WatchUI ausgelöst
                        onResync = {
                            lifecycleScope.launch {
                                blockTempoSend = true
                                oscSender.sendInt(
                                    "/composition/tempocontroller/resync",
                                    1
                                )
                                delay(40)
                                oscSender.sendInt(
                                    "/composition/tempocontroller/resync",
                                    0
                                )
                                blockTempoSend = false
                            }
                        }
                    )
                }
            }
        }
    }
}
