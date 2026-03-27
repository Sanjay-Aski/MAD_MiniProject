package com.example.miniproject.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.miniproject.R
import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.service.DeviceMotion
import com.example.miniproject.service.MotionSensorService
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.Polyline

class MapFragment : Fragment() {
    companion object {
        private const val TAG = "MapFragment"
        private const val FOLLOW_ZOOM = 18.0
    }

    private var mapView: MapView? = null
    private val gpsPoints = mutableListOf<GPSPoint>()
    private var currentLocationPoint: GPSPoint? = null
    private var pendingGpsPath: List<GPSPoint>? = null
    private val pendingPoints = mutableListOf<GPSPoint>()
    private var routePolyline: Polyline? = null
    private var markerOverlay: ItemizedIconOverlay<OverlayItem>? = null
    private var motionSensorService: MotionSensorService? = null
    private var isMapReady = false

    private var compassView: TextView? = null
    private var tiltView: TextView? = null
    private var motionView: TextView? = null
    private var directionView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            initializeOSMDroid()
            inflater.inflate(R.layout.fragment_map, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating layout: ${e.message}", e)
            Toast.makeText(context, "Error loading map", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            motionSensorService = MotionSensorService(requireContext())
            compassView = view.findViewById(R.id.tv_compass)
            tiltView = view.findViewById(R.id.tv_tilt)
            motionView = view.findViewById(R.id.tv_motion)
            directionView = view.findViewById(R.id.tv_direction)

            setupMap(view)
            setupMotionTracking()
            isMapReady = true

            pendingGpsPath?.let {
                updateGPSPath(it)
                pendingGpsPath = null
            }

            if (pendingPoints.isNotEmpty()) {
                val queuedPoints = pendingPoints.toList()
                pendingPoints.clear()
                queuedPoints.forEach { addPoint(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up map fragment: ${e.message}", e)
            Toast.makeText(context, "Map init error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeOSMDroid() {
        Configuration.getInstance().apply {
            osmdroidBasePath = requireContext().cacheDir
            osmdroidTileCache = requireContext().cacheDir
        }
    }

    private fun setupMap(view: View) {
        mapView = view.findViewById(R.id.map)
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            isHorizontalMapRepetitionEnabled = true
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            
            // Map starts centered - will be updated with first GPS point
            controller.setZoom(FOLLOW_ZOOM)
            controller.setCenter(GeoPoint(40.7128, -74.0060))
            
            Log.d(TAG, "Map initialized with default center (NYC). Will update with first GPS point.")

            // Create route polyline with distinct styling
            routePolyline = Polyline(this).apply {
                // Blue color for route
                outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.accent_blue)
                outlinePaint.strokeWidth = 8f
                outlinePaint.alpha = 200
                isGeodesic = true
            }
            overlays.add(routePolyline)
            
            Log.d(TAG, "Route polyline created and added to map overlays")
        }
    }

    private fun setupMotionTracking() {
        motionSensorService?.let { service ->
            service.deviceMotion.observe(viewLifecycleOwner) { motion ->
                updateCompassDisplay(motion.azimuth)
                updateTiltDisplay(motion.pitch, motion.roll)
                updateMotionDisplay(motion)
                updateDirectionOnMap(motion.azimuth)
            }
            service.startTracking()
        }
    }

    fun updateGPSPath(points: List<GPSPoint>) {
        if (!isMapReady) {
            pendingGpsPath = points
            return
        }
        try {
            gpsPoints.clear()
            gpsPoints.addAll(points)
            currentLocationPoint = points.lastOrNull()
            redrawRouteAndMarkers()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating GPS path: ${e.message}", e)
        }
    }

    fun addPoint(point: GPSPoint) {
        if (!isMapReady) {
            Log.w(TAG, "Map not ready, queuing point: (${point.latitude}, ${point.longitude})")
            pendingPoints.add(point)
            return
        }
        try {
            gpsPoints.add(point)
            currentLocationPoint = point
            appendPointToPolyline(point)
            redrawMarkers()
            centerMapOnLocation(point)
            mapView?.invalidate()
            
            Log.d(TAG, "Point added: (${point.latitude}, ${point.longitude}) - Total points: ${gpsPoints.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error appending GPS point: ${e.message}", e)
        }
    }

    fun updateCurrentLocation(point: GPSPoint) {
        if (!isMapReady) {
            Log.w(TAG, "Map not ready, ignoring location update: (${point.latitude}, ${point.longitude})")
            return
        }
        
        // If no points yet, add as first point
        if (gpsPoints.isEmpty()) {
            Log.d(TAG, "First location received: (${point.latitude}, ${point.longitude}), adding to map")
            addPoint(point)
            return
        }

        // Keep route points immutable and update only live marker/location.
        val oldPoint = currentLocationPoint ?: gpsPoints.last()
        currentLocationPoint = point
        redrawMarkers()
        
        // Make map follow current location
        centerMapOnLocation(point)
        mapView?.invalidate()
        
        Log.d(TAG, "Current location updated: (${oldPoint.latitude}, ${oldPoint.longitude}) → (${point.latitude}, ${point.longitude})")
    }

    private fun redrawRouteAndMarkers() {
        val map = mapView ?: return
        val polyline = routePolyline ?: return

        val routePoints = gpsPoints.map { GeoPoint(it.latitude, it.longitude) }
        polyline.setPoints(routePoints)

        redrawMarkers()

        if (routePoints.isNotEmpty()) {
            // Center on current (last) location
            val lastPoint = routePoints.last()
            map.controller.animateTo(lastPoint)
            map.controller.setZoom(FOLLOW_ZOOM)
            
            Log.d(TAG, "Route redrawn with ${routePoints.size} points, centered on current location: ${lastPoint.latitude}, ${lastPoint.longitude}")
        }
        map.invalidate()
    }

    private fun appendPointToPolyline(point: GPSPoint) {
        val polyline = routePolyline ?: return
        val existing = polyline.actualPoints.toMutableList()
        existing.add(GeoPoint(point.latitude, point.longitude))
        polyline.setPoints(existing)
    }

    private fun redrawMarkers() {
        val map = mapView ?: return
        markerOverlay?.let { map.overlays.remove(it) }

        if (gpsPoints.isEmpty()) return

        val items = mutableListOf<OverlayItem>()
        val start = gpsPoints.first()
        val current = currentLocationPoint ?: gpsPoints.last()

        // Start marker (green pin)
        items.add(OverlayItem(
            "START",
            "Run Started Here",
            GeoPoint(start.latitude, start.longitude)
        ))
        
        // Current location marker (bright cyan/yellow - most visible)
        items.add(OverlayItem(
            "📍 CURRENT",
            "Current Location - Lat: ${String.format("%.4f", current.latitude)}, Lon: ${String.format("%.4f", current.longitude)}",
            GeoPoint(current.latitude, current.longitude)
        ))
        
        Log.d(TAG, "Drawing markers: Start(${start.latitude}, ${start.longitude}) → Current(${current.latitude}, ${current.longitude})")

        markerOverlay = ItemizedIconOverlay(items, object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
            override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                Toast.makeText(context, "${item.title}: ${item.snippet}", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                return false
            }
        }, requireContext())
        map.overlays.add(markerOverlay)
        Log.d(TAG, "Markers redrawn: ${items.size} markers added")
    }

    fun clearPath() {
        gpsPoints.clear()
        currentLocationPoint = null
        routePolyline?.setPoints(emptyList())
        markerOverlay?.let { mapView?.overlays?.remove(it) }
        markerOverlay = null
        mapView?.invalidate()
    }

    private fun centerMapOnLocation(point: GPSPoint) {
        try {
            val geoPoint = GeoPoint(point.latitude, point.longitude)
            mapView?.controller?.setCenter(geoPoint)
            mapView?.controller?.setZoom(FOLLOW_ZOOM)
            Log.d(TAG, "Map centered on: (${point.latitude}, ${point.longitude}) at zoom ${FOLLOW_ZOOM}")
        } catch (e: Exception) {
            Log.e(TAG, "Error centering map: ${e.message}", e)
        }
    }

    private fun updateCompassDisplay(azimuth: Float) {
        compassView?.text = buildString {
            append("COMPASS\n")
            append(motionSensorService?.getCompassDirection(azimuth) ?: "-")
            append("\nHeading: ${String.format("%.1f", azimuth)}°")
        }
    }

    private fun updateTiltDisplay(pitch: Float, roll: Float) {
        tiltView?.text = buildString {
            append("DEVICE TILT\n")
            append(motionSensorService?.getDeviceOrientation(pitch, roll) ?: "-")
            append("\nPitch: ${String.format("%.1f", pitch)}°")
            append("\nRoll: ${String.format("%.1f", roll)}°")
        }
    }

    private fun updateMotionDisplay(motion: DeviceMotion) {
        motionView?.text = buildString {
            append("MOTION\n")
            append(motionSensorService?.getMotionIntensity(motion.magnitude) ?: "-")
            append("\nAccel: ${String.format("%.2f", motion.totalAcceleration)}")
            append("\nX:${String.format("%.1f", motion.accelerationX)} Y:${String.format("%.1f", motion.accelerationY)}")
        }
    }

    private fun updateDirectionOnMap(azimuth: Float) {
        val direction = when {
            azimuth < 45 || azimuth >= 315 -> "NORTH"
            azimuth < 135 -> "EAST"
            azimuth < 225 -> "SOUTH"
            else -> "WEST"
        }

        directionView?.text = buildString {
            append("ROUTE\n")
            append("Direction: $direction")
            append("\nAzimuth: ${String.format("%.1f", azimuth)}°")
            append("\nPoints: ${gpsPoints.size}")
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        motionSensorService?.stopTracking()
        mapView?.onDetach()
        mapView = null
        motionSensorService = null
        routePolyline = null
        markerOverlay = null
        super.onDestroyView()
    }
}
