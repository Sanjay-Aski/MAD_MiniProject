package com.example.miniproject.data.model

/**
 * Presence record published to Firestore `discovery/{uid}` while
 * a user has "Get Connect" active.  TTL-based: expires after 60 s.
 */
data class DiscoveringUser(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val discoveringUntil: Long = 0L,   // epoch-ms; entry is stale when < now
    val totalSteps: Long = 0L,
    val totalCalories: Double = 0.0
)
