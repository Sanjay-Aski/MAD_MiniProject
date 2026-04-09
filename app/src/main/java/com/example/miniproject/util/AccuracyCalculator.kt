package com.example.miniproject.util

import android.location.Location
import com.example.miniproject.data.model.GPSPoint
import kotlin.math.*

/**
 * AccuracyCalculator - Provides professional-grade accuracy improvements for GPS tracking
 * 
 * Features:
 * - GPS filtering (accuracy-based, jump detection)
 * - Distance calculation (GPS-based for high accuracy)
 * - Speed calculation (smoothed, with MET-based calorie burn)
 * - Hybrid distance model (GPS + step-based fallback)
 * - Stride calibration
 */
object AccuracyCalculator {
    
    // Constants
    private const val GPS_ACCURACY_THRESHOLD = 20f // meters
    private const val MAX_SPEED_JUMP = 15.0 // m/s
    private const val EARTH_RADIUS_M = 6371000.0 // meters
    
    // MET (Metabolic Equivalent) values for activities
    data class METValues(
        val walking: Double = 3.5,
        val running: Double = 9.0,
        val fastRunning: Double = 11.5
    )
    
    /**
     * Calculate distance between two GPS points using Haversine formula
     * Returns distance in meters
     */
    fun calculateDistanceBetweenPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }
    
    /**
     * Check if location data is accurate enough to use
     */
    fun isAccurate(location: Location, accuracyThreshold: Float = GPS_ACCURACY_THRESHOLD): Boolean {
        return location.accuracy <= accuracyThreshold
    }
    
    /**
     * Detect if speed jump is anomalous (likely GPS error)
     * Returns true if the jump should be ignored
     */
    fun isAnomalousSpeedJump(lastSpeed: Double, currentSpeed: Double, maxJump: Double = MAX_SPEED_JUMP): Boolean {
        return abs(currentSpeed - lastSpeed) > maxJump
    }
    
    /**
     * Calculate calories burned using hybrid method
     * 
     * Method 1 (GPS-based): Calories = Distance (km) × Weight (kg) × 1.036
     * Method 2 (MET-based): Calories = MET × Weight (kg) × Time (hours)
     * 
     * @param distanceKm Total distance in kilometers
     * @param weightKg User weight in kilograms
     * @param durationSeconds Duration in seconds
     * @param speed Current speed in km/h (for MET selection)
     * @return Estimated calories burned
     */
    fun calculateCaloriesBurned(
        distanceKm: Double,
        weightKg: Double,
        durationSeconds: Long,
        speed: Double = 0.0
    ): Double {
        // Method 1: GPS distance-based (most accurate)
        val caloriesFromDistance = distanceKm * weightKg * 1.036
        
        // Method 2: MET-based for comparison
        val durationHours = durationSeconds / 3600.0
        val met = when {
            speed < 6 -> METValues().walking       // < 6 km/h = walking
            speed < 12 -> METValues().running      // 6-12 km/h = jogging
            else -> METValues().fastRunning        // > 12 km/h = running
        }
        val caloriesFromMET = met * weightKg * durationHours
        
        // Use distance method if available, otherwise MET
        return if (distanceKm > 0) caloriesFromDistance else caloriesFromMET
    }
    
    /**
     * Smooth speed by averaging last few readings
     * Reduces GPS noise spikes
     */
    fun smoothSpeed(speedReadings: List<Double>): Double {
        if (speedReadings.isEmpty()) return 0.0
        return speedReadings.average()
    }
    
    /**
     * Calculate stride length from calibration walk
     * User walks known distance, app counts steps
     */
    fun calculateCalibratedStride(distanceMeters: Double, stepCount: Int): Double {
        return if (stepCount > 0) distanceMeters / stepCount else 0.75 // fallback to default
    }
    
    /**
     * Estimate distance from steps using stride length
     * Useful when GPS is unavailable
     */
    fun estimateDistanceFromSteps(stepCount: Int, strideLength: Double): Double {
        return (stepCount * strideLength) / 1000.0 // convert to km
    }
    
    /**
     * Hybrid distance calculator
     * Use GPS when accurate, fallback to steps when GPS is poor
     */
    fun calculateHybridDistance(
        gpsDistanceKm: Double,
        stepDistanceKm: Double,
        hasAccurateGPS: Boolean
    ): Double {
        return if (hasAccurateGPS && gpsDistanceKm > 0) {
            gpsDistanceKm
        } else {
            stepDistanceKm
        }
    }
    
    /**
     * Calculate pace (minutes per km)
     */
    fun calculatePace(speedKmh: Double): String {
        if (speedKmh <= 0) return "∞"
        val paceMinutes = 60.0 / speedKmh
        val minutes = paceMinutes.toInt()
        val seconds = ((paceMinutes - minutes) * 60).toInt()
        return String.format("%d:%02d /km", minutes, seconds)
    }
    
    /**
     * Calculate BMI-based stride coefficient (optional advanced method)
     */
    fun calculateStrideFromHeightAndGender(heightCm: Int, isMale: Boolean): Double {
        val coefficient = if (isMale) 0.415 else 0.413
        return (heightCm / 100.0) * coefficient
    }
    
    /**
     * Filter GPS points to remove noise and spikes
     * Returns cleaned GPS points
     */
    fun filterGPSPoints(points: List<GPSPoint>, accuracyThreshold: Float = GPS_ACCURACY_THRESHOLD): List<GPSPoint> {
        return points.filter { it.accuracy <= accuracyThreshold }
    }
    
    /**
     * Smooth route by averaging nearby points (basic smoothing)
     * Reduces zig-zag effect
     */
    fun smoothRoute(points: List<GPSPoint>, windowSize: Int = 3): List<GPSPoint> {
        if (points.size < windowSize) return points
        
        val smoothed = mutableListOf<GPSPoint>()
        
        for (i in points.indices) {
            val window = when {
                i < windowSize / 2 -> points.subList(0, i + windowSize / 2 + 1)
                i > points.size - windowSize / 2 - 1 -> points.subList(i - windowSize / 2, points.size)
                else -> points.subList(i - windowSize / 2, i + windowSize / 2 + 1)
            }
            
            val avgLat = window.map { it.latitude }.average()
            val avgLon = window.map { it.longitude }.average()
            val avgAccuracy = window.map { it.accuracy.toDouble() }.average().toFloat()
            
            smoothed.add(points[i].copy(latitude = avgLat, longitude = avgLon, accuracy = avgAccuracy))
        }
        
        return smoothed
    }
}
