package com.example.miniproject.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.GPSPoint
import org.osmdroid.util.GeoPoint

/**
 * MapViewModel - Manages map state and GPS path visualization
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MapViewModel"
    }

    private val localRunRepository = LocalRunRepository(application.applicationContext)

    // GPS path points
    private val _gpsPath = MutableLiveData<List<GeoPoint>>(emptyList())
    val gpsPath: LiveData<List<GeoPoint>> = _gpsPath

    // Current location
    private val _currentLocation = MutableLiveData<GeoPoint?>(null)
    val currentLocation: LiveData<GeoPoint?> = _currentLocation

    // Start and end markers
    private val _startPoint = MutableLiveData<GeoPoint?>(null)
    val startPoint: LiveData<GeoPoint?> = _startPoint

    private val _endPoint = MutableLiveData<GeoPoint?>(null)
    val endPoint: LiveData<GeoPoint?> = _endPoint

    // Map center
    private val _mapCenter = MutableLiveData<GeoPoint?>(null)
    val mapCenter: LiveData<GeoPoint?> = _mapCenter

    // Map zoom level
    private val _zoomLevel = MutableLiveData<Double>(18.0)
    val zoomLevel: LiveData<Double> = _zoomLevel

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Raw GPS points for storage
    private var rawGpsPoints = listOf<GPSPoint>()

    /**
     * Update GPS path with new points
     */
    fun updateGPSPath(points: List<GPSPoint>) {
        try {
            rawGpsPoints = points
            val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
            _gpsPath.value = geoPoints

            if (geoPoints.isNotEmpty()) {
                _startPoint.value = geoPoints.first()
                _endPoint.value = geoPoints.last()
                
                // Center on first point initially
                if (_mapCenter.value == null) {
                    _mapCenter.value = geoPoints.first()
                }
            }

            Log.d(TAG, "GPS path updated with ${points.size} points")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating GPS path: ${e.message}", e)
        }
    }

    /**
     * Update current location marker
     */
    fun updateCurrentLocation(latitude: Double, longitude: Double) {
        try {
            _currentLocation.value = GeoPoint(latitude, longitude)
            
            // Auto-center map on current location for live tracking
            if (_mapCenter.value == null || isNearbyPoint(_mapCenter.value!!, latitude, longitude)) {
                _mapCenter.value = GeoPoint(latitude, longitude)
            }
            
            Log.d(TAG, "Current location updated: $latitude, $longitude")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating current location: ${e.message}", e)
        }
    }

    /**
     * Center map on a point
     */
    fun centerMapOn(latitude: Double, longitude: Double, zoom: Double = 18.0) {
        _mapCenter.value = GeoPoint(latitude, longitude)
        _zoomLevel.value = zoom
    }

    /**
     * Center map on current location
     */
    fun centerOnCurrentLocation() {
        _currentLocation.value?.let {
            centerMapOn(it.latitude, it.longitude, 18.0)
        }
    }

    /**
     * Center map on entire path
     */
    fun centerOnPath() {
        val path = _gpsPath.value ?: return
        if (path.isEmpty()) return

        // Calculate center point
        var minLat = path[0].latitude
        var maxLat = path[0].latitude
        var minLon = path[0].longitude
        var maxLon = path[0].longitude

        for (point in path) {
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }

        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2
        centerMapOn(centerLat, centerLon, 14.0) // Zoom out to see full path
    }

    /**
     * Check if point is nearby (for auto-centering sensitivity)
     */
    private fun isNearbyPoint(point: GeoPoint, lat: Double, lon: Double): Boolean {
        val distance = calculateDistance(point.latitude, point.longitude, lat, lon)
        return distance > 50 // Center if new location is more than 50 meters away
    }

    /**
     * Calculate distance between two points in meters (Haversine formula)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * Load historical run path from local app database
     */
    suspend fun loadRunPath(runId: String) {
        try {
            _isLoading.value = true
            val points = localRunRepository.getRunRoutePoints(runId)
            updateGPSPath(points)
            centerOnPath()
            _isLoading.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error loading run path: ${e.message}", e)
            _isLoading.value = false
        }
    }

    /**
     * Get distance covered in meters
     */
    fun getTotalDistanceMeters(): Double {
        val path = _gpsPath.value ?: return 0.0
        if (path.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 0 until path.size - 1) {
            totalDistance += calculateDistance(
                path[i].latitude, path[i].longitude,
                path[i + 1].latitude, path[i + 1].longitude
            )
        }
        return totalDistance
    }

    /**
     * Get path statistics
     */
    data class PathStats(
        val totalDistance: Double,
        val pointCount: Int,
        val startPoint: GeoPoint?,
        val endPoint: GeoPoint?
    )

    fun getPathStatistics(): PathStats {
        return PathStats(
            totalDistance = getTotalDistanceMeters(),
            pointCount = _gpsPath.value?.size ?: 0,
            startPoint = _startPoint.value,
            endPoint = _endPoint.value
        )
    }

    /**
     * Clear map data
     */
    fun clearMap() {
        _gpsPath.value = emptyList()
        _currentLocation.value = null
        _startPoint.value = null
        _endPoint.value = null
        _mapCenter.value = null
        rawGpsPoints = emptyList()
    }
}
