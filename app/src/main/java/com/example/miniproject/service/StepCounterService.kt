package com.example.miniproject.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class StepCounterService(context: Context) : SensorEventListener {
    companion object {
        private const val TAG = "StepCounterService"
        private const val GRAVITY_ALPHA = 0.8f
        private const val MOTION_SMOOTHING_ALPHA = 0.85
        private const val MIN_MOVEMENT_THRESHOLD = 0.2
        private const val MAX_ACCEL_SPEED_KMH = 14.0
        private const val STEP_STALE_TIMEOUT_MS = 4000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _stepCount = MutableLiveData<Int>(0)
    val stepCount: LiveData<Int> = _stepCount

    private val _distance = MutableLiveData<Double>(0.0)
    val distance: LiveData<Double> = _distance

    private val _calories = MutableLiveData<Double>(0.0)
    val calories: LiveData<Double> = _calories

    private val _speed = MutableLiveData<Double>(0.0)
    val speed: LiveData<Double> = _speed

    private var initialStepCount = 0
    private var isTracking = false
    private var userWeight = 70 // kg (default)
    private var strideLength = 0.75 // meters (default)
    private var lastStepTime = 0L
    private var stepFrequency = 0.0 // steps per minute
    private val gravity = FloatArray(3)
    private var smoothedLinearAcceleration = 0.0

    fun restoreSnapshot(steps: Int, distanceKm: Double, calories: Double, speedKmh: Double) {
        _stepCount.value = steps.coerceAtLeast(0)
        _distance.value = distanceKm.coerceAtLeast(0.0)
        _calories.value = calories.coerceAtLeast(0.0)
        _speed.value = speedKmh.coerceAtLeast(0.0)
        Log.d(TAG, "Snapshot restored: steps=${_stepCount.value}, distance=${_distance.value}km")
    }

    fun startTracking(userWeight: Int = 70, strideLength: Double = 0.75, resetSession: Boolean = true) {
        this.userWeight = userWeight
        this.strideLength = strideLength
        isTracking = true

        if (resetSession) {
            initialStepCount = 0
            _stepCount.value = 0
            _distance.value = 0.0
            _calories.value = 0.0
            _speed.value = 0.0
            stepFrequency = 0.0
            smoothedLinearAcceleration = 0.0
            gravity.fill(0f)
        }

        lastStepTime = System.currentTimeMillis()

        Log.d(TAG, "Tracking started with weight=$userWeight, stride=$strideLength, reset=$resetSession")

        // Register step counter sensor
        stepSensor?.let {
            val registered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Step counter sensor registered: $registered")
        } ?: Log.w(TAG, "Step counter sensor not available")

        // Register accelerometer for additional speed/cadence data
        accelSensor?.let {
            val registered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer registered: $registered")
        } ?: Log.w(TAG, "Accelerometer sensor not available")
    }

    fun stopTracking() {
        isTracking = false
        sensorManager.unregisterListener(this)
        _speed.value = 0.0
        Log.d(TAG, "Tracking stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                handleStepCounterEvent(event)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                handleAccelerometerEvent(event)
            }
        }
    }

    private fun handleStepCounterEvent(event: SensorEvent) {
        if (initialStepCount == 0) {
            val restoredSteps = _stepCount.value ?: 0
            initialStepCount = (event.values[0].toInt() - restoredSteps).coerceAtLeast(0)
            Log.d(TAG, "Initial step count: $initialStepCount")
        }

        val currentSteps = event.values[0].toInt() - initialStepCount
        val previousSteps = _stepCount.value ?: 0

        if (currentSteps != previousSteps) {
            _stepCount.value = currentSteps

            // Calculate cadence (steps per minute)
            val currentTime = System.currentTimeMillis()
            if (lastStepTime > 0) {
                val timeDiffMs = currentTime - lastStepTime
                if (timeDiffMs > 0) {
                    stepFrequency = (1000.0 / timeDiffMs) * 60.0 // Convert to steps/minute
                }
            }
            lastStepTime = currentTime

            // Calculate distance (steps × stride length)
            val distanceMeters = currentSteps * strideLength
            _distance.value = distanceMeters / 1000.0 // Convert to km

            // Calculate speed based on step frequency and stride
            // Speed (m/s) = (steps per second) × stride length
            val stepsPerSecond = stepFrequency / 60.0
            val speedMs = stepsPerSecond * strideLength
            val cadenceSpeedKmh = speedMs * 3.6 // Convert m/s to km/h
            updateLiveSpeed(cadenceSpeedKmh)

            // Calculate calories
            // Formula: Calories = distance(km) × weight(kg) × 1.036
            val calories = (distanceMeters / 1000.0) * userWeight * 1.036
            _calories.value = calories

            Log.d(
                TAG,
                "Steps: $currentSteps, Distance: ${String.format("%.3f", _distance.value)} km, " +
                        "Speed: ${String.format("%.2f", _speed.value ?: 0.0)} km/h, Calories: ${String.format("%.1f", calories)}"
            )
        }
    }

    private fun handleAccelerometerEvent(event: SensorEvent) {
        if (event.values.size < 3) return

        // Remove gravity to keep only movement acceleration.
        val linear = FloatArray(3)
        for (i in 0..2) {
            gravity[i] = GRAVITY_ALPHA * gravity[i] + (1f - GRAVITY_ALPHA) * event.values[i]
            linear[i] = event.values[i] - gravity[i]
        }

        val linearMagnitude = Math.sqrt(
            (linear[0] * linear[0] + linear[1] * linear[1] + linear[2] * linear[2]).toDouble()
        )

        smoothedLinearAcceleration =
            MOTION_SMOOTHING_ALPHA * smoothedLinearAcceleration +
                    (1.0 - MOTION_SMOOTHING_ALPHA) * linearMagnitude

        // Map movement intensity to a soft speed estimate.
        val motionFactor = ((smoothedLinearAcceleration - MIN_MOVEMENT_THRESHOLD) / 2.8).coerceIn(0.0, 1.0)
        val accelEstimatedSpeedKmh = motionFactor * MAX_ACCEL_SPEED_KMH
        val cadenceSpeedKmh = ((stepFrequency / 60.0) * strideLength) * 3.6

        val fusedSpeed = when {
            cadenceSpeedKmh > 0.0 -> (cadenceSpeedKmh * 0.8) + (accelEstimatedSpeedKmh * 0.2)
            motionFactor > 0.25 -> accelEstimatedSpeedKmh * 0.65
            else -> 0.0
        }

        updateLiveSpeed(fusedSpeed)

        if (linearMagnitude > 3.5) {
            Log.d(TAG, "Motion detected: linearAcc=${String.format("%.2f", linearMagnitude)}m/s² fused=${String.format("%.2f", _speed.value ?: 0.0)}km/h")
        }
    }

    private fun updateLiveSpeed(rawSpeedKmh: Double) {
        val now = System.currentTimeMillis()
        val stepFresh = (now - lastStepTime) <= STEP_STALE_TIMEOUT_MS
        val prev = _speed.value ?: 0.0

        val clamped = rawSpeedKmh.coerceIn(0.0, MAX_ACCEL_SPEED_KMH + 4.0)
        val smoothed = (prev * 0.7) + (clamped * 0.3)
        val finalSpeed = if (stepFresh || smoothed > 0.8) smoothed else 0.0

        _speed.value = finalSpeed
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} = $accuracy")
    }

    fun getTotalSteps(): Int = _stepCount.value ?: 0
    fun getTotalDistance(): Double = _distance.value ?: 0.0
    fun getTotalCalories(): Double = _calories.value ?: 0.0
    fun getCurrentSpeed(): Double = _speed.value ?: 0.0
    fun getStepFrequency(): Double = stepFrequency
}
