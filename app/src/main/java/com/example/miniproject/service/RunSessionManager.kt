package com.example.miniproject.service

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.miniproject.data.model.RunSession
import java.util.UUID

/**
 * Module 1: Run Session Management
 * Handles the lifecycle of running sessions (Start/Pause/Resume/End)
 * This is the CORE differentiator for a running tracker vs. general fitness apps
 */

enum class RunSessionState {
    IDLE,           // No run in progress
    RUNNING,        // Active run
    PAUSED,         // Run paused (data preserved)
    ENDED           // Run completed and saved
}

interface SessionCallback {
    fun onSessionStateChanged(newState: RunSessionState)
    fun onMetricsUpdated(distance: Double, duration: Long, avgSpeed: Double)
    fun onSessionEnded(runSession: RunSession)
}

class RunSessionManager {
    private val TAG = "RunSessionManager"
    
    // Current session state
    private val _sessionState = MutableLiveData<RunSessionState>(RunSessionState.IDLE)
    val sessionState: LiveData<RunSessionState> = _sessionState
    
    // Current run metrics
    private val _currentRun = MutableLiveData<RunSession>()
    val currentRun: LiveData<RunSession> = _currentRun
    
    private val _elapsedTime = MutableLiveData<Long>(0L)
    val elapsedTime: LiveData<Long> = _elapsedTime
    
    // Session metadata
    private var currentRunId = ""
    private var sessionStartTime = 0L
    private var sessionPausedTime = 0L
    private var totalPausedDuration = 0L
    private var callbacks = mutableListOf<SessionCallback>()
    
    /**
     * ▶️ START RUN
     * Initialize a new running session
     */
    fun startRun(userWeight: Double = 70.0): RunSession {
        // Prevent double-start
        if (_sessionState.value == RunSessionState.RUNNING) {
            Log.w(TAG, "⚠️ Run already in progress!")
            return _currentRun.value ?: RunSession()
        }
        
        currentRunId = UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()
        totalPausedDuration = 0L
        
        val newRun = RunSession(
            runId = currentRunId,
            startTime = sessionStartTime,
            duration = 0L,
            distance = 0.0,
            avgSpeed = 0.0,
            maxSpeed = 0.0,
            steps = 0,
            calories = 0.0,
            pathPointsCount = 0,
            title = "Running Session",
            notes = "",
            createdAt = sessionStartTime
        )
        
        _currentRun.value = newRun
        _sessionState.value = RunSessionState.RUNNING
        _elapsedTime.value = 0L
        
        Log.d(TAG, "✅ Run STARTED - ID: $currentRunId at ${java.text.SimpleDateFormat("HH:mm:ss").format(sessionStartTime)}")
        notifyStateChanged(RunSessionState.RUNNING)
        
        return newRun
    }
    
    /**
     * ⏸️ PAUSE RUN
     * Temporarily pause the run without losing data
     */
    fun pauseRun(): Boolean {
        if (_sessionState.value != RunSessionState.RUNNING) {
            Log.w(TAG, "⚠️ Cannot pause - run not in progress")
            return false
        }
        
        sessionPausedTime = System.currentTimeMillis()
        _sessionState.value = RunSessionState.PAUSED
        
        Log.d(TAG, "⏸️ Run PAUSED at ${java.text.SimpleDateFormat("HH:mm:ss").format(sessionPausedTime)}")
        notifyStateChanged(RunSessionState.PAUSED)
        
        return true
    }
    
    /**
     * ▶️ RESUME RUN
     * Continue a paused run from where it left off
     */
    fun resumeRun(): Boolean {
        if (_sessionState.value != RunSessionState.PAUSED) {
            Log.w(TAG, "⚠️ Cannot resume - no paused run")
            return false
        }
        
        val pausedDuration = System.currentTimeMillis() - sessionPausedTime
        totalPausedDuration += pausedDuration
        _sessionState.value = RunSessionState.RUNNING
        
        Log.d(TAG, "▶️ Run RESUMED - Paused for ${pausedDuration}ms")
        notifyStateChanged(RunSessionState.RUNNING)
        
        return true
    }
    
