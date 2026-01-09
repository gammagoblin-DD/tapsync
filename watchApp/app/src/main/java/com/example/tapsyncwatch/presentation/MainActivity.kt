package com.example.tapsyncwatch.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tapsyncwatch.R
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val SERVICE_UUID =
        UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanning = false

    private val scanPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Log.e("TapSyncWatch", "✅ BLUETOOTH_SCAN granted")
                startScan()
            } else {
                Log.e("TapSyncWatch", "❌ BLUETOOTH_SCAN denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ hält die App sichtbar
        setContentView(R.layout.activity_main)

        Log.e("TapSyncWatch", "⌚ Tap Sync Ready – App stays visible")

        // 👉 TAP auf gesamtes Display
        findViewById<View>(R.id.root).setOnClickListener {
            Log.e("TapSyncWatch", "👆 TAP")

            if (!scanning) {
                checkPermissionAndStartScan()
            }
        }

        val bluetoothManager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        checkPermissionAndStartScan()
    }

    private fun checkPermissionAndStartScan() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            scanPermissionLauncher.launch(
                Manifest.permission.BLUETOOTH_SCAN
            )
        }
    }

    private fun startScan() {
        scanning = true

        val scanner = bluetoothAdapter.bluetoothLeScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(
            listOf(filter),
            settings,
            scanCallback
        )

        Log.e("TapSyncWatch", "🔍 BLE Scan gestartet (Service-Filter aktiv)")
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device

            Log.e(
                "TapSyncWatch",
                "🎯 GEFUNDEN: ${device.name ?: "Unbekannt"} / ${device.address}"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("TapSyncWatch", "❌ Scan fehlgeschlagen: $errorCode")
            scanning = false
        }
    }
}
