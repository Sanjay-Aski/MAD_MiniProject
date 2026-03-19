package com.example.miniproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.service.LocationService
import com.example.miniproject.service.StepCounterService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class RunTrackingActivity : AppCompatActivity() {
    private lateinit var locationService: LocationService
    private lateinit var stepCounterService: StepCounterService

    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvPace: TextView
    private lateinit var tvHeartRateZone: TextView
    private lateinit var chronometer: Chronometer
    private lateinit var btnStartPause: Button
    private lateinit var btnStop: Button
    private lateinit var btnEndRun: Button

    private var isRunning = false
    private var isPaused = false
    private var runId = UUID.randomUUID().toString()
    private var startTime = 0L
    private var pausedTime = 0L

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TAG = "RunTrackingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_tracking)

        initializeViews()
        initializeServices()
        setupListeners()
        checkPermissions()
        
        Log.d(TAG, "Activity created with runId: $runId")
    }

    private fun initializeViews() {
        tvDistance = findViewById(R.id.tv_distance)
        tvDuration = findViewById(R.id.tv_duration)
        tvAvgSpeed = findViewById(R.id.tv_avg_speed)
        tvMaxSpeed = findViewById(R.id.tv_max_speed)
        tvSteps = findViewById(R.id.tv_steps)
        tvCalories = findViewById(R.id.tv_calories)
        tvPace = findViewById(R.id.tv_pace)
        tvHeartRateZone = findViewById(R.id.tv_heart_rate_zone)
        chronometer = findViewById(R.id.chronometer)
        btnStartPause = findViewById(R.id.btn_start_pause)
        btnStop = findViewById(R.id.btn_stop)
        btnEndRun = findViewById(R.id.btn_end_run)

        // Initially disable buttons
        btnStop.isEnabled = false
        btnEndRun.isEnabled = false
    }

    private fun initializeServices() {
        locationService = LocationService(this)
        stepCounterService = StepCounterService(this)

        // ========== GPS/Location Data Observers ==========
        locationService.gpsPoints.observe(this) {
            updateDistance()
            Log.d(TAG, "GPS points updated: ${it.size} points")
        }
        
        locationService.currentSpeed.observe(this) {
            if (it > 0) {
                Log.d(TAG, "Current GPS speed: $it km/h")
            }
        }

        locationService.avgSpeed.observe(this) {
            tvAvgSpeed.text = String.format("%.2f km/h", it)
            updatePace(it)
            Log.d(TAG, "Average speed: $it km/h")
        }

        locationService.maxSpeed.observe(this) {
            tvMaxSpeed.text = String.format("%.2f km/h", it)
            Log.d(TAG, "Max speed: $it km/h")
        }

        // ========== Step Counter & Sensor Data Observers ==========
        stepCounterService.stepCount.observe(this) {
            tvSteps.text = "$it steps"
            Log.d(TAG, "Steps updated: $it")
        }

        stepCounterService.distance.observe(this) {
            Log.d(TAG, "Step-based distance: $it km")
        }

        stepCounterService.calories.observe(this) {
            tvCalories.text = String.format("%.1f cal", it)
            Log.d(TAG, "Calories: $it")
        }

        stepCounterService.speed.observe(this) {
            if (it > 0) {
                Log.d(TAG, "Sensor-based speed from cadence: $it km/h")
            }
        }
    }

    private fun setupListeners() {
        btnStartPause.setOnClickListener {
            if (isRunning) {
                pauseRun()
            } else {
                startRun()
            }
        }

        btnStop.setOnClickListener {
            stopRun()
        }

        btnEndRun.setOnClickListener {
            endRun()
        }
    }

    private fun startRun() {
        if (!isRunning) {
            isRunning = true
            isPaused = false
            startTime = System.currentTimeMillis() - pausedTime

            Log.d(TAG, "Run started with GPS and Step Counter")

            // Start GPS-based location tracking (PRIMARY)
            locationService.startLocationUpdates(runId)

            // Start step counter with sensor data (SECONDARY)
            stepCounterService.startTracking(userWeight = 70, strideLength = 0.75)

            // Start chronometer
            chronometer.base = SystemClock.elapsedRealtime() - pausedTime
            chronometer.start()

            btnStartPause.text = "Pause"
            btnStop.isEnabled = true
            btnEndRun.isEnabled = true

            Toast.makeText(this, "Run started - GPS & Sensors Active", Toast.LENGTH_SHORT).show()
        } else if (isPaused) {
            resumeRun()
        }
    }

    private fun pauseRun() {
        isRunning = false
        isPaused = true
        locationService.stopLocationUpdates()
        stepCounterService.stopTracking() // Pause step counter
        chronometer.stop()
        btnStartPause.text = "Resume"
        
        Log.d(TAG, "Run paused")
        Toast.makeText(this, "Run paused!", Toast.LENGTH_SHORT).show()
    }

    private fun resumeRun() {
        isRunning = true
        isPaused = false
        locationService.startLocationUpdates(runId)
        stepCounterService.startTracking(userWeight = 70, strideLength = 0.75) // Resume step counter
        chronometer.start()
        btnStartPause.text = "Pause"
        
        Log.d(TAG, "Run resumed")
        Toast.makeText(this, "Run resumed!", Toast.LENGTH_SHORT).show()
    }

    private fun stopRun() {
        isRunning = false
        isPaused = false
        locationService.stopLocationUpdates()
        stepCounterService.stopTracking()
        chronometer.stop()
        btnStartPause.text = "Start"
        btnStartPause.isEnabled = false
        btnStop.isEnabled = false
        
        Log.d(TAG, "Run stopped (not finished yet)")
        // Keep btnEndRun enabled to save the run
    }

    private fun endRun() {
        locationService.stopLocationUpdates()
        stepCounterService.stopTracking()
        chronometer.stop()

        // Calculate final metrics from both GPS and sensors
        val duration = (System.currentTimeMillis() - startTime) / 1000 // in seconds
        
        // GPS as PRIMARY source for distance and speed
        val gpsDistance = locationService.getTotalDistance()
        val gpsAvgSpeed = locationService.getAvgSpeed()
        val gpsMaxSpeed = locationService.getMaxSpeed()
        
        // Steps and calories from sensor
        val steps = stepCounterService.getTotalSteps()
        val sensorDistance = stepCounterService.getTotalDistance()
        val calories = stepCounterService.getTotalCalories()
        
        // Use GPS distance as primary, fallback to sensor if GPS data is minimal
        val finalDistance = if (gpsDistance > 0.1) gpsDistance else sensorDistance
        
        Log.d(TAG, "Run ended - GPS Distance: $gpsDistance km, Sensor Steps: $steps, Duration: $duration s")

        // Create and save run session
        val runSession = RunSession(
            runId = runId,
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            duration = duration,
            distance = finalDistance,
            avgSpeed = gpsAvgSpeed,
            maxSpeed = gpsMaxSpeed,
            steps = steps,
            calories = calories,
            pathPointsCount = locationService.getGPSPointsForRun().size,
            title = "Running Session"
        )

        // TODO: Save to Firebase and local database

        Toast.makeText(
            this,
            "Run ended! Distance: ${String.format("%.2f", finalDistance)} km, Steps: $steps",
            Toast.LENGTH_LONG
        ).show()
        
        finish()
    }

    private fun updateDistance() {
        val distance = locationService.getTotalDistance()
        tvDistance.text = String.format("%.2f km", distance)
    }

    private fun updatePace(avgSpeed: Double) {
        if (avgSpeed > 0) {
            val paceMinutesPerKm = 60.0 / avgSpeed
            val minutes = paceMinutesPerKm.toInt()
            val seconds = ((paceMinutesPerKm - minutes) * 60).toInt()
            tvPace.text = String.format("%02d:%02d /km", minutes, seconds)
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BODY_SENSORS_BACKGROUND
        )

        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted")
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "Some permissions are required for running tracker!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationService.stopLocationUpdates()
        stepCounterService.stopTracking()
        Log.d(TAG, "Activity destroyed")
    }
}
