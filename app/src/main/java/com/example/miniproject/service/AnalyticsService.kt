package com.example.miniproject.service

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.miniproject.data.model.RunSession
import kotlin.math.max
import kotlin.math.min

/**
 * Module 3: Performance Analytics
 * Converts raw GPS and sensor data into actionable performance insights
 */

data class RunMetrics(
    val distance: Double = 0.0,              // km
    val duration: Long = 0L,                 // seconds
    val avgSpeed: Double = 0.0,              // km/h
    val maxSpeed: Double = 0.0,              // km/h
    val pace: Double = 0.0,                  // min/km
    val steps: Int = 0,
    val calories: Double = 0.0,
    val cadence: Double = 0.0,               // steps/minute
    val elevationGain: Double = 0.0          // meters
)

data class WeeklyAnalytics(
    val totalDistance: Double = 0.0,
    val totalDuration: Long = 0L,
    val avgSpeed: Double = 0.0,
    val totalCalories: Double = 0.0,
    val totalSteps: Int = 0,
    val runCount: Int = 0,
    val bestRun: RunSession? = null,
    val bestDistance: Double = 0.0,
    val bestSpeed: Double = 0.0
)

class AnalyticsService(private val userWeight: Double = 70.0) {
    private val TAG = "AnalyticsService"
    
    // Real-time metrics during a run
    private val _currentMetrics = MutableLiveData<RunMetrics>()
    val currentMetrics: LiveData<RunMetrics> = _currentMetrics
    
    // Weekly summary
    private val _weeklyAnalytics = MutableLiveData<WeeklyAnalytics>()
    val weeklyAnalytics: LiveData<WeeklyAnalytics> = _weeklyAnalytics
    
    // Personal records
    private val _personalRecords = MutableLiveData<Map<String, Double>>()
    val personalRecords: LiveData<Map<String, Double>> = _personalRecords
    
    /**
     * Calculate real-time metrics during a run
     */
    fun calculateRunMetrics(
        totalDistance: Double,
        elapsedSeconds: Long,
        maxSpeedVal: Double,
        stepCount: Int,
        gpsPoints: Int = 0
    ): RunMetrics {
        if (elapsedSeconds <= 0) {
            Log.w(TAG, "⚠️ Invalid elapsed time for metrics: $elapsedSeconds")
            return RunMetrics()
        }
        
        // Average speed in km/h
        val avgSpeedKmh = if (elapsedSeconds > 0) {
            (totalDistance / (elapsedSeconds / 3600.0))
        } else {
            0.0
        }
        
        // Pace in minutes per km
        val paceMinKm = if (totalDistance > 0) {
            (elapsedSeconds / 60.0) / totalDistance
        } else {
            0.0
        }
        
        // Calories burned: distance × weight × 1.036
        val caloriesBurned = totalDistance * userWeight * 1.036
        
        // Cadence in steps per minute
        val cadenceStepMin = if (elapsedSeconds > 0) {
            (stepCount.toDouble() / (elapsedSeconds / 60.0))
        } else {
            0.0
        }
        
        val metrics = RunMetrics(
            distance = totalDistance,
            duration = elapsedSeconds,
            avgSpeed = avgSpeedKmh,
            maxSpeed = maxSpeedVal,
            pace = paceMinKm,
            steps = stepCount,
            calories = caloriesBurned,
            cadence = cadenceStepMin,
            elevationGain = 0.0 // Would need altimeter data
        )
        
        _currentMetrics.value = metrics
        
        Log.d(TAG, "📊 Metrics Updated:")
        Log.d(TAG, "   Distance: ${String.format("%.3f", totalDistance)} km")
        Log.d(TAG, "   Time: ${formatTime(elapsedSeconds)}")
        Log.d(TAG, "   Avg Speed: ${String.format("%.2f", avgSpeedKmh)} km/h")
        Log.d(TAG, "   Max Speed: ${String.format("%.2f", maxSpeedVal)} km/h")
        Log.d(TAG, "   Pace: ${String.format("%.2f", paceMinKm)} min/km")
        Log.d(TAG, "   Calories: ${String.format("%.1f", caloriesBurned)} cal")
        Log.d(TAG, "   Cadence: ${String.format("%.1f", cadenceStepMin)} steps/min")
        
        return metrics
    }
    
