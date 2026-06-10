package com.nexuzy.samvixo.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * MeshNetworkService — Emergency offline mesh networking (§14 PID)
 *
 * Uses:
 *   - Bluetooth LE advertising + scanning to discover nearby Samvixo devices
 *   - Wi-Fi Direct (WifiP2pManager) for actual data transfer
 *
 * Activated automatically when internet is unavailable.
 * Supports: text messages, location sharing, emergency broadcasts.
 *
 * On-device AI (ML Kit, Tier 2) continues to work while this service is running.
 */
class MeshNetworkService : Service() {

    companion object {
        const val TAG = "MeshNetworkService"
        // UUID identifying Samvixo mesh peers over BLE
        val SAMVIXO_MESH_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        const val ACTION_START_MESH  = "com.nexuzy.samvixo.START_MESH"
        const val ACTION_STOP_MESH   = "com.nexuzy.samvixo.STOP_MESH"
        const val ACTION_SEND_MESH   = "com.nexuzy.samvixo.SEND_MESH_MSG"
        const val EXTRA_MESH_PAYLOAD = "mesh_payload"
    }

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter
        bleAdvertiser = btAdapter?.bluetoothLeAdvertiser
        bleScanner    = btAdapter?.bluetoothLeScanner
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        wifiP2pChannel = wifiP2pManager?.initialize(this, mainLooper, null)
        Log.d(TAG, "MeshNetworkService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MESH -> {
                startBleAdvertising()
                startBleScanning()
                startWifiDirect()
            }
            ACTION_STOP_MESH -> stopMesh()
            ACTION_SEND_MESH -> {
                val payload = intent.getStringExtra(EXTRA_MESH_PAYLOAD) ?: return START_STICKY
                sendViaMesh(payload)
            }
        }
        return START_STICKY
    }

    // ── BLE Advertising — makes this device discoverable as Samvixo mesh node ──────────────
    private fun startBleAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SAMVIXO_MESH_UUID))
            .setIncludeDeviceName(false)
            .build()
        bleAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "BLE advertising started")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed: $errorCode")
            }
        })
    }

    // ── BLE Scanning — discovers other Samvixo mesh nodes nearby ────────────────────
    private fun startBleScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SAMVIXO_MESH_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner?.startScan(
            listOf(filter),
            scanSettings,
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let { device ->
                        Log.d(TAG, "Found Samvixo peer: ${device.address}")
                        // Initiate Wi-Fi Direct connection to this peer
                        connectWifiDirect(device.address)
                    }
                }
            }
        )
    }

    // ── Wi-Fi Direct — actual data transfer between peers ─────────────────────────
    private fun startWifiDirect() {
        wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Wi-Fi Direct peer discovery started") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Wi-Fi Direct discovery failed: $reason") }
        })
    }

    private fun connectWifiDirect(deviceMac: String) {
        val config = WifiP2pConfig().apply { deviceAddress = deviceMac }
        wifiP2pManager?.connect(wifiP2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Wi-Fi Direct connect initiated to $deviceMac") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Wi-Fi Direct connect failed: $reason") }
        })
    }

    /**
     * Sends encrypted payload to all connected mesh peers.
     * In real implementation: serialize MessageEntity → encrypt with
     * peer's public key → send via Wi-Fi Direct socket.
     */
    private fun sendViaMesh(payload: String) {
        Log.d(TAG, "Sending mesh payload: ${payload.take(50)}")
        // TODO: implement actual socket data transfer to connected peers
    }

    private fun stopMesh() {
        bleAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
        bleScanner?.stopScan(object : ScanCallback() {})
        Log.d(TAG, "Mesh stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMesh()
    }
}