    /**
     * ⏹️ END RUN
     * Complete the run and calculate final metrics
     */
    fun endRun(
        finalDistance: Double,
        finalGpsPoints: Int,
        finalSteps: Int,
        finalCalories: Double,
        maxSpeedAchieved: Double
    ): RunSession {
        if (_sessionState.value == RunSessionState.IDLE) {
            Log.w(TAG, "⚠️ No active run to end")
            return RunSession()
        }
        
        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - sessionStartTime - totalPausedDuration
        val durationSeconds = totalDuration / 1000
        
        // Calculate average speed
        val avgSpeed = if (durationSeconds > 0) {
            (finalDistance / (durationSeconds / 3600.0)) // km/h
        } else {
            0.0
        }
        
        val completedRun = RunSession(
            runId = currentRunId,
            userId = "", // Will be filled by auth
            startTime = sessionStartTime,
            endTime = endTime,
            duration = durationSeconds,
            distance = finalDistance,
            avgSpeed = avgSpeed,
            maxSpeed = maxSpeedAchieved,
            steps = finalSteps,
            calories = finalCalories,
            pathPointsCount = finalGpsPoints,
            title = "Running Session - ${String.format("%.2f", finalDistance)} km",
            notes = "Auto-generated run session",
            createdAt = sessionStartTime
        )
        
        _currentRun.value = completedRun
        _sessionState.value = RunSessionState.ENDED
        
        Log.d(TAG, """
            ✅ Run ENDED - Summary:
            ├─ Duration: ${durationSeconds}s (${durationSeconds / 60}m)
            ├─ Distance: ${String.format("%.3f", finalDistance)} km
            ├─ Avg Speed: ${String.format("%.2f", avgSpeed)} km/h
            ├─ Max Speed: ${String.format("%.2f", maxSpeedAchieved)} km/h
            ├─ GPS Points: $finalGpsPoints
            ├─ Steps: $finalSteps
            └─ Calories: ${String.format("%.1f", finalCalories)} cal
        """.trimIndent())
        
        notifySessionEnded(completedRun)
        return completedRun
    }
    
    /**
     * Get current run state
     */
    fun getCurrentState(): RunSessionState = _sessionState.value ?: RunSessionState.IDLE
    
    /**
     * Get current run ID
     */
    fun getCurrentRunId(): String = currentRunId
    
    /**
     * Get elapsed time since start (excluding paused duration)
     */
    fun getElapsedTime(): Long {
        val now = System.currentTimeMillis()
        return if (_sessionState.value == RunSessionState.RUNNING) {
            now - sessionStartTime - totalPausedDuration
        } else if (_sessionState.value == RunSessionState.PAUSED) {
            sessionPausedTime - sessionStartTime - totalPausedDuration
        } else {
            0L
        }
    }
    
    /**
     * Update elapsed time (call from UI timer)
     */
    fun updateElapsedTime(timeMillis: Long) {
        _elapsedTime.value = timeMillis
    }
    
    /**
     * Register callback for session state changes
     */
    fun addCallback(callback: SessionCallback) {
        callbacks.add(callback)
    }
    
    /**
     * Unregister callback
     */
    fun removeCallback(callback: SessionCallback) {
        callbacks.remove(callback)
    }
    
    // ========== Private Helper Methods ==========
    
    private fun notifyStateChanged(newState: RunSessionState) {
        for (callback in callbacks) {
            callback.onSessionStateChanged(newState)
        }
    }
    
    private fun notifySessionEnded(session: RunSession) {
        for (callback in callbacks) {
            callback.onSessionEnded(session)
        }
    }
    
    /**
     * Reset manager for next session
     */
    fun reset() {
        currentRunId = ""
        sessionStartTime = 0L
        sessionPausedTime = 0L
        totalPausedDuration = 0L
        _sessionState.value = RunSessionState.IDLE
        _currentRun.value = RunSession()
        _elapsedTime.value = 0L
        Log.d(TAG, "🔄 Session Manager reset")
    }
}
