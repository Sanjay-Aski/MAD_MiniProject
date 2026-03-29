package com.example.miniproject.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.data.model.DailyStats
import kotlinx.coroutines.launch

/**
 * DashboardViewModel - Manages dashboard statistics and aggregated data
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private val localRunRepository = LocalRunRepository(application.applicationContext)

    // Today's stats
    private val _todayDistance = MutableLiveData<Double>(0.0)
    val todayDistance: LiveData<Double> = _todayDistance

    private val _todaySteps = MutableLiveData<Int>(0)
    val todaySteps: LiveData<Int> = _todaySteps

    private val _todayCalories = MutableLiveData<Double>(0.0)
    val todayCalories: LiveData<Double> = _todayCalories

    private val _todayDuration = MutableLiveData<Long>(0L)
    val todayDuration: LiveData<Long> = _todayDuration

    private val _todayAvgSpeed = MutableLiveData<Double>(0.0)
    val todayAvgSpeed: LiveData<Double> = _todayAvgSpeed

    private val _todayMaxSpeed = MutableLiveData<Double>(0.0)
    val todayMaxSpeed: LiveData<Double> = _todayMaxSpeed

    // Weekly stats
    private val _weeklyDistance = MutableLiveData<Double>(0.0)
    val weeklyDistance: LiveData<Double> = _weeklyDistance

    private val _weeklyRuns = MutableLiveData<Int>(0)
    val weeklyRuns: LiveData<Int> = _weeklyRuns

    private val _weeklyCalories = MutableLiveData<Double>(0.0)
    val weeklyCalories: LiveData<Double> = _weeklyCalories

    // Recent runs
    private val _recentRuns = MutableLiveData<List<RunSession>>(emptyList())
    val recentRuns: LiveData<List<RunSession>> = _recentRuns

    private val _allRunsLive = MutableLiveData<List<RunSession>>(emptyList())
    val allRunsLiveData: LiveData<List<RunSession>> = _allRunsLive

    // Goals
    private val _dailyGoal = MutableLiveData<Double>(5.0) // 5 km default
    val dailyGoal: LiveData<Double> = _dailyGoal

    private val _goalProgress = MutableLiveData<Float>(0f)
    val goalProgress: LiveData<Float> = _goalProgress

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // All runs for calculation
    private var allRuns = listOf<RunSession>()

    /**
     * Load dashboard data from local app database
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val runs = localRunRepository.getRunSessions()
                allRuns = runs
                
                calculateStats()
                updateGoalProgress()
                
                Log.d(TAG, "Dashboard data loaded: ${runs.size} runs found")
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error loading dashboard: ${e.message}"
                Log.e(TAG, "Error loading dashboard: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }

    fun startRealtimeUpdates() {
        loadDashboardData()
    }

    fun stopRealtimeUpdates() {
        // No-op for local repository mode.
    }

    /**
     * Calculate today's stats
     */
    private fun calculateStats() {
        val today = getTodayRange()
        val todayRuns = allRuns.filter { 
            it.startTime >= today.first && it.startTime <= today.second
        }

        var totalDistance = 0.0
        var totalSteps = 0
        var totalCalories = 0.0
        var totalDuration = 0L
        var totalSpeed = 0.0
        var maxSpeed = 0.0

        for (run in todayRuns) {
            totalDistance += run.distance
            totalSteps += run.steps
            totalCalories += run.calories
            totalDuration += run.duration
            totalSpeed += run.avgSpeed
            if (run.maxSpeed > maxSpeed) {
                maxSpeed = run.maxSpeed
            }
        }

        _todayDistance.value = totalDistance
        _todaySteps.value = totalSteps
        _todayCalories.value = totalCalories
        _todayDuration.value = totalDuration
        _todayMaxSpeed.value = maxSpeed
        
        if (todayRuns.isNotEmpty()) {
            _todayAvgSpeed.value = totalSpeed / todayRuns.size
        }

        // Calculate weekly stats
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val weeklyRuns = allRuns.filter { it.startTime >= weekAgo }

        var weeklyDistance = 0.0
        var weeklyCalories = 0.0

        for (run in weeklyRuns) {
            weeklyDistance += run.distance
            weeklyCalories += run.calories
        }

        _weeklyDistance.value = weeklyDistance
        _weeklyCalories.value = weeklyCalories
        _weeklyRuns.value = weeklyRuns.size
        _allRunsLive.value = allRuns.sortedByDescending { it.startTime }

        // Get recent runs (last 5)
        _recentRuns.value = allRuns.sortedByDescending { it.startTime }.take(5)
    }

    /**
     * Get today's date range (start and end milliseconds)
     */
    private fun getTodayRange(): Pair<Long, Long> {
        val nowMs = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMs

        // Start of today (00:00:00)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        // End of today (23:59:59)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val todayEnd = cal.timeInMillis

        return Pair(todayStart, todayEnd)
    }

    /**
     * Update goal progress
     */
    private fun updateGoalProgress() {
        val today = (_todayDistance.value ?: 0.0)
        val goal = (_dailyGoal.value ?: 5.0)
        val progress = if (goal > 0) (today / goal).toFloat() else 0f
        _goalProgress.value = progress.coerceIn(0f, 1f)
    }

    /**
     * Set daily goal
     */
    fun setDailyGoal(goalKm: Double) {
        _dailyGoal.value = goalKm
        updateGoalProgress()
    }

    /**
     * Format time for display
     */
    fun getFormattedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, secs)
            else -> String.format("%ds", secs)
        }
    }

    /**
     * Format distance
     */
    fun getFormattedDistance(distance: Double): String {
        return String.format("%.2f km", distance)
    }

    /**
     * Format calories
     */
    fun getFormattedCalories(calories: Double): String {
        return String.format("%.0f cal", calories)
    }

    /**
     * Format timestamp to YYYY-MM-DD string
     */
    private fun formatDateString(timeMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timeMs))
    }

    /**
     * Get daily stats object
     */
    fun getDailyStats(): DailyStats {
        return DailyStats(
            date = formatDateString(System.currentTimeMillis()),
            totalDistance = _todayDistance.value ?: 0.0,
            totalSteps = _todaySteps.value ?: 0,
            totalCalories = _todayCalories.value ?: 0.0,
            totalDuration = _todayDuration.value ?: 0L,
            avgSpeed = _todayAvgSpeed.value ?: 0.0,
            maxSpeed = _todayMaxSpeed.value ?: 0.0,
            runsCount = allRuns.filter {
                val today = getTodayRange()
                it.startTime >= today.first && it.startTime <= today.second
            }.size
        )
    }

    /**
     * Clear error messages
     */
    fun clearErrors() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
    }
}
