package com.example.tapsyncwatch.presentation

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tapsyncwatch.R
import java.util.UUID
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattCharacteristic


class MainActivity : ComponentActivity() {

    private val SERVICE_UUID =
        UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")

    private val CHAR_TAP_UUID =
        UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var tapCharacteristic: BluetoothGattCharacteristic? = null
    private var scanning = false

    // ---------- Permission ----------
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

        // ✅ App bleibt sichtbar
        setContentView(R.layout.activity_main)
        Log.e("TapSyncWatch", "⌚ Tap Sync Ready")

        val root = findViewById<FrameLayout>(R.id.root)

        // 👆 TAP → BLE WRITE
        root.setOnClickListener {
            Log.e("TapSyncWatch", "👆 TAP")
            sendTap()
        }

        val bluetoothManager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        checkPermissionAndStartScan()
    }

    // ---------- TAP SENDEN ----------
    private fun sendTap() {
        val gatt = bluetoothGatt
        val characteristic = tapCharacteristic

        if (gatt == null || characteristic == null) {
            Log.e("TapSyncWatch", "⚠️ TAP Characteristic noch nicht bereit")
            return
        }

        characteristic.value = "TAP".toByteArray(Charsets.UTF_8)
        val success = gatt.writeCharacteristic(characteristic)

        Log.e("TapSyncWatch", "📤 TAP gesendet → success=$success")
    }

    // ---------- BLE PERMISSION ----------
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

    // ---------- SCAN ----------
    private fun startScan() {
        if (scanning) return
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

        Log.e("TapSyncWatch", "🔍 BLE Scan gestartet")
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device
            Log.e(
                "TapSyncWatch",
                "🎯 GEFUNDEN: ${device.name} / ${device.address}"
            )

            bluetoothAdapter.bluetoothLeScanner.stopScan(this)
            scanning = false

            bluetoothGatt = device.connectGatt(
                this@MainActivity,
                false,
                gattCallback
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("TapSyncWatch", "❌ Scan fehlgeschlagen: $errorCode")
            scanning = false
        }
    }

    // ---------- GATT ----------
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e("TapSyncWatch", "🔗 GATT verbunden")
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            val service = gatt.getService(SERVICE_UUID)
            tapCharacteristic = service?.getCharacteristic(CHAR_TAP_UUID)

            if (tapCharacteristic != null) {
                Log.e("TapSyncWatch", "✅ TAP Characteristic bereit")
            } else {
                Log.e("TapSyncWatch", "❌ TAP Characteristic NICHT gefunden")
            }
        }
    }
}
