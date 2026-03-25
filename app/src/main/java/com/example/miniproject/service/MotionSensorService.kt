package com.example.miniproject.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Motion Sensor Service
 * Detects device motion using Accelerometer, Gyroscope, and Magnetometer
 * Provides real-time orientation, tilt, and movement data for map visualization
 */

data class DeviceMotion(
    val pitch: Float = 0f,           // Device tilt forward/backward (-90 to 90°)
    val roll: Float = 0f,            // Device tilt left/right (-90 to 90°)
    val yaw: Float = 0f,             // Device rotation/heading (0-360°)
    val azimuth: Float = 0f,         // Compass direction (0-360°)
    val accelerationX: Float = 0f,   // Side-to-side acceleration
    val accelerationY: Float = 0f,   // Forward/backward acceleration
    val accelerationZ: Float = 0f,   // Up/down acceleration
    val totalAcceleration: Float = 0f, // Magnitude of acceleration
    val magnitude: Float = 0f        // Device movement intensity
)

class MotionSensorService(context: Context) : SensorEventListener {
    private val TAG = "MotionSensorService"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Sensors
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    // Sensor data arrays
    private val accelerometerData = FloatArray(3)
    private val magnetometerData = FloatArray(3)
    private val gyroscopeData = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    
    // Live data
    private val _deviceMotion = MutableLiveData<DeviceMotion>()
    val deviceMotion: LiveData<DeviceMotion> = _deviceMotion
    
    private val _isMoving = MutableLiveData<Boolean>(false)
    val isMoving: LiveData<Boolean> = _isMoving
    
    private val _motionHistory = MutableLiveData<List<DeviceMotion>>()
    val motionHistory: LiveData<List<DeviceMotion>> = _motionHistory
    
    private val motionBuffer = mutableListOf<DeviceMotion>()
    private var lastAccelerationMagnitude = 0f
    private var isTracking = false
    
    /**
     * Start motion tracking
     */
    fun startTracking() {
        if (isTracking) {
            Log.d(TAG, "⚠️ Motion tracking already active")
            return
        }
        
        isTracking = true
        motionBuffer.clear()
        
        // Register sensor listeners with high accuracy
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        Log.d(TAG, """
            ✅ Motion Tracking Started:
            ├─ Accelerometer: ${accelerometer?.name ?: "NOT AVAILABLE"}
            ├─ Gyroscope: ${gyroscope?.name ?: "NOT AVAILABLE"}
            └─ Magnetometer: ${magnetometer?.name ?: "NOT AVAILABLE"}
        """.trimIndent())
    }
    
