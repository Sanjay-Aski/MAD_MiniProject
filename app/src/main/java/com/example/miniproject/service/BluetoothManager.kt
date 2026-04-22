package com.example.miniproject.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.miniproject.data.model.BluetoothPeer
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Manages Bluetooth profile sharing and peer discovery
 * - Handles bidirectional profile exchange (User A initiates → User B accepts)
 * - Stores only mutually connected peers in leaderboard
 * - Manages Bluetooth device discovery and connection
 */
class AppBluetoothManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothManager"
        private const val PREFS_NAME = "bluetooth_peers_prefs"
        private const val PEERS_KEY = "peers_list"
        private const val LOCAL_DEVICE_ID = "local_device_id"
        private const val LOCAL_USER_ID = "local_user_id"
        private const val ACTION_SHARE_PROFILE = "com.example.miniproject.SHARE_PROFILE"
        private const val ACTION_ACCEPT_PROFILE = "com.example.miniproject.ACCEPT_PROFILE"
    }
    
    private val systemBluetoothManager: android.bluetooth.BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = systemBluetoothManager.adapter
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // State flows for observers
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices
    
    private val _connectedPeers = MutableStateFlow<List<BluetoothPeer>>(emptyList())
    val connectedPeers: StateFlow<List<BluetoothPeer>> = _connectedPeers
    
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { onDeviceDiscovered(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isDiscovering.value = true
                    Log.d(TAG, "Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                    Log.d(TAG, "Bluetooth discovery finished")
                }
                ACTION_SHARE_PROFILE -> {
                    val peerJson = intent.getStringExtra("peer_data")
                    peerJson?.let { onPeerProfileReceived(it) }
                }
                ACTION_ACCEPT_PROFILE -> {
                    val peerId = intent.getStringExtra("peer_id")
                    peerId?.let { acceptPeerProfile(it) }
                }
            }
        }
    }
    
    init {
        loadPeersFromStorage()
    }
    
    /**
     * Start Bluetooth device discovery
     * Requires: BLUETOOTH_SCAN permission
     */
    fun startDiscovery() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return
        }
        
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(ACTION_SHARE_PROFILE)
            addAction(ACTION_ACCEPT_PROFILE)
        }
        
        ContextCompat.registerReceiver(context, broadcastReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        bluetoothAdapter?.startDiscovery()
        Log.d(TAG, "Bluetooth discovery initiated")
    }
    
    /**
     * Stop Bluetooth discovery
     */
    fun stopDiscovery() {
        if (!hasBluetoothPermission()) return
        bluetoothAdapter?.cancelDiscovery()
        _isDiscovering.value = false
    }
    
    /**
     * Share current user's profile with a discovered peer
     * This initiates the connection request (one-way first)
     */
    fun shareProfileWithPeer(bluetoothDevice: BluetoothDevice, userProfile: UserProfileData) {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Missing Bluetooth permissions for sharing")
            return
        }
        
        val peer = BluetoothPeer(
            peerId = UUID.randomUUID().toString(),
            peerName = bluetoothDevice.name ?: "Unknown",
            peerDeviceId = bluetoothDevice.address,
            peerUserId = userProfile.userId,
            peerProfileName = userProfile.name,
            peerHeight = userProfile.height.toDouble(),
            peerWeight = userProfile.weight,
            peerGender = userProfile.gender,
            isConnected = true,
            isMutuallyAccepted = false, // Waiting for peer acceptance
            connectedById = "self", // This user initiated
            connectedAt = System.currentTimeMillis()
        )
        
        // Send profile data as broadcast (in real implementation, use Socket/NSD)
        val intent = Intent(ACTION_SHARE_PROFILE).apply {
            putExtra("peer_data", gson.toJson(peer))
        }
        context.sendBroadcast(intent)
        
        // Save locally
        savePeer(peer)
        Log.d(TAG, "Profile shared with peer: ${bluetoothDevice.name}")
    }
    
    /**
     * Accept an incoming profile from another user
     * Both users now appear in each other's leaderboards
     */
    fun acceptPeerProfile(peerId: String): Boolean {
        val peer = _connectedPeers.value.find { it.peerId == peerId } ?: return false
        
        val updatedPeer = peer.copy(
            isMutuallyAccepted = true,
            lastSync = System.currentTimeMillis()
        )
        
        // Broadcast acceptance back to peer
        val intent = Intent(ACTION_ACCEPT_PROFILE).apply {
            putExtra("peer_id", peerId)
        }
        context.sendBroadcast(intent)
        
        savePeer(updatedPeer)
        _connectedPeers.value = _connectedPeers.value.map {
            if (it.peerId == peerId) updatedPeer else it
        }
        
        Log.d(TAG, "Profile accepted from peer: ${peer.peerName}")
        return true
    }
    
    /**
     * Get only mutually connected peers (both sides accepted)
     * These are the only users who appear in the leaderboard
     */
    fun getMutuallyConnectedPeers(): List<BluetoothPeer> {
        return _connectedPeers.value.filter { it.isMutuallyAccepted }
    }
    
    /**
     * Update peer's latest performance data
     * Called when syncing data from peer's latest run
     */
    fun updatePeerPerformance(
        peerId: String,
        totalDistance: Double,
        avgSpeed: Double,
        bestTime: Long,
        runCount: Int,
        caloriesBurned: Double
    ) {
        val peer = _connectedPeers.value.find { it.peerId == peerId } ?: return
        
        val updatedPeer = peer.copy(
            peerTotalDistance = totalDistance,
            peerAvgSpeed = avgSpeed,
            peerBestTime = bestTime,
            peerRunCount = runCount,
            peerCaloriesBurned = caloriesBurned,
            lastSync = System.currentTimeMillis()
        )
        
        savePeer(updatedPeer)
        _connectedPeers.value = _connectedPeers.value.map {
            if (it.peerId == peerId) updatedPeer else it
        }
        
        Log.d(TAG, "Updated peer performance: ${peer.peerName}")
    }
    
    /**
     * Remove a peer from connections
     */
    fun removePeer(peerId: String) {
        _connectedPeers.value = _connectedPeers.value.filter { it.peerId != peerId }
        val peersJson = gson.toJson(_connectedPeers.value)
        prefs.edit().putString(PEERS_KEY, peersJson).apply()
        Log.d(TAG, "Peer removed")
    }
    
    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return if (hasBluetoothPermission()) {
            bluetoothAdapter?.isEnabled == true
        } else {
            false
        }
    }
    
    /**
     * Get local device's Bluetooth address
     */
    fun getLocalDeviceId(): String {
        return if (hasBluetoothPermission()) {
            bluetoothAdapter?.address ?: "unknown"
        } else {
            "unknown"
        }
    }
    
    // ============ PRIVATE METHODS ============
    
    private fun onDeviceDiscovered(device: BluetoothDevice) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        if (!currentDevices.any { it.address == device.address }) {
            currentDevices.add(device)
            _discoveredDevices.value = currentDevices
            Log.d(TAG, "Device discovered: ${device.name} (${device.address})")
        }
    }
    
    private fun onPeerProfileReceived(peerJson: String) {
        try {
            val peer = gson.fromJson(peerJson, BluetoothPeer::class.java)
            savePeer(peer)
            _connectedPeers.value = _connectedPeers.value.plus(peer)
            Log.d(TAG, "Peer profile received: ${peer.peerName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing peer profile: ${e.message}", e)
        }
    }
    
    private fun savePeer(peer: BluetoothPeer) {
        val updatedList = _connectedPeers.value.map {
            if (it.peerId == peer.peerId) peer else it
        }.ifEmpty { listOf(peer) }
        
        _connectedPeers.value = updatedList
        val peersJson = gson.toJson(updatedList)
        prefs.edit().putString(PEERS_KEY, peersJson).apply()
    }
    
    private fun loadPeersFromStorage() {
        try {
            val peersJson = prefs.getString(PEERS_KEY, "[]") ?: "[]"
            val peers = gson.fromJson(peersJson, Array<BluetoothPeer>::class.java).toList()
            _connectedPeers.value = peers
            Log.d(TAG, "Loaded ${peers.size} peers from storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading peers: ${e.message}", e)
        }
    }
    
    private fun hasBluetoothPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(broadcastReceiver)
            stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}

/**
 * Data class for user profile to share via Bluetooth
 */
data class UserProfileData(
    val userId: String = "",
    val name: String = "",
    val height: Int = 170,
    val weight: Double = 70.0,
    val gender: String = "Male"
)
