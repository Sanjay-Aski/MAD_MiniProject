package com.example.miniproject.data.model

/**
 * Represents a single entry in the leaderboard
 * Contains peer info + computed rank and stats
 */
data class LeaderboardEntry(
    val peer: BluetoothPeer,
    val rank: Int = 0,
    val scorePoints: Double = 0.0, // Calculated from multiple metrics
    
    // Computed metrics
    val displayDistance: String = "0.0 km",
    val displayAvgSpeed: String = "0.0 km/h",
    val displayBestTime: String = "00:00:00",
    val displayCalories: String = "0 kcal",
    
    // Comparison with current user
    val distanceDelta: Double = 0.0, // Positive if peer is ahead
    val speedDelta: Double = 0.0,
    val caloriesDelta: Double = 0.0,
)