    /**
     * Stop motion tracking
     */
    fun stopTracking() {
        if (!isTracking) return
        
        isTracking = false
        sensorManager.unregisterListener(this)
        
        Log.d(TAG, "⏹️ Motion Tracking Stopped")
        Log.d(TAG, "📊 Motion history recorded: ${motionBuffer.size} samples")
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isTracking) return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Store accelerometer readings
                accelerometerData[0] = event.values[0]
                accelerometerData[1] = event.values[1]
                accelerometerData[2] = event.values[2]
                calculateOrientation()
            }
            
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Store magnetometer readings for compass
                magnetometerData[0] = event.values[0]
                magnetometerData[1] = event.values[1]
                magnetometerData[2] = event.values[2]
                calculateOrientation()
            }
            
            Sensor.TYPE_GYROSCOPE -> {
                // Store gyroscope data
                gyroscopeData[0] = event.values[0]
                gyroscopeData[1] = event.values[1]
                gyroscopeData[2] = event.values[2]
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }
    
    /**
     * Calculate device orientation from accelerometer + magnetometer
     */
    private fun calculateOrientation() {
        // Get rotation matrix from accelerometer and magnetometer
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerData,
            magnetometerData
        )
        
        if (!success) return
        
        // Get orientation values (in radians)
        SensorManager.getOrientation(rotationMatrix, orientationValues)
        
        // Convert to degrees
        val azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat() // Z-axis rotation
        val pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()   // X-axis rotation
        val roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()    // Y-axis rotation
        
        // Normalize azimuth to 0-360 range
        val normalizedAzimuth = if (azimuth < 0) azimuth + 360 else azimuth
        
        // Calculate total acceleration magnitude
        val accelX = accelerometerData[0]
        val accelY = accelerometerData[1]
        val accelZ = accelerometerData[2]
        val totalAcceleration = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        
        // Detect if device is moving (significant acceleration change)
        val accelerationDelta = totalAcceleration - lastAccelerationMagnitude
        val isMoving = kotlin.math.abs(accelerationDelta) > 0.5f || totalAcceleration > 11f
        
        lastAccelerationMagnitude = totalAcceleration
        _isMoving.value = isMoving
        
        // Create motion data
        val motion = DeviceMotion(
            pitch = pitch,
            roll = roll,
            yaw = gyroscopeData[2],  // Z-axis rotation from gyroscope
            azimuth = normalizedAzimuth,
            accelerationX = accelX,
            accelerationY = accelY,
            accelerationZ = accelZ,
            totalAcceleration = totalAcceleration,
            magnitude = kotlin.math.abs(accelerationDelta)
        )
        
        _deviceMotion.value = motion
        
        // Keep last 100 motion samples for history
        motionBuffer.add(motion)
        if (motionBuffer.size > 100) {
            motionBuffer.removeAt(0)
        }
        _motionHistory.value = motionBuffer.toList()
        
        // Detailed logging
        Log.d(TAG, """
            📍 Device Motion:
            ├─ Compass: ${String.format("%.1f", normalizedAzimuth)}° (N=0°)
            ├─ Tilt: Pitch=${String.format("%.1f", pitch)}° Roll=${String.format("%.1f", roll)}°
            ├─ Accel: X=${String.format("%.2f", accelX)}g Y=${String.format("%.2f", accelY)}g Z=${String.format("%.2f", accelZ)}g
            ├─ Total Acceleration: ${String.format("%.2f", totalAcceleration)}g
            ├─ Moving: ${if (isMoving) "🔴 YES" else "🟢 NO"}
            └─ Motion Magnitude: ${String.format("%.2f", motion.magnitude)}
        """.trimIndent())
    }
    
    /**
     * Get compass direction name
     */
    fun getCompassDirection(azimuth: Float): String {
        return when {
            azimuth < 22.5f || azimuth >= 337.5f -> "↑ N (North)"
            azimuth < 67.5f -> "↗ NE (Northeast)"
            azimuth < 112.5f -> "→ E (East)"
            azimuth < 157.5f -> "↘ SE (Southeast)"
            azimuth < 202.5f -> "↓ S (South)"
            azimuth < 247.5f -> "↙ SW (Southwest)"
            azimuth < 292.5f -> "← W (West)"
            else -> "↖ NW (Northwest)"
        }
    }
    
    /**
     * Get device orientation category
     */
    fun getDeviceOrientation(pitch: Float, roll: Float): String {
        return when {
            kotlin.math.abs(pitch) < 15 && kotlin.math.abs(roll) < 15 -> "📱 Flat/Level"
            pitch < -30 -> "📱 Looking Down"
            pitch > 30 -> "📱 Looking Up"
            roll < -30 -> "📱 Tilted Left"
            roll > 30 -> "📱 Tilted Right"
            else -> "📱 Tilted"
        }
    }
    
    /**
     * Get motion intensity description
     */
    fun getMotionIntensity(magnitude: Float): String {
        return when {
            magnitude < 0.5f -> "🟢 Stable"
            magnitude < 2.0f -> "🟡 Light Motion"
            magnitude < 5.0f -> "🟠 Moderate Motion"
            else -> "🔴 Heavy Motion"
        }
    }
    
    /**
     * Get current motion data
     */
    fun getCurrentMotion(): DeviceMotion = _deviceMotion.value ?: DeviceMotion()
    
    /**
     * Get motion summary
     */
    fun getMotionSummary(): String {
        val motion = getCurrentMotion()
        return """
            🧭 Motion Summary:
            ├─ Direction: ${getCompassDirection(motion.azimuth)}
            ├─ Orientation: ${getDeviceOrientation(motion.pitch, motion.roll)}
            ├─ Intensity: ${getMotionIntensity(motion.magnitude)}
            ├─ Heading: ${String.format("%.1f", motion.azimuth)}°
            └─ Angle: P=${String.format("%.0f", motion.pitch)}° R=${String.format("%.0f", motion.roll)}°
        """.trimIndent()
    }
}
