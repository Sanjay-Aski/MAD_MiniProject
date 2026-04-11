package com.example.miniproject.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "run_sessions")
data class RunSession(
    @PrimaryKey
    val runId: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val duration: Long = 0L, // in seconds
    val distance: Double = 0.0, // in km
    val avgSpeed: Double = 0.0, // in km/h
    val maxSpeed: Double = 0.0, // in km/h
    val steps: Int = 0,
    val calories: Double = 0.0,
    val pathPointsCount: Int = 0,
    val title: String = "Running Session",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
