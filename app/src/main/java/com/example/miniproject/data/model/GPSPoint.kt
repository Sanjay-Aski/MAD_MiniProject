package com.example.miniproject.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.osmdroid.util.GeoPoint

@Entity(tableName = "gps_points")
data class GPSPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val runId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}
