package com.example.miniproject.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.miniproject.data.model.GPSPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationService(context: Context) {
    companion object {
        private const val TAG = "LocationService"
        private const val MAX_ACCURACY_METERS = 20f
        private const val MAX_POINT_AGE_MS = 5000L
        private const val MIN_DISTANCE_DELTA_METERS = 1.5f
        private const val MAX_RUNNING_SPEED_MPS = 10.0f
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val context = context

    private val _gpsPoints = MutableLiveData<List<GPSPoint>>(emptyList())
    val gpsPoints: LiveData<List<GPSPoint>> = _gpsPoints

    private val _currentLocation = MutableLiveData<Location>()
    val currentLocation: LiveData<Location> = _currentLocation

    private val _totalDistance = MutableLiveData<Double>(0.0)
    val totalDistance: LiveData<Double> = _totalDistance

    private val _avgSpeed = MutableLiveData<Double>(0.0)
    val avgSpeed: LiveData<Double> = _avgSpeed

    private val _maxSpeed = MutableLiveData<Double>(0.0)
    val maxSpeed: LiveData<Double> = _maxSpeed

    private val _currentSpeed = MutableLiveData<Double>(0.0)
    val currentSpeed: LiveData<Double> = _currentSpeed

    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private var totalDistanceMeters = 0.0
    private var maxSpeedKmh = 0.0
    private var startTimeMillis = 0L
    private var currentRunId = ""
    private val pointsList = mutableListOf<GPSPoint>()
    private val speedsList = mutableListOf<Double>()  // Track all speeds for averaging
    private var isTrackingActive = false
    private var acceptedPointsCount = 0
    private var rejectedPointsCount = 0

    fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasPermission = fineLocationGranted || coarseLocationGranted
        Log.d(
            TAG,
            "Permission check - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted, Result: $hasPermission"
        )
        return hasPermission
    }

    fun startLocationUpdates(runId: String, resetSession: Boolean = true) {
        Log.d(TAG, "[startLocationUpdates] CALLED - runId=$runId, resetSession=$resetSession, isTrackingActive was=$isTrackingActive")
        if (!hasLocationPermission()) {
            Log.e(TAG, "[startLocationUpdates] Cannot start: location permission not granted")
            return
        }

        currentRunId = runId
        if (resetSession) {
            Log.d(TAG, "[startLocationUpdates] Resetting session - clearing metrics")
            pointsList.clear()
            speedsList.clear()  // Clear speeds list
            totalDistanceMeters = 0.0
            maxSpeedKmh = 0.0
            lastLocation = null
            startTimeMillis = System.currentTimeMillis()
            acceptedPointsCount = 0
            rejectedPointsCount = 0
            _gpsPoints.value = emptyList()
            _totalDistance.value = 0.0
            _avgSpeed.value = 0.0
            _maxSpeed.value = 0.0
            _currentSpeed.value = 0.0
        } else if (startTimeMillis <= 0L) {
            startTimeMillis = System.currentTimeMillis()
        }
        isTrackingActive = true

        Log.d(TAG, "[startLocationUpdates] Starting location updates - startTime=$startTimeMillis, metrics initialized")

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).apply {
            setMinUpdateDistanceMeters(2f)
            setMaxUpdateDelayMillis(3000)
        }.build()

        locationCallback = object : LocationCallback() {
            private var lastCallTime = System.currentTimeMillis()
            override fun onLocationResult(locationResult: LocationResult) {
                val now = System.currentTimeMillis()
                val timeSinceLastCall = (now - lastCallTime) / 1000.0
                lastCallTime = now
                
                Log.d(TAG, "[onLocationResult] CALLED - timeSinceLastCall=${String.format("%.1f", timeSinceLastCall)}s, isTrackingActive=$isTrackingActive, numLocations=${locationResult.locations.size}")
                
                if (!isTrackingActive) {
                    Log.d(TAG, "[onLocationResult] isTrackingActive=false, returning")
                    return
                }

                for (location in locationResult.locations) {
                    Log.d(TAG, "[onLocationResult] Processing location - lat=${String.format("%.6f", location.latitude)} lng=${String.format("%.6f", location.longitude)} speed=${location.speed}m/s")
                    processLocationUpdate(location)
                }
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Location updates requested with HIGH_ACCURACY")
            } else {
                Log.w(TAG, "Location permission check failed in requestLocationUpdates")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location updates: ${e.message}", e)
        }
    }

    private fun processLocationUpdate(location: Location) {
        Log.d(TAG, "[processLocationUpdate] CALLED - isTrackingActive=$isTrackingActive, locSpeed=${location.speed}m/s")
        if (!isLocationUsable(location)) {
            rejectedPointsCount++
            Log.d(TAG, "[processLocationUpdate] Location rejected - not usable")
            return
        }

        _currentLocation.value = location
        Log.d(TAG, "[processLocationUpdate] Location accepted - accuracy=${location.accuracy}m")

        if (location.accuracy <= MAX_ACCURACY_METERS) {
            val gpsPoint = GPSPoint(
                runId = currentRunId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = location.time,
                speed = location.speed,
                altitude = location.altitude,
                accuracy = location.accuracy
            )
            pointsList.add(gpsPoint)
            _gpsPoints.value = pointsList.toList()
            acceptedPointsCount++

            if (lastLocation != null) {
                val distance = lastLocation!!.distanceTo(location)
                val dtSec = ((location.time - (lastLocation?.time ?: location.time)).coerceAtLeast(1L)) / 1000f
                val inferredSpeed = distance / dtSec

                if (distance >= MIN_DISTANCE_DELTA_METERS && inferredSpeed <= MAX_RUNNING_SPEED_MPS) {
                    totalDistanceMeters += distance
                    _totalDistance.value = totalDistanceMeters / 1000.0
                } else {
                    rejectedPointsCount++
                    Log.w(
                        TAG,
                        "Rejected movement delta=${String.format("%.2f", distance)}m speed=${String.format("%.2f", inferredSpeed)}m/s"
                    )
                }
            }

            lastLocation = location
        } else {
            rejectedPointsCount++
            Log.w(
                TAG,
                "Accuracy too low (${String.format("%.1f", location.accuracy)}m > ${MAX_ACCURACY_METERS}m), skipping"
            )
        }

        val speedKmh = location.speed.coerceAtLeast(0f) * 3.6
        _currentSpeed.value = speedKmh.toDouble()
        Log.d(TAG, "[processLocationUpdate] currentSpeed set to ${String.format("%.2f", speedKmh)} km/h")

        // Add speed to history for averaging
        speedsList.add(speedKmh.toDouble())
        
        // Update max speed - use maximum of all recorded speeds
        if (speedKmh > maxSpeedKmh) {
            maxSpeedKmh = speedKmh.toDouble()
            _maxSpeed.value = maxSpeedKmh
            Log.d(TAG, "[processLocationUpdate] maxSpeed updated to ${String.format("%.2f", maxSpeedKmh)} km/h")
        }

        // Calculate average speed from all recorded speeds
        if (speedsList.isNotEmpty()) {
            val avgSpeedKmh = speedsList.average()
            _avgSpeed.value = avgSpeedKmh.coerceAtLeast(0.0)
            Log.d(
                TAG,
                "Speed current=${String.format("%.2f", speedKmh)}km/h avg=${String.format("%.2f", avgSpeedKmh)}km/h max=${String.format("%.2f", maxSpeedKmh)}km/h distance=${String.format("%.2f", totalDistanceMeters/1000.0)}km accepted=$acceptedPointsCount rejected=$rejectedPointsCount"
            )
        } else {
            Log.d(TAG, "[processLocationUpdate] No speeds recorded yet")
        }
    }

    private fun isLocationUsable(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        if (ageMs > MAX_POINT_AGE_MS) {
            Log.w(TAG, "Stale location skipped age=${ageMs}ms")
            return false
        }

        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            Log.w(TAG, "Invalid coordinates skipped")
            return false
        }

        if (lastLocation != null) {
            val deltaTimeMs = location.time - (lastLocation?.time ?: location.time)
            if (deltaTimeMs <= 0L) {
                Log.w(TAG, "Non-increasing timestamp skipped")
                return false
            }
        }

        return true
    }

    fun stopLocationUpdates() {
        try {
            isTrackingActive = false
            if (::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}", e)
        }
    }

    fun pauseTracking() {
        isTrackingActive = false
        Log.d(TAG, "[pauseTracking] CALLED - tracking paused")
    }

    fun resumeTracking() {
        isTrackingActive = true
        Log.d(TAG, "[resumeTracking] CALLED - tracking resumed")
    }

    fun restoreSnapshot(
        runId: String,
        distanceKm: Double,
        avgSpeedKmh: Double,
        maxSpeedKmhSnapshot: Double,
        gpsPoints: List<GPSPoint>
    ) {
        currentRunId = runId
        totalDistanceMeters = distanceKm.coerceAtLeast(0.0) * 1000.0
        maxSpeedKmh = maxSpeedKmhSnapshot.coerceAtLeast(0.0)

        pointsList.clear()
        speedsList.clear()  // Clear speeds list when restoring
        pointsList.addAll(gpsPoints.sortedBy { it.timestamp })
        
        // Reconstruct speeds list from GPS points
        for (point in pointsList) {
            speedsList.add(point.speed.toDouble() * 3.6)  // Convert m/s to km/h
        }

        if (pointsList.isNotEmpty()) {
            val lastPoint = pointsList.last()
            lastLocation = Location("restored").apply {
                latitude = lastPoint.latitude
                longitude = lastPoint.longitude
                time = lastPoint.timestamp
                accuracy = lastPoint.accuracy
                speed = lastPoint.speed
                altitude = lastPoint.altitude
            }
        }

        _gpsPoints.value = pointsList.toList()
        _totalDistance.value = totalDistanceMeters / 1000.0
        _avgSpeed.value = avgSpeedKmh.coerceAtLeast(0.0)
        _maxSpeed.value = maxSpeedKmh

        Log.d(TAG, "Location snapshot restored for run=$runId points=${pointsList.size} distance=${_totalDistance.value}")
    }

    fun getGPSPointsForRun(): List<GPSPoint> = pointsList.toList()

    fun getTotalDistance(): Double {
        val dist = _totalDistance.value ?: 0.0
        if (dist == 0.0) Log.d(TAG, "[getTotalDistance] ZERO - isTrackingActive=$isTrackingActive")
        return dist
    }
    fun getAvgSpeed(): Double {
        val speed = _avgSpeed.value ?: 0.0
        if (speed == 0.0) Log.d(TAG, "[getAvgSpeed] ZERO - isTrackingActive=$isTrackingActive")
        return speed
    }
    fun getMaxSpeed(): Double {
        val speed = _maxSpeed.value ?: 0.0
        if (speed == 0.0) Log.d(TAG, "[getMaxSpeed] ZERO - isTrackingActive=$isTrackingActive")
        return speed
    }
    fun getCurrentSpeed(): Double {
        val speed = _currentSpeed.value ?: 0.0
        if (speed == 0.0) Log.d(TAG, "[getCurrentSpeed] ZERO - isTrackingActive=$isTrackingActive")
        return speed
    }
}
