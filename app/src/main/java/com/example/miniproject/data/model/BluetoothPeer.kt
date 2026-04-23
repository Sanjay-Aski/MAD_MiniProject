package com.example.miniproject.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a user connected via Bluetooth
 * Both users must mutually accept the connection to appear in leaderboard
 */
@Entity(tableName = "bluetooth_peers")
data class BluetoothPeer(
    @PrimaryKey
    val peerId: String = UUID.randomUUID().toString(),
    
    // Peer's identification
    val peerName: String = "",
    val peerDeviceId: String = "", // Bluetooth MAC address
    
    // Peer's profile info
    val peerUserId: String = "",
    val peerProfileName: String = "",
    val peerHeight: Double = 170.0, // cm
    val peerWeight: Double = 70.0, // kg
    val peerGender: String = "Male",
    
    // Connection status
    val isConnected: Boolean = false,
    val isMutuallyAccepted: Boolean = false, // Both sides accepted
    
    // Performance summary (from their latest runs)
    val peerTotalDistance: Double = 0.0, // km
    val peerAvgSpeed: Double = 0.0, // km/h
    val peerBestTime: Long = 0L, // seconds
    val peerRunCount: Int = 0,
    val peerCaloriesBurned: Double = 0.0,
    
    // Timestamps
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSync: Long = System.currentTimeMillis(),
    val connectedById: String = "", // Who initiated: "self" or "peer"
)
