package com.example.miniproject.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.RunSession
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AnalyticsViewModel - Manages analytics and chart data
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "AnalyticsViewModel"
    }

    private val localRunRepository = LocalRunRepository(application.applicationContext)

    // Weekly data for charts
    private val _weeklyDistances = MutableLiveData<List<Pair<String, Double>>>()
    val weeklyDistances: LiveData<List<Pair<String, Double>>> = _weeklyDistances

    private val _weeklySpeeds = MutableLiveData<List<Pair<String, Double>>>()
    val weeklySpeeds: LiveData<List<Pair<String, Double>>> = _weeklySpeeds

    private val _weeklyCalories = MutableLiveData<List<Pair<String, Double>>>()
    val weeklyCalories: LiveData<List<Pair<String, Double>>> = _weeklyCalories

    private val _weeklySteps = MutableLiveData<List<Pair<String, Int>>>()
    val weeklySteps: LiveData<List<Pair<String, Int>>> = _weeklySteps

    // Monthly data
    private val _monthlyStats = MutableLiveData<List<Pair<String, Double>>>()
    val monthlyStats: LiveData<List<Pair<String, Double>>> = _monthlyStats

    // Summary stats
    private val _totalDistance = MutableLiveData<Double>(0.0)
    val totalDistance: LiveData<Double> = _totalDistance

    private val _totalCalories = MutableLiveData<Double>(0.0)
    val totalCalories: LiveData<Double> = _totalCalories

    private val _totalRuns = MutableLiveData<Int>(0)
    val totalRuns: LiveData<Int> = _totalRuns

    private val _avgDistance = MutableLiveData<Double>(0.0)
    val avgDistance: LiveData<Double> = _avgDistance

    private val _avgSpeed = MutableLiveData<Double>(0.0)
    val avgSpeed: LiveData<Double> = _avgSpeed

    private val _personalBest = MutableLiveData<Double>(0.0)
    val personalBest: LiveData<Double> = _personalBest

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    /**
     * Load analytics data
     */
    fun loadAnalyticsData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val runs = localRunRepository.getRunSessions()
                
                calculateWeeklyAnalytics(runs)
                calculateMonthlyAnalytics(runs)
                calculateSummaryStats(runs)
                
                Log.d(TAG, "Analytics data loaded successfully")
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error loading analytics: ${e.message}"
                Log.e(TAG, "Error loading analytics: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Calculate weekly analytics (last 7 days)
     */
    private fun calculateWeeklyAnalytics(runs: List<RunSession>) {
        val calendar = Calendar.getInstance()
        val weekData = mutableMapOf<String, MutableList<RunSession>>()
        
        // Initialize 7 days
        val dateFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        repeat(7) { dayOffset ->
            calendar.add(Calendar.DAY_OF_MONTH, -dayOffset)
            val dayLabel = dateFormat.format(calendar.time)
            weekData[dayLabel] = mutableListOf()
            calendar.add(Calendar.DAY_OF_MONTH, dayOffset)
        }
        
        // Group runs by day
        for (run in runs) {
            val runCalendar = Calendar.getInstance()
            runCalendar.timeInMillis = run.startTime
            
            val today = Calendar.getInstance()
            val daysAgo = Calendar.getInstance()
            
            // Check if run is within last 7 days
            daysAgo.add(Calendar.DAY_OF_MONTH, -7)
            
            if (run.startTime >= daysAgo.timeInMillis) {
                val dayLabel = dateFormat.format(runCalendar.time)
                weekData[dayLabel]?.add(run)
            }
        }

        // Calculate daily aggregates
        val distances = mutableListOf<Pair<String, Double>>()
        val speeds = mutableListOf<Pair<String, Double>>()
        val calories = mutableListOf<Pair<String, Double>>()
        val steps = mutableListOf<Pair<String, Int>>()

        for ((day, dayRuns) in weekData) {
            var dayDistance = 0.0
            var dayCalories = 0.0
            var daySpeed = 0.0
            var daySteps = 0

            for (run in dayRuns) {
                dayDistance += run.distance
                dayCalories += run.calories
                daySpeed += run.avgSpeed
                daySteps += run.steps
            }

            distances.add(Pair(day, dayDistance))
            calories.add(Pair(day, dayCalories))
            steps.add(Pair(day, daySteps))
            
            if (dayRuns.isNotEmpty()) {
                speeds.add(Pair(day, daySpeed / dayRuns.size))
            } else {
                speeds.add(Pair(day, 0.0))
            }
        }

        _weeklyDistances.value = distances.reversed()
        _weeklySpeeds.value = speeds.reversed()
        _weeklyCalories.value = calories.reversed()
        _weeklySteps.value = steps.reversed()
    }

    /**
     * Calculate monthly analytics
     */
    private fun calculateMonthlyAnalytics(runs: List<RunSession>) {
        val monthAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        val monthlyRuns = runs.filter { it.startTime >= monthAgo }

        val monthlyMap = mutableMapOf<String, Double>()
        val dateFormat = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())

        for (run in monthlyRuns) {
            val date = dateFormat.format(run.startTime)
            monthlyMap[date] = (monthlyMap[date] ?: 0.0) + run.distance
        }

        val monthlyList = monthlyMap.toList().sortedBy { it.first }
        _monthlyStats.value = monthlyList
    }

    /**
     * Calculate summary statistics
     */
    private fun calculateSummaryStats(runs: List<RunSession>) {
        if (runs.isEmpty()) {
            _totalDistance.value = 0.0
            _totalCalories.value = 0.0
            _totalRuns.value = 0
            _avgDistance.value = 0.0
            _avgSpeed.value = 0.0
            _personalBest.value = 0.0
            return
        }

        var totalDist = 0.0
        var totalCal = 0.0
        var totalSpeed = 0.0
        var maxDist = 0.0

        for (run in runs) {
            totalDist += run.distance
            totalCal += run.calories
            totalSpeed += run.avgSpeed
            if (run.distance > maxDist) {
                maxDist = run.distance
            }
        }

        _totalDistance.value = totalDist
        _totalCalories.value = totalCal
        _totalRuns.value = runs.size
        _avgDistance.value = totalDist / runs.size
        _avgSpeed.value = totalSpeed / runs.size
        _personalBest.value = maxDist
    }

    /**
     * Get formatted distance
     */
    fun getFormattedDistance(distance: Double): String {
        return String.format("%.2f km", distance)
    }

    /**
     * Get formatted calories
     */
    fun getFormattedCalories(calories: Double): String {
        return String.format("%.0f  cal", calories)
    }

    /**
     * Get formatted speed
     */
    fun getFormattedSpeed(speed: Double): String {
        return String.format("%.1f km/h", speed)
    }

    /**
     * Get chart data as floats for MPAndroidChart
     */
    fun getWeeklyDistanceChartData(): List<Float> {
        return _weeklyDistances.value?.map { it.second.toFloat() } ?: emptyList()
    }

    /**
     * Get weekly labels for chart
     */
    fun getWeeklyLabels(): List<String> {
        return _weeklyDistances.value?.map { it.first } ?: emptyList()
    }

    /**
     * Get speed trend data
     */
    fun getSpeedTrendData(): List<Float> {
        return _weeklySpeeds.value?.map { it.second.toFloat() } ?: emptyList()
    }

    /**
     * Clear error messages
     */
    fun clearErrors() {
        _errorMessage.value = null
    }
}
