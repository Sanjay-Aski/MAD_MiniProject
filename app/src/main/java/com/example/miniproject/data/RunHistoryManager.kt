package com.example.miniproject.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.miniproject.data.model.RunSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Module 4: Run History & Reports
 * Manages storage, retrieval, filtering, and reporting of past runs
 */

data class RunFilter(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minDistance: Double? = null,
    val maxDistance: Double? = null,
    val minSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val sortBy: SortOption = SortOption.DATE_DESC
)

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    DISTANCE_DESC,
    DISTANCE_ASC,
    SPEED_DESC,
    SPEED_ASC,
    DURATION_DESC,
    DURATION_ASC
}

class RunHistoryManager(private val localRunRepository: LocalRunRepository) {
    private val TAG = "RunHistoryManager"
    
    // All user runs
    private val _allRuns = MutableLiveData<List<RunSession>>()
    val allRuns: LiveData<List<RunSession>> = _allRuns
    
    // Filtered results
    private val _filteredRuns = MutableLiveData<List<RunSession>>()
    val filteredRuns: LiveData<List<RunSession>> = _filteredRuns
    
    // Statistics
    private val _runStatistics = MutableLiveData<Map<String, Any>>()
    val runStatistics: LiveData<Map<String, Any>> = _runStatistics
    
    /**
     * Load all runs from database
     */
    suspend fun loadAllRuns(): List<RunSession> {
        return try {
            val runs = localRunRepository.getRunSessions()
            _allRuns.value = runs
            Log.d(TAG, "Loaded ${runs.size} runs from database")
            runs
        } catch (e: Exception) {
            Log.e(TAG, " Error loading runs: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Filter runs by various criteria
     */
    fun filterRuns(filter: RunFilter) {
        val runs = _allRuns.value ?: return
        
        var filtered = runs
            .filter { run ->
                filter.startDate?.let { run.startTime >= it } ?: true
            }
            .filter { run ->
                filter.endDate?.let { run.startTime <= it } ?: true
            }
            .filter { run ->
                filter.minDistance?.let { run.distance >= it } ?: true
            }
            .filter { run ->
                filter.maxDistance?.let { run.distance <= it } ?: true
            }
            .filter { run ->
                filter.minSpeed?.let { run.avgSpeed >= it } ?: true
            }
            .filter { run ->
                filter.maxSpeed?.let { run.avgSpeed <= it } ?: true
            }
        
        // Sort results
        filtered = when (filter.sortBy) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.startTime }
            SortOption.DATE_ASC -> filtered.sortedBy { it.startTime }
            SortOption.DISTANCE_DESC -> filtered.sortedByDescending { it.distance }
            SortOption.DISTANCE_ASC -> filtered.sortedBy { it.distance }
            SortOption.SPEED_DESC -> filtered.sortedByDescending { it.avgSpeed }
            SortOption.SPEED_ASC -> filtered.sortedBy { it.avgSpeed }
            SortOption.DURATION_DESC -> filtered.sortedByDescending { it.duration }
            SortOption.DURATION_ASC -> filtered.sortedBy { it.duration }
        }
        
        _filteredRuns.value = filtered
        
        Log.d(TAG, "🔍 Filtered ${filtered.size} runs (from ${runs.size} total)")
        Log.d(TAG, "   Filter: startDate=${filter.startDate}, endDate=${filter.endDate}")
        Log.d(TAG, "   Distance: ${filter.minDistance}-${filter.maxDistance} km")
        Log.d(TAG, "   Speed: ${filter.minSpeed}-${filter.maxSpeed} km/h")
    }
    
    /**
     * Get runs for a specific date
     */
    fun getRunsForDate(date: Long): List<RunSession> {
        val runs = _allRuns.value ?: return emptyList()
        
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val dayStart = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val dayEnd = calendar.timeInMillis
        
        return runs.filter { it.startTime in dayStart..dayEnd }
    }
    
    /**
     * Get runs for current week
     */
    fun getWeekRuns(): List<RunSession> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val weekStart = calendar.timeInMillis
        
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val weekEnd = calendar.timeInMillis
        
        val runs = _allRuns.value ?: return emptyList()
        return runs.filter { it.startTime in weekStart..weekEnd }
    }
    
