package com.example.miniproject.data.model

/**
 * Represents a mutually accepted friend — stored in Firestore.
 * Both sides must accept before appearing in leaderboard.
 */
data class Friend(
    val friendUserId: String = "",
    val friendName: String = "",
    val friendEmail: String = "",
    val addedAt: Long = System.currentTimeMillis(),

    // Live stats for leaderboard (updated after each run)
    val totalSteps: Long = 0L,
    val totalCalories: Double = 0.0,
    val totalDistance: Double = 0.0, // km
    val totalRuns: Int = 0,

    // Leaderboard rank (computed locally, not stored)
    val rank: Int = 0
)
