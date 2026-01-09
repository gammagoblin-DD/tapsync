package com.example.tapsyncwatch.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tapsyncwatch.R
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val SERVICE_UUID =
        UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var permissionGranted = false

    private val scanPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted
            Log.e("TapSyncWatch", "Permission result: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔴 Hält App sichtbar
        setContentView(R.layout.activity_main)
        Log.e("TapSyncWatch", "⌚ Tap Sync Ready – App stays visible")

        val bluetoothManager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted = true
        } else {
            scanPermissionLauncher.launch(
                Manifest.permission.BLUETOOTH_SCAN
            )
        }
    }

    override fun onResume() {
        super.onResume()

        // 🔥 ERST HIER starten!
        if (permissionGranted) {
            startScan()
        }
    }

    private fun startScan() {
        Log.e("TapSyncWatch", "🔍 startScan() SAFE")

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
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            Log.e(
                "TapSyncWatch",
                "🎯 GEFUNDEN: ${result.device.address}"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("TapSyncWatch", "❌ Scan failed: $errorCode")
        }
    }
}
