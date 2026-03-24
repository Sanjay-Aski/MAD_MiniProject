package com.example.miniproject

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.service.LocationService
import com.example.miniproject.service.StepCounterService
import com.example.miniproject.ui.fragments.MapFragment
import com.example.miniproject.ui.viewmodel.RunTrackingViewModel
import com.example.miniproject.util.RunTargetSettings
import com.example.miniproject.util.RunTargetSettingsStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class RunTrackingActivity : AppCompatActivity() {
    // ViewModel
    private lateinit var viewModel: RunTrackingViewModel
        
    // Services
    private lateinit var locationService: LocationService
    private lateinit var stepCounterService: StepCounterService
    private var mapFragment: MapFragment? = null
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var localRunRepository: LocalRunRepository

    // User Profile Data (from Firebase)
    private var userId: String = "" // Current user ID
    private var userHeight: Int = 170 // cm (for stride calculation)
    private var userWeight: Double = 70.0 // kg (for calorie calculation)
    private var userGender: String = "Male" // for stride formula
    private var calibratedStrideLength: Double? = null // User-calibrated stride

    // UI Components
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
    private lateinit var btnEndRun: Button

    private var lastRenderedGpsCount = 0
    private var strideLength = 0.75 // meters (calculated or calibrated)
    private var isEndingRunInProgress = false
    private var lastRunSavedToCloud = false
    private var lastRunSavedLocally = false
    private var runTargets = RunTargetSettings(5.0, 6000, 200.0, 30)
    private var distanceTargetHit = false
    private var stepsTargetHit = false
    private var caloriesTargetHit = false
    private var durationTargetHit = false
    
    // GPS Accuracy Settings
    private val GPS_ACCURACY_THRESHOLD = 20f // meters - ignore worse accuracy
    private val LOCATION_UPDATE_INTERVAL = 1000L // 1 second
    private val MAX_SPEED_JUMP = 15.0 // m/s - ignore sudden jumps

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TAG = "RunTrackingActivity"
        private const val SHARED_PREFS_NAME = "RunTrackerPrefs"
        private const val CALIBRATED_STRIDE_KEY = "calibrated_stride_length"
        private const val USER_ID_PREF = "current_user_id"
        private const val NOTIFICATION_CHANNEL_ID = "run_session_channel"
        private const val TARGET_NOTIFICATION_CHANNEL_ID = "run_target_achievement_channel"
        private const val NOTIFICATION_ID = 2001
        private const val DISTANCE_TARGET_NOTIFICATION_ID = 2101
        private const val STEPS_TARGET_NOTIFICATION_ID = 2102
        private const val CALORIES_TARGET_NOTIFICATION_ID = 2103
        private const val DURATION_TARGET_NOTIFICATION_ID = 2104
        private const val ACTION_TOGGLE_RUN = "action_toggle_run"
        private const val ACTION_END_RUN = "action_end_run"
        private const val PENDING_RUN_BACKUP_KEY = "pending_run_backup"

        private const val ACTIVE_RUN_KEY = "active_run"
        private const val ACTIVE_RUN_ID_KEY = "active_run_id"
        private const val ACTIVE_RUN_START_TIME_KEY = "active_run_start_time"
        private const val ACTIVE_RUN_TOTAL_PAUSED_KEY = "active_run_total_paused"
        private const val ACTIVE_RUN_IS_RUNNING_KEY = "active_run_is_running"
        private const val ACTIVE_RUN_IS_PAUSED_KEY = "active_run_is_paused"
        private const val ACTIVE_RUN_DISTANCE_KEY = "active_run_distance"
        private const val ACTIVE_RUN_CURRENT_SPEED_KEY = "active_run_current_speed"
        private const val ACTIVE_RUN_AVG_SPEED_KEY = "active_run_avg_speed"
        private const val ACTIVE_RUN_MAX_SPEED_KEY = "active_run_max_speed"
        private const val ACTIVE_RUN_STEPS_KEY = "active_run_steps"
        private const val ACTIVE_RUN_CALORIES_KEY = "active_run_calories"
        private const val ACTIVE_RUN_ELAPSED_KEY = "active_run_elapsed"
        private const val ACTIVE_RUN_POINTS_JSON_KEY = "active_run_points_json"
        private const val ACTIVE_RUN_TARGET_DISTANCE_HIT_KEY = "active_run_target_distance_hit"
        private const val ACTIVE_RUN_TARGET_STEPS_HIT_KEY = "active_run_target_steps_hit"
        private const val ACTIVE_RUN_TARGET_CALORIES_HIT_KEY = "active_run_target_calories_hit"
        private const val ACTIVE_RUN_TARGET_DURATION_HIT_KEY = "active_run_target_duration_hit"

        private var sharedLocationService: LocationService? = null
        private var sharedStepCounterService: StepCounterService? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_tracking)

        // Initialize Firebase Repository
        firebaseRepository = FirebaseRepository()
        localRunRepository = LocalRunRepository(applicationContext)
        createNotificationChannel()
        loadRunTargets()
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(RunTrackingViewModel::class.java)
        val restoredSession = restoreSessionStateFromPrefs()
        if (!restoredSession) {
            viewModel.initializeRun()
        }
        
        initializeViews()
        initializeMapFragment()
        initializeServices()
        if (restoredSession) {
            restoreTrackingServicesFromSnapshot()
        }
        
        // Load user profile BEFORE setting up observers
        loadUserProfileFromFirebase()
        
        setupObservers()
        setupListeners()
        checkPermissions()
        handleRunActionIntent(intent)
        
        Log.d(TAG, "Activity created with userId: $userId, runId: ${viewModel.runId.value}, restoredSession=$restoredSession")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRunActionIntent(intent)
    }

    private fun initializeMapFragment() {
        try {
            mapFragment = MapFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_container, mapFragment!!)
                .commit()
            Log.d(TAG, "MapFragment loaded into container")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MapFragment: ${e.message}", e)
        }
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
        btnEndRun = findViewById(R.id.btn_end_run)

        // Initially disable buttons
        btnEndRun.isEnabled = false
    }

    private fun initializeServices() {
        locationService = sharedLocationService ?: LocationService(applicationContext).also {
            sharedLocationService = it
        }
        stepCounterService = sharedStepCounterService ?: StepCounterService(applicationContext).also {
            sharedStepCounterService = it
        }

        Log.d(TAG, "Services initialized")
    }

    private fun restoreSessionStateFromPrefs(): Boolean {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val isActive = prefs.getBoolean(ACTIVE_RUN_KEY, false)
        if (!isActive) {
            return false
        }

        val runId = prefs.getString(ACTIVE_RUN_ID_KEY, "") ?: ""
        val startTime = prefs.getLong(ACTIVE_RUN_START_TIME_KEY, 0L)
        val totalPaused = prefs.getLong(ACTIVE_RUN_TOTAL_PAUSED_KEY, 0L)
        val isRunning = prefs.getBoolean(ACTIVE_RUN_IS_RUNNING_KEY, false)
        val isPaused = prefs.getBoolean(ACTIVE_RUN_IS_PAUSED_KEY, false)
        val distance = prefs.getFloat(ACTIVE_RUN_DISTANCE_KEY, 0f).toDouble()
        val currentSpeed = prefs.getFloat(ACTIVE_RUN_CURRENT_SPEED_KEY, 0f).toDouble()
        val avgSpeed = prefs.getFloat(ACTIVE_RUN_AVG_SPEED_KEY, 0f).toDouble()
        val maxSpeed = prefs.getFloat(ACTIVE_RUN_MAX_SPEED_KEY, 0f).toDouble()
        val steps = prefs.getInt(ACTIVE_RUN_STEPS_KEY, 0)
        val calories = prefs.getFloat(ACTIVE_RUN_CALORIES_KEY, 0f).toDouble()
        val elapsedSeconds = prefs.getLong(ACTIVE_RUN_ELAPSED_KEY, 0L)
        val pointsJson = prefs.getString(ACTIVE_RUN_POINTS_JSON_KEY, "[]") ?: "[]"
        val gpsPoints = parseGpsPointsFromJson(runId, pointsJson)
        distanceTargetHit = prefs.getBoolean(ACTIVE_RUN_TARGET_DISTANCE_HIT_KEY, false)
        stepsTargetHit = prefs.getBoolean(ACTIVE_RUN_TARGET_STEPS_HIT_KEY, false)
        caloriesTargetHit = prefs.getBoolean(ACTIVE_RUN_TARGET_CALORIES_HIT_KEY, false)
        durationTargetHit = prefs.getBoolean(ACTIVE_RUN_TARGET_DURATION_HIT_KEY, false)

        if (runId.isBlank() || startTime <= 0L || (!isRunning && !isPaused)) {
            clearSessionStatePrefs()
            return false
        }

        viewModel.restoreRunState(
            runId = runId,
            startTimeMillis = startTime,
            isRunning = isRunning,
            isPaused = isPaused,
            totalPausedDurationMillis = totalPaused
        )
        viewModel.restoreMetricsSnapshot(
            distance = distance,
            currentSpeed = currentSpeed,
            avgSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            stepCount = steps,
            calories = calories,
            elapsedSeconds = elapsedSeconds,
            gpsPoints = gpsPoints
        )

        Log.d(TAG, "Restored active run from prefs: runId=$runId, running=$isRunning, paused=$isPaused")
        return true
    }

    private fun persistSessionState() {
        val isRunning = viewModel.isRunning.value == true
        val isPaused = viewModel.isPaused.value == true
        val isActive = isRunning || isPaused
        val pointsJson = serializeGpsPointsToJson(viewModel.gpsPoints.value ?: emptyList())

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ACTIVE_RUN_KEY, isActive)
            .putString(ACTIVE_RUN_ID_KEY, viewModel.runId.value ?: "")
            .putLong(ACTIVE_RUN_START_TIME_KEY, viewModel.getStartTimeMillis())
            .putLong(ACTIVE_RUN_TOTAL_PAUSED_KEY, viewModel.getTotalPausedDurationMillis())
            .putBoolean(ACTIVE_RUN_IS_RUNNING_KEY, isRunning)
            .putBoolean(ACTIVE_RUN_IS_PAUSED_KEY, isPaused)
            .putFloat(ACTIVE_RUN_DISTANCE_KEY, (viewModel.distance.value ?: 0.0).toFloat())
            .putFloat(ACTIVE_RUN_CURRENT_SPEED_KEY, (viewModel.currentSpeed.value ?: 0.0).toFloat())
            .putFloat(ACTIVE_RUN_AVG_SPEED_KEY, (viewModel.avgSpeed.value ?: 0.0).toFloat())
            .putFloat(ACTIVE_RUN_MAX_SPEED_KEY, (viewModel.maxSpeed.value ?: 0.0).toFloat())
            .putInt(ACTIVE_RUN_STEPS_KEY, viewModel.stepCount.value ?: 0)
            .putFloat(ACTIVE_RUN_CALORIES_KEY, (viewModel.calories.value ?: 0.0).toFloat())
            .putLong(ACTIVE_RUN_ELAPSED_KEY, viewModel.elapsedSeconds.value ?: 0L)
            .putString(ACTIVE_RUN_POINTS_JSON_KEY, pointsJson)
            .putBoolean(ACTIVE_RUN_TARGET_DISTANCE_HIT_KEY, distanceTargetHit)
            .putBoolean(ACTIVE_RUN_TARGET_STEPS_HIT_KEY, stepsTargetHit)
            .putBoolean(ACTIVE_RUN_TARGET_CALORIES_HIT_KEY, caloriesTargetHit)
            .putBoolean(ACTIVE_RUN_TARGET_DURATION_HIT_KEY, durationTargetHit)
            .apply()
    }

    private fun restoreTrackingServicesFromSnapshot() {
        val runId = viewModel.runId.value ?: return
        val points = viewModel.gpsPoints.value ?: emptyList()
        locationService.restoreSnapshot(
            runId = runId,
            distanceKm = viewModel.distance.value ?: 0.0,
            avgSpeedKmh = viewModel.avgSpeed.value ?: 0.0,
            maxSpeedKmhSnapshot = viewModel.maxSpeed.value ?: 0.0,
            gpsPoints = points
        )
        stepCounterService.restoreSnapshot(
            steps = viewModel.stepCount.value ?: 0,
            distanceKm = viewModel.distance.value ?: 0.0,
            calories = viewModel.calories.value ?: 0.0,
            speedKmh = viewModel.currentSpeed.value ?: 0.0
        )

        val isRunning = viewModel.isRunning.value == true
        val isPaused = viewModel.isPaused.value == true

        if (locationService.hasLocationPermission()) {
            locationService.startLocationUpdates(runId, resetSession = false)
            if (isPaused) {
                locationService.pauseTracking()
            } else if (isRunning) {
                locationService.resumeTracking()
            }
        }

        if (isRunning || isPaused) {
            stepCounterService.startTracking(
                userWeight = userWeight.toInt(),
                strideLength = strideLength,
                resetSession = false
            )
            if (isPaused) {
                stepCounterService.stopTracking()
            }
        }

        if (isRunning) {
            startElapsedTimeUpdate()
        }
        updateRunControlState()
        updateSessionNotification()
    }

    private fun serializeGpsPointsToJson(points: List<GPSPoint>): String {
        return try {
            val array = JSONArray()
            points.takeLast(500).forEach { point ->
                array.put(
                    JSONObject().apply {
                        put("latitude", point.latitude)
                        put("longitude", point.longitude)
                        put("timestamp", point.timestamp)
                        put("speed", point.speed)
                        put("altitude", point.altitude)
                        put("accuracy", point.accuracy)
                    }
                )
            }
            array.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize GPS points: ${e.message}", e)
            "[]"
        }
    }

    private fun parseGpsPointsFromJson(runId: String, rawJson: String): List<GPSPoint> {
        return try {
            val array = JSONArray(rawJson)
            val parsed = mutableListOf<GPSPoint>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parsed.add(
                    GPSPoint(
                        runId = runId,
                        latitude = item.optDouble("latitude", 0.0),
                        longitude = item.optDouble("longitude", 0.0),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        speed = item.optDouble("speed", 0.0).toFloat(),
                        altitude = item.optDouble("altitude", 0.0),
                        accuracy = item.optDouble("accuracy", 0.0).toFloat()
                    )
                )
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse GPS points snapshot: ${e.message}", e)
            emptyList()
        }
    }

    private fun clearSessionStatePrefs() {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .remove(ACTIVE_RUN_KEY)
            .remove(ACTIVE_RUN_ID_KEY)
            .remove(ACTIVE_RUN_START_TIME_KEY)
            .remove(ACTIVE_RUN_TOTAL_PAUSED_KEY)
            .remove(ACTIVE_RUN_IS_RUNNING_KEY)
            .remove(ACTIVE_RUN_IS_PAUSED_KEY)
                .remove(ACTIVE_RUN_DISTANCE_KEY)
                .remove(ACTIVE_RUN_CURRENT_SPEED_KEY)
                .remove(ACTIVE_RUN_AVG_SPEED_KEY)
                .remove(ACTIVE_RUN_MAX_SPEED_KEY)
                .remove(ACTIVE_RUN_STEPS_KEY)
                .remove(ACTIVE_RUN_CALORIES_KEY)
                .remove(ACTIVE_RUN_ELAPSED_KEY)
                .remove(ACTIVE_RUN_POINTS_JSON_KEY)
                .remove(ACTIVE_RUN_TARGET_DISTANCE_HIT_KEY)
                .remove(ACTIVE_RUN_TARGET_STEPS_HIT_KEY)
                .remove(ACTIVE_RUN_TARGET_CALORIES_HIT_KEY)
                .remove(ACTIVE_RUN_TARGET_DURATION_HIT_KEY)
            .apply()
    }

    private fun setupObservers() {
        // ========== ViewModel Observers ==========
        viewModel.distance.observe(this) { distance ->
            tvDistance.text = viewModel.getFormattedDistance(distance)
            Log.d(TAG, "Distance updated: $distance")
            checkTargetAchievements()
            updateSessionNotification()
        }

        viewModel.elapsedSeconds.observe(this) { seconds ->
            tvDuration.text = viewModel.getFormattedTime(seconds)
            updateChronometer(seconds)
            checkTargetAchievements()
            updateSessionNotification()
            val isActive = viewModel.isRunning.value == true || viewModel.isPaused.value == true
            if (isActive && seconds % 5L == 0L) {
                persistSessionState()
            }
        }

        viewModel.avgSpeed.observe(this) { speed ->
            tvAvgSpeed.text = String.format("%.1f km/h", speed)
            updatePace(speed)
            updateSessionNotification()
        }

        viewModel.maxSpeed.observe(this) { speed ->
            tvMaxSpeed.text = String.format("%.1f km/h", speed)
            updateSessionNotification()
        }

        viewModel.stepCount.observe(this) { steps ->
            tvSteps.text = "$steps steps"
            checkTargetAchievements()
            updateSessionNotification()
        }

        viewModel.calories.observe(this) { calories ->
            tvCalories.text = String.format("%.0f cal", calories)
            checkTargetAchievements()
            updateSessionNotification()
        }

        viewModel.isRunning.observe(this) { isRunning ->
            updateRunControlState()
            updateSessionNotification()
        }

        viewModel.isPaused.observe(this) { isPaused ->
            updateRunControlState()
            updateSessionNotification()
        }

        viewModel.gpsPoints.observe(this) { gpsPoints ->
            // Update map with GPS path
            if (gpsPoints.size == lastRenderedGpsCount + 1) {
                mapFragment?.addPoint(gpsPoints.last())
            } else if (gpsPoints.size > lastRenderedGpsCount) {
                mapFragment?.updateGPSPath(gpsPoints)
            }
            lastRenderedGpsCount = gpsPoints.size
            Log.d(TAG, "GPS path updated: ${gpsPoints.size} points")
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        viewModel.successMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        // ========== Location Service Observers ==========
        locationService.gpsPoints.observe(this) { gpsPoints ->
            syncLocationMetricsFromService(gpsPoints)
        }

        // Keep metrics live even when GPS polyline list does not change.
        locationService.totalDistance.observe(this) {
            syncLocationMetricsFromService()
        }

        locationService.currentSpeed.observe(this) {
            syncLocationMetricsFromService()
        }

        locationService.avgSpeed.observe(this) {
            syncLocationMetricsFromService()
        }

        locationService.maxSpeed.observe(this) {
            syncLocationMetricsFromService()
        }

        locationService.currentLocation.observe(this) { location ->
            if (location != null) {
                // Apply accuracy filter - only use good GPS signals
                if (location.accuracy <= GPS_ACCURACY_THRESHOLD) {
                    // Create GPSPoint from location data and update map
                    val gpsPoint = GPSPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        speed = (location.speed * 3.6).toFloat() // m/s to km/h
                    )
                    mapFragment?.updateCurrentLocation(gpsPoint)
                    Log.d(TAG, "Map updated with accurate location (accuracy: ${location.accuracy}m)")
                } else {
                    Log.d(TAG, "Location ignored - poor accuracy: ${location.accuracy}m (threshold: ${GPS_ACCURACY_THRESHOLD}m)")
                }
            }
        }

        // ========== Step Counter Observers ==========
        stepCounterService.stepCount.observe(this) { steps ->
            viewModel.updateStepMetrics(steps)
        }
    }

    private fun syncLocationMetricsFromService(gpsPointsOverride: List<GPSPoint>? = null) {
        val gpsPoints = gpsPointsOverride ?: locationService.gpsPoints.value ?: emptyList()
        viewModel.updateLocationMetrics(
            locationService.getTotalDistance(),
            locationService.getCurrentSpeed(),
            locationService.getAvgSpeed(),
            locationService.getMaxSpeed(),
            gpsPoints
        )
    }

    private fun updateRunControlState() {
        val isRunning = viewModel.isRunning.value == true
        val isPaused = viewModel.isPaused.value == true

        btnStartPause.text = when {
            isRunning -> "Pause"
            isPaused -> "Resume"
            else -> "Start"
        }

        // End should be available while active or paused.
        val canControlSession = isRunning || isPaused || hasSessionData()
        btnEndRun.isEnabled = canControlSession
    }

    private fun hasSessionData(): Boolean {
        return (viewModel.elapsedSeconds.value ?: 0L) > 0L ||
                (viewModel.distance.value ?: 0.0) > 0.0 ||
                (viewModel.stepCount.value ?: 0) > 0
    }

    private fun updateChronometer(seconds: Long) {
        // Only update if running - check ViewModel state
        val isCurrentlyRunning = viewModel.isRunning.value ?: false
        if (isCurrentlyRunning) {
            chronometer.base = SystemClock.elapsedRealtime() - (seconds * 1000)
            // Note: start() is idempotent and safe to call multiple times
            chronometer.start()
        }
    }

    private fun loadUserProfileFromFirebase() {
        // Prefer authenticated user, fallback to SharedPreferences
        val authUserId = FirebaseAuth.getInstance().currentUser?.uid
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val prefUserId = prefs.getString(USER_ID_PREF, "") ?: ""
        userId = authUserId ?: prefUserId
        
        // Load calibrated stride if available
        val savedStride = prefs.getFloat(CALIBRATED_STRIDE_KEY, 0f)
        if (savedStride > 0) {
            calibratedStrideLength = savedStride.toDouble()
            strideLength = calibratedStrideLength!!
            Log.d(TAG, "Loaded calibrated stride: $strideLength m")
        }
        
        // Use lifecycle scope to load from Firebase
        lifecycleScope.launch {
            try {
                val userProfile = firebaseRepository.getUserProfile(userId)
                if (userProfile != null) {
                    userHeight = userProfile.height // cm
                    userWeight = userProfile.weight.toDouble() // kg
                    userGender = userProfile.gender
                    
                    // Calculate stride length if not calibrated
                    if (calibratedStrideLength == null) {
                        strideLength = calculateStrideLength(userHeight, userGender)
                    }
                    
                    viewModel.setUserWeight(userWeight)
                    
                    Log.d(TAG, "User profile loaded: height=$userHeight cm, weight=$userWeight kg, stride=$strideLength m")
                    Toast.makeText(this@RunTrackingActivity, 
                        "Profile: ${userProfile.name} (${userWeight.toInt()} kg)", 
                        Toast.LENGTH_SHORT).show()
                } else {
                    // Use defaults if profile not found
                    Log.w(TAG, "No user profile found, using defaults")
                    strideLength = calculateStrideLength(userHeight, userGender)
                    viewModel.setUserWeight(userWeight)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user profile: ${e.message}", e)
                // Use defaults on error
                strideLength = calculateStrideLength(userHeight, userGender)
                viewModel.setUserWeight(userWeight)
            }
        }
    }
    
    /**
     * Calculate stride length based on height and gender
     * Formula: height (meters) × coefficient
     * Male: 0.415, Female: 0.413
     */
    private fun calculateStrideLength(heightCm: Int, gender: String): Double {
        val coefficient = if (gender.equals("Male", ignoreCase = true)) 0.415 else 0.413
        return (heightCm / 100.0) * coefficient
    }
    
    /**
     * Calibrate stride length by user walking distance
     * User input: walked 100m, counted steps
     */
    fun calibrateStrideLengthFromWalk(distanceMeters: Double, stepCount: Int) {
        if (stepCount > 0) {
            calibratedStrideLength = distanceMeters / stepCount
            strideLength = calibratedStrideLength!!
            
            // Save to SharedPreferences
            val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putFloat(CALIBRATED_STRIDE_KEY, strideLength.toFloat()).apply()
            
            Log.d(TAG, "Stride calibrated: $strideLength m (from $distanceMeters m / $stepCount steps)")
            Toast.makeText(this, "Stride calibrated: ${String.format("%.2f", strideLength)} m", 
                Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        btnStartPause.setOnClickListener {
            // Get current running and paused states from ViewModel
            val isCurrentlyRunning = viewModel.isRunning.value ?: false
            val isCurrentlyPaused = viewModel.isPaused.value ?: false

            when {
                isCurrentlyRunning -> pauseRun()
                isCurrentlyPaused -> resumeRun()
                else -> startRun()
            }
        }

        btnEndRun.setOnClickListener { endRun() }
    }

    private fun startRun() {
        // Check if location permission is granted before starting run
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted - cannot start run")
            Toast.makeText(
                this,
                "Location permission is required to start tracking",
                Toast.LENGTH_SHORT
            ).show()
            requestLocationPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }

        loadRunTargets()
        resetTargetAchievementFlags()
        viewModel.startRun()
        lastRenderedGpsCount = 0
        mapFragment?.clearPath()

        Log.d(TAG, "Run started with GPS and Step Counter")

        // Start GPS-based location tracking
        locationService.startLocationUpdates(viewModel.runId.value ?: "")

        // Start step counter with sensor data (cast Double to Int)
        stepCounterService.startTracking(userWeight = userWeight.toInt(), strideLength = strideLength, resetSession = true)

        // Start chronometer
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()

        // Start updating elapsed time
        startElapsedTimeUpdate()

        Toast.makeText(this, "Run started - GPS & Sensors Active", Toast.LENGTH_SHORT).show()
        showSessionNotification()
        persistSessionState()
    }

    private fun loadRunTargets() {
        runTargets = RunTargetSettingsStore.load(this)
        Log.d(
            TAG,
            "Run targets loaded: distance=${runTargets.distanceKm}km steps=${runTargets.steps} calories=${runTargets.calories} duration=${runTargets.durationMinutes}min"
        )
    }

    private fun resetTargetAchievementFlags() {
        distanceTargetHit = false
        stepsTargetHit = false
        caloriesTargetHit = false
        durationTargetHit = false
        persistSessionState()
    }

    private fun checkTargetAchievements() {
        val active = viewModel.isRunning.value == true || viewModel.isPaused.value == true
        if (!active) {
            return
        }

        var flagsChanged = false

        val distance = viewModel.distance.value ?: 0.0
        val steps = viewModel.stepCount.value ?: 0
        val calories = viewModel.calories.value ?: 0.0
        val durationMinutes = (viewModel.elapsedSeconds.value ?: 0L) / 60.0

        if (!distanceTargetHit && distance >= runTargets.distanceKm) {
            distanceTargetHit = true
            flagsChanged = true
            showTargetAchievedNotification(
                notificationId = DISTANCE_TARGET_NOTIFICATION_ID,
                title = "Distance Target Achieved",
                message = String.format("Great job! You reached %.2f km.", runTargets.distanceKm)
            )
        }

        if (!stepsTargetHit && steps >= runTargets.steps) {
            stepsTargetHit = true
            flagsChanged = true
            showTargetAchievedNotification(
                notificationId = STEPS_TARGET_NOTIFICATION_ID,
                title = "Steps Target Achieved",
                message = "Great job! You reached ${runTargets.steps} steps."
            )
        }

        if (!caloriesTargetHit && calories >= runTargets.calories) {
            caloriesTargetHit = true
            flagsChanged = true
            showTargetAchievedNotification(
                notificationId = CALORIES_TARGET_NOTIFICATION_ID,
                title = "Calories Target Achieved",
                message = String.format("Great job! You reached %.0f calories.", runTargets.calories)
            )
        }

        if (!durationTargetHit && durationMinutes >= runTargets.durationMinutes) {
            durationTargetHit = true
            flagsChanged = true
            showTargetAchievedNotification(
                notificationId = DURATION_TARGET_NOTIFICATION_ID,
                title = "Duration Target Achieved",
                message = "Great job! You reached ${runTargets.durationMinutes} minutes."
            )
        }

        if (flagsChanged) {
            persistSessionState()
        }
    }

    private fun showTargetAchievedNotification(notificationId: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = android.content.Intent(this, RunTrackingActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TARGET_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_dashboard)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun pauseRun() {
        viewModel.pauseRun()
        
        // Pause location tracking
        locationService.pauseTracking()
        stepCounterService.stopTracking()
        chronometer.stop()
        
        Log.d(TAG, "Run paused")
        Toast.makeText(this, "Run paused! ⏸️", Toast.LENGTH_SHORT).show()
        updateSessionNotification()
        persistSessionState()
    }

    private fun resumeRun() {
        viewModel.resumeRun()
        
        // Resume location tracking
        locationService.resumeTracking()
        stepCounterService.startTracking(userWeight = userWeight.toInt(), strideLength = strideLength, resetSession = false) // Cast Double to Int
        chronometer.start()
        
        // Resume elapsed time update
        startElapsedTimeUpdate()
        
        Log.d(TAG, "Run resumed")
        Toast.makeText(this, "Run resumed! ▶️", Toast.LENGTH_SHORT).show()
        updateSessionNotification()
        persistSessionState()
    }

    private fun stopRun() {
        if (viewModel.isRunning.value == true) {
            viewModel.pauseRun() // Pause without ending
        }
        locationService.pauseTracking()
        stepCounterService.stopTracking()
        chronometer.stop()
        updateRunControlState()
        persistSessionState()
        
        Log.d(TAG, "Run stopped (not finished yet)")
    }

    private fun hasEndableSession(): Boolean {
        val activeState = viewModel.isRunning.value == true || viewModel.isPaused.value == true
        val startedAtLeastOnce = viewModel.getStartTimeMillis() > 0L
        return activeState || startedAtLeastOnce || hasSessionData()
    }

    private fun endRun() {
        if (isEndingRunInProgress) {
            Log.d(TAG, "End run ignored - already in progress")
            return
        }

        if (!hasEndableSession()) {
            Toast.makeText(this, "No active run to end", Toast.LENGTH_SHORT).show()
            return
        }

        isEndingRunInProgress = true
        btnEndRun.isEnabled = false
        btnEndRun.text = "Ending..."

        // Stop all tracking
        locationService.stopLocationUpdates()
        stepCounterService.stopTracking()
        chronometer.stop()

        // End run and save
        val runSession = viewModel.endRun()
        if (runSession != null) {
            val resolvedUserId = FirebaseAuth.getInstance().currentUser?.uid ?: userId
            val finalizedRun = runSession.copy(
                userId = resolvedUserId,
                title = if (runSession.title.isBlank() || runSession.title == "Running Session") {
                    "Run ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(java.util.Date(runSession.startTime))}"
                } else {
                    runSession.title
                }
            )

            Log.d(
                TAG,
                "Run ended: distance=${finalizedRun.distance}km, steps=${finalizedRun.steps}, duration=${finalizedRun.duration}s"
            )

            lifecycleScope.launch {
                val gps = viewModel.gpsPoints.value ?: emptyList()
                val savedRun = saveRunReliably(finalizedRun, gps)

                if (savedRun) {
                    val saveMessage = if (lastRunSavedToCloud && lastRunSavedLocally) {
                        "Run saved to Firebase and device. Opening report..."
                    } else if (lastRunSavedToCloud) {
                        "Run saved to Firebase. Opening report..."
                    } else if (lastRunSavedLocally) {
                        "Run saved on device. Cloud sync failed, opening report..."
                    } else {
                        "Run backup prepared. Opening report..."
                    }
                    Toast.makeText(
                        this@RunTrackingActivity,
                        saveMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                    clearSessionStatePrefs()
                    NotificationManagerCompat.from(this@RunTrackingActivity).cancel(NOTIFICATION_ID)
                    openReportPage(finalizedRun.runId)
                } else {
                    clearSessionStatePrefs()
                    NotificationManagerCompat.from(this@RunTrackingActivity).cancel(NOTIFICATION_ID)
                    Toast.makeText(
                        this@RunTrackingActivity,
                        "Run ended but local save failed. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Run save failed for runId=${finalizedRun.runId}")
                }

                isEndingRunInProgress = false
            }
        } else {
            isEndingRunInProgress = false
            btnEndRun.text = "End Run"
            updateRunControlState()
            Toast.makeText(this, "Unable to finalize run. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun saveRunReliably(runSession: RunSession, points: List<GPSPoint>): Boolean {
        var cloudSavedAtLeastOnce = false

        for (attempt in 1..3) {
            val cloudSaved = withContext(Dispatchers.IO) {
                withTimeoutOrNull(10000L) {
                    firebaseRepository.saveRunSessionWithRoute(
                        runSession = runSession,
                        points = points
                    )
                } ?: false
            }

            if (cloudSaved) {
                cloudSavedAtLeastOnce = true
                break
            }

            Log.w(TAG, "Cloud save attempt $attempt failed for runId=${runSession.runId}")
        }

        val localSaved = withContext(Dispatchers.IO) {
            localRunRepository.saveRunSessionWithRoute(
                runSession = runSession,
                points = points
            )
        }

        lastRunSavedToCloud = cloudSavedAtLeastOnce
        lastRunSavedLocally = localSaved

        if (!cloudSavedAtLeastOnce && !localSaved) {
            persistPendingRunBackup(runSession, points)
        }

        return cloudSavedAtLeastOnce || localSaved
    }

    private fun persistPendingRunBackup(runSession: RunSession, points: List<GPSPoint>) {
        try {
            val runJson = JSONObject().apply {
                put("runId", runSession.runId)
                put("userId", runSession.userId)
                put("startTime", runSession.startTime)
                put("endTime", runSession.endTime)
                put("duration", runSession.duration)
                put("distance", runSession.distance)
                put("avgSpeed", runSession.avgSpeed)
                put("maxSpeed", runSession.maxSpeed)
                put("steps", runSession.steps)
                put("calories", runSession.calories)
                put("pathPointsCount", runSession.pathPointsCount)
                put("title", runSession.title)
                put("notes", runSession.notes)
                put("createdAt", runSession.createdAt)
            }

            val pointsJson = JSONArray().apply {
                points.forEach { point ->
                    put(
                        JSONObject().apply {
                            put("runId", point.runId)
                            put("latitude", point.latitude)
                            put("longitude", point.longitude)
                            put("timestamp", point.timestamp)
                            put("speed", point.speed)
                            put("altitude", point.altitude)
                            put("accuracy", point.accuracy)
                        }
                    )
                }
            }

            val payload = JSONObject().apply {
                put("savedAt", System.currentTimeMillis())
                put("run", runJson)
                put("routePoints", pointsJson)
            }

            getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PENDING_RUN_BACKUP_KEY, payload.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write local run backup: ${e.message}", e)
        }
    }

    private fun openReportPage(runId: String) {
        val intent = android.content.Intent(this, HomeActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_OPEN_RUN_ID, runId)
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Run Session",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live run stats and controls"
            }

            val targetChannel = android.app.NotificationChannel(
                TARGET_NOTIFICATION_CHANNEL_ID,
                "Run Targets",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Target achievement alerts"
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(targetChannel)
        }
    }

    private fun buildSessionNotification(): android.app.Notification {
        val toggleIntent = android.content.Intent(this, RunTrackingActivity::class.java).apply {
            action = ACTION_TOGGLE_RUN
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val endIntent = android.content.Intent(this, RunTrackingActivity::class.java).apply {
            action = ACTION_END_RUN
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val togglePending = PendingIntent.getActivity(
            this,
            11,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endPending = PendingIntent.getActivity(
            this,
            12,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = android.content.Intent(this, RunTrackingActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            13,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = if (viewModel.isRunning.value == true) "Pause" else "Resume"
        val toggleIcon = if (viewModel.isRunning.value == true) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val distance = viewModel.distance.value ?: 0.0
        val steps = viewModel.stepCount.value ?: 0
        val calories = viewModel.calories.value ?: 0.0
        val avgSpeed = viewModel.avgSpeed.value ?: 0.0
        val maxSpeed = viewModel.maxSpeed.value ?: 0.0
        val pace = viewModel.calculatePace(avgSpeed)
        val running = viewModel.isRunning.value == true
        val status = if (running) "Running" else "Paused"
        val distanceLine = String.format("Distance: %.2f km", distance)
        val stepsLine = "Steps: $steps"
        val caloriesLine = String.format("Calories: %.0f cal", calories)
        val durationLine = "Duration: ${tvDuration.text}"
        val speedLine = String.format("Pace: %s | Avg %.1f km/h | Max %.1f km/h", pace, avgSpeed, maxSpeed)
        val statusLine = "Status: $status • Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())}"
        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_dashboard)
            .setContentTitle("Run Session $status")
            .setContentText(String.format("%.2f km • %d steps • %.0f cal", distance, steps, calories))
            .setLargeIcon(largeIcon)
            .setColor(ContextCompat.getColor(this, R.color.accent_blue))
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setSubText("RunSense Live")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .addLine(distanceLine)
                    .addLine(stepsLine)
                    .addLine(caloriesLine)
                    .addLine(durationLine)
                    .addLine(speedLine)
                    .addLine(statusLine)
                    .setSummaryText("Tap for live map and controls")
            )
            .setOngoing(viewModel.isRunning.value == true || viewModel.isPaused.value == true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPending)
            .addAction(toggleIcon, toggleLabel, togglePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endPending)
            .build()
    }

    private fun showSessionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildSessionNotification())
    }

    private fun updateSessionNotification() {
        val active = (viewModel.isRunning.value == true || viewModel.isPaused.value == true)
        if (!active) return
        showSessionNotification()
    }

    private fun handleRunActionIntent(intent: android.content.Intent?) {
        when (intent?.action) {
            ACTION_TOGGLE_RUN -> {
                val running = viewModel.isRunning.value ?: false
                val paused = viewModel.isPaused.value ?: false
                when {
                    running -> pauseRun()
                    paused -> resumeRun()
                    else -> startRun()
                }
            }

            ACTION_END_RUN -> endRun()
        }
    }

    private fun updatePace(avgSpeed: Double) {
        if (avgSpeed > 0) {
            tvPace.text = viewModel.calculatePace(avgSpeed)
        }
    }

    private var elapsedTimeRunnable: Runnable? = null

    private fun startElapsedTimeUpdate() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        elapsedTimeRunnable = object : Runnable {
            override fun run() {
                val isCurrentlyRunning = viewModel.isRunning.value ?: false
                if (isCurrentlyRunning) {
                    viewModel.updateElapsedTime()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(elapsedTimeRunnable!!)
    }

    private fun checkPermissions() {
        // On modern Android, step counter access uses ACTIVITY_RECOGNITION.
        val permissionsList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsList.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissions = permissionsList.toTypedArray()

        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Check if we need to show rationale for location permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                showLocationPermissionRationale()
            } else {
                requestLocationPermissions(permissionsToRequest.toTypedArray())
            }
        } else {
            Log.d(TAG, "All permissions already granted")
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage(
                "This app needs your location to track your running route, speed, and distance. " +
                        "Step counting uses activity recognition on supported devices."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                val permissionsList = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionsList.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                requestLocationPermissions(permissionsList.toTypedArray())
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.w(TAG, "User cancelled location permission request")
                Toast.makeText(
                    this,
                    "Location permission is required for run tracking",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestLocationPermissions(permissions: Array<String>) {
        Log.d(TAG, "Requesting permissions: ${permissions.joinToString(", ")}")
        ActivityCompat.requestPermissions(
            this,
            permissions,
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            val grantedPermissions = mutableListOf<String>()

            // Check which permissions were granted and which were denied
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(permissions[i])
                    Log.d(TAG, "Permission granted: ${permissions[i]}")
                } else {
                    deniedPermissions.add(permissions[i])
                    Log.w(TAG, "Permission denied: ${permissions[i]}")
                }
            }

            // Check if location permission (the critical one) was granted
            val locationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (locationGranted) {
                Log.d(TAG, "Location permission granted! Starting location tracking")
                Toast.makeText(
                    this,
                    "Location permission granted! You can now start tracking your run.",
                    Toast.LENGTH_LONG
                ).show()
                // Re-initialize location service to start tracking
                initializeServices()
            } else {
                Log.w(TAG, "Location permission denied")
                showLocationPermissionDeniedDialog()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val activityGranted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

                if (!activityGranted) {
                    Toast.makeText(
                        this,
                        "Activity recognition not granted. Run tracking works, but step count may be limited.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Inform user about other permissions
            if (deniedPermissions.isNotEmpty()) {
                Log.w(TAG, "Some permissions were denied: $deniedPermissions")
            }
        }
    }

    private fun showLocationPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Denied")
            .setMessage(
                "Location tracking is essential for this app. Without it, you cannot track your running sessions. " +
                        "Please grant location permission in app settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        val isActive = viewModel.isRunning.value == true || viewModel.isPaused.value == true
        if (!isActive) {
            locationService.stopLocationUpdates()
            stepCounterService.stopTracking()
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            clearSessionStatePrefs()
        } else {
            persistSessionState()
            Log.d(TAG, "Activity destroyed while run active - keeping trackers alive")
        }
        Log.d(TAG, "Activity destroyed")
    }

    override fun onPause() {
        super.onPause()
        val isActive = viewModel.isRunning.value == true || viewModel.isPaused.value == true
        if (isActive) {
            persistSessionState()
        }
    }
}