    /**
     * Calculate weekly analytics from a list of runs
     */
    fun calculateWeeklyAnalytics(weekRuns: List<RunSession>): WeeklyAnalytics {
        if (weekRuns.isEmpty()) {
            Log.d(TAG, "📅 No runs this week for analytics")
            return WeeklyAnalytics()
        }
        
        var totalDistance = 0.0
        var totalDuration = 0L
        var totalCalories = 0.0
        var totalSteps = 0
        var totalAvgSpeed = 0.0
        var bestRun: RunSession? = null
        var bestDistance = 0.0
        var bestSpeed = 0.0
        
        for (run in weekRuns) {
            totalDistance += run.distance
            totalDuration += run.duration
            totalCalories += run.calories
            totalSteps += run.steps
            totalAvgSpeed += run.avgSpeed
            
            if (run.distance > bestDistance) {
                bestDistance = run.distance
                bestRun = run
            }
            
            if (run.maxSpeed > bestSpeed) {
                bestSpeed = run.maxSpeed
            }
        }
        
        val avgSpeed = if (weekRuns.isNotEmpty()) totalAvgSpeed / weekRuns.size else 0.0
        
        val analytics = WeeklyAnalytics(
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            avgSpeed = avgSpeed,
            totalCalories = totalCalories,
            totalSteps = totalSteps,
            runCount = weekRuns.size,
            bestRun = bestRun,
            bestDistance = bestDistance,
            bestSpeed = bestSpeed
        )
        
        _weeklyAnalytics.value = analytics
        
        Log.d(TAG, """
            📅 Weekly Analytics:
            ├─ Total Runs: ${weekRuns.size}
            ├─ Total Distance: ${String.format("%.2f", totalDistance)} km
            ├─ Total Time: ${formatTime(totalDuration)}
            ├─ Avg Speed: ${String.format("%.2f", avgSpeed)} km/h
            ├─ Best Distance: ${String.format("%.2f", bestDistance)} km
            ├─ Best Speed: ${String.format("%.2f", bestSpeed)} km/h
            ├─ Total Steps: $totalSteps
            └─ Total Calories: ${String.format("%.1f", totalCalories)} cal
        """.trimIndent())
        
        return analytics
    }
    
    /**
     * Extract performance trends (for charting)
     */
    fun calculatePerformanceTrends(runs: List<RunSession>): Map<String, List<Double>> {
        val distances = mutableListOf<Double>()
        val speeds = mutableListOf<Double>()
        val calories = mutableListOf<Double>()
        val paces = mutableListOf<Double>()
        
        for (run in runs.sortedBy { it.startTime }) {
            distances.add(run.distance)
            speeds.add(run.avgSpeed)
            calories.add(run.calories)
            
            // Calculate pace (min/km)
            val pace = if (run.distance > 0) {
                (run.duration / 60.0) / run.distance
            } else {
                0.0
            }
            paces.add(pace)
        }
        
        val trends = mapOf(
            "distances" to distances,
            "avgSpeeds" to speeds,
            "calories" to calories,
            "paces" to paces
        )
        
        Log.d(TAG, "📈 Performance Trends calculated for ${runs.size} runs")
        
        return trends
    }
    
    /**
     * Calculate personal records
     */
    fun updatePersonalRecords(run: RunSession): Map<String, Double> {
        val records = mutableMapOf<String, Double>()
        
        // These would typically be stored in database and compared
        records["longestDistance"] = run.distance
        records["fastestSpeed"] = run.maxSpeed
        records["bestAvgSpeed"] = run.avgSpeed
        records["mostSteps"] = run.steps.toDouble()
        
        _personalRecords.value = records
        
        Log.d(TAG, " Personal Records Updated:")
        records.forEach { (key, value) ->
            Log.d(TAG, "   $key: $value")
        }
        
        return records
    }
    
    /**
     * Get pace category (slow/moderate/fast)
     */
    fun getPaceCategory(paceMinKm: Double): String {
        return when {
            paceMinKm < 5.0 -> "🔥 Very Fast (< 5 min/km)"
            paceMinKm < 6.0 -> "💨 Fast (5-6 min/km)"
            paceMinKm < 7.5 -> "⚡ Moderate (6-7.5 min/km)"
            paceMinKm < 10.0 -> "🚶 Slow (7.5-10 min/km)"
            else -> "🐢 Very Slow (> 10 min/km)"
        }
    }
    
    /**
     * Get performance rating based on metrics
     */
    fun getRatingMessage(metrics: RunMetrics): String {
        return buildString {
            append("⭐ ")
            when {
                metrics.avgSpeed > 12.0 -> append("Excellent Pace!")
                metrics.avgSpeed > 10.0 -> append("Great Effort!")
                metrics.avgSpeed > 8.0 -> append("Good Run!")
                metrics.avgSpeed > 6.0 -> append("Solid Performance!")
                else -> append("Keep it Up!")
            }
        }
    }
    
    // ========== Helper Methods ==========
    
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "$hours:${String.format("%02d", minutes)}:${String.format("%02d", secs)}"
    }
    
    fun getUserWeight(): Double = userWeight
}
