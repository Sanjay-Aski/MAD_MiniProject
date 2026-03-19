package com.example.miniproject.data.model

data class DailyStats(
    val date: String = "", // YYYY-MM-DD format
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0, // in km
    val totalDuration: Long = 0L, // in seconds
    val totalCalories: Double = 0.0,
    val avgSpeed: Double = 0.0, // in km/h
    val maxSpeed: Double = 0.0, // in km/h
    val runsCount: Int = 0,
    val stepGoal: Int = 10000,
    val calorieGoal: Double = 500.0
)

data class WeeklyStats(
    val weekStart: String = "", // YYYY-MM-DD format
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0,
    val totalDuration: Long = 0L,
    val totalCalories: Double = 0.0,
    val avgDailySteps: Int = 0,
    val runsCount: Int = 0,
    val dailyStatsMap: Map<String, DailyStats> = emptyMap()
)
