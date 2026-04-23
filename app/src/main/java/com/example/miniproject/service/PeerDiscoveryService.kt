package com.example.miniproject.service

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.miniproject.data.model.BluetoothPeer
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Background service for peer discovery and communication via Bluetooth
 * - Advertises device presence using Bluetooth RFCOMM sockets
 * - Discovers other devices running this app
 * - Handles peer data exchange
 */
class PeerDiscoveryService : Service() {
    
    companion object {
        private const val TAG = "PeerDiscoveryService"
        private const val UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB" // Standard RFCOMM UUID
        private const val SERVICE_NAME = "MiniProjectRunTracker"
        private const val PREFS_NAME = "peer_discovery_prefs"
    }
    
    private val binder = PeerDiscoveryBinder()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    
    private var serverSocket: BluetoothServerSocket? = null
    private var isAcceptingConnections = false
    
    // State flows
    private val _discoveredPeers = MutableStateFlow<List<BluetoothPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<BluetoothPeer>> = _discoveredPeers
    
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering
    
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "PeerDiscoveryService created")
        startBluetoothServer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PeerDiscoveryService started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    /**
     * Start Bluetooth server to accept incoming connections
     */
    private fun startBluetoothServer() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Missing Bluetooth permissions for server — skipping RFCOMM server start")
            return
        }
        
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth adapter not available")
            return
        }

        if (bluetoothAdapter.isEnabled.not()) {
            Log.w(TAG, "Bluetooth is disabled — skipping RFCOMM server start")
            return
        }
        
        scope.launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    UUID.fromString(UUID_STRING)
                )
                isAcceptingConnections = true
                Log.d(TAG, "Bluetooth server started, listening for connections")
                
                while (isAcceptingConnections) {
                    try {
                        val socket = serverSocket?.accept()
                        socket?.let { 
                            handleIncomingConnection(it)
                        }
                    } catch (e: IOException) {
                        if (isAcceptingConnections) {
                            Log.e(TAG, "Error accepting connection: ${e.message}")
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException — BLUETOOTH_CONNECT permission missing: ${e.message}")
            } catch (e: IOException) {
                Log.e(TAG, "Error creating server socket: ${e.message}")
            }
        }
    }
    
    /**
     * Handle incoming Bluetooth connection and peer data exchange
     */
    private fun handleIncomingConnection(socket: BluetoothSocket) {
        scope.launch {
            try {
                val remoteDevice = socket.remoteDevice
                Log.d(TAG, "Connection from ${remoteDevice.name} (${remoteDevice.address})")
                
                val inputStream = socket.inputStream
                val outputStream = socket.outputStream
                
                // Receive peer data
                val peerJson = readFromSocket(inputStream)
                if (peerJson.isNotEmpty()) {
                    val receivedPeer = gson.fromJson(peerJson, BluetoothPeer::class.java)
                    addDiscoveredPeer(receivedPeer)
                    
                    // Send our profile back
                    val ourProfile = buildCurrentUserProfile()
                    val ourProfileJson = gson.toJson(ourProfile)
                    writeToSocket(outputStream, ourProfileJson)
                    
                    Log.d(TAG, "Peer data exchanged with ${remoteDevice.name}")
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling connection: ${e.message}")
            }
        }
    }
    
    /**
     * Start discovering nearby peers with Bluetooth devices
     */
    fun startDiscovery() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Missing Bluetooth permissions for discovery")
            return
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        
        _isDiscovering.value = true
        scope.launch {
            try {
                // Get bonded devices (already paired)
                val bondedDevices = bluetoothAdapter.bondedDevices
                Log.d(TAG, "Found ${bondedDevices.size} bonded devices")
                
                for (device in bondedDevices) {
                    attemptConnectionWithPeer(device)
                }
                
                // Simulate discovery delay
                handler.postDelayed({
                    _isDiscovering.value = false
                    Log.d(TAG, "Discovery completed")
                }, 3000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during discovery: ${e.message}")
                _isDiscovering.value = false
            }
        }
    }
    
    /**
     * Attempt to connect with a discovered peer device
     */
    private fun attemptConnectionWithPeer(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) return
        
        scope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(UUID_STRING))
                
                // Cancel discovery while connecting
                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter.cancelDiscovery()
                }
                
                socket.connect()
                Log.d(TAG, "Connected to ${device.name}")
                
                // Send our profile
                val ourProfile = buildCurrentUserProfile()
                val ourProfileJson = gson.toJson(ourProfile)
                writeToSocket(socket.outputStream, ourProfileJson)
                
                // Receive peer's profile
                val peerJson = readFromSocket(socket.inputStream)
                if (peerJson.isNotEmpty()) {
                    val receivedPeer = gson.fromJson(peerJson, BluetoothPeer::class.java)
                    addDiscoveredPeer(receivedPeer)
                }
                
                socket.close()
            } catch (e: IOException) {
                Log.d(TAG, "Could not connect to ${device.name}: ${e.message}")
            }
        }
    }
    
    /**
     * Add discovered peer to list
     */
    private fun addDiscoveredPeer(peer: BluetoothPeer) {
        val currentPeers = _discoveredPeers.value.toMutableList()
        
        // Update if peer already exists, otherwise add
        val existingIndex = currentPeers.indexOfFirst { it.peerId == peer.peerId }
        if (existingIndex >= 0) {
            currentPeers[existingIndex] = peer
        } else {
            currentPeers.add(peer)
        }
        
        _discoveredPeers.value = currentPeers
        Log.d(TAG, "Peer added/updated: ${peer.peerName}, Total peers: ${currentPeers.size}")
    }
    
    /**
     * Stop discovery
     */
    fun stopDiscovery() {
        _isDiscovering.value = false
    }
    
    /**
     * Accept a peer connection (mutual acceptance)
     */
    fun acceptPeer(peerId: String): Boolean {
        val peer = _discoveredPeers.value.find { it.peerId == peerId } ?: return false
        
        val updatedPeer = peer.copy(
            isMutuallyAccepted = true,
            lastSync = System.currentTimeMillis()
        )
        
        // Update in local list
        _discoveredPeers.value = _discoveredPeers.value.map {
            if (it.peerId == peerId) updatedPeer else it
        }
        
        // Save to persistent storage
        savePeerToPrefs(updatedPeer)
        
        Log.d(TAG, "Peer accepted: ${peer.peerName}")
        return true
    }
    
    /**
     * Read data from Bluetooth socket
     */
    private fun readFromSocket(inputStream: InputStream): String {
        return try {
            val buffer = ByteArray(1024)
            val bytesRead = inputStream.read(buffer)
            if (bytesRead > 0) {
                String(buffer, 0, bytesRead)
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from socket: ${e.message}")
            ""
        }
    }
    
    /**
     * Write data to Bluetooth socket
     */
    private fun writeToSocket(outputStream: OutputStream, data: String) {
        try {
            outputStream.write(data.toByteArray())
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to socket: ${e.message}")
        }
    }
    
    /**
     * Build current user's profile
     */
    private fun buildCurrentUserProfile(): BluetoothPeer {
        val userPrefs = getSharedPreferences("user_profile", MODE_PRIVATE)
        val userEmail = getSharedPreferences("email_preferences", MODE_PRIVATE)
            .getString("selected_email", "unknown") ?: "unknown"

        // Safely get Bluetooth address only if BLUETOOTH_CONNECT permission is granted
        val deviceId = if (hasBluetoothPermission() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothAdapter?.address ?: "unknown"
        } else {
            "unknown"
        }
        
        return BluetoothPeer(
            peerId = UUID.randomUUID().toString(),
            peerName = Build.MODEL,
            peerDeviceId = deviceId,
            peerUserId = userEmail,
            peerProfileName = userPrefs.getString("user_name", "User") ?: "User",
            peerHeight = userPrefs.getFloat("user_height", 0f).toDouble(),
            peerWeight = userPrefs.getFloat("user_weight", 0f).toDouble(),
            peerGender = userPrefs.getString("user_gender", "Other") ?: "Other",
            peerTotalDistance = userPrefs.getFloat("total_distance", 0f).toDouble(),
            peerAvgSpeed = userPrefs.getFloat("avg_speed", 0f).toDouble(),
            peerRunCount = userPrefs.getInt("run_count", 0),
            peerCaloriesBurned = userPrefs.getFloat("calories_burned", 0f).toDouble(),
            isConnected = true,
            isMutuallyAccepted = false,
            connectedById = "peer",
            connectedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Save peer to persistent storage
     */
    private fun savePeerToPrefs(peer: BluetoothPeer) {
        try {
            val peersJson = prefs.getString("connected_peers", "[]") ?: "[]"
            val peers = gson.fromJson(peersJson, Array<BluetoothPeer>::class.java).toMutableList()
            
            val existingIndex = peers.indexOfFirst { it.peerId == peer.peerId }
            if (existingIndex >= 0) {
                peers[existingIndex] = peer
            } else {
                peers.add(peer)
            }
            
            prefs.edit().putString("connected_peers", gson.toJson(peers)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving peer to prefs: ${e.message}")
        }
    }
    
    /**
     * Check if Bluetooth permissions are granted
     */
    private fun hasBluetoothPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.BLUETOOTH)
        }
        
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isAcceptingConnections = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        Log.d(TAG, "PeerDiscoveryService destroyed")
    }
    
    /**
     * Binder for local service binding
     */
    inner class PeerDiscoveryBinder : Binder() {
        fun getService(): PeerDiscoveryService = this@PeerDiscoveryService
    }
}