    /**
     * Get runs for current month
     */
    fun getMonthRuns(): List<RunSession> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val monthStart = calendar.timeInMillis
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val monthEnd = calendar.timeInMillis
        
        val runs = _allRuns.value ?: return emptyList()
        return runs.filter { it.startTime in monthStart..monthEnd }
    }
    
    /**
     * Calculate summary statistics
     */
    fun calculateStatistics(): Map<String, Any> {
        val runs = _allRuns.value ?: return emptyMap()
        
        if (runs.isEmpty()) {
            Log.w(TAG, "⚠️ No runs available for statistics")
            return emptyMap()
        }
        
        val stats = mutableMapOf<String, Any>()
        
        // Basic counts
        stats["totalRuns"] = runs.size
        
        // Distance statistics
        val totalDistance = runs.sumOf { it.distance }
        val avgDistance = totalDistance / runs.size
        val maxDistance = runs.maxOf { it.distance }
        val minDistance = runs.minOf { it.distance }
        
        stats["totalDistance"] = totalDistance
        stats["avgDistance"] = avgDistance
        stats["maxDistance"] = maxDistance
        stats["minDistance"] = minDistance
        
        // Duration statistics
        val totalDuration = runs.sumOf { it.duration }
        val avgDuration = totalDuration / runs.size
        
        stats["totalDuration"] = totalDuration
        stats["avgDuration"] = avgDuration
        
        // Speed statistics
        val avgSpeed = runs.sumOf { it.avgSpeed } / runs.size
        val maxSpeed = runs.maxOf { it.maxSpeed }
        
        stats["avgSpeed"] = avgSpeed
        stats["maxSpeed"] = maxSpeed
        
        // Calories
        val totalCalories = runs.sumOf { it.calories }
        stats["totalCalories"] = totalCalories
        
        // Steps
        val totalSteps = runs.sumOf { it.steps }
        stats["totalSteps"] = totalSteps
        
        _runStatistics.value = stats
        
        Log.d(TAG, """
            📊 Run Statistics:
            ├─ Total Runs: ${stats["totalRuns"]}
            ├─ Total Distance: ${String.format("%.2f", totalDistance)} km
            ├─ Avg Distance: ${String.format("%.2f", avgDistance)} km
            ├─ Total Duration: ${formatTime(totalDuration as Long)}
            ├─ Avg Speed: ${String.format("%.2f", avgSpeed)} km/h
            ├─ Max Speed: ${String.format("%.2f", maxSpeed)} km/h
            ├─ Total Calories: ${String.format("%.1f", totalCalories)}
            └─ Total Steps: $totalSteps
        """.trimIndent())
        
        return stats
    }
    
    /**
     * Generate run summary for display
     */
    fun getRunSummary(run: RunSession): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val date = dateFormat.format(run.startTime)
        
        val pace = if (run.distance > 0) {
            (run.duration / 60.0) / run.distance
        } else {
            0.0
        }
        
        return """
            📍 ${run.title}
            └─ Date: $date
               Distance: ${String.format("%.2f", run.distance)} km
               Duration: ${formatTime(run.duration)}
               Avg Speed: ${String.format("%.2f", run.avgSpeed)} km/h
               Pace: ${String.format("%.2f", pace)} min/km
               Steps: ${run.steps}
               Calories: ${String.format("%.1f", run.calories)}
        """.trimIndent()
    }
    
    /**
     * Save a completed run
     */
    suspend fun saveRun(run: RunSession): Boolean {
        return try {
            val success = localRunRepository.saveRunSessionWithRoute(run, emptyList())
            if (success) {
                // Add to local list
                val currentRuns = _allRuns.value?.toMutableList() ?: mutableListOf()
                currentRuns.add(run)
                _allRuns.value = currentRuns
                Log.d(TAG, "Run saved: ${run.runId}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error saving run: ${e.message}", e)
            false
        }
    }
    
    // ========== Helper Methods ==========
    
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "$hours:${String.format("%02d", minutes)}:${String.format("%02d", secs)}"
        } else {
            "${String.format("%02d", minutes)}:${String.format("%02d", secs)}"
        }
    }
}
