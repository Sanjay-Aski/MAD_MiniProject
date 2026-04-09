package com.example.miniproject.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.service.LocationService
import com.example.miniproject.service.StepCounterService
import com.example.miniproject.util.AccuracyCalculator
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * RunTrackingViewModel - Core MVVM ViewModel for run tracking
 * Manages run state, metrics calculation, and data persistence
 */
class RunTrackingViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "RunTrackingViewModel"
        private const val STEPS_PER_CALORIE = 90.0
    }

    private val firebaseRepository = FirebaseRepository()
    private val localRunRepository = LocalRunRepository(application.applicationContext)
    
    // Run state
    private val _runId = MutableLiveData<String>()
    val runId: LiveData<String> = _runId

    private val _isRunning = MutableLiveData<Boolean>(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _isPaused = MutableLiveData<Boolean>(false)
    val isPaused: LiveData<Boolean> = _isPaused

    // Metrics from location service
    private val _distance = MutableLiveData<Double>(0.0)
    val distance: LiveData<Double> = _distance

    private val _currentSpeed = MutableLiveData<Double>(0.0)
    val currentSpeed: LiveData<Double> = _currentSpeed

    private val _avgSpeed = MutableLiveData<Double>(0.0)
    val avgSpeed: LiveData<Double> = _avgSpeed

    private val _maxSpeed = MutableLiveData<Double>(0.0)
    val maxSpeed: LiveData<Double> = _maxSpeed

    // Metrics from step counter service
    private val _stepCount = MutableLiveData<Int>(0)
    val stepCount: LiveData<Int> = _stepCount

    private val _calories = MutableLiveData<Double>(0.0)
    val calories: LiveData<Double> = _calories

    // Time tracking
    private val _elapsedSeconds = MutableLiveData<Long>(0L)
    val elapsedSeconds: LiveData<Long> = _elapsedSeconds

    // GPS path
    private val _gpsPoints = MutableLiveData<List<GPSPoint>>(emptyList())
    val gpsPoints: LiveData<List<GPSPoint>> = _gpsPoints

    // Data flow events
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _successMessage = MutableLiveData<String>()
    val successMessage: LiveData<String> = _successMessage

    // Internal state
    private var startTime = 0L
    private var pausedTime = 0L
    private var totalPausedDuration = 0L
    private var userWeight = 70.0 // default weight in kg

    init {
        _runId.value = UUID.randomUUID().toString()
    }

    /**
     * Initialize a new running session
     */
    fun initializeRun() {
        _runId.value = UUID.randomUUID().toString()
        _isRunning.value = false
        _isPaused.value = false
        _distance.value = 0.0
        _currentSpeed.value = 0.0
        _avgSpeed.value = 0.0
        _maxSpeed.value = 0.0
        _stepCount.value = 0
        _calories.value = 0.0
        _elapsedSeconds.value = 0L
        _gpsPoints.value = emptyList()
        startTime = 0L
        pausedTime = 0L
        totalPausedDuration = 0L
        Log.d(TAG, "Run initialized with ID: ${_runId.value}")
    }

    fun restoreRunState(
        runId: String,
        startTimeMillis: Long,
        isRunning: Boolean,
        isPaused: Boolean,
        totalPausedDurationMillis: Long
    ) {
        _runId.value = runId
        _isRunning.value = isRunning
        _isPaused.value = isPaused
        startTime = startTimeMillis
        totalPausedDuration = totalPausedDurationMillis.coerceAtLeast(0L)

        if (isPaused) {
            pausedTime = System.currentTimeMillis()
        } else {
            pausedTime = 0L
        }

        if (startTime > 0L) {
            val elapsedMs = if (isPaused) {
                pausedTime - startTime - totalPausedDuration
            } else {
                System.currentTimeMillis() - startTime - totalPausedDuration
            }
            _elapsedSeconds.value = (elapsedMs.coerceAtLeast(0L) / 1000L)
        }

        Log.d(TAG, "Run state restored: runId=$runId running=$isRunning paused=$isPaused")
    }

    fun getStartTimeMillis(): Long = startTime

    fun getTotalPausedDurationMillis(): Long = totalPausedDuration

    fun restoreMetricsSnapshot(
        distance: Double,
        currentSpeed: Double,
        avgSpeed: Double,
        maxSpeed: Double,
        stepCount: Int,
        calories: Double,
        elapsedSeconds: Long,
        gpsPoints: List<GPSPoint>
    ) {
        _distance.value = distance.coerceAtLeast(0.0)
        _currentSpeed.value = currentSpeed.coerceAtLeast(0.0)
        _avgSpeed.value = avgSpeed.coerceAtLeast(0.0)
        _maxSpeed.value = maxSpeed.coerceAtLeast(0.0)
        _stepCount.value = stepCount.coerceAtLeast(0)
        _calories.value = calories.coerceAtLeast(0.0)
        _elapsedSeconds.value = elapsedSeconds.coerceAtLeast(0L)
        _gpsPoints.value = gpsPoints

        Log.d(
            TAG,
            "Metrics snapshot restored: distance=${_distance.value}, steps=${_stepCount.value}, avg=${_avgSpeed.value}, max=${_maxSpeed.value}, points=${gpsPoints.size}"
        )
    }

    /**
     * Start the running session
     */
    fun startRun() {
        if (_isRunning.value == true) {
            _errorMessage.value = "Run already in progress"
            return
        }

        _isRunning.value = true
        _isPaused.value = false
        startTime = System.currentTimeMillis()
        pausedTime = 0L
        Log.d(TAG, "Run started")
    }

    /**
     * Pause the current running session
     */
    fun pauseRun() {
        if (_isRunning.value != true) {
            _errorMessage.value = "No active run to pause"
            return
        }

        _isRunning.value = false
        _isPaused.value = true
        pausedTime = System.currentTimeMillis()
        Log.d(TAG, "Run paused")
    }

    /**
     * Resume a paused running session
     */
    fun resumeRun() {
        if (_isPaused.value != true) {
            _errorMessage.value = "No paused run to resume"
            return
        }

        _isRunning.value = true
        _isPaused.value = false
        totalPausedDuration += (System.currentTimeMillis() - pausedTime)
        pausedTime = 0L
        Log.d(TAG, "Run resumed")
    }

    /**
     * Update metrics from location service
     */
    fun updateLocationMetrics(
        distance: Double,
        currentSpeed: Double,
        avgSpeed: Double,
        maxSpeed: Double,
        gpsPoints: List<GPSPoint>
    ) {
        _distance.value = distance
        _currentSpeed.value = currentSpeed
        _avgSpeed.value = avgSpeed
        _maxSpeed.value = maxSpeed
        _gpsPoints.value = gpsPoints
        
        // Calculate pace (min/km)
        updateCalories()
    }

    /**
     * Update metrics from step counter service
     */
    fun updateStepMetrics(steps: Int) {
        _stepCount.value = steps
        updateCalories()
    }

    /**
     * Update elapsed time
     */
    fun updateElapsedTime() {
        if (_isRunning.value == true && startTime > 0) {
            val elapsedMs = System.currentTimeMillis() - startTime - totalPausedDuration
            _elapsedSeconds.value = elapsedMs / 1000
        }
    }

    /**
     * Calculate calories burned using hybrid method
     * Method: Distance (km) × Weight (kg) × 1.036 (most accurate)
     * Also considers speed/intensity via MET values
     */
    private fun updateCalories() {
        val steps = (_stepCount.value ?: 0).coerceAtLeast(0)
        val calories = steps / STEPS_PER_CALORIE
        _calories.value = calories

        Log.d(TAG, "Calories updated: ${String.format("%.2f", calories)} cal (steps=$steps, ratio=90:1)")
    }

    /**
     * Set user weight for calorie calculation
     */
    fun setUserWeight(weight: Double) {
        userWeight = weight
        updateCalories()
    }

    /**
     * Get current run summary
     */
    private fun getCurrentRunSummary(): RunSession {
        val elapsedSecond = (_elapsedSeconds.value ?: 0L)
        return RunSession(
            runId = _runId.value ?: "",
            userId = "", // Will be filled from Firebase
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            duration = elapsedSecond,
            distance = _distance.value ?: 0.0,
            avgSpeed = _avgSpeed.value ?: 0.0,
            maxSpeed = _maxSpeed.value ?: 0.0,
            steps = _stepCount.value ?: 0,
            calories = _calories.value ?: 0.0,
            pathPointsCount = _gpsPoints.value?.size ?: 0,
            title = "Running Session",
            notes = "",
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Save run session to Firebase first, then fall back to local storage if needed.
     */
    fun saveRunToFirebase() {
        viewModelScope.launch {
            try {
                val runSession = getCurrentRunSummary()
                val gpsPointsList = _gpsPoints.value ?: emptyList()
                val cloudSaved = firebaseRepository.saveRunSessionWithRoute(runSession, gpsPointsList)
                
                if (cloudSaved) {
                    _successMessage.value = "Run saved to Firebase successfully!"
                    Log.d(TAG, "Run saved to Firebase with ID: ${runSession.runId}")
                } else {
                    val localSaved = localRunRepository.saveRunSessionWithRoute(runSession, gpsPointsList)
                    if (localSaved) {
                        _successMessage.value = "Run saved locally. Cloud sync failed."
                        Log.w(TAG, "Firebase save failed, run stored locally with ID: ${runSession.runId}")
                    } else {
                        _errorMessage.value = "Failed to save run"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error saving run: ${e.message}"
                Log.e(TAG, "Error saving run: ${e.message}", e)
            }
        }
    }

    /**
     * End the run and return the session
     */
    fun endRun(): RunSession? {
        if (_isRunning.value == true || _isPaused.value == true || hasTrackableData()) {
            _isRunning.value = false
            _isPaused.value = false

            val runSession = getCurrentRunSummary()
            Log.d(TAG, "Run ended: distance=${runSession.distance}km, duration=${runSession.duration}s")
            return runSession
        }
        return null
    }

    private fun hasTrackableData(): Boolean {
        return (_elapsedSeconds.value ?: 0L) > 0L ||
                (_distance.value ?: 0.0) > 0.0 ||
                (_stepCount.value ?: 0) > 0
    }

    /**
     * Clear error/success messages
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * Format elapsed time as MM:SS
     */
    fun getFormattedTime(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    /**
     * Format distance with proper decimal places
     */
    fun getFormattedDistance(distance: Double): String {
        return String.format("%.2f", distance)
    }

    /**
     * Format speed with proper decimal places
     */
    fun getFormattedSpeed(speed: Double): String {
        return String.format("%.1f", speed)
    }

    /**
     * Calculate pace (min/km) from speed
     * Uses AccuracyCalculator for consistent calculations
     */
    fun calculatePace(speedKmh: Double): String {
        return AccuracyCalculator.calculatePace(speedKmh)
    }
}
