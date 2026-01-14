package com.example.tapsyncwatch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tapsyncwatch.domain.clock.Clock
import com.example.tapsyncwatch.domain.clock.ClockEvent
import com.example.tapsyncwatch.input.osc.OscInputReceiver
import com.example.tapsyncwatch.presentation.ui.WatchUI
import kotlinx.coroutines.*
import com.example.tapsyncwatch.input.osc.OscOutputSender


class MainActivity : ComponentActivity() {

    // =========================
    // 1️⃣ Zentrale Objekte
    // =========================

    private lateinit var clock: Clock
    private lateinit var oscInputReceiver: OscInputReceiver
    private lateinit var oscOutputSender: OscOutputSender


    private val activityScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob()
    )

    private val networkScope = CoroutineScope(Dispatchers.IO)


    // =========================
    // 2️⃣ Activity Start
    // =========================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        oscOutputSender = OscOutputSender(
            targetIp = "192.168.178.24", // <-- DEINE RESOLUME-IP
            targetPort = 7002
        )


        // 🧠 Clock erstellen
        clock = Clock()

        // 👂 OSC Receiver erstellen
        oscInputReceiver = OscInputReceiver(clock)

        // 👂 OSC Receiver starten
        oscInputReceiver.start()

        // ⏱ Tick-Loop starten
        startTickLoop()

        // 🎨 UI starten
        setContent {
            WatchUI(
                clock = clock,
                onTap = {
                    clock.handle(ClockEvent.Tap(System.currentTimeMillis()))
                    networkScope.launch {
                        oscOutputSender.sendTempoTap()
                    }
                }
,
                onMultiply = {
                    clock.handle(ClockEvent.Multiply)
                },
                onDivide = {
                    clock.handle(ClockEvent.Divide)
                },
                onResync = {
                    clock.handle(ClockEvent.Resync)
                }
            )
        }
    }

    // =========================
    // 3️⃣ Herzschlag (Tick)
    // =========================

    private fun startTickLoop() {
        activityScope.launch {
            while (isActive) {
                clock.handle(
                    ClockEvent.Tick(System.currentTimeMillis())
                )
                delay(16L) // ca. 60 FPS
            }
        }
    }

    // =========================
    // 4️⃣ Activity Ende
    // =========================

    override fun onDestroy() {
        super.onDestroy()

        oscOutputSender.close()
        oscInputReceiver.stop()
        activityScope.cancel()
    }
}
